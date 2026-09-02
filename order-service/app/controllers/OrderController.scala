package controllers

import javax.inject._
import scala.concurrent.ExecutionContext
import play.api.mvc._
import play.api.libs.json._
import clients.{UserClient, InventoryClient}

// GUIDE: Part 2, Step 5
// NOTE: Action.async (not Action) because the body returns a Future[Result].
@Singleton
class OrderController @Inject()(cc: ControllerComponents,
                                userClient: UserClient,
                                inventoryClient: InventoryClient)
                               (implicit ec: ExecutionContext)
  extends AbstractController(cc) {

  // TODO: def createOrder(userId: String) = Action.async { ... }
  //
  //   .map    -> translate the downstream answer into YOUR response
  //   .recover-> TimeoutException      => ServiceUnavailable (503)
  //              any other Exception   => BadGateway        (502)
  //
  //   Never let a downstream failure surface as a raw 500 stack trace.

  // TODO (Part 2, Step 6): a second endpoint that calls BOTH downstreams.
  //   Start both Futures as vals BEFORE the for-comprehension, or you have
  //   accidentally made them sequential and doubled your latency.

}
