# Архитектура Transaction Processing Platform

## 1. Обзор Архитектурных Решений

### 1.1 Выбор: Модульный Монолит

Мы выбрали **Modular Monolith** вместо микросервисов по следующим причинам:

| Фактор | Модульный Монолит | Микросервисы |
|---|---|---|
| **Команда** | 1-3 разработчика | Требует 5+ |
| **Операционные затраты** | Минимальные | K8S, monitoring, tracing |
| **Скорость разработки** | Высокая (один деплой) | Низкая (координация) |
| **Границы модулей** | Чёткие, компиляционные | Физические (сети) |
| **Миграция к микросервисам** | ✅ Модули → отдельные сервисы | Уже микросервисы |

**Принцип:** если модуль можно выделить в отдельный сервис без изменения его кода — архитектура успешна.

---

### 1.2 Гибридная Архитектура: Clean Architecture + Event Store

```
┌──────────────────────────────────────────────────────────┐
│                    CLIENT (Browser / Postman)             │
└─────────────────────────┬────────────────────────────────┘
                          │ HTTP
┌─────────────────────────▼────────────────────────────────┐
│              API LAYER (http4s routes)                    │
│  ┌──────────────┐  ┌──────────────┐  ┌───────────────┐  │
│  │ TxRoutes     │  │ AccountRoutes│  │ HealthRoutes  │  │
│  │ (DTO→Cmd)    │  │ (DTO→Query)  │  │ (ping/pong)   │  │
│  └──────┬───────┘  └──────┬───────┘  └───────────────┘  │
└─────────┼────────────────┼──────────────────────────────┘
          │                │
┌─────────▼────────────────▼──────────────────────────────┐
│          APPLICATION LAYER (ZIO Services)                │
│  ┌──────────────────────┐  ┌─────────────────────────┐  │
│  │ CreateTxHandler     │  │ GetTxByIdHandler        │  │
│  │ CancelTxHandler     │  │ ListTxsByAccountHandler │  │
│  │                      │  │ GetAccountHandler       │  │
│  └──────────┬──────────┘  └───────────┬─────────────┘  │
└─────────────┼────────────────────────┼─────────────────┘
              │                        │
   ┌──────────▼──────────┐   ┌────────▼────────────────┐
   │  DOMAIN LAYER       │   │  EVENT STORE (Port)     │
   │  (Pure Scala)       │   │  ┌──────────────────┐   │
   │  ┌──────────────┐   │   │  │ append(event)    │   │
   │  │ Transaction  │   │   │  │ getEvents(id)    │   │
   │  │ Account      │   │   │  │ getNextVersion() │   │
   │  │ Amount       │   │   │  └────────┬─────────┘   │
   │  │ DomainEvent  │   │   └───────────┼─────────────┘
   │  │ DomainError  │   │               │
   │  └──────────────┘   │               │
   └─────────────────────┘               │
                                         │
┌────────────────────────────────────────┼────────────────┐
│         INFRASTRUCTURE LAYER           │                │
│  ┌─────────────────────────┐           │                │
│  │ EventStorePostgres      │◄──────────┘                │
│  │ (implements Port)       │                            │
│  │                         │   ┌─────────────────────┐  │
│  │ ┌─────────────────────┐ │   │ ReadModelPostgres   │  │
│  │ │ events table        │ │   │ (projections)       │  │
│  │ │ (append-only)       │ │   │                     │  │
│  │ └─────────────────────┘ │   │ ┌─────────────────┐ │  │
│  └─────────────────────────┘   │ │ transactions    │ │  │
│                                │ │ accounts        │ │  │
│  ┌─────────────────────────┐   │ └─────────────────┘ │  │
│  │ KafkaPublisher          │   └─────────────────────┘  │
│  │ (publishes events)      │                            │
│  └─────────────────────────┘   ┌─────────────────────┐  │
│                                │ KafkaConsumer       │  │
│  ┌─────────────────────────┐   │ (notifications)     │  │
│  │ ZIO Config              │   └─────────────────────┘  │
│  │ (HOCON / env vars)      │                            │
│  └─────────────────────────┘   ┌─────────────────────┐  │
│                                │ ZIO Logging         │  │
│  ┌─────────────────────────┐   │ (structured JSON)   │  │
│  │ Doobie (Connection Pool)│   └─────────────────────┘  │
│  └─────────────────────────┘                            │
└─────────────────────────────────────────────────────────┘
```

