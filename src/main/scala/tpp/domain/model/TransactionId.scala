package tpp.domain.model

import java.util.UUID
import scala.util.Try

/** Уникальный идентификатор транзакции.
  *
  * Value Object — неизменяем, равенство по значению UUID.
  */
opaque type TransactionId = UUID

object TransactionId:
  def apply(value: UUID): TransactionId = value
  def apply(value: String): Either[String, TransactionId] =
    Try(UUID.fromString(value)).toEither.left.map(_ => s"Invalid TransactionId: $value")
  def generate: TransactionId = UUID.randomUUID()
  def unsafe(value: String): TransactionId = apply(value).fold(sys.error, identity)

  extension (id: TransactionId)
    def value: UUID      = id
    def asString: String = id.toString
