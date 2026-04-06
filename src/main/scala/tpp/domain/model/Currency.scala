package tpp.domain.model

/** Валюта в формате ISO 4217.
  *
  * Поддерживаемые валюты: USD, EUR, RUB.
  */
enum Currency(val code: String, val symbol: String, val decimalDigits: Int = 2):
  case USD extends Currency("USD", "$")
  case EUR extends Currency("EUR", "€")
  case RUB extends Currency("RUB", "₽")

object Currency:
  def fromCode(code: String): Either[String, Currency] =
    values.find(_.code.equalsIgnoreCase(code)) match
      case Some(c) => Right(c)
      case None    => Left(s"Unsupported currency: $code. Supported: ${values.map(_.code).mkString(", ")}")

  def unsafe(code: String): Currency =
    fromCode(code).fold(sys.error, identity)
