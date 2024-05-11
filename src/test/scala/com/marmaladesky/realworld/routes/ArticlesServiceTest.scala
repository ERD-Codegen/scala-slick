package com.marmaladesky.realworld.routes

import cats.data.OptionT
import cats.effect.IO
import cats.effect.unsafe.IORuntime
import cats.implicits._
import com.marmaladesky.realworld.db.gen.Tables.{ArticlesRow, UsersRow}
import com.marmaladesky.realworld.db.{ArticlesRepo, FollowsRepo, UsersRepo}
import com.marmaladesky.realworld.services.ArticlesService
import com.marmaladesky.realworld.utils.SlugGenerator
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._

import java.time.OffsetDateTime

class ArticlesServiceTest extends munit.FunSuite {

  private implicit val ioGlobal: IORuntime = IORuntime.global

  test("should return created article with expected values from repos") {

    val articlesRepo = mock(classOf[ArticlesRepo[IO]])
    when(articlesRepo.createArticle(any[String], any[String], any[String], any[String], any[Long], any[Set[String]]))
      .thenReturn(IO.pure {
        (
          ArticlesRow(1, "expected_slug", "", "", "", 1L, OffsetDateTime.now(), OffsetDateTime.now()),
          Seq.empty
        )
      })
    when(articlesRepo.isFavorited(any[Long], any[Long])).thenReturn { IO.pure(false) }
    when(articlesRepo.totalFavorites(any[Long])).thenReturn { IO.pure(0) }

    val usersRepo = mock(classOf[UsersRepo[IO]])
    when(usersRepo.getUser(any[Long])).thenReturn {
      OptionT.some[IO](UsersRow(1L, "", Array.empty, Array.empty, "mr_author", None, "https://expected_url/".some))
    }

    val followsRepo = mock(classOf[FollowsRepo[IO]])
    when(followsRepo.isFollowed(any[Long], any[Long])).thenReturn {
      IO.pure(false)
    }

    val slugGen = mock(classOf[SlugGenerator[IO]])
    when(slugGen.generateSlug(any[String], any[String => IO[Boolean]]))
      .thenReturn { IO.pure("expected_slug".asRight) }

    val service = ArticlesService.impl[IO](articlesRepo, usersRepo, followsRepo, slugGen)

    val createdArticle = service.createArticle(1, "Expected Slug", "", "", Set.empty).unsafeRunSync()

    assert(createdArticle.author.username == "mr_author")
    assert(createdArticle.author.bio.isEmpty)
    assert(createdArticle.author.image contains "https://expected_url/")
    assert(createdArticle.slug == "expected_slug")
  }

}
