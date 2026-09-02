package clients

import javax.inject._
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import play.api.Configuration
import play.api.libs.ws._
import play.api.libs.json._
// import models.User            // TODO: uncomment once models.User exists

// GUIDE: Part 2, Step 4  --  the CLIENT side (the caller)
//
// Checklist for this file (GUIDE Part 6):
//   [ ] baseUrl read from Configuration, not hardcoded
//   [ ] .withRequestTimeout(...) on EVERY call
//   [ ] resp.status handled explicitly: 200 / 404 / everything else
//   [ ] X-Request-Id correlation header propagated
@Singleton
class UserClient @Inject()(ws: WSClient, config: Configuration)
                          (implicit ec: ExecutionContext) {

  // TODO: private val baseUrl = config.get[String]("services.user.baseUrl")
  // TODO: private val timeout = config.get[FiniteDuration]("services.user.timeout")

  // TODO: def getUser(id: String): Future[Option[User]] = ...

}
