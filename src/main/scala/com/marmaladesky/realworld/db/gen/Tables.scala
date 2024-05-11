package com.marmaladesky.realworld.db.gen
// AUTO-GENERATED Slick data model
/** Stand-alone Slick data model for immediate use */
object Tables extends Tables {
  val profile: slick.jdbc.JdbcProfile = com.marmaladesky.realworld.db.DbProfile
}

/** Slick data model trait for extension, choice of backend or usage in the cake pattern. (Make sure to initialize this late.) */
trait Tables {
  val profile: slick.jdbc.JdbcProfile
  import profile.api._
  import slick.model.ForeignKeyAction
  // NOTE: GetResult mappers for plain SQL are only generated for
  // tables where Slick knows how to map the types of all columns.
  import slick.jdbc.{GetResult => GR}

  /** DDL for all tables. Call .create to execute. */
  lazy val schema: profile.SchemaDescription = Array(ArticleComments.schema, ArticleFavorites.schema, Articles.schema, ArticleTags.schema, FlywaySchemaHistory.schema, Follows.schema, Users.schema).reduceLeft(_ ++ _)

  /** Entity class storing rows of table ArticleComments
   *  @param commentId Database column comment_id SqlType(bigserial), AutoInc, PrimaryKey
   *  @param articleId Database column article_id SqlType(int8)
   *  @param body Database column body SqlType(text)
   *  @param author Database column author SqlType(int8)
   *  @param creationDate Database column creation_date SqlType(timestamptz)
   *  @param lastUpdateDate Database column last_update_date SqlType(timestamptz) */
  case class ArticleCommentsRow(commentId: Long, articleId: Long, body: String, author: Long, creationDate: java.time.OffsetDateTime, lastUpdateDate: java.time.OffsetDateTime)
  /** GetResult implicit for fetching ArticleCommentsRow objects using plain SQL queries */
  implicit def GetResultArticleCommentsRow(implicit e0: GR[Long], e1: GR[String], e2: GR[java.time.OffsetDateTime]): GR[ArticleCommentsRow] = GR{
    prs => import prs._
    (ArticleCommentsRow.apply _).tupled((<<[Long], <<[Long], <<[String], <<[Long], <<[java.time.OffsetDateTime], <<[java.time.OffsetDateTime]))
  }
  /** Table description of table article_comments. Objects of this class serve as prototypes for rows in queries. */
  class ArticleComments(_tableTag: Tag) extends profile.api.Table[ArticleCommentsRow](_tableTag, Some("condoit"), "article_comments") {
    def * = ((commentId, articleId, body, author, creationDate, lastUpdateDate)).mapTo[ArticleCommentsRow]
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = ((Rep.Some(commentId), Rep.Some(articleId), Rep.Some(body), Rep.Some(author), Rep.Some(creationDate), Rep.Some(lastUpdateDate))).shaped.<>({r=>import r._; _1.map(_=> (ArticleCommentsRow.apply _).tupled((_1.get, _2.get, _3.get, _4.get, _5.get, _6.get)))}, (_:Any) => throw new Exception("Inserting into ? projection not supported."))

    /** Database column comment_id SqlType(bigserial), AutoInc, PrimaryKey */
    val commentId: Rep[Long] = column[Long]("comment_id", O.AutoInc, O.PrimaryKey)
    /** Database column article_id SqlType(int8) */
    val articleId: Rep[Long] = column[Long]("article_id")
    /** Database column body SqlType(text) */
    val body: Rep[String] = column[String]("body")
    /** Database column author SqlType(int8) */
    val author: Rep[Long] = column[Long]("author")
    /** Database column creation_date SqlType(timestamptz) */
    val creationDate: Rep[java.time.OffsetDateTime] = column[java.time.OffsetDateTime]("creation_date")
    /** Database column last_update_date SqlType(timestamptz) */
    val lastUpdateDate: Rep[java.time.OffsetDateTime] = column[java.time.OffsetDateTime]("last_update_date")

    /** Foreign key referencing Articles (database name article_comments_article_id_fkey) */
    lazy val articlesFk = foreignKey("article_comments_article_id_fkey", articleId, Articles)(r => r.articleId, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.Cascade)
    /** Foreign key referencing Users (database name article_comments_author_fkey) */
    lazy val usersFk = foreignKey("article_comments_author_fkey", author, Users)(r => r.userId, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.Cascade)
  }
  /** Collection-like TableQuery object for table ArticleComments */
  lazy val ArticleComments = new TableQuery(tag => new ArticleComments(tag))

