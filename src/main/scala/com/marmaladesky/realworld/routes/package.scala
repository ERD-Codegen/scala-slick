package com.marmaladesky.realworld

import cats.effect.kernel.Concurrent
import cats.implicits._
import com.marmaladesky.realworld.model.AuthContext
import org.http4s.Status

package object routes {

  def withAuthRequired[F[_]](authOpt: Option[AuthContext])(implicit C: Concurrent[F]): F[AuthContext] = {
    authOpt match {
      case Some(value) => value.pure[F]
      case None => C.raiseError {
        ReadableError("Missing authentication", codeHint = Status.Forbidden.code)
      }
    }
  }

}
