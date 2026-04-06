package tpp.domain.event

import tpp.domain.model.{AccountId, Amount, Currency, TransactionId}

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
    transactionId: TransactionId,
    sourceAccountId: AccountId,
    destinationAccountId: AccountId,
    amount: Amount,
    currency: Currency,
    occurredAt: Instant
) extends DomainEvent:
  val eventType = "TransactionCreated"

/** Транзакция завершена. */
case class TransactionCompleted(
    transactionId: TransactionId,
    sourceAccountId: AccountId,
    destinationAccountId: AccountId,
    amount: Amount,
    currency: Currency,
    occurredAt: Instant
) extends DomainEvent:
  val eventType = "TransactionCompleted"

/** Транзакция завершена с ошибкой. */
case class TransactionFailed(
    transactionId: TransactionId,
    reason: String,
    occurredAt: Instant
) extends DomainEvent:
  val eventType = "TransactionFailed"

/** Транзакция отменена. */
case class TransactionCancelled(
    transactionId: TransactionId,
    occurredAt: Instant
) extends DomainEvent:
  val eventType = "TransactionCancelled"

/** Баланс счёта обновлён. */
case class AccountBalanceUpdated(
    accountId: AccountId,
    previousBalance: Amount,
    newBalance: Amount,
    difference: BigDecimal,
    occurredAt: Instant
) extends DomainEvent:
  val eventType = "AccountBalanceUpdated"
