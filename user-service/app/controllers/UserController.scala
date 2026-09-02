package controllers

import javax.inject._
import play.api.mvc._
import play.api.libs.json._
// import models.User            // TODO: uncomment once models.User exists

// GUIDE: Part 2, Step 1  --  this is the SERVER side (the callee)
@Singleton
class UserController @Inject()(cc: ControllerComponents) extends AbstractController(cc) {

  // TODO: def getUser(id: String) = Action { ... Ok(Json.toJson(user)) / NotFound(...) }

}
