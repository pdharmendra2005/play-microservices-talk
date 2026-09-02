package controllers

import org.scalatestplus.play._
import org.scalatestplus.play.guice._
import play.api.test._
import play.api.test.Helpers._

// GUIDE: Part 5, Level 1
class UserControllerSpec extends PlaySpec with GuiceOneAppPerTest with Injecting {

  "UserController GET /users/:id" should {

    // TODO: "return 200 and the user JSON for a known id" in { ... }

    // TODO: "return 404 for an unknown id" in { ... }

  }
}
