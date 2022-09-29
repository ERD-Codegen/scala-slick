package com.marmaladesky.realworld.routes

import cats.implicits._
import cats.data.OptionT
import cats.effect.IO
import com.marmaladesky.realworld.services.ArticlesService.{Article, Author}
import munit.CatsEffectSuite
import org.http4s.Uri
import org.http4s.circe._

import java.time.OffsetDateTime
import com.marmaladesky.realworld.model.AuthContext
import com.marmaladesky.realworld.services.ArticlesService
import org.http4s.{ContextRequest, Request}
import org.mockito.ArgumentMatchers.{eq => eql}
import org.mockito.Mockito._

class ArticleRoutesTest extends CatsEffectSuite {

  test("should return ISO 8601 date that is expected by Conduit postman collection") {
    val service = mock(classOf[ArticlesService[IO]])
    when(service.getArticle(eql[String]("article_slug"))).thenReturn {
      OptionT.some[IO](
        Article(
          articleId = 0,
          slug = "",
          title = "",
          description = "",
          body = "",
          author = Author(
            username = "username",
            bio = None,
            image = None,
            following = false
          ),
          favorited = false,
          favoritesCount = 0,
          createdAt = OffsetDateTime.parse("2022-09-29T09:42:11.457125622+00:00"),
          updatedAt = OffsetDateTime.parse("2022-09-29T09:42:11.457125622+00:00"),
          tags = Set.empty
        )
      )
    }
    val routes = ArticlesRoutes.authedRoutes[IO](service).orNotFound

    val req = Request[IO](
      uri = Uri.unsafeFromString("api/articles/article_slug")
    )

    routes
      .apply(ContextRequest[IO, Option[AuthContext]](None, req))
      .flatMap { res =>
        for {
          body <- res.json
        } yield {
          assert { res.status.isSuccess }
          assertEquals(
            body.hcursor.downField("article").downField("createdAt").as[String],
            "2022-09-29T09:42:11.457Z".asRight
          )
          assertEquals(
            body.hcursor.downField("article").downField("updatedAt").as[String],
            "2022-09-29T09:42:11.457Z".asRight
          )
        }
      }
  }

}
