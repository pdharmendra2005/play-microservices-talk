# How Microservices Talk to Each Other in Play (Scala) — A QA Engineer's Deep Dive

You are a QA engineer. So this guide is written with two goals:
1. Understand *mechanically* what happens when Service A calls Service B.
2. Know *where things break*, because that is what you will be testing.

---

## Part 0 — The one sentence that matters

**Play does not have any special "microservice magic". Two Play services talk to each other exactly the way a browser talks to a website: over HTTP, across the network.**

Everything else (gRPC, Kafka, service discovery) is a variation on "send bytes over a socket".

If you remember only one thing: *a microservice call is a network call, and network calls fail.*

---

## Part 1 — The mental model

Imagine a shopping site split into 3 services:

```
                +------------------+
   browser ---> |  order-service   |  port 9000   (the "edge" / caller)
                +------------------+
                     |        |
        HTTP GET     |        |     HTTP GET
        /users/42    |        |     /inventory/sku-99
                     v        v
        +----------------+  +-------------------+
        |  user-service  |  | inventory-service |
        |   port 9001    |  |     port 9002     |
        +----------------+  +-------------------+
```

- `order-service` is a **client** of the other two.
- `user-service` and `inventory-service` are **servers**.
- A single service is usually both at the same time.

Each one is its own Play application, its own JVM process, its own `application.conf`, its own port, deployed independently.

---

## Part 2 — Step by step: one synchronous HTTP call

We will build `order-service` calling `user-service`. Slowly.

### Step 1 — The callee (`user-service`) exposes an endpoint

`conf/routes`:

```
GET   /users/:id      controllers.UserController.getUser(id: String)
```

`app/controllers/UserController.scala`:

```scala
package controllers

import javax.inject._
import play.api.mvc._
import play.api.libs.json._

case class User(id: String, name: String, email: String)
object User {
  implicit val format: OFormat[User] = Json.format[User]   // JSON <-> case class
}

@Singleton
class UserController @Inject()(cc: ControllerComponents) extends AbstractController(cc) {

  def getUser(id: String) = Action {
    if (id == "42") Ok(Json.toJson(User("42", "Ada", "ada@example.com")))
    else            NotFound(Json.obj("error" -> "user not found"))
  }
}
```

Run it on port 9001: `sbt "run 9001"`.

Verify with curl — **this is your first QA checkpoint**:

```bash
curl -i http://localhost:9001/users/42
# HTTP/1.1 200 OK
# Content-Type: application/json
# {"id":"42","name":"Ada","email":"ada@example.com"}
```

Nothing about this is "microservice specific". It is just an HTTP API.

### Step 2 — The caller needs an HTTP client: Play WS

Add to `build.sbt`:

```scala
libraryDependencies += ws
```

`ws` = **W**eb **S**ervices client. It is Play's built-in async HTTP client (backed by AsyncHttpClient/Netty).

### Step 3 — Never hardcode the URL. Put it in config.

`conf/application.conf` of `order-service`:

```hocon
services {
  user.baseUrl      = "http://localhost:9001"
  user.baseUrl      = ${?USER_SERVICE_URL}     # env var overrides it
  user.timeout      = 2 seconds
}
```

The `${?ENV_VAR}` syntax means "override from environment if present". This is *the* mechanism that lets the same build run against localhost, staging, and prod. As a QA engineer you will use this constantly to point a service at a mock.

### Step 4 — Write a typed client class

`app/clients/UserClient.scala`:

```scala
package clients

import javax.inject._
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import play.api.Configuration
import play.api.libs.ws._
import play.api.libs.json._
import models.User

@Singleton
class UserClient @Inject()(ws: WSClient, config: Configuration)
                          (implicit ec: ExecutionContext) {

  private val baseUrl = config.get[String]("services.user.baseUrl")
  private val timeout = config.get[FiniteDuration]("services.user.timeout")

  def getUser(id: String): Future[Option[User]] = {
    ws.url(s"$baseUrl/users/$id")            // 1. build the request
      .addHttpHeaders("Accept" -> "application/json")
      .withRequestTimeout(timeout)           // 2. ALWAYS set a timeout
      .get()                                 // 3. fire it -> Future[WSResponse]
      .map { resp =>                         // 4. interpret the response
        resp.status match {
          case 200 => Some(resp.json.as[User])
          case 404 => None
          case s   => throw new RuntimeException(s"user-service returned $s: ${resp.body}")
        }
      }
  }
}
```

Read that again line by line:

- `ws.url(...)` builds a request **description**. Nothing has hit the network yet.
- `.get()` actually sends it and immediately returns a `Future[WSResponse]` — a *promise* of a response that will arrive later. The calling thread is **not blocked**.
- `.map { ... }` says "when the response eventually arrives, run this".
- `resp.status` is the raw HTTP status code. **You must handle non-200 yourself.** WS does not throw on a 404 or a 500 — it only fails the Future on transport-level errors (connection refused, DNS failure, timeout).

That last point is the #1 source of bugs I'd tell you to hunt for in code review: a service that does `.map(_.json.as[User])` with no status check will explode with a confusing JSON parse error when the downstream returns a 500 HTML error page.

