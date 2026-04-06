package tpp.domain.model

import java.util.UUID
import scala.util.Try

/** Уникальный идентификатор счёта.
  *
  * Value Object — неизменяем, равенство по значению UUID.
  */
opaque type AccountId = UUID

object AccountId:
  def apply(value: UUID): AccountId = value
  def apply(value: String): Either[String, AccountId] =
    Try(UUID.fromString(value)).toEither.left.map(_ => s"Invalid AccountId: $value")
  def generate: AccountId = UUID.randomUUID()
  def unsafe(value: String): AccountId = apply(value).fold(sys.error, identity)

  extension (id: AccountId)
    def value: UUID      = id
    def asString: String = id.toString
