package tpp.domain.model

import scala.util.Try
import scala.util.control.NoStackTrace

/** Сумма денежных средств.
  *
  * Инварианты:
  *   - `value` не может быть отрицательным
  *   - `value` не может превышать 1_000_000_000 (1 миллиард)
  *   - Масштаб — 2 десятичных знака (копейки/центы)
  */
opaque type Amount = BigDecimal

object Amount:
  val MaxValue: BigDecimal = BigDecimal(1_000_000_000L)
  val Zero: Amount         = BigDecimal(0)

  def apply(value: BigDecimal): Either[AmountError, Amount] =
    if value < 0 then Left(AmountNegative(value))
    else if value > MaxValue then Left(AmountExceedsMax(value, MaxValue))
    else
      val scaled = value.setScale(2, BigDecimal.RoundingMode.HALF_UP)
      Right(scaled)

  def unsafe(value: BigDecimal): Amount =
    apply(value).fold(err => throw err, identity)

  def fromString(s: String): Either[AmountError, Amount] =
    Try(BigDecimal(s)).toEither
      .left.map(_ => AmountParseError(s))
      .flatMap(apply)

  extension (a: Amount)
    def value: BigDecimal = a
    def +(b: Amount): Amount =
      val result = a.value + b.value
      unsafe(result)
    def -(b: Amount): Amount =
      val result = a.value - b.value
      unsafe(result)
    def *(factor: BigDecimal): Amount =
      val result = a.value * factor
      unsafe(result)
    def >=(b: Amount): Boolean = a.value >= b.value
    def >(b: Amount): Boolean  = a.value > b.value
    def <=(b: Amount): Boolean = a.value <= b.value
    def <(b: Amount): Boolean  = a.value < b.value
    def isZero: Boolean        = a.value == 0

  sealed trait AmountError extends NoStackTrace:
    def msg: String
    override def toString: String = msg

  case class AmountNegative(value: BigDecimal) extends AmountError:
    val msg = s"Amount cannot be negative: $value"

  case class AmountExceedsMax(value: BigDecimal, max: BigDecimal) extends AmountError:
    val msg = s"Amount $value exceeds max $max"

  case class AmountParseError(input: String) extends AmountError:
    val msg = s"Cannot parse Amount: $input"
