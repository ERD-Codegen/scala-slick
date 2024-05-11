package com.marmaladesky.realworld.db

import cats.data.OptionT
import cats.effect.Async
import cats.implicits._
import com.marmaladesky.realworld.db.ArticlesRepo.{ArticleCommentWithDetails, ArticleWithDetails}
import com.marmaladesky.realworld.db.DbProfile.api._
import com.marmaladesky.realworld.db.gen.Tables
import com.marmaladesky.realworld.db.gen.Tables.{
  ArticleCommentsRow,
  ArticleFavoritesRow,
  ArticleTagsRow,
  ArticlesRow,
  UsersRow
}
import com.marmaladesky.realworld.model.Page
import slick.dbio.SuccessAction
import slick.jdbc.JdbcBackend
import slick.jdbc.JdbcBackend.Database

import scala.concurrent.ExecutionContext

class ArticlesRepo[F[_]: Async](db: Database, ec: ExecutionContext) {

  private implicit val database: JdbcBackend.Database = db
  private implicit val executionContext: ExecutionContext = ec

  def createArticle(
      slug: String,
      title: String,
      description: String,
      body: String,
      author: Long,
      tags: Set[String]
  ): F[(ArticlesRow, Seq[ArticleTagsRow])] = {

    val q = for {
      articleId <- (Tables.Articles returning Tables.Articles.map(_.articleId)) += ArticlesRow(
        articleId = 0L,
        slug = slug,
        title = title,
        description = description,
        body = body,
        author = author,
        creationDate = ZeroTime,
        lastUpdateDate = ZeroTime
      )
      _ <- Tables.ArticleTags.++=(tags.map(ArticleTagsRow(articleId, _)))
      inserted <- Tables.Articles
        .filter(_.articleId === articleId)
        .result
        .map { _.headOption.getOrElse(throw new RuntimeException("Cannot to verify created article")) }
      newTags <- Tables.ArticleTags.filter(_.articleId === articleId).result
    } yield inserted -> newTags

    liftQ { q.transactionally }
  }

  def getArticle(slug: String): OptionT[F, (ArticlesRow, Seq[ArticleTagsRow])] = {
    val q = for {
      articleOpt <- Tables.Articles
        .filter(_.slug === slug)
        .result
        .map { _.headOption }
      result <- articleOpt match {
        case Some(article) =>
          for {
            tags <- Tables.ArticleTags.filter(_.articleId === article.articleId).result
          } yield (article, tags).some
        case None =>
          SuccessAction(None)
      }
    } yield result

    liftQ { q.transactionally }
  }

  def updateArticle(
      slug: String,
      author: Long,
      newSlug: Option[String],
      title: Option[String],
      description: Option[String],
      body: Option[String]
  ): OptionT[F, (ArticlesRow, Seq[ArticleTagsRow])] = {

    val q = for {
      existingOpt <- Tables.Articles.filter(r => r.slug === slug && r.author === author).result.map { _.headOption }
      _ <- existingOpt match {
        case Some(existing) =>
          Tables.Articles
            .filter(_.articleId === existing.articleId)
            .map { r => (r.slug, r.title, r.description, r.body) }
            .update {
              (
                newSlug.getOrElse(existing.slug),
                title.getOrElse(existing.title),
                description.getOrElse(existing.description),
                body.getOrElse(existing.body)
              )
            }
        case None =>
          SuccessAction(())
      }
      articleOpt <- Tables.Articles
        .filter(_.slug === slug)
        .result
        .map { _.headOption }
      result <- articleOpt match {
        case Some(article) =>
          for {
            tags <- Tables.ArticleTags.filter(_.articleId === article.articleId).result
          } yield (article, tags).some
        case None =>
          SuccessAction(None)
      }
    } yield result

    liftQ { q.transactionally }
  }

  def deleteArticle(userId: Long, slug: String): F[Boolean] = {
    val q = Tables.Articles.filter(r => r.slug === slug && r.author === userId).delete.map(_ > 0)

    liftQ { q.transactionally }
  }

  def isFavorited(articleId: Long, userId: Long): F[Boolean] = {
    val q = Tables.ArticleFavorites
      .filter { r => r.articleId === articleId && r.userId === userId }
      .exists
      .result

    liftQ { q.transactionally }
  }

  def totalFavorites(articleId: Long): F[Int] = {
    val q = Tables.ArticleFavorites
      .filter { r => r.articleId === articleId }
      .length
      .result

    liftQ { q.transactionally }
  }

  private def tagsQ(articlesIds: Set[Long]) = {
    Tables.ArticleTags
      .filter(_.articleId inSet articlesIds)
      .result
      .map {
        _.groupBy { _.articleId }.view
          .mapValues { _.toSet }
      }
  }

  private def favoritesCountQ(articlesIds: Set[Long]) = {
    Tables.ArticleFavorites
      .filter(_.articleId inSet articlesIds)
      .groupBy { _.articleId }
      .map { case (articleId, rows) => articleId -> rows.length }
      .result
      .map { _.toMap }
  }

