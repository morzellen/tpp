package tpp.domain.error

import tpp.domain.model.{AccountId, Amount, TransactionId}
import tpp.domain.model.AccountStatus

import scala.util.control.NoStackTrace

/** ADT доменных ошибок. */
sealed trait DomainError extends NoStackTrace:
  def message: String

/** Счёт не найден. */
case class AccountNotFound(id: AccountId) extends DomainError:
  val message = s"Account not found: $id"

/** Счёт не активен. */
case class AccountNotActive(id: AccountId, status: AccountStatus) extends DomainError:
  val message = s"Account $id is not active (status: $status)"

/** Недостаточно средств. */
case class InsufficientFunds(id: AccountId, available: Amount, required: Amount) extends DomainError:
  val message = s"Insufficient funds on account $id: available $available, required $required"

/** Транзакция не найдена. */
case class TransactionNotFound(id: TransactionId) extends DomainError:
  val message = s"Transaction not found: $id"

/** Некорректная транзакция (счёт отправителя и получателя совпадают). */
case class SameSourceAndDestination(id: AccountId) extends DomainError:
  val message = s"Source and destination accounts must differ, got: $id"

/** Ошибка валидации. */
case class ValidationError(message: String) extends DomainError