---

## 2. Domain Layer — Ядро Системы

### 2.1 Принципы

- **0 внешних зависимостей** — только Scala стандартная библиотека
- **Иммутабельность** — все модели являются case class
- **Типобезопасность** — Value Objects вместо примитивов
- **Total Functions** — где возможно, функции без частичности

### 2.2 Value Objects

```scala
// Строгая типизация IDs
opaque type TransactionId = UUID
object TransactionId:
  def generate: TransactionId = UUID.randomUUID()
  def fromString(s: String): Either[ParseError, TransactionId] = ...

// Amount с валютой
case class Amount(value: BigDecimal, currency: Currency):
  require(value >= 0, "Amount cannot be negative")
  
  def +(other: Amount): Either[CurrencyMismatch, Amount] =
    if this.currency == other.currency then
      Right(Amount(this.value + other.value, this.currency))
    else
      Left(CurrencyMismatch(this.currency, other.currency))

// Currency как enum
enum Currency:
  case RUB, USD, EUR
  
  def fromCode(code: String): Either[ParseError, Currency] =
    code.toUpperCase match
      case "RUB" => Right(RUB)
      case "USD" => Right(USD)
      case "EUR" => Right(EUR)
      case _     => Left(ParseError(s"Unknown currency: $code"))
```

**Зачем:** компилятор не даст перепутать IDs разных сущностей или валюты

### 2.3 Aggregate Roots

#### Transaction

```scala
case class Transaction(
  id: TransactionId,
  fromAccount: AccountId,
  toAccount: AccountId,
  amount: Amount,
  status: TransactionStatus,
  createdAt: Instant,
  completedAt: Option[Instant]
)

enum TransactionStatus:
  case Pending, Completed, Failed, Cancelled
```

**Инварианты:**
- `fromAccount != toAccount` (нельзя переводить на тот же счёт)
- `amount > 0` (сумма положительная)
- `Completed` транзакцию нельзя изменить

#### Account

```scala
case class Account(
  id: AccountId,
  ownerId: String,
  balance: Amount,
  status: AccountStatus,
  createdAt: Instant
)

enum AccountStatus:
  case Active, Frozen, Closed
```

**Инварианты:**
- `balance >= 0` (без овердрафта на MVP)
- `Frozen` / `Closed` счёт не участвует в транзакциях

### 2.4 Domain Events

```scala
sealed trait DomainEvent:
  def eventId: UUID
  def aggregateId: UUID
  def occurredAt: Instant

case class TransactionCreated(
  eventId: UUID,
  aggregateId: UUID,
  fromAccount: AccountId,
  toAccount: AccountId,
  amount: Amount,
  occurredAt: Instant
) extends DomainEvent

case class TransactionCompleted(
  eventId: UUID,
  aggregateId: UUID,
  occurredAt: Instant
) extends DomainEvent

case class TransactionFailed(
  eventId: UUID,
  aggregateId: UUID,
  reason: String,
  occurredAt: Instant
) extends DomainEvent

case class AccountBalanceUpdated(
  eventId: UUID,
  aggregateId: UUID,
  previousBalance: Amount,
  newBalance: Amount,
  occurredAt: Instant
) extends DomainEvent
```

**Зачем события?**
- Полная воспроизводимость состояния
- Аудит для compliance
- Асинхронные проекции
- Интеграция через Kafka

### 2.5 Domain Errors

