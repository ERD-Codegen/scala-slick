package com.marmaladesky.realworld

import cats.data.Kleisli
import cats.effect.Sync
import cats.implicits._
import com.typesafe.scalalogging.LazyLogging
import io.circe.Json
import io.circe.syntax._
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl
import org.http4s.server.ServiceErrorHandler
import org.http4s.{Http, Request, Response}

import java.util.UUID

class DefaultErrorHandler[F[_]: Sync] extends ServiceErrorHandler[F] with LazyLogging {
  private val dsl = new Http4sDsl[F] {}
  import dsl._

  private def responseWithHint(code: Int, body: Json): F[Response[F]] = {
    code match {
      case NotFound.code   => NotFound(body)
      case BadRequest.code => BadRequest(body)
      case other =>
        logger.error(s"Code '$other' cannot be used as a response hint")
        BadRequest(body)
    }
  }

  private def handle(unexpected: Throwable): F[Response[F]] = {
    unexpected match {
      case ReadableError(message, _, responseCodeHint) =>
        val responseBody = ReadableError(message).asJson
        responseWithHint(responseCodeHint, responseBody)

      case _ =>
        val uuid = UUID.randomUUID()
        val message = s"Unexpected error in request processing: $uuid"
        logger.error(message, unexpected)
        InternalServerError { ReadableError(message).asJson }
    }
  }

  def apply(service: Http[F, F]): Http[F, F] = Kleisli { req =>
    try {
      service.apply(req).handleErrorWith(handle)
    } catch {
      case unexpected: Throwable => handle(unexpected)
    }
  }

  override def apply(req: Request[F]): PartialFunction[Throwable, F[Response[F]]] = { case unexpected: Throwable =>
    handle(unexpected)
  }

}
