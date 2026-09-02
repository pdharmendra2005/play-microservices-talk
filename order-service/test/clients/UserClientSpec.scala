package clients

import org.scalatestplus.play._
import play.api.test._
import play.api.test.Helpers._
import play.api.routing.sird._
import play.api.mvc._
import play.api.libs.json._
import play.core.server.Server

// GUIDE: Part 5, Level 1 -- unit-test the client against an in-memory stub server.
// No network, no Docker, milliseconds fast.
//
// Write ONE test per row of the failure table in GUIDE Part 3:
class UserClientSpec extends PlaySpec {

  "UserClient.getUser" should {

    // TODO: "parse the user on 200" in { ... }

    // TODO: "return None on 404" in { ... }

    // TODO: "fail loudly (not silently) on 500" in { ... }

    // TODO: "fail with a TimeoutException when the server is slow" in { ... }

    // TODO: "fail clearly when the JSON is malformed / a field was renamed" in { ... }

  }
}