```scala
sealed trait DomainError extends Product with Serializable:
  def message: String

case object InsufficientFunds extends DomainError:
  val message = "Недостаточно средств на счёте"

case object AccountNotFound extends DomainError:
  val message = "Счёт не найден"

case object AccountFrozen extends DomainError:
  val message = "Счёт заморожен или закрыт"

case class InvalidAmount(reason: String) extends DomainError:
  val message = s"Невалидная сумма: $reason"

case class CurrencyMismatch(from: Currency, to: Currency) extends DomainError:
  val message = s"Валюты не совпадают: $from → $to"

case class TransactionNotFound(id: TransactionId) extends DomainError:
  val message = s"Транзакция $id не найдена"

case class EventStoreConflict(expected: Int, actual: Int) extends DomainError:
  val message = s"Конфликт версий: ожидалась $expected, фактически $actual"
```

### 2.6 Чистая Доменная Логика

```scala
object TransactionProcessor:
  
  /** Создать транзакцию — чистая функция */
  def create(
    from: Account,
    to: Account,
    amount: Amount
  ): Either[DomainError, (Transaction, List[DomainEvent])] =
    for
      _ <- validateAccounts(from, to)
      _ <- validateAmount(amount)
      _ <- checkBalance(from, amount)
      
      tx = Transaction(
        id = TransactionId.generate,
        fromAccount = from.id,
        toAccount = to.id,
        amount = amount,
        status = Pending,
        createdAt = Instant.now(),
        completedAt = None
      )
      
      events = List(
        TransactionCreated(
          eventId = UUID.randomUUID(),
          aggregateId = tx.id,
          fromAccount = from.id,
          toAccount = to.id,
          amount = amount,
          occurredAt = Instant.now()
        )
      )
    yield (tx, events)
  
  /** Завершить транзакцию */
  def complete(
    tx: Transaction,
    from: Account,
    to: Account
  ): Either[DomainError, (Transaction, List[DomainEvent])] =
    if tx.status != Pending then
      Left(InvalidAmount("Can only complete Pending transaction"))
    else
      val updated = tx.copy(
        status = Completed,
        completedAt = Some(Instant.now())
      )
      
      val events = List(
        TransactionCompleted(
          eventId = UUID.randomUUID(),
          aggregateId = updated.id,
          occurredAt = Instant.now()
        ),
        AccountBalanceUpdated(
          eventId = UUID.randomUUID(),
          aggregateId = from.id,
          previousBalance = from.balance,
          newBalance = from.balance - tx.amount,
          occurredAt = Instant.now()
        ),
        AccountBalanceUpdated(
          eventId = UUID.randomUUID(),
          aggregateId = to.id,
          previousBalance = to.balance,
          newBalance = to.balance + tx.amount,
          occurredAt = Instant.now()
        )
      )
      
      Right((updated, events))
```

---

## 3. Application Layer — Use Cases

### 3.1 Command Pattern

```scala
sealed trait Command
case class CreateTransaction(
  fromAccountId: AccountId,
  toAccountId: AccountId,
  amount: Amount
) extends Command

case class CancelTransaction(
  transactionId: TransactionId,
  reason: String
) extends Command
```

### 3.2 Command Handler

```scala
trait CreateTransactionHandler:
  def execute(cmd: CreateTransaction): ZIO[Any, DomainError, Transaction]

class CreateTransactionHandlerLive(
  eventStore: EventStore,
  accountRepository: AccountRepository,
  txRepository: TransactionRepository
) extends CreateTransactionHandler:
  
  def execute(cmd: CreateTransaction): ZIO[Any, DomainError, Transaction] =
    for
      // Загрузить счета
      from <- accountRepository.getById(cmd.fromAccountId)
        .orElse(ZIO.fail(AccountNotFound))
      to <- accountRepository.getById(cmd.toAccountId)
        .orElse(ZIO.fail(AccountNotFound))
      
      // Доменная валидация (чистая функция)
      (tx, events) <- ZIO.fromEither(
        TransactionProcessor.create(from, to, cmd.amount)
      )
      
      // Сохранить события (atomic)
      _ <- eventStore.append(events)
      
      // Обновить ReadModel
      _ <- txRepository.save(tx)
      
      // Опубликовать в Kafka (асинхронно)
      _ <- eventPublisher.publish(events).forkDaemon
      
    yield tx
```

