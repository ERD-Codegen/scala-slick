package com.marmaladesky.realworld.utils

import cats.effect.kernel.Concurrent
import cats.implicits._
import com.github.slugify.Slugify
import com.marmaladesky.realworld.ReadableError

import scala.util.Random

class SlugGenerator[F[_]: Concurrent] {

  private val slg = {
    Slugify.builder
      .lowerCase(true)
      .underscoreSeparator(true)
      .build()
  }

  private val MaxRandomSuffix = 255

  private def makeUnique(slug: String): String = {
    s"${slug}_${Random.between(2, MaxRandomSuffix)}"
  }

  private def recursiveGen(
      title: String,
      verifyUnique: String => F[Boolean],
      maxAttempts: Int = 5,
      isFirst: Boolean = true
  ): F[Either[Throwable, String]] = {
    if (maxAttempts == 0) {
      return Concurrent[F].pure { ReadableError(s"Failed to generate unique slug from '$title'").asLeft }
    }

    val slug = if (isFirst) {
      slg.slugify(title)
    } else {
      makeUnique { slg.slugify(title) }
    }

    verifyUnique(slug)
      .flatMap { isUnique =>
        if (isUnique) {
          Concurrent[F].pure { slug.asRight }
        } else {
          recursiveGen(title, verifyUnique, maxAttempts - 1, isFirst = false)
        }
      }
  }

  /** Generate slug by title
    * @param verifyUnique
    *   If the generated title doesn't unique in your context, you can use this parameter to request repeat generation
    *   with the random value suffixed
    */
  def generateSlug(
      title: String,
      verifyUnique: String => F[Boolean] = _ => true.pure[F]
  ): F[Either[Throwable, String]] = {
    recursiveGen(title, verifyUnique)
  }

}