### Step 5 — Use the client in a controller

```scala
@Singleton
class OrderController @Inject()(cc: ControllerComponents, userClient: UserClient)
                               (implicit ec: ExecutionContext)
  extends AbstractController(cc) {

  def createOrder(userId: String) = Action.async {   // note: Action.async
    userClient.getUser(userId).map {
      case Some(user) => Ok(Json.obj("status" -> "created", "for" -> user.name))
      case None       => BadRequest(Json.obj("error" -> "unknown user"))
    }.recover {
      case _: java.util.concurrent.TimeoutException =>
        ServiceUnavailable(Json.obj("error" -> "user-service slow"))
      case _: Exception =>
        BadGateway(Json.obj("error" -> "user-service unavailable"))
    }
  }
}
```

Key points:
- `Action.async` is used when the body returns a `Future[Result]` instead of a `Result`.
- `.recover` turns downstream failures into a *sensible* status code for **your** caller. Without it, Play returns a generic 500 and the browser has no idea what went wrong.

### Step 6 — Calling two services at once (this trips people up)

**Sequential (slow — 200ms + 200ms = 400ms):**

```scala
for {
  user <- userClient.getUser(id)          // waits
  item <- inventoryClient.get(sku)        // then waits again
} yield (user, item)
```

**Parallel (fast — max(200ms, 200ms) = 200ms):**

```scala
val fUser = userClient.getUser(id)        // both started BEFORE the for-comprehension
val fItem = inventoryClient.get(sku)
for {
  user <- fUser
  item <- fItem
} yield (user, item)
```

The difference: a `for`-comprehension chains Futures, so the second only *starts* after the first completes. Starting them as `val`s first launches both immediately.

This is a real, testable performance bug. As QA: hit an endpoint that fans out to N services and check whether latency looks like a sum or a max.

---

## Part 3 — What goes wrong (your actual test charter)

| Failure | What it looks like | How to trigger it in test |
|---|---|---|
| Downstream **down** | connection refused | stop the service / point config at a dead port |
| Downstream **slow** | request timeout | mock server with a delay > timeout |
| Downstream returns **500** | bad JSON parse if unhandled | mock returning 500 + HTML body |
| Downstream returns **malformed JSON** | `JsResultException` | mock returning `{"nam":"Ada"}` (typo field) |
| **Retry storm** | one slow service takes down the fleet | slow mock + client with retries |
| **No timeout configured** | threads pile up, whole service hangs | mock that never responds |
| **Partial failure** | order created but email never sent | fail service B *after* A succeeded |
| **Contract drift** | B renamed a field, A breaks in prod | change mock's schema |

### Timeouts — the single most important config

```scala
.withRequestTimeout(2.seconds)
```

Rule: **caller timeout must be shorter than the caller's own caller's timeout.** If the browser gives up after 5s, order-service must give up on user-service well before 5s, or you waste work. A service with no timeout will eventually exhaust its connection pool and go down while looking "healthy".

### Retries — only for idempotent calls

Retrying a `GET` is safe. Retrying a `POST /payments` may charge the customer twice. This is why services use **idempotency keys**:

```scala
ws.url(s"$baseUrl/payments")
  .addHttpHeaders("Idempotency-Key" -> orderId)   // server dedupes on this
  .post(Json.toJson(payment))
```

QA test: send the same request twice with the same key → exactly one payment must exist. That is a real bug you will find.

### Circuit breaker — stop hammering a dying service

```scala
import org.apache.pekko.pattern.CircuitBreaker   // (akka.pattern.CircuitBreaker on Play 2.8)

private val breaker = new CircuitBreaker(
  scheduler    = actorSystem.scheduler,
  maxFailures  = 5,          // after 5 consecutive failures...
  callTimeout  = 2.seconds,
  resetTimeout = 30.seconds  // ...stop calling for 30s, then try one probe
)

def getUser(id: String): Future[Option[User]] =
  breaker.withCircuitBreaker(rawGetUser(id))
```