### 3.3 Query Handler

```scala
trait GetTransactionHandler:
  def execute(id: TransactionId): ZIO[Any, DomainError, Option[Transaction]]

class GetTransactionHandlerLive(
  txRepository: TransactionRepository
) extends GetTransactionHandler:
  
  def execute(id: TransactionId): ZIO[Any, DomainError, Option[Transaction]] =
    txRepository.getById(id)  // Простой SELECT из ReadModel
```

---

## 4. Event Store

### 4.1 Порт (Интерфейс)

```scala
trait EventStore:
  def append(events: List[DomainEvent]): ZIO[Any, DomainError, Unit]
  def getEvents(aggregateId: UUID): ZIO[Any, Nothing, List[DomainEvent]]
  def getNextVersion(aggregateId: UUID): ZIO[Any, Nothing, Int]
```

### 4.2 Инфраструктура (PostgreSQL)

```scala
class EventStorePostgres(transactor: Transactor[Task]) extends EventStore:
  
  def append(events: List[DomainEvent]): ZIO[Any, DomainError, Unit] =
    ZIO.scoped {
      for
        conn <- ZIO.acquireRelease(
          ZIO.attempt(transactor.connect)
        )(conn => ZIO.attempt(conn.close()).orDie)
        
        _ <- ZIO.foreachDiscard(events.zipWithIndex) { (event, idx) =>
          val version = getNextVersionSync(event.aggregateId) + idx
          insertEvent(conn, event, version)
        }
      yield ()
    }.mapError(e => EventStoreConflict(-1, -1))
  
  private def insertEvent(conn: Connection, event: DomainEvent, version: Int): Task[Unit] =
    ZIO.attempt {
      val stmt = conn.prepareStatement(
        """INSERT INTO events (id, aggregate_id, aggregate_type, event_type, event_data, version)
           VALUES (?, ?, ?, ?, ?, ?)"""
      )
      stmt.setObject(1, event.eventId)
      stmt.setObject(2, event.aggregateId)
      stmt.setString(3, event.getClass.getSimpleName.replace("$", ""))
      stmt.setString(4, event.getClass.getSimpleName)
      stmt.setString(5, serializeToJson(event))
      stmt.setInt(6, version)
      stmt.executeUpdate()
    }.unit
```

### 4.3 Проекции (ReadModel)

```scala
trait TransactionProjection:
  def handle(event: DomainEvent): ZIO[Any, Nothing, Unit]

class TransactionProjectionLive(
  transactor: Transactor[Task]
) extends TransactionProjection:
  
  def handle(event: DomainEvent): ZIO[Any, Nothing, Unit] =
    event match
      case e: TransactionCreated =>
        insertTransaction(e).catchAll(logError)
      case e: TransactionCompleted =>
        updateTransactionStatus(e, Completed).catchAll(logError)
      case e: TransactionFailed =>
        updateTransactionStatus(e, Failed).catchAll(logError)
      case _ => ZIO.unit
  
  private def insertTransaction(e: TransactionCreated): Task[Unit] =
    sql"""
      INSERT INTO transactions (id, from_account_id, to_account_id, 
                                amount, currency, status, created_at)
      VALUES (${e.aggregateId}, ${e.fromAccount}, ${e.toAccount},
              ${e.amount.value}, ${e.amount.currency}, 'Pending', ${e.occurredAt})
    """.update.run.transact(transactor).unit
```

---

## 5. API Layer

### 5.1 http4s Routes

