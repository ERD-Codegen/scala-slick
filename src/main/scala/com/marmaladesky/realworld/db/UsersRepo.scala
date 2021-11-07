package com.marmaladesky.realworld.db

import cats.data.OptionT
import cats.effect.Async
import com.marmaladesky.realworld.ReadableError
import com.marmaladesky.realworld.db.DbProfile.api._
import com.marmaladesky.realworld.db.gen.Tables
import org.http4s.Status
import slick.dbio.SuccessAction
import slick.jdbc.JdbcBackend
import slick.jdbc.JdbcBackend.Database

import scala.concurrent.ExecutionContext

class UsersRepo[F[_]: Async](db: Database, ec: ExecutionContext) {

  private implicit val database: JdbcBackend.Database = db
  private implicit val executionContext: ExecutionContext = ec

  def createUser(username: String, email: String, salt: Array[Byte], hashedPbkdf2: Array[Byte]): F[Tables.UsersRow] = {

    val createUserRow = Tables.UsersRow(
      userId = 0L,
      email = email,
      salt = salt,
      hashedPbkdf2 = hashedPbkdf2,
      username = username,
      bio = None,
      imageUrl = None
    )

    val q = for {
      existingLogin <- Tables.Users.filter(_.username === username).result.map { _.headOption }
      existingEmail <- Tables.Users.filter(_.email === email).result.map { _.headOption }
      _ = if (existingLogin.isDefined)
            throw ReadableError(s"Username '$username' already exists", codeHint = Status.BadRequest.code)
          else if (existingEmail.isDefined) {
            throw ReadableError(s"Email '$email' already exists", codeHint = Status.BadRequest.code)
          } else ()
      id <- (Tables.Users returning Tables.Users.map(_.userId)) += createUserRow
      userCreated <- Tables.Users
        .filter { _.userId === id }
        .result
        .map { _.headOption.getOrElse { throw new RuntimeException(s"Failed to get created user with id '$id'") } }
    } yield userCreated

    liftQ { q.transactionally }
  }

  def getUser(userId: Long): OptionT[F, Tables.UsersRow] = {
    val q = Tables.Users.filter(_.userId === userId).result.map(_.headOption)

    liftQ { q.transactionally }
  }

  def getUserByUsername(username: String): OptionT[F, Tables.UsersRow] = {
    val q = Tables.Users.filter(_.username === username).result.map(_.headOption)

    liftQ { q.transactionally }
  }

  def readUserByEmail(email: String): OptionT[F, Tables.UsersRow] = {
    val q = Tables.Users.filter(_.email === email).result.map(_.headOption)

    liftQ { q.transactionally }
  }

  def updateUser(
    userId: Long,
    email: Option[String] = None,
    username: Option[String] = None,
    bio: Option[String] = None,
    image: Option[String] = None,
    salt: Option[Array[Byte]] = None,
    hashedPbkdf2: Option[Array[Byte]] = None
  ): OptionT[F, Tables.UsersRow] = {

    val q = for {
      existingOpt <-
        Tables.Users
          .filter { _.userId === userId }
          .forUpdate
          .result
          .map { _.headOption }
      _ <- existingOpt match {
        case Some(existing) =>
          val row = existing.copy(
            email = email.getOrElse(existing.email),
            salt = salt.getOrElse(existing.salt),
            hashedPbkdf2 = hashedPbkdf2.getOrElse(existing.hashedPbkdf2),
            username = username.getOrElse(existing.username),
            bio = bio.orElse(existing.bio),
            imageUrl = image.orElse(existing.imageUrl)
          )
          Tables.Users.filter { _.userId === userId }.update(row)

        case None =>
          SuccessAction(None)
      }
      updatedRow <- if (existingOpt.isDefined) {
        Tables.Users
          .filter { _.userId === userId }
          .result
          .map { _.headOption }
      } else {
        SuccessAction(None)
      }

    } yield updatedRow

    liftQ { q.transactionally }
  }

}