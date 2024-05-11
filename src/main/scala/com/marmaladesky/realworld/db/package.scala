package com.marmaladesky.realworld

import cats.data.OptionT
import cats.effect.Async
import slick.dbio.{DBIOAction, Effect, NoStream}
import slick.jdbc.JdbcBackend.Database

import java.time.{Instant, OffsetDateTime, ZoneOffset}
import scala.concurrent.Future

package object db {

  private[db] val ZeroTime = OffsetDateTime.ofInstant(Instant.EPOCH, ZoneOffset.UTC)

  private[db] def lift[F[_]: Async, T](f: => Future[T]): F[T] = {
    Async[F].fromFuture { Async[F].delay { f } }
  }

  def liftQ[F[_], R, E <: Effect](q: DBIOAction[R, NoStream, E])(implicit async: Async[F], db: Database): F[R] = {
    lift[F, R] { db.run(q) }
  }

  def liftQ[F[_], R, E <: Effect](
      q: DBIOAction[Option[R], NoStream, E]
  )(implicit async: Async[F], db: Database): OptionT[F, R] = {
    OptionT { lift[F, Option[R]] { db.run(q) } }
  }

}
