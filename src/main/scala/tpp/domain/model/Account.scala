package tpp.domain.model

import tpp.domain.error.{AccountNotActive, DomainError, InsufficientFunds, ValidationError}

/** Статус счёта. */
enum AccountStatus:
  case Active, Frozen, Closed

/** Банковский счёт.
  *
  * Иммутабельная модель. Изменение баланса возвращает новый экземпляр.
  */
case class Account(
    id: AccountId,
    balance: Amount,
    currency: Currency,
    status: AccountStatus,
    version: Long = 0L
):
  /** Списать средства. Возвращает новый Account или ошибку. */
  def debit(amount: Amount): Either[DomainError, Account] =
    if status != AccountStatus.Active then
      Left(AccountNotActive(id, status))
    else if balance < amount then
      Left(InsufficientFunds(id, balance, amount))
    else
      Right(copy(balance = balance - amount, version = version + 1))

  /** Зачислить средства. */
  def credit(amount: Amount): Either[DomainError, Account] =
    if status != AccountStatus.Active then
      Left(AccountNotActive(id, status))
    else if balance.value + amount.value > Amount.MaxValue then
      Left(ValidationError(s"Credit would exceed max balance for account $id"))
    else
      Right(copy(balance = balance + amount, version = version + 1))

object Account:
  def create(id: AccountId, initialBalance: Amount, currency: Currency): Account =
    Account(
      id = id,
      balance = initialBalance,
      currency = currency,
      status = AccountStatus.Active,
      version = 0L
    )
