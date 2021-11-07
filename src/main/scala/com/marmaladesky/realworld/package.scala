package com.marmaladesky

import io.circe.{Encoder, Json}

package object realworld {

  implicit val j_ReadableErrorEncoder: Encoder[ReadableError] = (a: ReadableError) => {
    Json.obj("message" -> Json.fromString(a.message))
  }

}