```scala
object TransactionRoutes:
  
  def createRoutes(
    createHandler: CreateTransactionHandler,
    getHandler: GetTransactionHandler
  )(using log: Logging): HttpApp[Any with ZIOAppArgs with Scope] =
    HttpRoutes.of[Any with ZIOAppArgs with Scope] {
      
      case req @ POST -> Root / "api" / "v1" / "transactions" =>
        for
          body <- req.as[CreateTransactionRequest]
          cmd = CreateTransaction(
            AccountId(body.fromAccountId),
            AccountId(body.toAccountId),
            Amount(body.amount, Currency.RUB)
          )
          tx <- createHandler.execute(cmd)
            .mapError(domainErrorToHttp)
          resp <- Created(TransactionResponse.from(tx))
        yield resp
      
      case GET -> Root / "api" / "v1" / "transactions" / id =>
        for
          txId <- ZIO.fromEither(TransactionId.fromString(id))
            .mapError(_ => BadRequest("Invalid transaction ID"))
          tx <- getHandler.execute(txId)
          resp <- tx match
            case Some(t) => Ok(TransactionResponse.from(t))
            case None    => NotFound("Transaction not found")
        yield resp
      
    }.orNotFound
```

### 5.2 Error Handling

```scala
def domainErrorToHttp: DomainError => ZIO[Any, Nothing, Response[Task]] =
  case InsufficientFunds =>
    Status.UnprocessableEntity(json(ErrorResponse("InsufficientFunds", ...)))
  case AccountNotFound =>
    Status.NotFound(json(ErrorResponse("AccountNotFound", ...)))
  case e: InvalidAmount =>
    Status.BadRequest(json(ErrorResponse("InvalidAmount", e.message)))
  case e =>
    Status.InternalServerError(json(ErrorResponse("Unknown", e.message)))
```

---

## 6. ZIO Layers — Сборка Приложения

```scala
object Main extends ZIOAppDefault:
  
  val app: ZIO[Any, Throwable, Unit] =
    for
      config <- ZIO.service[AppConfig]
      
      // Инфраструктурные слои
      dbLayer      = DatabaseLayer.live(config.database)
      eventStore    = EventStorePostgres.live(dbLayer)
      accountRepo   = AccountRepositoryPostgres.live(dbLayer)
      txRepo        = TransactionRepositoryPostgres.live(dbLayer)
      kafka         = KafkaPublisher.live(config.kafka)
      
      // Application слой
      createHandler = CreateTransactionHandler.live(
        eventStore, accountRepo, txRepo, kafka
      )
      getHandler    = GetTransactionHandler.live(txRepo)
      
      // API слой
      routes = TransactionRoutes.createRoutes(createHandler, getHandler)
      
      // Запуск сервера
      _ <- ZIO.logInfo(s"Starting TPP server on ${config.server.host}:${config.server.port}")
      _ <- Server.serve(routes)
        .provide(Server.live)
        
    yield ()
  
  override def run = app.provide(
    ZLayer.fromZIO(loadConfig),
    // ... остальные слои
  )
```

---

## 7. Обработка Ошибок и Fault Tolerance

### 7.1 Retry Policy

```scala
val dbRetryPolicy: Schedule[Any, Throwable, Any] =
  Schedule.exponential(100.millis) &&
  Schedule.recurs(5) @@
  Schedule.jittered

val kafkaRetryPolicy: Schedule[Any, Throwable, Any] =
  Schedule.fixed(1.second) &&
  Schedule.recurs(3)
```

### 7.2 Circuit Breaker

```scala
val circuitBreaker: ZIO[Any, Nothing, CircuitBreaker[Nothing]] =
  CircuitBreaker.slidingWindow(
    maxFailures = 5,
    windowSize = 10,
    resetTimeout = 30.seconds
  )
```

### 7.3 Graceful Shutdown

```scala
Runtime.addShutdownHook(
  for
    _ <- ZIO.logInfo("Shutting down TPP...")
    _ <- kafkaPublisher.flush()
    _ <- dbPool.close()
    _ <- ZIO.logInfo("TPP shutdown complete")
  yield ()
)
```