  /** Entity class storing rows of table ArticleFavorites
   *  @param articleId Database column article_id SqlType(int8)
   *  @param userId Database column user_id SqlType(int8) */
  case class ArticleFavoritesRow(articleId: Long, userId: Long)
  /** GetResult implicit for fetching ArticleFavoritesRow objects using plain SQL queries */
  implicit def GetResultArticleFavoritesRow(implicit e0: GR[Long]): GR[ArticleFavoritesRow] = GR{
    prs => import prs._
    (ArticleFavoritesRow.apply _).tupled((<<[Long], <<[Long]))
  }
  /** Table description of table article_favorites. Objects of this class serve as prototypes for rows in queries. */
  class ArticleFavorites(_tableTag: Tag) extends profile.api.Table[ArticleFavoritesRow](_tableTag, Some("condoit"), "article_favorites") {
    def * = ((articleId, userId)).mapTo[ArticleFavoritesRow]
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = ((Rep.Some(articleId), Rep.Some(userId))).shaped.<>({r=>import r._; _1.map(_=> (ArticleFavoritesRow.apply _).tupled((_1.get, _2.get)))}, (_:Any) => throw new Exception("Inserting into ? projection not supported."))

    /** Database column article_id SqlType(int8) */
    val articleId: Rep[Long] = column[Long]("article_id")
    /** Database column user_id SqlType(int8) */
    val userId: Rep[Long] = column[Long]("user_id")

    /** Primary key of ArticleFavorites (database name article_favorites_pkey) */
    val pk = primaryKey("article_favorites_pkey", (articleId, userId))

    /** Foreign key referencing Articles (database name article_favorites_article_id_fkey) */
    lazy val articlesFk = foreignKey("article_favorites_article_id_fkey", articleId, Articles)(r => r.articleId, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.Cascade)
    /** Foreign key referencing Users (database name article_favorites_user_id_fkey) */
    lazy val usersFk = foreignKey("article_favorites_user_id_fkey", userId, Users)(r => r.userId, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.Cascade)
  }
  /** Collection-like TableQuery object for table ArticleFavorites */
  lazy val ArticleFavorites = new TableQuery(tag => new ArticleFavorites(tag))

  /** Entity class storing rows of table Articles
   *  @param articleId Database column article_id SqlType(bigserial), AutoInc, PrimaryKey
   *  @param slug Database column slug SqlType(text)
   *  @param title Database column title SqlType(text)
   *  @param description Database column description SqlType(text)
   *  @param body Database column body SqlType(text)
   *  @param author Database column author SqlType(int8)
   *  @param creationDate Database column creation_date SqlType(timestamptz)
   *  @param lastUpdateDate Database column last_update_date SqlType(timestamptz) */
  case class ArticlesRow(articleId: Long, slug: String, title: String, description: String, body: String, author: Long, creationDate: java.time.OffsetDateTime, lastUpdateDate: java.time.OffsetDateTime)
  /** GetResult implicit for fetching ArticlesRow objects using plain SQL queries */
  implicit def GetResultArticlesRow(implicit e0: GR[Long], e1: GR[String], e2: GR[java.time.OffsetDateTime]): GR[ArticlesRow] = GR{
    prs => import prs._
    (ArticlesRow.apply _).tupled((<<[Long], <<[String], <<[String], <<[String], <<[String], <<[Long], <<[java.time.OffsetDateTime], <<[java.time.OffsetDateTime]))
  }
  /** Table description of table articles. Objects of this class serve as prototypes for rows in queries. */
  class Articles(_tableTag: Tag) extends profile.api.Table[ArticlesRow](_tableTag, Some("condoit"), "articles") {
    def * = ((articleId, slug, title, description, body, author, creationDate, lastUpdateDate)).mapTo[ArticlesRow]
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = ((Rep.Some(articleId), Rep.Some(slug), Rep.Some(title), Rep.Some(description), Rep.Some(body), Rep.Some(author), Rep.Some(creationDate), Rep.Some(lastUpdateDate))).shaped.<>({r=>import r._; _1.map(_=> (ArticlesRow.apply _).tupled((_1.get, _2.get, _3.get, _4.get, _5.get, _6.get, _7.get, _8.get)))}, (_:Any) => throw new Exception("Inserting into ? projection not supported."))

