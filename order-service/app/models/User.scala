package models

import play.api.libs.json._

// The CONSUMER's own copy of the contract. It only needs the fields it uses.
// GUIDE: Part 5, Level 2 -- this is exactly what a Pact contract test pins down.

// TODO: case class User(id: String, name: String, email: String)
// TODO: object User { implicit val format: OFormat[User] = Json.format[User] }
