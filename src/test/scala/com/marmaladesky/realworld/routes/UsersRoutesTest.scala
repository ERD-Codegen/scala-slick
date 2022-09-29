package com.marmaladesky.realworld.routes

import cats.data.OptionT
import cats.effect._
import cats.effect.unsafe.implicits.global
import com.marmaladesky.realworld.services.UsersService
import fs2.text
import io.circe.optics.JsonPath.root
import io.circe.parser
import org.http4s._
import org.http4s.implicits._
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._

class UsersRoutesTest extends munit.FunSuite {

  private def buildRoutes(s: UsersService[IO]) = {
    UsersRoutes.publicRoutes(s).orNotFound
  }

  test("unknown route should return 404") {

    val routes = buildRoutes { mock(classOf[UsersService[IO]]) }

    val request = Request.apply[IO](method = Method.GET, uri = uri"/not_existing_routes" )

    val response = routes.apply(request).unsafeRunSync()

    assert(response.status == Status.NotFound)

  }

  test("requesting existing profile should return expected structure") {

    val routes = buildRoutes {
      val m = mock(classOf[UsersService[IO]])
      when(m.getUser(any[String]))
        .thenReturn { OptionT.some { UsersService.User(1, "username", "email", None, None) } }
      m
    }

    val request = Request.apply[IO](method = Method.GET, uri = uri"/api/profiles/username" )

    val response = routes.apply(request).unsafeRunSync()

    assert(response.status != Status.NotFound)

    val body = response.body.through(text.utf8.decode).compile.string.unsafeRunSync()

    val jsonBody = parser.parse(body).getOrElse(throw new RuntimeException("Unexpected response"))

    assert { root.profile.username.string.getOption(jsonBody) contains "username" }
    assert { root.profile.bio.`null`.getOption(jsonBody).nonEmpty }

  }

}
