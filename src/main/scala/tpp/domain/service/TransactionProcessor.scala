package tpp.domain.service

import tpp.domain.error.DomainError
import tpp.domain.event.{AccountBalanceUpdated, DomainEvent, TransactionCompleted, TransactionCreated}
import tpp.domain.model.TransactionStatus.Pending
import tpp.domain.model.{Account, Amount, Currency, Transaction}
import tpp.domain.error.{AccountNotFound, AccountNotActive, InsufficientFunds, SameSourceAndDestination, ValidationError}
import tpp.domain.model.AccountStatus.Active

import java.time.Instant

/** Результат доменной операции.
  *
  * Содержит обновлённые модели и список сгенерированных событий.
  */
case class DomainResult(
    transaction: Transaction,
    sourceAccount: Account,
    destinationAccount: Account,
    events: List[DomainEvent]
)

/** Чистая доменная логика обработки транзакций.
  *
  * Не зависит от внешних эффектов (БД, Kafka, HTTP).
  * Принимает модели, возвращает новые модели + события.
  */
object TransactionProcessor:

  /** Создать и немедленно завершить транзакцию (перевод между счетами).
    *
    * Валидирует:
    *   - оба счёта существуют и активны
    *   - source != destination
    *   - сумма > 0
    *   - валюта транзакции совпадает с валютой счетов
    *   - достаточно средств
    */
  def createAndComplete(
      transaction: Transaction,
      sourceAccount: Account,
      destinationAccount: Account
  ): Either[DomainError, DomainResult] =
    for
      _ <- validateAccounts(sourceAccount, destinationAccount, transaction.amount)
      _ <- validateCurrency(transaction.currency, sourceAccount.currency, destinationAccount.currency)
      now = Instant.now()
      completedTx = transaction.complete
      newSource   = sourceAccount.debit(transaction.amount).toOption.get
      newDest     = destinationAccount.credit(transaction.amount).toOption.get
      events = List(
        TransactionCreated(
          transactionId = transaction.id.asString,
          sourceAccountId = sourceAccount.id.asString,
          destinationAccountId = destinationAccount.id.asString,
          amount = transaction.amount.value,
          currency = transaction.currency.code,
          occurredAt = now
        ),
        TransactionCompleted(
          transactionId = transaction.id.asString,
          sourceAccountId = sourceAccount.id.asString,
          destinationAccountId = destinationAccount.id.asString,
          amount = transaction.amount.value,
          currency = transaction.currency.code,
          occurredAt = now
        ),
        AccountBalanceUpdated(
          accountId = sourceAccount.id.asString,
          previousBalance = sourceAccount.balance.value,
          newBalance = newSource.balance.value,
          difference = -transaction.amount.value,
          occurredAt = now
        ),
        AccountBalanceUpdated(
          accountId = destinationAccount.id.asString,
          previousBalance = destinationAccount.balance.value,
          newBalance = newDest.balance.value,
          difference = transaction.amount.value,
          occurredAt = now
        )
      )
    yield DomainResult(completedTx, newSource, newDest, events)

  /** Отменить транзакцию (если она в Pending).
    *
    * Возвращает средства на счёт отправителя.
    */
  def cancel(
      transaction: Transaction,
      sourceAccount: Account
  ): Either[DomainError, (Transaction, Account, List[DomainEvent])] =
    if transaction.status != Pending then
      Left(ValidationError(s"Transaction ${transaction.id.asString} is not Pending (status: ${transaction.status}), cannot cancel"))
    else
      transaction.cancel match
        case Left(err) => Left(ValidationError(err))
        case Right(cancelledTx) =>
          val now = Instant.now()
          sourceAccount.credit(transaction.amount) match
            case Left(e: DomainError) => Left(e)
            case Right(refundedSource) =>
              val events = List(
                tpp.domain.event.TransactionCancelled(
                  transactionId = transaction.id.asString,
                  occurredAt = now
                ),
                AccountBalanceUpdated(
                  accountId = sourceAccount.id.asString,
                  previousBalance = sourceAccount.balance.value,
                  newBalance = refundedSource.balance.value,
                  difference = transaction.amount.value,
                  occurredAt = now
                )
              )
              Right((cancelledTx, refundedSource, events))

  /** Завершить Pending-транзакцию (алиас для createAndComplete). */
  def complete(
      transaction: Transaction,
      sourceAccount: Account,
      destinationAccount: Account
  ): Either[DomainError, DomainResult] =
    if transaction.status != Pending then
      Left(ValidationError(s"Transaction ${transaction.id.asString} is not Pending, cannot complete"))
    else
      createAndComplete(transaction, sourceAccount, destinationAccount)

  // --- Private helpers ---

  private def validateAccounts(
      source: Account,
      destination: Account,
      amount: Amount
  ): Either[DomainError, Unit] =
    if source.id == destination.id then
      Left(SameSourceAndDestination(source.id))
    else if source.status != Active then
      Left(AccountNotActive(source.id, source.status))
    else if destination.status != Active then
      Left(AccountNotActive(destination.id, destination.status))
    else if source.balance < amount then
      Left(InsufficientFunds(source.id, source.balance, amount))
    else if amount <= Amount.Zero then
      Left(ValidationError(s"Transaction amount must be positive, got: ${amount.value}"))
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