    /** Database column article_id SqlType(bigserial), AutoInc, PrimaryKey */
    val articleId: Rep[Long] = column[Long]("article_id", O.AutoInc, O.PrimaryKey)
    /** Database column slug SqlType(text) */
    val slug: Rep[String] = column[String]("slug")
    /** Database column title SqlType(text) */
    val title: Rep[String] = column[String]("title")
    /** Database column description SqlType(text) */
    val description: Rep[String] = column[String]("description")
    /** Database column body SqlType(text) */
    val body: Rep[String] = column[String]("body")
    /** Database column author SqlType(int8) */
    val author: Rep[Long] = column[Long]("author")
    /** Database column creation_date SqlType(timestamptz) */
    val creationDate: Rep[java.time.OffsetDateTime] = column[java.time.OffsetDateTime]("creation_date")
    /** Database column last_update_date SqlType(timestamptz) */
    val lastUpdateDate: Rep[java.time.OffsetDateTime] = column[java.time.OffsetDateTime]("last_update_date")

    /** Foreign key referencing Users (database name articles_author_fkey) */
    lazy val usersFk = foreignKey("articles_author_fkey", author, Users)(r => r.userId, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.Cascade)
  }
  /** Collection-like TableQuery object for table Articles */
  lazy val Articles = new TableQuery(tag => new Articles(tag))

  /** Entity class storing rows of table ArticleTags
   *  @param articleId Database column article_id SqlType(int8)
   *  @param tag Database column tag SqlType(text) */
  case class ArticleTagsRow(articleId: Long, tag: String)
  /** GetResult implicit for fetching ArticleTagsRow objects using plain SQL queries */
  implicit def GetResultArticleTagsRow(implicit e0: GR[Long], e1: GR[String]): GR[ArticleTagsRow] = GR{
    prs => import prs._
    (ArticleTagsRow.apply _).tupled((<<[Long], <<[String]))
  }
  /** Table description of table article_tags. Objects of this class serve as prototypes for rows in queries. */
  class ArticleTags(_tableTag: Tag) extends profile.api.Table[ArticleTagsRow](_tableTag, Some("condoit"), "article_tags") {
    def * = ((articleId, tag)).mapTo[ArticleTagsRow]
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = ((Rep.Some(articleId), Rep.Some(tag))).shaped.<>({r=>import r._; _1.map(_=> (ArticleTagsRow.apply _).tupled((_1.get, _2.get)))}, (_:Any) => throw new Exception("Inserting into ? projection not supported."))

    /** Database column article_id SqlType(int8) */
    val articleId: Rep[Long] = column[Long]("article_id")
    /** Database column tag SqlType(text) */
    val tag: Rep[String] = column[String]("tag")

    /** Primary key of ArticleTags (database name article_tags_pkey) */
    val pk = primaryKey("article_tags_pkey", (articleId, tag))

    /** Foreign key referencing Articles (database name article_tags_article_id_fkey) */
    lazy val articlesFk = foreignKey("article_tags_article_id_fkey", articleId, Articles)(r => r.articleId, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.Cascade)
  }
  /** Collection-like TableQuery object for table ArticleTags */
  lazy val ArticleTags = new TableQuery(tag => new ArticleTags(tag))

