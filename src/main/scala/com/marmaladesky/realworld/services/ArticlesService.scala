package com.marmaladesky.realworld.services

import cats.data.OptionT
import cats.effect.kernel.{Async, Concurrent}
import cats.implicits._
import com.marmaladesky.realworld.ReadableError
import com.marmaladesky.realworld.db.ArticlesRepo.ArticleCommentWithDetails
import com.marmaladesky.realworld.db.gen.Tables.{ArticleTagsRow, ArticlesRow, FollowsRow, UsersRow}
import com.marmaladesky.realworld.db.{ArticlesRepo, FollowsRepo, UsersRepo}
import com.marmaladesky.realworld.model.Page
import com.marmaladesky.realworld.services.ArticlesService.{Article, Comment}
import com.marmaladesky.realworld.utils.SlugGenerator
import org.http4s.Status

import java.time.OffsetDateTime

trait ArticlesService[F[_]] {

  def createArticle(userId: Long, title: String, description: String, body: String, tagList: Set[String]): F[Article]

  def getArticle(userId: Long, slug: String): OptionT[F, Article]

  def getArticle(slug: String): OptionT[F, Article]

  def updateArticle(
      userId: Long,
      slug: String,
      title: Option[String],
      description: Option[String],
      body: Option[String]
  ): OptionT[F, Article]

  def deleteArticle(userId: Long, slug: String): F[Boolean]

  def listArticles(
      tag: Option[String],
      author: Option[String],
      favoritedByUser: Option[String],
      limit: Int,
      offset: Int,
      userId: Option[Long] = None
  ): F[Page[Article]]

  def feedArticles(
      userId: Long,
      limit: Int,
      offset: Int
  ): F[Page[Article]]

  def favoriteArticle(
      userId: Long,
      slug: String
  ): OptionT[F, Article]

  def unFavoriteArticle(
      userId: Long,
      slug: String
  ): OptionT[F, Article]

  def addComment(
      userId: Long,
      slug: String,
      body: String
  ): OptionT[F, Comment]

  def listComments(
      userId: Option[Long],
      slug: String
  ): OptionT[F, Seq[Comment]]

  def deleteComment(
      userId: Long,
      slug: String,
      commentId: Long
  ): F[Boolean]

  def tags: F[Set[String]]

}

object ArticlesService {

  case class Article(
      articleId: Long,
      slug: String,
      title: String,
      description: String,
      body: String,
      author: Author,
      favorited: Boolean,
      favoritesCount: Int,
      createdAt: OffsetDateTime,
      updatedAt: OffsetDateTime,
      tags: Set[String]
  )

  case class Author(
      username: String,
      bio: Option[String],
      image: Option[String],
      following: Boolean
  )

  case class Comment(
      commentId: Long,
      createdAt: OffsetDateTime,
      updatedAt: OffsetDateTime,
      body: String,
      author: Author
  )

  private def readRow(authorRow: UsersRow, isFollowingAuthor: Boolean): Author = {
    Author(
      username = authorRow.username,
      bio = authorRow.bio,
      image = authorRow.imageUrl,
      following = isFollowingAuthor
    )
  }

  private def readRow(
      row: ArticlesRow,
      authorRow: UsersRow,
      isFollowingAuthor: Boolean,
      isFavoriteArticle: Boolean,
      articleFavoritesCount: Int,
      tags: Seq[ArticleTagsRow]
  ): Article = {
    Article(
      articleId = row.articleId,
      slug = row.slug,
      title = row.title,
      description = row.description,
      body = row.body,
      author = readRow(authorRow, isFollowingAuthor),
      favorited = isFavoriteArticle,
      favoritesCount = articleFavoritesCount,
      createdAt = row.creationDate,
      updatedAt = row.lastUpdateDate,
      tags = tags.map(_.tag).toSet
    )
  }

