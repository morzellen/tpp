package tpp.domain.model

import tpp.domain.error.{DomainError, TransactionNotCancellable}

import java.time.Instant

/** Статус транзакции. */
enum TransactionStatus:
  case Pending, Completed, Failed, Cancelled

/** Финансовая транзакция.
  *
  * Иммутабельная модель. Изменение состояния возвращает новый экземпляр.
  */
case class Transaction(
    id: TransactionId,
    sourceAccountId: AccountId,
    destinationAccountId: AccountId,
    amount: Amount,
    currency: Currency,
    status: TransactionStatus,
    createdAt: Instant,
    updatedAt: Instant,
    version: Long = 0L,
    failureReason: Option[String] = None
):
  /** Завершить транзакцию. Только из Pending. */
  def complete: Either[String, Transaction] =
    status match
      case TransactionStatus.Pending =>
        Right(copy(status = TransactionStatus.Completed, updatedAt = Instant.now(), version = version + 1))
      case _ =>
        Left(s"Cannot complete transaction in status $status")

  /** Завершить с ошибкой. Только из Pending. */
  def fail(reason: String): Either[String, Transaction] =
    status match
      case TransactionStatus.Pending =>
        Right(copy(status = TransactionStatus.Failed, updatedAt = Instant.now(), version = version + 1, failureReason = Some(reason)))
      case _ =>
        Left(s"Cannot fail transaction in status $status")

  /** Отменить транзакцию. Только из Pending. */
  def cancel: Either[DomainError, Transaction] =
    status match
      case TransactionStatus.Pending =>
        Right(copy(status = TransactionStatus.Cancelled, updatedAt = Instant.now(), version = version + 1))
      case _ =>
        Left(TransactionNotCancellable(id, status))

object Transaction:
  def create(
      id: TransactionId,
      sourceAccountId: AccountId,
      destinationAccountId: AccountId,
      amount: Amount,
      currency: Currency,
      now: Instant
  ): Transaction =
    Transaction(
      id = id,
      sourceAccountId = sourceAccountId,
      destinationAccountId = destinationAccountId,
      amount = amount,
      currency = currency,
      status = TransactionStatus.Pending,
      createdAt = now,
      updatedAt = now,
      version = 0L
    )