  /** Entity class storing rows of table FlywaySchemaHistory
   *  @param installedRank Database column installed_rank SqlType(int4), PrimaryKey
   *  @param version Database column version SqlType(varchar), Length(50,true), Default(None)
   *  @param description Database column description SqlType(varchar), Length(200,true)
   *  @param `type` Database column type SqlType(varchar), Length(20,true)
   *  @param script Database column script SqlType(varchar), Length(1000,true)
   *  @param checksum Database column checksum SqlType(int4), Default(None)
   *  @param installedBy Database column installed_by SqlType(varchar), Length(100,true)
   *  @param installedOn Database column installed_on SqlType(timestamp)
   *  @param executionTime Database column execution_time SqlType(int4)
   *  @param success Database column success SqlType(bool) */
  case class FlywaySchemaHistoryRow(installedRank: Int, version: Option[String] = None, description: String, `type`: String, script: String, checksum: Option[Int] = None, installedBy: String, installedOn: java.sql.Timestamp, executionTime: Int, success: Boolean)
  /** GetResult implicit for fetching FlywaySchemaHistoryRow objects using plain SQL queries */
  implicit def GetResultFlywaySchemaHistoryRow(implicit e0: GR[Int], e1: GR[Option[String]], e2: GR[String], e3: GR[Option[Int]], e4: GR[java.sql.Timestamp], e5: GR[Boolean]): GR[FlywaySchemaHistoryRow] = GR{
    prs => import prs._
    (FlywaySchemaHistoryRow.apply _).tupled((<<[Int], <<?[String], <<[String], <<[String], <<[String], <<?[Int], <<[String], <<[java.sql.Timestamp], <<[Int], <<[Boolean]))
  }
  /** Table description of table flyway_schema_history. Objects of this class serve as prototypes for rows in queries.
   *  NOTE: The following names collided with Scala keywords and were escaped: type */
  class FlywaySchemaHistory(_tableTag: Tag) extends profile.api.Table[FlywaySchemaHistoryRow](_tableTag, Some("condoit"), "flyway_schema_history") {
    def * = ((installedRank, version, description, `type`, script, checksum, installedBy, installedOn, executionTime, success)).mapTo[FlywaySchemaHistoryRow]
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = ((Rep.Some(installedRank), version, Rep.Some(description), Rep.Some(`type`), Rep.Some(script), checksum, Rep.Some(installedBy), Rep.Some(installedOn), Rep.Some(executionTime), Rep.Some(success))).shaped.<>({r=>import r._; _1.map(_=> (FlywaySchemaHistoryRow.apply _).tupled((_1.get, _2, _3.get, _4.get, _5.get, _6, _7.get, _8.get, _9.get, _10.get)))}, (_:Any) => throw new Exception("Inserting into ? projection not supported."))

    /** Database column installed_rank SqlType(int4), PrimaryKey */
    val installedRank: Rep[Int] = column[Int]("installed_rank", O.PrimaryKey)
    /** Database column version SqlType(varchar), Length(50,true), Default(None) */
    val version: Rep[Option[String]] = column[Option[String]]("version", O.Length(50,varying=true), O.Default(None))
    /** Database column description SqlType(varchar), Length(200,true) */
    val description: Rep[String] = column[String]("description", O.Length(200,varying=true))
    /** Database column type SqlType(varchar), Length(20,true)
     *  NOTE: The name was escaped because it collided with a Scala keyword. */
    val `type`: Rep[String] = column[String]("type", O.Length(20,varying=true))
    /** Database column script SqlType(varchar), Length(1000,true) */
    val script: Rep[String] = column[String]("script", O.Length(1000,varying=true))
    /** Database column checksum SqlType(int4), Default(None) */
    val checksum: Rep[Option[Int]] = column[Option[Int]]("checksum", O.Default(None))
    /** Database column installed_by SqlType(varchar), Length(100,true) */
    val installedBy: Rep[String] = column[String]("installed_by", O.Length(100,varying=true))
    /** Database column installed_on SqlType(timestamp) */
    val installedOn: Rep[java.sql.Timestamp] = column[java.sql.Timestamp]("installed_on")
    /** Database column execution_time SqlType(int4) */
    val executionTime: Rep[Int] = column[Int]("execution_time")
    /** Database column success SqlType(bool) */
    val success: Rep[Boolean] = column[Boolean]("success")

    /** Index over (success) (database name flyway_schema_history_s_idx) */
    val index1 = index("flyway_schema_history_s_idx", success)
  }
  /** Collection-like TableQuery object for table FlywaySchemaHistory */
  lazy val FlywaySchemaHistory = new TableQuery(tag => new FlywaySchemaHistory(tag))

  /** Entity class storing rows of table Follows
   *  @param masterId Database column master_id SqlType(int8)
   *  @param slaveId Database column slave_id SqlType(int8) */
  case class FollowsRow(masterId: Long, slaveId: Long)
  /** GetResult implicit for fetching FollowsRow objects using plain SQL queries */
  implicit def GetResultFollowsRow(implicit e0: GR[Long]): GR[FollowsRow] = GR{
    prs => import prs._
    (FollowsRow.apply _).tupled((<<[Long], <<[Long]))
  }
  /** Table description of table follows. Objects of this class serve as prototypes for rows in queries. */
  class Follows(_tableTag: Tag) extends profile.api.Table[FollowsRow](_tableTag, Some("condoit"), "follows") {
    def * = ((masterId, slaveId)).mapTo[FollowsRow]
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = ((Rep.Some(masterId), Rep.Some(slaveId))).shaped.<>({r=>import r._; _1.map(_=> (FollowsRow.apply _).tupled((_1.get, _2.get)))}, (_:Any) => throw new Exception("Inserting into ? projection not supported."))

