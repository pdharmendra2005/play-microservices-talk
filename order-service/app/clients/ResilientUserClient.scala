package clients

import javax.inject._
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.pattern.CircuitBreaker
// import models.User            // TODO: uncomment once models.User exists

// GUIDE: Part 3, "Circuit breaker"
// Wraps UserClient. Closed -> Open -> Half-Open.
// Write a test that proves it OPENS after N failures and CLOSES again after recovery.
@Singleton
class ResilientUserClient @Inject()(underlying: UserClient, actorSystem: ActorSystem)
                                   (implicit ec: ExecutionContext) {

  // TODO: private val breaker = new CircuitBreaker(actorSystem.scheduler, maxFailures = 5,
  // TODO:                                          callTimeout = 2.seconds, resetTimeout = 30.seconds)

  // TODO: def getUser(id: String): Future[Option[User]] =
  // TODO:   breaker.withCircuitBreaker(underlying.getUser(id))

}
