package tpp.domain

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks._
import tpp.domain.error.{AccountNotActive, DomainError, InsufficientFunds, SameSourceAndDestination, ValidationError}
import tpp.domain.event.{AccountBalanceUpdated, DomainEvent, TransactionCancelled, TransactionCompleted, TransactionCreated}
import tpp.domain.model.AccountStatus.{Active, Closed, Frozen}
import tpp.domain.model.TransactionStatus.{Cancelled, Completed, Failed, Pending}
import tpp.domain.model.{Account, AccountId, Amount, Currency, Transaction, TransactionId}
import tpp.domain.service.{DomainResult, TransactionProcessor}

import java.time.Instant
import java.util.UUID

// ==================== Amount Tests ====================

class AmountSpec extends AnyFlatSpec with Matchers:

  "Amount.apply" should "accept valid positive values" in:
    Amount(BigDecimal("100.00")).isRight shouldBe true
    Amount(BigDecimal("0")).isRight shouldBe true
    Amount(BigDecimal("0.01")).isRight shouldBe true
    Amount(BigDecimal("100.00")).map(_.value) shouldBe Right(BigDecimal("100.00"))

  it should "reject negative values" in:
    Amount(BigDecimal("-0.01")).isLeft shouldBe true
    Amount(BigDecimal("-1000")).isLeft shouldBe true

  it should "reject values exceeding max" in:
    Amount(BigDecimal("1000000001")).isLeft shouldBe true

  it should "scale to 2 decimal places" in:
    Amount(BigDecimal("100.1234")).map(_.value) shouldBe Right(BigDecimal("100.12"))
    Amount(BigDecimal("100.126")).map(_.value) shouldBe Right(BigDecimal("100.13"))

  "Amount extension" should "support addition" in:
    val a = Amount.unsafe(BigDecimal("100.00"))
    val b = Amount.unsafe(BigDecimal("50.00"))
    (a + b).value shouldBe BigDecimal("150.00")

  it should "support subtraction" in:
    val a = Amount.unsafe(BigDecimal("100.00"))
    val b = Amount.unsafe(BigDecimal("30.00"))
    (a - b).value shouldBe BigDecimal("70.00")

  it should "support multiplication" in:
    val a = Amount.unsafe(BigDecimal("100.00"))
    (a * BigDecimal("2.5")).value shouldBe BigDecimal("250.00")

  it should "support comparisons" in:
    val a = Amount.unsafe(BigDecimal("100.00"))
    val b = Amount.unsafe(BigDecimal("50.00"))
    (a > b) shouldBe true
    (a >= b) shouldBe true
    (a < b) shouldBe false
    (a <= b) shouldBe false
    (a == a) shouldBe true

  it should "detect zero" in:
    Amount.Zero.isZero shouldBe true
    Amount.unsafe(BigDecimal("0.01")).isZero shouldBe false

  // --- Property-based tests without ScalaCheck ---

  "Amount addition" should "be closed for valid amounts" in:
    val testCases = Table(
      ("a", "b", "expected"),
      ("0", "0", "0.00"),
      ("10.00", "20.00", "30.00"),
      ("0.01", "0.01", "0.02"),
      ("999.99", "0.01", "1000.00")
    )
    forAll(testCases) { (a: String, b: String, expected: String) =>
      val amountA = Amount.unsafe(BigDecimal(a))
      val amountB = Amount.unsafe(BigDecimal(b))
      val sum = amountA + amountB
      sum.value shouldBe BigDecimal(expected)
      sum.value should be >= BigDecimal(0)
    }

  "Amount addition" should "preserve identity with zero" in:
    val testCases = Table("value", "0", "100.00", "0.01", "999999.99")
    forAll(testCases) { (v: String) =>
      val amount = Amount.unsafe(BigDecimal(v))
      (amount + Amount.Zero).value shouldBe amount.value
    }

// ==================== Currency Tests ====================

class CurrencySpec extends AnyFlatSpec with Matchers:

  "Currency.fromCode" should "accept valid currencies" in:
    Currency.fromCode("USD") shouldBe Right(Currency.USD)
    Currency.fromCode("EUR") shouldBe Right(Currency.EUR)
    Currency.fromCode("RUB") shouldBe Right(Currency.RUB)

  it should "be case-insensitive" in:
    Currency.fromCode("usd") shouldBe Right(Currency.USD)
    Currency.fromCode("Eur") shouldBe Right(Currency.EUR)

  it should "reject invalid codes" in:
    Currency.fromCode("GBP").isLeft shouldBe true
    Currency.fromCode("XYZ").isLeft shouldBe true

  "Currency.unsafe" should "throw for invalid codes" in:
    assertThrows[RuntimeException](Currency.unsafe("GBP"))

// ==================== TransactionId & AccountId Tests ====================

