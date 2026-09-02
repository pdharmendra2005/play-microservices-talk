package clients

import javax.inject._
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import play.api.Configuration
import play.api.libs.ws._

// Second downstream. Exists so you can practise the PARALLEL vs SEQUENTIAL
// fan-out from GUIDE Part 2, Step 6.
@Singleton
class InventoryClient @Inject()(ws: WSClient, config: Configuration)
                               (implicit ec: ExecutionContext) {

  // TODO: def getStock(sku: String): Future[Int] = ...

}