---

## 8. Диаграммы

### 8.1 Sequence Diagram: Создание Транзакции

```
Client          API Layer        CreateTxHandler    EventStore       Kafka
  │                │                    │                │              │
  │ POST /tx       │                    │                │              │
  │───────────────>│                    │                │              │
  │                │  CreateTx Cmd      │                │              │
  │                │───────────────────>│                │              │
  │                │                    │  Get Account   │              │
  │                │                    │───────────────>│              │
  │                │                    │<───────────────│              │
  │                │                    │                │              │
  │                │                    │ Validate(Domain)│              │
  │                │                    │───┐            │              │
  │                │                    │<──┘            │              │
  │                │                    │                │              │
  │                │                    │  Append Events │              │
  │                │                    │───────────────>│              │
  │                │                    │                │              │
  │                │                    │                │  Publish     │
  │                │                    │                │─────────────>│
  │                │                    │                │              │
  │                │  Transaction DTO   │                │              │
  │                │<───────────────────│                │              │
  │  201 Created   │                    │                │              │
  │<───────────────│                    │                │              │
```

### 8.2 Event Flow

```
TransactionCreated ──► Event Store ──► Projection ──► transactions table
                            │
                            ├──► Kafka ──► Notification Consumer
                            │
                            └──► Audit Log
```

---

## 9. Тестовая Стратегия

### 9.1 Unit Тесты (Domain)

```scala
test("create transaction with valid data") {
  val from = TestAccounts.active(balance = Amount(5000, RUB))
  val to   = TestAccounts.active(balance = Amount(1000, RUB))
  val amt  = Amount(500, RUB)
  
  val result = TransactionProcessor.create(from, to, amt)
  
  assertTrue(result.isRight) &&
  assertTrue(result.toOption.get._1.status == Pending) &&
  assertTrue(result.toOption.get._2.nonEmpty)
}

test("fail when insufficient funds") {
  val from = TestAccounts.active(balance = Amount(100, RUB))
  val to   = TestAccounts.active(balance = Amount(1000, RUB))
  val amt  = Amount(500, RUB)
  
  val result = TransactionProcessor.create(from, to, amt)
  
  assertTrue(result.left.toOption.get == InsufficientFunds)
}
```

### 9.2 Property-Based Тесты

```scala
test("amount addition is commutative") {
  check(Gen.bigDecimal(0, 1000000)) { (v1, v2) =>
    val a1 = Amount(v1, RUB)
    val a2 = Amount(v2, RUB)
    
    (a1 + a2) == (a2 + a1)
  }
}
```

### 9.3 Integration Тесты

```scala
test("append and retrieve events") {
  for {
    eventStore <- TestContainers.eventStore
    events      = List(TestEvents.txCreated)
    
    _ <- eventStore.append(events)
    retrieved <- eventStore.getEvents(events.head.aggregateId)
    
  } yield assertTrue(retrieved.size == 1)
}
```

---

## 10. Эволюция Архитектуры

### Текущее Состояние (MVP)
```
┌─────────────────────────────────────┐
│         Single Node Deploy          │
│  ┌───────────────────────────────┐  │
│  │    TPP Application            │  │
│  │  (API + Domain + Infra)       │  │
│  └───────────────────────────────┘  │
│         PostgreSQL │ Kafka          │
└─────────────────────────────────────┘
```

### Будущее (Рост Команды)
```
┌──────────┐  ┌──────────┐  ┌──────────┐
│   API     │  │  Tx      │  │  Account │
│ Gateway   │──│ Service  │  │  Service │
└──────────┘  └──────────┘  └──────────┘
                   │              │
              ┌────▼──────────────▼────┐
              │    Event Store (DB)    │
              │    Kafka Cluster       │
              └────────────────────────┘
```

---

*Документ является живым и обновляется по мере развития проекта*