class IdSpec extends AnyFlatSpec with Matchers:

  "TransactionId.generate" should "produce unique IDs" in:
    TransactionId.generate should not be TransactionId.generate

  "TransactionId.apply(String)" should "parse valid UUIDs" in:
    val uuid = UUID.randomUUID().toString
    TransactionId(uuid).isRight shouldBe true

  it should "reject invalid strings" in:
    TransactionId("not-a-uuid").isLeft shouldBe true

  "TransactionId.unsafe" should "throw for invalid strings" in:
    assertThrows[RuntimeException](TransactionId.unsafe("bad"))

  "TransactionId.asString" should "round-trip correctly" in:
    val id = TransactionId.generate
    TransactionId.unsafe(id.asString) shouldBe id

  "AccountId" should "behave the same as TransactionId" in:
    val id = AccountId.generate
    AccountId.unsafe(id.asString) shouldBe id

// ==================== Account Tests ====================

class AccountSpec extends AnyFlatSpec with Matchers:

  private val usd = Currency.USD
  private val id  = AccountId.generate

  "Account.create" should "create active account with given balance" in:
    val balance = Amount.unsafe(BigDecimal("1000.00"))
    val acc     = Account.create(id, balance, usd)
    acc.id shouldBe id
    acc.balance shouldBe balance
    acc.currency shouldBe usd
    acc.status shouldBe Active
    acc.version shouldBe 0L

  "Account.debit" should "reduce balance for active account with sufficient funds" in:
    val acc     = Account.create(id, Amount.unsafe(BigDecimal("1000.00")), usd)
    val result  = acc.debit(Amount.unsafe(BigDecimal("300.00")))
    result.isRight shouldBe true
    result.toOption.get.balance shouldBe Amount.unsafe(BigDecimal("700.00"))
    result.toOption.get.version shouldBe 1L

  it should "fail for insufficient funds" in:
    val acc    = Account.create(id, Amount.unsafe(BigDecimal("100.00")), usd)
    val result = acc.debit(Amount.unsafe(BigDecimal("200.00")))
    result.isLeft shouldBe true
    result.left.get.isInstanceOf[InsufficientFunds] shouldBe true

  it should "fail for non-active account" in:
    val acc    = Account.create(id, Amount.unsafe(BigDecimal("1000.00")), usd).copy(status = Frozen)
    val result = acc.debit(Amount.unsafe(BigDecimal("100.00")))
    result.isLeft shouldBe true
    result.left.get.isInstanceOf[AccountNotActive] shouldBe true

  "Account.credit" should "increase balance for active account" in:
    val acc    = Account.create(id, Amount.unsafe(BigDecimal("1000.00")), usd)
    val result = acc.credit(Amount.unsafe(BigDecimal("500.00")))
    result.isRight shouldBe true
    result.toOption.get.balance shouldBe Amount.unsafe(BigDecimal("1500.00"))
    result.toOption.get.version shouldBe 1L

  it should "fail for closed account" in:
    val acc    = Account.create(id, Amount.unsafe(BigDecimal("1000.00")), usd).copy(status = Closed)
    val result = acc.credit(Amount.unsafe(BigDecimal("100.00")))
    result.isLeft shouldBe true

// ==================== Transaction Tests ====================

class TransactionSpec extends AnyFlatSpec with Matchers:

  private val txId  = TransactionId.generate
  private val srcId = AccountId.generate
  private val dstId = AccountId.generate
  private val now   = Instant.now()

  "Transaction.create" should "create Pending transaction" in:
    val amount = Amount.unsafe(BigDecimal("500.00"))
    val tx     = Transaction.create(txId, srcId, dstId, amount, Currency.USD, now)
    tx.id shouldBe txId
    tx.status shouldBe Pending
    tx.amount shouldBe amount
    tx.createdAt shouldBe now
    tx.updatedAt shouldBe now
    tx.version shouldBe 0L
    tx.failureReason shouldBe None

  "Transaction.complete" should "transition to Completed" in:
    val tx       = Transaction.create(txId, srcId, dstId, Amount.unsafe(BigDecimal("100.00")), Currency.USD, now)
    val complete = tx.complete
    complete.status shouldBe Completed
    complete.version shouldBe 1L
    complete.updatedAt should not be now

  "Transaction.fail" should "transition to Failed with reason" in:
    val tx    = Transaction.create(txId, srcId, dstId, Amount.unsafe(BigDecimal("100.00")), Currency.USD, now)
    val failed = tx.fail("Insufficient funds")
    failed.status shouldBe Failed
    failed.failureReason shouldBe Some("Insufficient funds")
    failed.version shouldBe 1L

  "Transaction.cancel" should "succeed for Pending transaction" in:
    val tx     = Transaction.create(txId, srcId, dstId, Amount.unsafe(BigDecimal("100.00")), Currency.USD, now)
    val result = tx.cancel
    result.isRight shouldBe true
    result.toOption.get.status shouldBe Cancelled
    result.toOption.get.version shouldBe 1L

  it should "fail for Completed transaction" in:
    val tx     = Transaction.create(txId, srcId, dstId, Amount.unsafe(BigDecimal("100.00")), Currency.USD, now).complete
    val result = tx.cancel
    result.isLeft shouldBe true

  it should "fail for Failed transaction" in:
    val tx     = Transaction.create(txId, srcId, dstId, Amount.unsafe(BigDecimal("100.00")), Currency.USD, now).fail("err")
    val result = tx.cancel
    result.isLeft shouldBe true