    /** Database column master_id SqlType(int8) */
    val masterId: Rep[Long] = column[Long]("master_id")
    /** Database column slave_id SqlType(int8) */
    val slaveId: Rep[Long] = column[Long]("slave_id")

    /** Primary key of Follows (database name follows_pkey) */
    val pk = primaryKey("follows_pkey", (masterId, slaveId))

    /** Foreign key referencing Users (database name follows_master_id_fkey) */
    lazy val usersFk1 = foreignKey("follows_master_id_fkey", masterId, Users)(r => r.userId, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.Cascade)
    /** Foreign key referencing Users (database name follows_slave_id_fkey) */
    lazy val usersFk2 = foreignKey("follows_slave_id_fkey", slaveId, Users)(r => r.userId, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.Cascade)
  }
  /** Collection-like TableQuery object for table Follows */
  lazy val Follows = new TableQuery(tag => new Follows(tag))

  /** Entity class storing rows of table Users
   *  @param userId Database column user_id SqlType(bigserial), AutoInc, PrimaryKey
   *  @param email Database column email SqlType(text)
   *  @param salt Database column salt SqlType(bytea)
   *  @param hashedPbkdf2 Database column hashed_pbkdf2 SqlType(bytea)
   *  @param username Database column username SqlType(text)
   *  @param bio Database column bio SqlType(text), Default(None)
   *  @param imageUrl Database column image_url SqlType(text), Default(None) */
  case class UsersRow(userId: Long, email: String, salt: Array[Byte], hashedPbkdf2: Array[Byte], username: String, bio: Option[String] = None, imageUrl: Option[String] = None)
  /** GetResult implicit for fetching UsersRow objects using plain SQL queries */
  implicit def GetResultUsersRow(implicit e0: GR[Long], e1: GR[String], e2: GR[Array[Byte]], e3: GR[Option[String]]): GR[UsersRow] = GR{
    prs => import prs._
    (UsersRow.apply _).tupled((<<[Long], <<[String], <<[Array[Byte]], <<[Array[Byte]], <<[String], <<?[String], <<?[String]))
  }
  /** Table description of table users. Objects of this class serve as prototypes for rows in queries. */
  class Users(_tableTag: Tag) extends profile.api.Table[UsersRow](_tableTag, Some("condoit"), "users") {
    def * = ((userId, email, salt, hashedPbkdf2, username, bio, imageUrl)).mapTo[UsersRow]
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = ((Rep.Some(userId), Rep.Some(email), Rep.Some(salt), Rep.Some(hashedPbkdf2), Rep.Some(username), bio, imageUrl)).shaped.<>({r=>import r._; _1.map(_=> (UsersRow.apply _).tupled((_1.get, _2.get, _3.get, _4.get, _5.get, _6, _7)))}, (_:Any) => throw new Exception("Inserting into ? projection not supported."))

    /** Database column user_id SqlType(bigserial), AutoInc, PrimaryKey */
    val userId: Rep[Long] = column[Long]("user_id", O.AutoInc, O.PrimaryKey)
    /** Database column email SqlType(text) */
    val email: Rep[String] = column[String]("email")
    /** Database column salt SqlType(bytea) */
    val salt: Rep[Array[Byte]] = column[Array[Byte]]("salt")
    /** Database column hashed_pbkdf2 SqlType(bytea) */
    val hashedPbkdf2: Rep[Array[Byte]] = column[Array[Byte]]("hashed_pbkdf2")
    /** Database column username SqlType(text) */
    val username: Rep[String] = column[String]("username")
    /** Database column bio SqlType(text), Default(None) */
    val bio: Rep[Option[String]] = column[Option[String]]("bio", O.Default(None))
    /** Database column image_url SqlType(text), Default(None) */
    val imageUrl: Rep[Option[String]] = column[Option[String]]("image_url", O.Default(None))

    /** Uniqueness Index over (email) (database name users_email_key) */
    val index1 = index("users_email_key", email, unique=true)
    /** Uniqueness Index over (username) (database name users_username_key) */
    val index2 = index("users_username_key", username, unique=true)
  }
  /** Collection-like TableQuery object for table Users */
  lazy val Users = new TableQuery(tag => new Users(tag))
}