Three states: **Closed** (normal) → **Open** (fail fast, don't even try) → **Half-Open** (let one request through to test recovery). Testing that the breaker actually opens, and *closes again*, is a genuinely valuable test that most teams never write.

### Correlation IDs — how you debug across services

```scala
.addHttpHeaders("X-Request-Id" -> requestId)
```

Every service logs that ID and passes it on. Without it, tracing one user's failed order across 6 services is impossible. As QA, when you file a bug, **always include the correlation ID** — it makes you enormously more useful to the devs.

---

## Part 4 — The other communication styles

### A. Asynchronous messaging (Kafka / RabbitMQ / SQS)

HTTP is *synchronous*: A waits for B. Messaging is *fire-and-forget*: A publishes an event, and whoever cares reacts later.

```scala
// order-service publishes and moves on
producer.send(new ProducerRecord("orders", orderId, Json.stringify(event)))

// email-service consumes, whenever it feels like it
Consumer.plainSource(settings, Subscriptions.topics("orders"))
  .mapAsync(1)(record => sendEmail(parse(record.value())))
  .runWith(Sink.ignore)
```

Use it when the caller doesn't need an answer right now (send email, update analytics, rebuild a cache).

**QA implications, and they are big:**
- The system is now **eventually consistent**. After `POST /orders` returns 200, the email has *not* been sent yet. A test that immediately asserts "email exists" is flaky by design — you must poll/await.
- **Ordering** is only guaranteed within a Kafka partition.
- **At-least-once delivery** is the norm → consumers must be idempotent → test by replaying the same message twice.
- **Dead-letter queues**: what happens to a message that fails 10 times? Is anyone alerted? Usually nobody tested this.

### B. gRPC

Instead of ad-hoc JSON, you define a contract in a `.proto` file and generate typed Scala client and server code:

```protobuf
service UserService {
  rpc GetUser (GetUserRequest) returns (User) {}
}
```

Benefits: compile-time-checked contract, binary (faster/smaller), HTTP/2 streaming. Cost: not human-readable in curl; you need `grpcurl` for manual testing. Common for internal service-to-service where both sides are yours.

### C. Service discovery

In Kubernetes you don't hardcode `http://localhost:9001`; you use the DNS name `http://user-service.default.svc.cluster.local`. Same code, different config value — which is exactly why Step 3 (config, not hardcoded URLs) matters.

### D. Service mesh (Istio/Linkerd)

A sidecar proxy next to each service handles retries, timeouts, mTLS, and tracing *outside* your code. Worth knowing it exists, because "who set this timeout?" may have the answer "the mesh, not the app".

---

## Part 5 — How to actually test this (the part that pays your salary)

### Level 1 — Unit test the client against a stub server

Play gives you a real in-memory server:

```scala
import play.api.test._
import play.api.routing.sird._
import play.api.mvc._

"UserClient" should "return None on 404" in {
  Server.withRouterFromComponents() { cs => {
    case GET(p"/users/99") => cs.defaultActionBuilder(Results.NotFound)
  }} { implicit port =>
    WsTestClient.withClient { client =>
      val result = await(new UserClient(client, cfg).getUser("99"))
      result shouldBe None
    }
  }
}
```

No network, no Docker, milliseconds fast. Write one of these for **every** failure mode in the table above: 200, 404, 500, timeout, malformed JSON.

### Level 2 — Contract testing (Pact)

The killer problem in microservices: user-service renames `name` → `fullName`, its own tests pass, and order-service breaks in production.

Pact fixes this. The consumer (order-service) declares "I expect `GET /users/42` to return a body with `name`". That expectation is published as a *pact file*, and the provider (user-service) runs it against itself in **its** CI. If the provider breaks the contract, the provider's build goes red — *before* deployment.

If you introduce contract testing to a team that doesn't have it, you will prevent an entire category of outages. This is the highest-leverage thing a QA engineer can do in a microservices shop.

### Level 3 — Integration test with mocks

Use WireMock or a `docker-compose` stack, and point `USER_SERVICE_URL` at it (remember the `${?ENV_VAR}` from Step 3). Now you can script:
- 2-second delay → assert your service returns 503, not a hang.
- Return 500 → assert graceful degradation.
- Kill the container mid-test → assert the circuit breaker opens.

### Level 4 — End-to-end

Real services, real network. Slowest and flakiest — keep these few and focused on the true happy path. The classic mistake is trying to test every edge case at this level.

---

## Part 6 — Checklist you can bring to work on Monday

For every service-to-service call in your codebase, ask:

1. Is there a **timeout**? What is it? Is it shorter than the caller's timeout?
2. Are **non-2xx statuses** handled explicitly, or does the code assume 200?
3. Is there a **retry**? Is the operation **idempotent**? Is there an idempotency key?
4. Is there a **circuit breaker** or bulkhead?
5. Is the URL in **config**, overridable by env var? (If not, you can't point it at a mock.)
6. Is a **correlation ID** propagated?
7. If it's async: is the consumer **idempotent**? Is there a **DLQ**? Who is alerted?
8. Is there a **contract test**, or are you relying on hope?
9. What does the caller return to *its* caller when this fails — a clear error, or a generic 500?
10. Is there a **fallback** (cached value, degraded response) or does the whole request die?

If you can walk into a design review and ask questions 1, 3, and 8, you will immediately be the most useful person in the room.

---

## Part 7 — Try it yourself (30 minutes, highly recommended)

1. `sbt new playframework/play-scala-seed.g8` — twice, into `user-service` and `order-service`.
2. In user-service, add the `GET /users/:id` route above. Run with `sbt "run 9001"`. Curl it.
3. In order-service, add `ws` to `build.sbt`, add `UserClient` and `OrderController`. Run with `sbt "run 9000"`.
4. `curl http://localhost:9000/orders?userId=42` → works.
5. **Now kill user-service** and curl again. Watch what your error handling does. Fix it until the response is a clean 502/503 instead of a 500 stack trace.
6. Restart user-service, but add `Thread.sleep(5000)` before the response. Set your client timeout to 2 seconds. Confirm you get a timeout, not a hang.

Steps 5 and 6 are where the real learning happens. Everything in Part 3 becomes obvious once you have watched it fail with your own eyes.
