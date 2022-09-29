package com.marmaladesky.realworld.routes

import cats.effect.kernel.Concurrent
import cats.implicits._
import com.marmaladesky.realworld.model.{AuthContext, Page}
import com.marmaladesky.realworld.services.ArticlesService
import com.marmaladesky.realworld.services.ArticlesService.{Article, Comment}
import io.circe.generic.extras.{Configuration, ConfiguredJsonCodec}
import io.circe.{Encoder, Json}
import org.http4s.AuthedRoutes
import org.http4s.dsl.Http4sDsl

import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

object ArticlesRoutes {

  private object json {

    implicit val j_Config: Configuration = Configuration.default.withDefaults

    implicit val custom_offset_encoder: Encoder[OffsetDateTime] = {
      val formatter = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSZ")
      (a: OffsetDateTime) => Json.fromString(a.format(formatter))
    }

    @ConfiguredJsonCodec
    case class ArticleResponse(article: ArticleBody)

    @ConfiguredJsonCodec
    case class ArticleBody(
      slug: String,
      title: String,
      description: String,
      body: String,
      tagList: Seq[String],
      createdAt: OffsetDateTime,
      updatedAt: OffsetDateTime,
      favorited: Boolean,
      favoritesCount: Int,
      author: Author
    )

    @ConfiguredJsonCodec
    case class Author(username: String, bio: Option[String], image: Option[String], following: Boolean)

    @ConfiguredJsonCodec
    case class CreateArticleRequest(article: CreateArticleBody)

    @ConfiguredJsonCodec
    case class CreateArticleBody(title: String, description: String, body: String, tagList: Option[Seq[String]])

    @ConfiguredJsonCodec
    case class UpdateArticleRequest(article: UpdateArticleBody)

    @ConfiguredJsonCodec
    case class UpdateArticleBody(title: Option[String], description: Option[String], body: Option[String])

    @ConfiguredJsonCodec
    case class ArticleListResponse(articles: Seq[ArticleBody], articlesCount: Int)

    @ConfiguredJsonCodec
    case class CreateCommentRequest(comment: CreateCommentBody)

    @ConfiguredJsonCodec
    case class CreateCommentBody(body: String)

    @ConfiguredJsonCodec
    case class CommentBody(id: Long, createdAt: OffsetDateTime, updatedAt: OffsetDateTime, body: String, author: Author)

    @ConfiguredJsonCodec
    case class CommentResponse(comment: CommentBody)

    @ConfiguredJsonCodec
    case class CommentsListResponse(comments: Seq[CommentBody])

    @ConfiguredJsonCodec
    case class TagsResponse(tags: Seq[String])

  }
  import json._

  private def toBody(author: ArticlesService.Author): json.Author = {
    Author(
      username = author.username,
      bio = author.bio,
      image = author.image,
      following = author.following
    )
  }

  private def toBody(article: Article): ArticleBody = {
    ArticleBody(
      slug = article.slug,
      title = article.title,
      description = article.description,
      body = article.body,
      tagList = article.tags.toSeq.sorted,
      createdAt = article.createdAt,
      updatedAt = article.updatedAt,
      favorited = article.favorited,
      favoritesCount = article.favoritesCount,
      author = toBody(article.author)
    )
  }

  private def toBody(comment: Comment): CommentBody = {
    CommentBody(
      id = comment.commentId,
      createdAt = comment.createdAt,
      updatedAt = comment.updatedAt,
      body = comment.body,
      author = toBody(comment.author)
    )
  }

  private def toResponse(article: Article): ArticleResponse = {
    ArticleResponse(toBody(article))
  }

  private def toResponse(comment: Comment): CommentResponse = {
    CommentResponse(toBody(comment))
  }

  private def toResponse(comments: Seq[Comment]): CommentsListResponse = {
    CommentsListResponse(comments.map { toBody })
  }

