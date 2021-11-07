package com.marmaladesky.realworld.services

import cats.data.OptionT
import cats.effect.kernel.Sync
import cats.implicits._
import com.marmaladesky.realworld.db.{FollowsRepo, UsersRepo}
import com.marmaladesky.realworld.db.gen.Tables.UsersRow
import com.marmaladesky.realworld.services.UsersService.{Profile, User}
import pdi.jwt.{JwtAlgorithm, JwtCirce, JwtClaim}

import java.nio.charset.StandardCharsets
import java.time.Clock
import java.util.UUID
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.{PBEKeySpec, SecretKeySpec}
import scala.concurrent.duration._

trait UsersService[F[_]] {

  def genJwtToken(userId: Long, password: String): F[String]

  def verifyToken(token: String): OptionT[F, JwtClaim]

  def registration(username: String, email: String, password: String): F[User]

  def login(email: String, password: String): OptionT[F, User]

  def getUser(userId: Long): OptionT[F, User]

  def getUser(username: String): OptionT[F, User]

  def updateUser(update: User.UserPartialUpdate): OptionT[F, User]

  def addFollow(masterUsername: String, slaveId: Long): OptionT[F, Profile]

  def deleteFollow(masterUsername: String, slave: Long): OptionT[F, Profile]

}

object UsersService {

  // JWT
  // In this example we just generate a random secret key.
  // In real life, you most probably will create a separated service to own the secret key, generate tokens and share
  // the public key to allow other services to validate the tokens.
  private val algo = JwtAlgorithm.HS256
  private val SecretKey = new SecretKeySpec(UUID.randomUUID().toString.getBytes("UTF-8"), algo.fullName)
  private val DefaultTokenLength = 30.days

  // Password hashing
  private val SecretKeysFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512")
  private val DefaultIterations = 64000
  private val DefaultKeyLength = 512


  case class User(userId: Long, username: String, email: String, bio: Option[String], image: Option[String])

  object User {

    case class UserPartialUpdate(
      userId: Long,
      email: Option[String],
      username: Option[String],
      password: Option[String],
      bio: Option[String],
      image: Option[String]
    )

  }

  case class Profile(username: String, bio: Option[String], image: Option[String], following: Boolean)


  def impl[F[_]: Sync](
    clock: Clock,
    usersRepo: UsersRepo[F],
    followsRepo: FollowsRepo[F]
  ): UsersService[F] = new UsersService[F] {

    override def genJwtToken(userId: Long, password: String): F[String] = {
      val claim = JwtClaim()
        .issuedNow(clock)
        .expiresIn(DefaultTokenLength.toSeconds)(clock)
        .about(userId.toString)
      JwtCirce.encode(claim, SecretKey, algo).pure[F]
    }

    override def verifyToken(token: String): OptionT[F, JwtClaim] = {
      for {
        claim <- OptionT.fromOption[F] { JwtCirce.decode(token, SecretKey).toOption }
        validClaim <- OptionT.when[F, JwtClaim](claim.isValid(clock)) { claim }
      } yield validClaim
    }

    private def generateSalt(): Array[Byte] = {
      UUID.randomUUID().toString.getBytes(StandardCharsets.UTF_8)
    }

    private def generateKey(password: String, salt: Array[Byte]): Array[Byte] = {
      val spec = new PBEKeySpec(password.toCharArray, salt, DefaultIterations, DefaultKeyLength)
      SecretKeysFactory.generateSecret(spec).getEncoded
    }

    private def readDbRow(row: UsersRow): User = {
      User(
        userId = row.userId,
        username = row.username,
        email = row.email,
        bio = row.bio,
        image = row.imageUrl
      )
    }


    override def registration(username: String, email: String, password: String): F[User] = {
      val salt = generateSalt()
      val hash = generateKey(password, salt)
      usersRepo.createUser(username, email, salt, hash).map { readDbRow }
    }

    override def login(email: String, password: String): OptionT[F, User] = {
      for {
        userRow <- usersRepo.readUserByEmail(email)
        salt = userRow.salt
        hash = generateKey(password, salt)
        user <- OptionT.when[F, User](userRow.hashedPbkdf2 sameElements hash) { readDbRow(userRow) }
      } yield user
    }

    override def getUser(userId: Long): OptionT[F, User] = {
      usersRepo.getUser(userId).map { readDbRow }
    }

    override def getUser(username: String): OptionT[F, User] = {
      usersRepo.getUserByUsername(username).map { readDbRow }
    }

    override def updateUser(update: User.UserPartialUpdate): OptionT[F, User] = {

      val (salt, hash) = update.password match {
        case Some(password) =>
          val newSalt = generateSalt()
          newSalt.some -> generateKey(password, newSalt).some
        case None =>
          None -> None
      }

      for {
        updated <- usersRepo.updateUser(
          userId = update.userId,
          email = update.email,
          username = update.username,
          bio = update.bio,
          image = update.image,
          salt = salt,
          hashedPbkdf2 = hash,
        ).map { readDbRow }
      } yield updated

    }


    override def addFollow(masterUsername: String, slaveId: Long): OptionT[F, Profile] = {
      usersRepo
        .getUserByUsername(masterUsername)
        .semiflatMap { master =>
          for {
            _ <- followsRepo.addFollow(master.userId, slaveId)
          } yield Profile(master.username, master.bio, master.imageUrl, following = true)
        }
    }

    override def deleteFollow(masterUsername: String, slaveId: Long): OptionT[F, Profile] = {
      usersRepo
        .getUserByUsername(masterUsername)
        .semiflatMap { master =>
          for {
            _ <- followsRepo.deleteFollow(master.userId, slaveId)
          } yield Profile(master.username, master.bio, master.imageUrl, following = false)
        }
    }

  }

}