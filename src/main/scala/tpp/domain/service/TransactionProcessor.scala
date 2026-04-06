package tpp.domain.service

import tpp.domain.error.DomainError
import tpp.domain.event.{AccountBalanceUpdated, DomainEvent, TransactionCancelled, TransactionCompleted, TransactionCreated}
import tpp.domain.model.TransactionStatus.Pending
import tpp.domain.model.{Account, AccountId, Amount, Currency, Transaction, TransactionId}
import tpp.domain.error.{AccountNotActive, InsufficientFunds, SameSourceAndDestination, TransactionNotCancellable, ValidationError}
import tpp.domain.model.AccountStatus.Active

import java.time.Instant

/** Результат доменной операции. */
case class DomainResult(
    transaction: Transaction,
    sourceAccount: Account,
    destinationAccount: Account,
    events: List[DomainEvent]
)

/** Результат отмены транзакции. */
case class CancelResult(
    transaction: Transaction,
    sourceAccount: Account,
    events: List[DomainEvent]
)

/** Чистая доменная логика обработки транзакций. */
object TransactionProcessor:

  /** Создать и немедленно завершить транзакцию (перевод между счетами). */
  def createAndComplete(
      transaction: Transaction,
      sourceAccount: Account,
      destinationAccount: Account
  ): Either[DomainError, DomainResult] =
    if transaction.status != Pending then
      Left(ValidationError(s"Transaction ${transaction.id.asString} is not Pending, cannot complete"))
    else
      for
        _ <- validateAccounts(sourceAccount, destinationAccount, transaction.amount)
        _ <- validateCurrency(transaction.currency, sourceAccount.currency, destinationAccount.currency)
        newSource <- sourceAccount.debit(transaction.amount)
        newDest   <- destinationAccount.credit(transaction.amount)
      yield
        val completedTx = transaction.copy(
          status = tpp.domain.model.TransactionStatus.Completed,
          updatedAt = Instant.now(),
          version = transaction.version + 1
        )
        val now = Instant.now()
        val txId    = transaction.id
        val srcId   = sourceAccount.id
        val dstId   = destinationAccount.id
        val curr    = transaction.currency
        val amt     = transaction.amount
        val events = List(
          TransactionCreated(txId, srcId, dstId, amt, curr, now),
          TransactionCompleted(txId, srcId, dstId, amt, curr, now),
          AccountBalanceUpdated(srcId, sourceAccount.balance, newSource.balance, -(amt.value), now),
          AccountBalanceUpdated(dstId, destinationAccount.balance, newDest.balance, amt.value, now)
        )
        DomainResult(completedTx, newSource, newDest, events)

  /** Отменить транзакцию (если она в Pending). */
  def cancel(
      transaction: Transaction,
      sourceAccount: Account
  ): Either[DomainError, CancelResult] =
    if sourceAccount.id != transaction.sourceAccountId then
      Left(ValidationError(s"Account ${sourceAccount.id.asString} is not the source of transaction ${transaction.id.asString}"))
    else if transaction.status != Pending then
      Left(TransactionNotCancellable(transaction.id, transaction.status))
    else
      sourceAccount.credit(transaction.amount) match
        case Left(e: DomainError) => Left(e)
        case Right(refundedSource) =>
          val now = Instant.now()
          val cancelledTx = transaction.copy(
            status = tpp.domain.model.TransactionStatus.Cancelled,
            updatedAt = now,
            version = transaction.version + 1
          )
          val events = List(
            TransactionCancelled(transaction.id, now),
            AccountBalanceUpdated(sourceAccount.id, sourceAccount.balance, refundedSource.balance, transaction.amount.value, now)
          )
          Right(CancelResult(cancelledTx, refundedSource, events))

  /** Завершить Pending-транзакцию. */
  def complete(
      transaction: Transaction,
      sourceAccount: Account,
      destinationAccount: Account
  ): Either[DomainError, DomainResult] =
    createAndComplete(transaction, sourceAccount, destinationAccount)

  // --- Private helpers ---

  private def validateAccounts(
      source: Account,
      destination: Account,
      amount: Amount
  ): Either[DomainError, Unit] =
    if amount <= Amount.Zero then
      Left(ValidationError(s"Transaction amount must be positive, got: ${amount.value}"))
    else if source.id == destination.id then
      Left(SameSourceAndDestination(source.id))
    else if source.status != Active then
      Left(AccountNotActive(source.id, source.status))
    else if destination.status != Active then
      Left(AccountNotActive(destination.id, destination.status))
    else if source.balance < amount then
      Left(InsufficientFunds(source.id, source.balance, amount))
    else
      Right(())

  private def validateCurrency(
      txCurrency: Currency,
      sourceCurrency: Currency,
      destCurrency: Currency
  ): Either[DomainError, Unit] =
    if txCurrency != sourceCurrency then
      Left(ValidationError(s"Transaction currency $txCurrency does not match source account currency $sourceCurrency"))
    else if txCurrency != destCurrency then
      Left(ValidationError(s"Transaction currency $txCurrency does not match destination account currency $destCurrency"))
    else
      Right(())