// ==================== TransactionProcessor Tests ====================

class TransactionProcessorSpec extends AnyFlatSpec with Matchers:

  private val srcId   = AccountId.generate
  private val dstId   = AccountId.generate
  private val txId    = TransactionId.generate
  private val usd     = Currency.USD
  private val balance = Amount.unsafe(BigDecimal("10000.00"))
  private val amount  = Amount.unsafe(BigDecimal("1500.00"))

  private def makeAccounts(srcBal: Amount = balance, dstBal: Amount = balance): (Account, Account) =
    (Account.create(srcId, srcBal, usd), Account.create(dstId, dstBal, usd))

  private def makeTx(txAmount: Amount = amount): Transaction =
    Transaction.create(txId, srcId, dstId, txAmount, usd, Instant.now())

  "TransactionProcessor.createAndComplete" should "succeed for valid transfer" in:
    val (src, dst) = makeAccounts()
    val tx         = makeTx()
    val result     = TransactionProcessor.createAndComplete(tx, src, dst)
    result.isRight shouldBe true
    val DomainResult(completedTx, newSrc, newDst, events) = result.toOption.get
    completedTx.status shouldBe Completed
    newSrc.balance shouldBe Amount.unsafe(BigDecimal("8500.00"))
    newDst.balance shouldBe Amount.unsafe(BigDecimal("11500.00"))
    newSrc.version shouldBe 1L
    newDst.version shouldBe 1L
    events should have size 4
    events.head.isInstanceOf[TransactionCreated] shouldBe true
    events(1).isInstanceOf[TransactionCompleted] shouldBe true
    events(2).isInstanceOf[AccountBalanceUpdated] shouldBe true
    events(3).isInstanceOf[AccountBalanceUpdated] shouldBe true

  it should "fail for same source and destination" in:
    val acc  = Account.create(srcId, balance, usd)
    val tx   = Transaction.create(txId, srcId, srcId, amount, usd, Instant.now())
    val result = TransactionProcessor.createAndComplete(tx, acc, acc)
    result.isLeft shouldBe true
    result.left.get.isInstanceOf[SameSourceAndDestination] shouldBe true

  it should "fail for insufficient funds" in:
    val (src, dst) = makeAccounts(Amount.unsafe(BigDecimal("100.00")), balance)
    val tx         = makeTx()
    val result     = TransactionProcessor.createAndComplete(tx, src, dst)
    result.isLeft shouldBe true
    result.left.get.isInstanceOf[InsufficientFunds] shouldBe true

  it should "fail for frozen source account" in:
    val src  = Account.create(srcId, balance, usd).copy(status = Frozen)
    val dst  = Account.create(dstId, balance, usd)
    val tx   = makeTx()
    val result = TransactionProcessor.createAndComplete(tx, src, dst)
    result.isLeft shouldBe true

  it should "fail for currency mismatch" in:
    val src  = Account.create(srcId, balance, usd)
    val dst  = Account.create(dstId, balance, Currency.EUR)
    val tx   = makeTx()
    val result = TransactionProcessor.createAndComplete(tx, src, dst)
    result.isLeft shouldBe true

  it should "fail for zero amount" in:
    val (src, dst) = makeAccounts()
    val tx         = makeTx(Amount.Zero)
    val result     = TransactionProcessor.createAndComplete(tx, src, dst)
    result.isLeft shouldBe true

  "TransactionProcessor.cancel" should "refund source account for Pending transaction" in:
    val src  = Account.create(srcId, balance, usd)
    val tx   = makeTx()
    val result = TransactionProcessor.cancel(tx, src)
    result.isRight shouldBe true
    val (cancelledTx, refundedAcc, events) = result.toOption.get
    cancelledTx.status shouldBe Cancelled
    refundedAcc.balance shouldBe Amount.unsafe(BigDecimal("11500.00"))
    events should have size 2
    events.head.isInstanceOf[TransactionCancelled] shouldBe true
    events(1).isInstanceOf[AccountBalanceUpdated] shouldBe true

  it should "fail for Completed transaction" in:
    val src  = Account.create(srcId, balance, usd)
    val tx   = makeTx().complete
    val result = TransactionProcessor.cancel(tx, src)
    result.isLeft shouldBe true
    result.left.get.isInstanceOf[ValidationError] shouldBe true

// ==================== DomainError Tests ====================

class DomainErrorSpec extends AnyFlatSpec with Matchers:

  "DomainError messages" should "be descriptive" in:
    val accountId = AccountId.generate
    tpp.domain.error.AccountNotFound(accountId).message should include(accountId.asString)

    val amount1 = Amount.unsafe(BigDecimal("100.00"))
    val amount2 = Amount.unsafe(BigDecimal("200.00"))
    InsufficientFunds(accountId, amount1, amount2).message should include("100.00")
    InsufficientFunds(accountId, amount1, amount2).message should include("200.00")

    ValidationError("custom error").message shouldBe "custom error"

    SameSourceAndDestination(accountId).message should include(accountId.asString)