  def authedRoutes[F[_]: Concurrent](service: ArticlesService[F]): AuthedRoutes[Option[AuthContext], F] = {
    val dsl: Http4sDsl[F] = new Http4sDsl[F] {}
    import dsl._
    import io.circe.syntax._
    import org.http4s.circe._

    implicit def pageEncoder[T](implicit E: Encoder[T]): Encoder[Page[T]] = {
      Encoder.instance { p =>
        Json.obj(
          "articlesCount" -> Json.fromInt(p.total),
          "articles" -> Json.arr(p.items.map(E.apply):_*)
        )
      }
    }

    object TagP extends OptionalQueryParamDecoderMatcher[String]("tag")
    object AuthorP extends OptionalQueryParamDecoderMatcher[String]("author")
    object FavoritedP extends OptionalQueryParamDecoderMatcher[String]("favorited")
    object LimitP extends OptionalQueryParamDecoderMatcher[Int]("limit")
    object OffsetP extends OptionalQueryParamDecoderMatcher[Int]("offset")

    AuthedRoutes.of {
      case req @ POST -> Root / "api" / "articles" as authOpt =>
        for {
          auth <- withAuthRequired(authOpt)
          json <- req.req.asJson
          createReq <- Concurrent[F].fromEither { json.as[CreateArticleRequest] }
          article <- service.createArticle(
            userId = auth.userId,
            title = createReq.article.title,
            description = createReq.article.description,
            body = createReq.article.body,
            tagList = createReq.article.tagList.map(_.toSet).getOrElse { Set.empty }
          )
          response <-  Ok { toResponse(article).asJson }
        } yield response

      case GET -> Root / "api" / "articles" / "feed" :? LimitP(limit) +& OffsetP(offset) as authOpt =>
        for {
          auth <- withAuthRequired(authOpt)
          response <- service
            .feedArticles(auth.userId, limit.getOrElse(20), offset.getOrElse(0))
            .flatMap { page => Ok { page.map(toBody).asJson } }
        } yield response

      case GET -> Root / "api" / "articles" / slug as authOpt =>
        val article = authOpt match {
          case Some(auth) => service.getArticle(auth.userId, slug)
          case None => service.getArticle(slug)
        }
        article
          .semiflatMap { article => Ok { toResponse(article).asJson } }
          .getOrElseF(NotFound())

      case req @ PUT -> Root / "api" / "articles" / slug as authOpt =>
        for {
          auth <- withAuthRequired(authOpt)
          json <- req.req.asJson
          updateReq <- Concurrent[F].fromEither { json.as[UpdateArticleRequest] }
          articleOpt <- service.updateArticle(
            userId = auth.userId,
            slug = slug,
            title = updateReq.article.title,
            description = updateReq.article.description,
            body = updateReq.article.body
          ).value
          response <- articleOpt match {
            case Some(article) => Ok { toResponse(article).asJson }
            case None => NotFound()
          }
        } yield response

      case DELETE -> Root / "api" / "articles" / slug as authOpt =>
        for {
          auth <- withAuthRequired(authOpt)
          wasDeleted <- service.deleteArticle(auth.userId, slug)
          response <- if (wasDeleted) {
            Ok()
          } else {
            NotFound()
          }
        } yield response

      case GET -> Root / "api" / "articles" :?
        TagP(tag) +&
        AuthorP(author) +&
        FavoritedP(favorited) +&
        LimitP(limit) +&
        OffsetP(offset) as auth =>
        val userIdOpt = auth.map(_.userId)

        service
          .listArticles(tag, author, favorited, limit.getOrElse(20), offset.getOrElse(0), userIdOpt)
          .flatMap { page => Ok { page.map(toBody).asJson } }

      case POST -> Root / "api" / "articles" / slug / "favorite" as authOpt =>
        for {
          auth <- withAuthRequired(authOpt)
          response <- service
            .favoriteArticle(auth.userId, slug)
            .semiflatMap { article => Ok { toResponse(article).asJson } }
            .getOrElseF(NotFound())
        } yield response

      case DELETE -> Root / "api" / "articles" / slug / "favorite" as authOpt =>
        for {
          auth <- withAuthRequired(authOpt)
          response <- service
            .unFavoriteArticle(auth.userId, slug)
            .semiflatMap { article => Ok { toResponse(article).asJson } }
            .getOrElseF(NotFound())
        } yield response

      case req @ POST -> Root / "api" / "articles" / slug / "comments" as authOpt =>
        for {
          auth <- withAuthRequired(authOpt)
          json <- req.req.asJson
          createReq <- Concurrent[F].fromEither { json.as[CreateCommentRequest] }
          commentOpt <- service.addComment(auth.userId, slug, createReq.comment.body).value
          response <- commentOpt match {
            case Some(comment) => Ok { toResponse(comment).asJson }
            case None => NotFound("")
          }
        } yield response

      case GET -> Root / "api" / "articles" / slug / "comments" as authOpt =>
        val userIdOpt = authOpt.map(_.userId)

        for {
          commentsOpt <- service.listComments(userIdOpt, slug).value
          response <- commentsOpt match {
            case Some(comments) => Ok { toResponse(comments).asJson }
            case None => NotFound("")
          }
        } yield response

      case DELETE -> Root / "api" / "articles" / slug / "comments" / LongVar(commentId) as authOpt =>
        for {
          auth <- withAuthRequired(authOpt)
          wasDeleted <- service.deleteComment(auth.userId, slug, commentId)
          response <- if (wasDeleted) {
            Ok("")
          } else {
            NotFound("")
          }
        } yield response

      case GET -> Root / "api" / "tags" as _ =>
        for {
          tags <- service.tags
          response <- Ok { TagsResponse(tags.toSeq.sorted).asJson }
        } yield response
    }

  }

}