  private def favoritedArticlesQ(articlesIds: Set[Long], userId: Long) = {
    Tables.ArticleFavorites
      .filter { r => (r.articleId inSet articlesIds) && r.userId === userId }
      .result
      .map { _.map { _.articleId }.toSet }
  }

  private def followedAuthorsQ(authorsIds: Set[Long], userId: Long) = {
    Tables.Follows
      .filter { r => (r.masterId inSet authorsIds) && r.slaveId === userId }
      .map { _.masterId }
      .result
      .map { _.toSet }
  }

  def listArticles(
      tag: Option[String],
      author: Option[String],
      favoritedByUser: Option[Long],
      requestorIdOpt: Option[Long],
      limit: Int,
      offset: Int
  ): F[Page[ArticleWithDetails]] = {

    val articlesQ = Tables.Articles
      .filterOpt(tag) { (article, tag) =>
        article.articleId.in {
          Tables.ArticleTags.filter(t => t.tag === tag && t.articleId === article.articleId).map(_.articleId)
        }
      }
      .filterOpt(favoritedByUser) { (article, favUser) =>
        article.articleId.in {
          Tables.ArticleFavorites
            .filter(f => f.userId === favUser && f.articleId === article.articleId)
            .map(_.articleId)
        }
      }

    val authorsQ = Tables.Users.filterOpt(author) { (r, a) => r.username === a }

    val mainQ = articlesQ
      .join(authorsQ)
      .on { case (article, author) => article.author === author.userId }
      .distinct
      .sortBy { case (article, _) => article.creationDate.desc }

    val countQ = mainQ.length.result
    val pageQ = mainQ.drop(offset).take(limit).result

    val q = for {
      total <- countQ
      items <- pageQ
      articleIds = items.map(_._1.articleId).toSet
      tagsByArticle <- tagsQ(articleIds)
      favoritesCountByArticle <- favoritesCountQ(articleIds)
      favoritedArticles <- requestorIdOpt match {
        case Some(requestorId) => favoritedArticlesQ(articleIds, requestorId)
        case None              => SuccessAction(Set.empty[Long])
      }
      followedAuthors <- requestorIdOpt match {
        case Some(requestorId) => followedAuthorsQ(items.map(_._1.author).toSet, requestorId)
        case None              => SuccessAction(Set.empty[Long])
      }
      detailedItems = items.map { case (article, author) =>
        ArticleWithDetails(
          article,
          tagsByArticle.getOrElse(article.articleId, Set.empty),
          author,
          followedAuthors.contains(article.author),
          favoritedArticles.contains(article.articleId),
          favoritesCountByArticle.getOrElse(article.articleId, 0)
        )
      }
    } yield Page(total, detailedItems)

    liftQ { q.transactionally }
  }

  def feedArticles(
      clientUserId: Long,
      limit: Int,
      offset: Int
  ): F[Page[ArticleWithDetails]] = {

    val articlesQ = Tables.Articles
      .filter { _.author in { Tables.Follows.filter(_.slaveId === clientUserId).map(_.masterId) } }
      .sortBy { _.creationDate.desc }

    val mainQ = articlesQ
      .join(Tables.Users)
      .on { case (article, author) => article.author === author.userId }
      .distinct
      .sortBy { case (article, _) => article.creationDate.desc }

    val countQ = mainQ.length.result
    val pageQ = mainQ.drop(offset).take(limit).result

    val q = for {
      total <- countQ
      items <- pageQ
      articleIds = items.map(_._1.articleId).toSet
      tagsByArticle <- tagsQ(articleIds)
      favoritesCountByArticle <- favoritesCountQ(articleIds)
      favoritedArticles <- favoritedArticlesQ(articleIds, clientUserId)
      followedAuthors <- followedAuthorsQ(items.map(_._1.author).toSet, clientUserId)
      detailedItems = items.map { case (article, author) =>
        ArticleWithDetails(
          article,
          tagsByArticle.getOrElse(article.articleId, Set.empty),
          author,
          followedAuthors.contains(article.author),
          favoritedArticles.contains(article.articleId),
          favoritesCountByArticle.getOrElse(article.articleId, 0)
        )
      }
    } yield Page(total, detailedItems)

    liftQ { q.transactionally }
  }

