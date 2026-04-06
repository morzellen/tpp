package tpp.domain.event

import java.time.Instant

/** Базовый тип для всех доменных событий.
  *
  * Сериализация/десериализация делегируется infrastructure layer (Circe).
  */
sealed trait DomainEvent:
  def eventType: String
  def occurredAt: Instant

/** Транзакция создана (Pending). */
case class TransactionCreated(
    transactionId: String,
    sourceAccountId: String,
    destinationAccountId: String,
    amount: BigDecimal,
    currency: String,
    occurredAt: Instant
) extends DomainEvent:
  val eventType = "TransactionCreated"

/** Транзакция завершена. */
case class TransactionCompleted(
    transactionId: String,
    sourceAccountId: String,
    destinationAccountId: String,
    amount: BigDecimal,
    currency: String,
    occurredAt: Instant
) extends DomainEvent:
  val eventType = "TransactionCompleted"

/** Транзакция завершена с ошибкой. */
case class TransactionFailed(
    transactionId: String,
    reason: String,
    occurredAt: Instant
) extends DomainEvent:
  val eventType = "TransactionFailed"

/** Транзакция отменена. */
case class TransactionCancelled(
    transactionId: String,
    occurredAt: Instant
) extends DomainEvent:
  val eventType = "TransactionCancelled"

/** Баланс счёта обновлён. */
case class AccountBalanceUpdated(
    accountId: String,
    previousBalance: BigDecimal,
    newBalance: BigDecimal,
    difference: BigDecimal,
    occurredAt: Instant
) extends DomainEvent:
  val eventType = "AccountBalanceUpdated"
