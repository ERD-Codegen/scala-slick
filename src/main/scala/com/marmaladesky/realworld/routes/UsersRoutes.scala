package com.marmaladesky.realworld.routes

import cats.data.{Kleisli, OptionT}
import cats.effect.kernel.{Async, Concurrent}
import cats.implicits._
import com.marmaladesky.realworld.services.UsersService
import com.marmaladesky.realworld.model.AuthContext
import com.marmaladesky.realworld.services.UsersService.User
import io.circe.generic.extras.{Configuration, ConfiguredJsonCodec}
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl
import org.http4s.{AuthedRoutes, HttpRoutes, Request}
import org.typelevel.ci._

import scala.util.Try

object UsersRoutes {

  private object json {
    implicit val j_Config: Configuration = Configuration.default.withSnakeCaseMemberNames.withDefaults

    @ConfiguredJsonCodec
    case class ProfileResponse(profile: UserProfile)
    @ConfiguredJsonCodec
    case class UserProfile(username: String, bio: Option[String], image: Option[String], following: Boolean)

    @ConfiguredJsonCodec
    case class LoginRequest(user: UserAuth)
    @ConfiguredJsonCodec
    case class UserAuth(email: String, password: String)

    @ConfiguredJsonCodec
    case class RegistrationRequest(user: UserRegistration)
    @ConfiguredJsonCodec
    case class UserRegistration(username: String, email: String, password: String)

    @ConfiguredJsonCodec
    case class UserAuthResponse(user: UserServer)
    @ConfiguredJsonCodec
    case class UserServer(email: String, token: String, username: String, bio: Option[String], image: Option[String])

    @ConfiguredJsonCodec
    case class UpdateUserRequest(user: UpdateUserRequestBody)
    @ConfiguredJsonCodec
    case class UpdateUserRequestBody(
        email: Option[String],
        username: Option[String],
        password: Option[String],
        bio: Option[String],
        image: Option[String]
    )

  }
  import json._

  private def writeUserModel(user: User, token: String): UserAuthResponse = {
    UserAuthResponse(
      UserServer(
        email = user.email,
        token = token,
        username = user.username,
        bio = user.bio,
        image = user.image
      )
    )
  }

  def publicRoutes[F[_]: Concurrent](userService: UsersService[F]): HttpRoutes[F] = {
    val dsl: Http4sDsl[F] = new Http4sDsl[F] {}
    import dsl._
    import io.circe.syntax._

    HttpRoutes.of[F] {

      case req @ POST -> Root / "api" / "users" / "login" =>
        for {
          json <- req.asJson
          loginReq <- Concurrent[F].fromEither { json.as[LoginRequest] }
          userOpt <- userService.login(loginReq.user.email, loginReq.user.password).value
          response <- userOpt match {
            case Some(user) =>
              for {
                token <- userService.genJwtToken(user.userId, loginReq.user.password)
                response <- Ok { writeUserModel(user, token).asJson }
              } yield response
            case None =>
              BadRequest("Username or password is incorrect")
          }
        } yield response

      case req @ POST -> Root / "api" / "users" =>
        for {
          json <- req.asJson
          registrationReq <- Concurrent[F].fromEither { json.as[RegistrationRequest] }
          user <- userService.registration(
            username = registrationReq.user.username,
            email = registrationReq.user.email,
            password = registrationReq.user.password
          )
          token <- userService.genJwtToken(user.userId, registrationReq.user.password)
          r <- Ok {
            UserAuthResponse(
              UserServer(
                email = user.email,
                token = token,
                username = user.username,
                bio = user.bio,
                image = user.image
              )
            ).asJson
          }
        } yield r

      case GET -> Root / "api" / "profiles" / username =>
        userService
          .getUser(username)
          .semiflatMap { u =>
            Ok {
              ProfileResponse(
                UserProfile(username = u.username, bio = u.bio, image = u.image, following = false)
              ).asJson
            }
          }
          .getOrElseF(NotFound())

    }
  }

  def authedRoutes[F[_]: Concurrent](userService: UsersService[F]): AuthedRoutes[Option[AuthContext], F] = {
    val dsl: Http4sDsl[F] = new Http4sDsl[F] {}
    import dsl._
    import io.circe.syntax._

    AuthedRoutes.of {

      case GET -> Root / "api" / "user" as authOpt =>
        for {
          auth <- withAuthRequired(authOpt)
          userOpt <- userService.getUser(auth.userId).value
          response <- userOpt match {
            case Some(user) => Ok { writeUserModel(user, auth.token).asJson }
            case None       => NotFound()
          }

        } yield response

      case req @ PUT -> Root / "api" / "user" as authOpt =>
        for {
          auth <- withAuthRequired(authOpt)
          json <- req.req.asJson
          updateReq <- Concurrent[F].fromEither { json.as[UpdateUserRequest] }
          userOpt <- userService
            .updateUser(
              User.UserPartialUpdate(
                userId = auth.userId,
                email = updateReq.user.email,
                username = updateReq.user.username,
                password = updateReq.user.password,
                bio = updateReq.user.bio,
                image = updateReq.user.image
              )
            )
            .value
          response <- userOpt match {
            case Some(user) => Ok { writeUserModel(user, auth.token).asJson }
            case None       => NotFound()
          }

        } yield response

      case POST -> Root / "api" / "profiles" / username / "follow" as authOpt =>
        for {
          auth <- withAuthRequired(authOpt)
          result <- userService
            .addFollow(username, auth.userId)
            .semiflatMap { x =>
              Ok {
                ProfileResponse(UserProfile(username = x.username, bio = x.bio, image = x.image, x.following)).asJson
              }
            }
            .getOrElseF { NotFound() }
        } yield result

      case DELETE -> Root / "api" / "profiles" / username / "follow" as authOpt =>
        for {
          auth <- withAuthRequired(authOpt)
          result <- userService
            .deleteFollow(username, auth.userId)
            .semiflatMap { x =>
              Ok {
                ProfileResponse(UserProfile(username = x.username, bio = x.bio, image = x.image, x.following)).asJson
              }
            }
            .getOrElseF { NotFound() }
        } yield result

    }
  }

  def authUserOpt[F[_]: Async](
      userService: UsersService[F]
  ): Kleisli[OptionT[F, *], Request[F], Option[AuthContext]] = {
    Kleisli { req =>
      val tokenOpt = req.headers
        .get(ci"Authorization")
        .map { _.head }
        .flatMap { _.value.split(" ").lift(1) }

      val x = for {
        token <- OptionT.fromOption[F] { tokenOpt }
        claim <- userService.verifyToken(token)
        userId <- OptionT.fromOption[F] { claim.subject.flatMap { s => Try(s.toLong).toOption } }
      } yield AuthContext(token, userId).some

      x.orElse(OptionT.some(None))
    }
  }

}