  def favoriteArticle(userId: Long, slug: String): OptionT[F, ArticleWithDetails] = {
    val q = for {
      articleOpt <- Tables.Articles.filter(_.slug === slug).result.map(_.headOption)
      _ <- articleOpt match {
        case Some(article) =>
          for {
            isFavorite <- Tables.ArticleFavorites
              .filter(r => r.articleId === article.articleId && r.userId === userId)
              .exists
              .result
            result <-
              if (!isFavorite) {
                Tables.ArticleFavorites += ArticleFavoritesRow(article.articleId, userId)
              } else {
                SuccessAction(0)
              }
          } yield result
        case None =>
          SuccessAction(0)
      }
      fullArticle <- articleOpt match {
        case Some(article) =>
          for {
            author <- Tables.Users.filter(_.userId === article.author).result.map(_.head)
            tagsByArticle <- tagsQ(Set(article.articleId))
            favoritesCountByArticle <- favoritesCountQ(Set(article.articleId))
            favoritedArticles <- favoritedArticlesQ(Set(article.articleId), userId)
            followedAuthors <- followedAuthorsQ(Set(article.author), userId)
            detailedItem = ArticleWithDetails(
              article,
              tagsByArticle.getOrElse(article.articleId, Set.empty),
              author,
              followedAuthors.contains(article.author),
              favoritedArticles.contains(article.articleId),
              favoritesCountByArticle.getOrElse(article.articleId, 0)
            )
          } yield detailedItem.some
        case None =>
          SuccessAction(None)
      }
    } yield fullArticle

    liftQ { q.transactionally }
  }

  def unFavoriteArticle(userId: Long, slug: String): OptionT[F, ArticleWithDetails] = {
    val q = for {
      articleOpt <- Tables.Articles.filter(_.slug === slug).result.map(_.headOption)
      _ <- articleOpt match {
        case Some(article) =>
          Tables.ArticleFavorites.filter(r => r.articleId === article.articleId && r.userId === userId).delete
        case None =>
          SuccessAction(0)
      }
      fullArticle <- articleOpt match {
        case Some(article) =>
          for {
            author <- Tables.Users.filter(_.userId === article.author).result.map(_.head)
            tagsByArticle <- tagsQ(Set(article.articleId))
            favoritesCountByArticle <- favoritesCountQ(Set(article.articleId))
            favoritedArticles <- favoritedArticlesQ(Set(article.articleId), userId)
            followedAuthors <- followedAuthorsQ(Set(article.author), userId)
            detailedItem = ArticleWithDetails(
              article,
              tagsByArticle.getOrElse(article.articleId, Set.empty),
              author,
              followedAuthors.contains(article.author),
              favoritedArticles.contains(article.articleId),
              favoritesCountByArticle.getOrElse(article.articleId, 0)
            )
          } yield detailedItem.some
        case None =>
          SuccessAction(None)
      }
    } yield fullArticle

    liftQ { q.transactionally }
  }

  def addComment(userId: Long, slug: String, body: String): OptionT[F, ArticleCommentsRow] = {
    val q = for {
      articleOpt <- Tables.Articles.filter(_.slug === slug).result.map { _.headOption }
      insertedIdOpt <- articleOpt match {
        case Some(article) =>
          val row = ArticleCommentsRow(
            commentId = 0L,
            articleId = article.articleId,
            body = body,
            author = userId,
            creationDate = ZeroTime,
            lastUpdateDate = ZeroTime
          )
          ((Tables.ArticleComments returning Tables.ArticleComments.map(_.commentId)) += row).map(_.some)
        case None => SuccessAction(None)
      }
      inserted <- insertedIdOpt match {
        case Some(insertedId) =>
          Tables.ArticleComments
            .filter(_.commentId === insertedId)
            .result
            .map {
              _.headOption
                .getOrElse { throw new RuntimeException(s"Failed to verify inserted comment with id '$insertedId'") }
                .some
            }
        case None =>
          SuccessAction(None)
      }
    } yield inserted

    liftQ { q.transactionally }
  }

  def listComments(slug: String): OptionT[F, Seq[ArticleCommentWithDetails]] = {
    val q = for {
      articleOpt <- Tables.Articles.filter(_.slug === slug).result.map {
        _.headOption
      }
      result <- articleOpt match {
        case Some(article) =>
          for {
            comments <- Tables.ArticleComments.filter(_.articleId === article.articleId).result
            authors <- Tables.Users
              .filter(_.userId in Tables.ArticleComments.filter(_.articleId === article.articleId).map(_.author))
              .result
              .map {
                _.map { a => a.userId -> a }.toMap
              }
          } yield {
            comments.flatMap { commentRow =>
              authors
                .get(commentRow.author)
                .map { author => ArticleCommentWithDetails(commentRow, author) }
                .toSeq
            }.some
          }
        case None =>
          SuccessAction(None)
      }
    } yield result

    liftQ { q.transactionally }
  }

  def deleteComment(userId: Long, commentId: Long): F[Boolean] = {
    val q = Tables.ArticleComments
      .filter { r => r.author === userId && r.commentId === commentId }
      .delete
      .map { _ > 0 }

    liftQ { q.transactionally }
  }

  def tags: F[Set[String]] = {
    val q = Tables.ArticleTags.map(_.tag).distinct.result.map { _.toSet }

    liftQ { q.transactionally }
  }

}

object ArticlesRepo {

  case class ArticleWithDetails(
      article: ArticlesRow,
      tags: Set[ArticleTagsRow],
      author: UsersRow,
      isFollowingAuthor: Boolean,
      isFavorited: Boolean,
      totalFavorites: Int
  )

  case class ArticleCommentWithDetails(
      comment: ArticleCommentsRow,
      author: UsersRow
  )

}