  def impl[F[_]: Async](
      articlesRepo: ArticlesRepo[F],
      usersRepo: UsersRepo[F],
      followsRepo: FollowsRepo[F],
      slugGenerator: SlugGenerator[F]
  ): ArticlesService[F] = new ArticlesService[F] {

    private def verifySlug(slug: String): F[Boolean] = {
      articlesRepo.getArticle(slug).isEmpty
    }

    private def generateSlug(title: String): F[Either[Throwable, String]] = {
      slugGenerator.generateSlug(title, verifySlug)
    }

    override def createArticle(
        userId: Long,
        title: String,
        description: String,
        body: String,
        tags: Set[String]
    ): F[Article] = {
      for {
        slugAttempt <- generateSlug(title)
        slug <- Concurrent[F].fromEither(slugAttempt)
        articleAndTags <- articlesRepo.createArticle(slug, title, description, body, userId, tags)
        (article, tags) = articleAndTags
        author <- usersRepo
          .getUser(article.author)
          .getOrElse(throw new RuntimeException(s"Failed to find author '${article.author}'"))
        isFollowingAuthor <- followsRepo.isFollowed(master = article.author, slave = userId)
        isMyFavorite <- articlesRepo.isFavorited(articleId = article.articleId, userId = userId)
        totalFavorites <- articlesRepo.totalFavorites(article.articleId)
      } yield readRow(
        row = article,
        authorRow = author,
        isFollowingAuthor = isFollowingAuthor,
        isFavoriteArticle = isMyFavorite,
        articleFavoritesCount = totalFavorites,
        tags = tags
      )
    }

    override def getArticle(userId: Long, slug: String): OptionT[F, Article] = {
      for {
        articleAndTags <- articlesRepo.getArticle(slug)
        (article, tags) = articleAndTags
        author <- usersRepo.getUser(article.author)
        isFollowingAuthor <- OptionT.liftF {
          followsRepo.isFollowed(master = article.author, userId)
        }
        isMyFavorite <- OptionT.liftF {
          articlesRepo.isFavorited(articleId = article.articleId, userId = userId)
        }
        totalFavorites <- OptionT.liftF {
          articlesRepo.totalFavorites(article.articleId)
        }
      } yield readRow(
        row = article,
        authorRow = author,
        isFollowingAuthor = isFollowingAuthor,
        isFavoriteArticle = isMyFavorite,
        articleFavoritesCount = totalFavorites,
        tags = tags
      )
    }

    override def getArticle(slug: String): OptionT[F, Article] = {
      for {
        articleAndTags <- articlesRepo.getArticle(slug)
        (article, tags) = articleAndTags
        author <- usersRepo.getUser(article.author)
        totalFavorites <- OptionT.liftF {
          articlesRepo.totalFavorites(article.articleId)
        }
      } yield readRow(
        row = article,
        authorRow = author,
        isFollowingAuthor = false,
        isFavoriteArticle = false,
        articleFavoritesCount = totalFavorites,
        tags = tags
      )
    }

    override def updateArticle(
        userId: Long,
        slug: String,
        title: Option[String],
        description: Option[String],
        body: Option[String]
    ): OptionT[F, Article] = {
      for {
        newSlug <- title match {
          case Some(t) =>
            OptionT
              .liftF { generateSlug(t) }
              .semiflatMap { Concurrent[F].fromEither }
              .map { _.some }
          case None =>
            OptionT.some[F] { Option.empty[String] }
        }

        articleAndTags <- articlesRepo.updateArticle(
          slug = slug,
          author = userId,
          newSlug = newSlug,
          title = title,
          description = description,
          body = body
        )
        (article, tags) = articleAndTags
        author <- usersRepo.getUser(article.author)
        isFollowingAuthor <- OptionT.liftF {
          followsRepo.isFollowed(master = article.author, userId)
        }
        isMyFavorite <- OptionT.liftF {
          articlesRepo.isFavorited(articleId = article.articleId, userId = userId)
        }
        totalFavorites <- OptionT.liftF {
          articlesRepo.totalFavorites(article.articleId)
        }
        updatedArticle = readRow(
          row = article,
          authorRow = author,
          isFollowingAuthor = isFollowingAuthor,
          isFavoriteArticle = isMyFavorite,
          articleFavoritesCount = totalFavorites,
          tags = tags
        )
      } yield updatedArticle
    }

    override def deleteArticle(userId: Long, slug: String): F[Boolean] = {
      articlesRepo.deleteArticle(userId, slug)
    }

    override def listArticles(
        tag: Option[String],
        author: Option[String],
        favoritedByUser: Option[String],
        limit: Int,
        offset: Int,
        userId: Option[Long] = None
    ): F[Page[Article]] = {

      for {
        favoritedByUserRow <- favoritedByUser match {
          case Some(username) =>
            usersRepo
              .getUserByUsername(username)
              .getOrElseF {
                Async[F].raiseError {
                  ReadableError(s"User $username doesn't exist", codeHint = Status.NotFound.code)
                }
              }
              .map(_.some)
          case None => None.pure[F]
        }
        articles <- articlesRepo.listArticles(tag, author, favoritedByUserRow.map(_.userId), userId, limit, offset)
        page = articles.map(article =>
          readRow(
            article.article,
            article.author,
            article.isFollowingAuthor,
            article.isFavorited,
            article.totalFavorites,
            article.tags.toSeq
          )
        )
      } yield page
    }

    override def feedArticles(userId: Long, limit: Int, offset: Int): F[Page[Article]] = {
      for {
        articles <- articlesRepo.feedArticles(userId, limit, offset)
        page = articles.map(article =>
          readRow(
            article.article,
            article.author,
            article.isFollowingAuthor,
            article.isFavorited,
            article.totalFavorites,
            article.tags.toSeq
          )
        )
      } yield page
    }

    override def favoriteArticle(userId: Long, slug: String): OptionT[F, Article] = {
      articlesRepo
        .favoriteArticle(userId: Long, slug: String)
        .map { detailed =>
          readRow(
            row = detailed.article,
            authorRow = detailed.author,
            isFollowingAuthor = detailed.isFollowingAuthor,
            isFavoriteArticle = detailed.isFavorited,
            articleFavoritesCount = detailed.totalFavorites,
            tags = detailed.tags.toSeq
          )
        }
    }

    override def unFavoriteArticle(userId: Long, slug: String): OptionT[F, Article] = {
      articlesRepo
        .unFavoriteArticle(userId: Long, slug: String)
        .map { detailed =>
          readRow(
            row = detailed.article,
            authorRow = detailed.author,
            isFollowingAuthor = detailed.isFollowingAuthor,
            isFavoriteArticle = detailed.isFavorited,
            articleFavoritesCount = detailed.totalFavorites,
            tags = detailed.tags.toSeq
          )
        }
    }

    override def addComment(userId: Long, slug: String, body: String): OptionT[F, Comment] = {
      for {
        author <- usersRepo.getUser(userId)
        comment <- articlesRepo.addComment(userId, slug, body)
        isFollowing <- OptionT.liftF { followsRepo.isFollowed(author.userId, userId) }
      } yield {
        Comment(
          commentId = comment.commentId,
          createdAt = comment.creationDate,
          updatedAt = comment.lastUpdateDate,
          body = comment.body,
          author = readRow(author, isFollowing)
        )
      }
    }

    override def listComments(userIdOpt: Option[Long], slug: String): OptionT[F, Seq[Comment]] = {
      for {
        comments <- articlesRepo.listComments(slug)
        followed <- userIdOpt match {
          case Some(userId) =>
            OptionT.liftF { followsRepo.getFollowed(userId).map(_.map(_.masterId)) }
          case None =>
            OptionT.some[F] { Seq.empty[FollowsRow] }
        }
      } yield {
        comments.map { case ArticleCommentWithDetails(comment, author) =>
          Comment(
            commentId = comment.commentId,
            createdAt = comment.creationDate,
            updatedAt = comment.lastUpdateDate,
            body = comment.body,
            author = readRow(author, followed.contains(author.userId))
          )
        }
      }
    }

    override def deleteComment(userId: Long, slug: String, commentId: Long): F[Boolean] = {
      articlesRepo.deleteComment(userId, commentId)
    }

    override def tags: F[Set[String]] = {
      articlesRepo.tags
    }
  }

}
