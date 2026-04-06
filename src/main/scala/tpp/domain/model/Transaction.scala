package tpp.domain.model

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
  def complete: Transaction =
    copy(status = TransactionStatus.Completed, updatedAt = Instant.now(), version = version + 1)

  def fail(reason: String): Transaction =
    copy(status = TransactionStatus.Failed, updatedAt = Instant.now(), version = version + 1, failureReason = Some(reason))

  def cancel: Either[String, Transaction] =
    status match
      case TransactionStatus.Pending =>
        Right(copy(status = TransactionStatus.Cancelled, updatedAt = Instant.now(), version = version + 1))
      case _ =>
        Left(s"Cannot cancel transaction in status $status")

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
