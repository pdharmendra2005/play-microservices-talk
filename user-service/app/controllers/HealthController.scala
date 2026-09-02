package controllers

import javax.inject._
import play.api.mvc._
import play.api.libs.json._

@Singleton
class HealthController @Inject()(cc: ControllerComponents) extends AbstractController(cc) {

  // TODO: def health = Action { Ok(Json.obj("status" -> "UP")) }

}
