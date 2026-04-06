# Техническая Спецификация: Transaction Processing Platform (TPP)

## 1. Обзор

**Transaction Processing Platform (TPP)** — платформа обработки финансовых транзакций, реализующая гибридную архитектуру (Clean Architecture + Event Store) на стеке Scala 3 / ZIO.

### 1.1 Назначение

- Приём, валидация и обработка финансовых транзакций
- Гарантированное хранение всех событий (Event Sourcing для критичных операций)
- Асинхронная обработка через Kafka
- REST API для внешних систем
- Полный аудит изменений для compliance

### 1.2 Целевая Аудитория

- Разработчики, изучающие enterprise Scala/ZIO
- Команды, оценивающие подход Modular Monolith + Event Store
- Финтех-проекты начального уровня

---

## 2. Технологический Стек

| Категория | Технология | Версия | Обоснование |
|---|---|---|---|
| **Язык** | Scala | 3.8.3 | Latest stable, union types, context functions |
| **Build** | sbt | 1.12.8 | Стандарт де-факто для Scala |
| **Effect System** | ZIO | 2.x | Типобезопасность, ZLayers, ZScheduler |
| **HTTP** | http4s | 1.0.x (M39+) | Функциональный, cats-effect совместимый |
| **DB Driver** | Doobie | 1.0.x | Функциональный JDBC, PostgreSQL |
| **БД** | PostgreSQL | 15+ | Надёжность, JSONB для Event Store |
| **Message Broker** | Apache Kafka | 3.x | Event-driven коммуникация |
| **Serialization** | Circe | 0.14.x | Де-факто стандарт для JSON в Scala |
| **Testing** | ZIO Test + ScalaCheck | — | Property-based + unit тесты |
| **Containerization** | Docker Compose | — | Локальная инфраструктура |
| **CI/CD** | GitHub Actions | — | Автоматизация сборки и тестов |
| **Logging** | ZIO Logging + Slf4j | — | Структурированные логи |
| **Config** | ZIO Config | — | HOCON / environment variables |

---

## 3. Архитектурные Принципы

### 3.1 Слои (Clean Architecture)

```
┌─────────────────────────────────────────┐
│         API Layer (http4s)              │  ← Routes, DTOs, сериализация
├─────────────────────────────────────────┤
│     Application Layer (ZIO Services)    │  ← Command/Query Handlers
├─────────────────────────────────────────┤
│       Domain Layer (Pure Scala)         │  ← Модели, события, правила
├─────────────────────────────────────────┤
│    Infrastructure Layer (Adapters)      │  ← DB, Kafka, Config, Logging
└─────────────────────────────────────────┘
```

**Правило зависимостей:** каждый слой зависит только от внутреннего. Инфраструктура зависит от Domain через интерфейсы (Ports).

### 3.2 Event Store

- Все изменения состояния сохраняются как **неизменяемые события**
- Таблица `events` в PostgreSQL (append-only)
- Оптимистичная блокировка через `version`
- Проекции (ReadModels) строятся асинхронно из потока событий

### 3.3 CQRS (Command Query Responsibility Segregation)

- **Command Side:** записывает события, бизнес-валидация
- **Query Side:** оптимизированные ReadModels для чтения

---

## 4. Доменная Модель

### 4.1 Value Objects

| Тип | Поля | Инварианты |
|---|---|---|
| `TransactionId` | `value: UUID` | Не пустой |
| `AccountId` | `value: UUID` | Не пустой |
| `Amount` | `value: BigDecimal`, `currency: Currency` | `value >= 0` |
| `Currency` | `code: String` | ISO 4217 (RUB, USD, EUR) |

### 4.2 Aggregate Roots

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

sealed trait TransactionStatus
case object Pending extends TransactionStatus
case object Completed extends TransactionStatus
case object Failed extends TransactionStatus
case object Cancelled extends TransactionStatus
```

#### Account

```scala
case class Account(
    id: AccountId,
    ownerId: String,
    balance: Amount,
    status: AccountStatus,
    createdAt: Instant
)

sealed trait AccountStatus
case object Active extends AccountStatus
case object Frozen extends AccountStatus
case object Closed extends AccountStatus
```

### 4.3 Domain Events

```scala
sealed trait DomainEvent {
  def eventId: UUID
  def aggregateId: UUID
  def occurredAt: Instant
}

case class TransactionCreated(...) extends DomainEvent
case class TransactionCompleted(...) extends DomainEvent
case class TransactionFailed(reason: String, ...) extends DomainEvent
case class TransactionCancelled(reason: String, ...) extends DomainEvent
case class AccountBalanceUpdated(...) extends DomainEvent
```

### 4.4 Domain Errors

```scala
sealed trait DomainError extends Product with Serializable
case object InsufficientFunds extends DomainError
case object AccountNotFound extends DomainError
case object AccountFrozen extends DomainError
case class InvalidAmount(reason: String) extends DomainError
case class CurrencyMismatch(from: Currency, to: Currency) extends DomainError
case class TransactionNotFound(id: TransactionId) extends DomainError
```

---

## 5. API Спецификация

### 5.1 Endpoints

| Метод | Путь | Описание | Auth |
|---|---|---|---|
| `POST` | `/api/v1/transactions` | Создать транзакцию | Да |
| `GET` | `/api/v1/transactions/{id}` | Получить транзакцию | Да |
| `GET` | `/api/v1/transactions?accountId=...` | Список транзакций | Да |
| `POST` | `/api/v1/transactions/{id}/cancel` | Отменить транзакцию | Да |
| `GET` | `/api/v1/accounts/{id}` | Получить счёт | Да |
| `GET` | `/api/v1/accounts/{id}/balance` | Баланс счёта | Да |
| `GET` | `/health` | Health Check | Нет |

### 5.2 Request/Response DTOs

#### POST /transactions — Request

```json
{
  "fromAccountId": "550e8400-e29b-41d4-a716-446655440000",
  "toAccountId": "6ba7b810-9dad-11d1-80b4-00c04fd430c8",
  "amount": 1500.00,
  "currency": "RUB"
}
```

#### POST /transactions — Response (201 Created)

```json
{
  "id": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "fromAccountId": "550e8400-e29b-41d4-a716-446655440000",
  "toAccountId": "6ba7b810-9dad-11d1-80b4-00c04fd430c8",
  "amount": 1500.00,
  "currency": "RUB",
  "status": "PENDING",
  "createdAt": "2026-04-06T10:30:00Z"
}
```

#### Error Response (4xx)

```json
{
  "error": "InsufficientFunds",
  "message": "Недостаточно средств на счёте",
  "timestamp": "2026-04-06T10:30:00Z"
}
```

---

## 6. Схема Базы Данных

### 6.1 Event Store (Append-Only)

```sql
CREATE TABLE events (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_id UUID NOT NULL,
    aggregate_type TEXT NOT NULL,       -- "Transaction", "Account"
    event_type   TEXT NOT NULL,         -- "TransactionCreated", ...
    event_data   JSONB NOT NULL,
    version      INT  NOT NULL,
    created_at   TIMESTAMPTZ DEFAULT NOW(),
    CONSTRAINT unique_aggregate_version 
        UNIQUE (aggregate_id, version)
);

CREATE INDEX idx_events_aggregate ON events(aggregate_id);
```

### 6.2 ReadModel — Transactions

```sql
CREATE TABLE transactions (
    id             UUID PRIMARY KEY,
    from_account_id UUID NOT NULL,
    to_account_id   UUID NOT NULL,
    amount          NUMERIC(19, 4) NOT NULL,
    currency        CHAR(3) NOT NULL,
    status          TEXT NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL,
    completed_at    TIMESTAMPTZ,
    updated_at      TIMESTAMPTZ DEFAULT NOW()
);
```

### 6.3 ReadModel — Accounts

```sql
CREATE TABLE accounts (
    id         UUID PRIMARY KEY,
    owner_id   TEXT NOT NULL,
    balance    NUMERIC(19, 4) NOT NULL DEFAULT 0,
    currency   CHAR(3) NOT NULL DEFAULT 'RUB',
    status     TEXT NOT NULL DEFAULT 'Active',
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);
```

---

## 7. Инфраструктура

### 7.1 Docker Compose (Локальная Среда)

```yaml
services:
  postgres:
    image: postgres:15-alpine
    ports: ["5432:5432"]
    environment:
      POSTGRES_DB: tpp
      POSTGRES_USER: tpp_user
      POSTGRES_PASSWORD: tpp_password
    volumes:
      - pg_data:/var/lib/postgresql/data
      - ./docker/postgres/init.sql:/docker-entrypoint-initdb.d/init.sql

  kafka:
    image: confluentinc/cp-kafka:7.5.0
    ports: ["9092:9092"]
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://localhost:9092
    depends_on: [zookeeper]

  zookeeper:
    image: confluentinc/cp-zookeeper:7.5.0
    ports: ["2181:2181"]
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181

volumes:
  pg_data:
```

### 7.2 Kafka Topics

| Topic | Key | Payload | Описание |
|---|---|---|---|
| `transactions.created` | TransactionId | TransactionCreated event | Новые транзакции |
| `transactions.completed` | TransactionId | TransactionCompleted event | Завершённые транзакции |
| `transactions.failed` | TransactionId | TransactionFailed event | Ошибки обработки |
| `notifications.send` | NotificationType | Уведомление для отправки | Consumer для рассылки |

---

## 8. Обработка Ошибок

### 8.1 Уровни Ошибок

| Уровень | Типы | Стратегия |
|---|---|---|
| **Domain** | `InsufficientFunds`, `AccountNotFound` | Возврат клиенту (4xx) |
| **Application** | `EventStoreConflict`, `ProjectionLag` | Retry / Circuit Breaker |
| **Infrastructure** | `DbConnectionFailed`, `KafkaDown` | Alert + Fallback |

### 8.2 Retry Policy (ZIO)

```scala
val retryPolicy: Schedule[Any, Throwable, Any] = 
  Schedule.exponential(100.millis) &&
  Schedule.recurs(5)
```

---

## 9. Тестирование

### 9.1 Уровни Тестов

| Уровень | Что тестируем | Инструменты |
|---|---|---|
| **Unit (Domain)** | Чистые функции, инварианты | ZIO Test |
| **Property-Based** | Законы (например, balance >= 0) | ScalaCheck |
| **Integration** | DB, Kafka, HTTP endpoints | TestContainers |
| **E2E** | Полный сценарий | http4s client + embedded services |

### 9.2 Метрика Покрытия

- Domain: **90%+**
- Application: **80%+**
- Infrastructure: **60%+** (ограниченно моками)

---

## 10. Non-Functional Требования

| Параметр | Требование |
|---|---|
| **Latency (p99)** | < 200ms для GET запросов |
| **Latency (p99)** | < 500ms для POST запросов |
| **Throughput** | 1000 req/s (локально) |
| **Availability** | 99.9% (single node) |
| **Data Durability** | 100% (Event Store append-only) |

---

## 11. Конфигурация

```hocon
tpp {
  server {
    host = "0.0.0.0"
    port = 8080
  }
  
  database {
    url = "jdbc:postgresql://localhost:5432/tpp"
    user = "tpp_user"
    password = "tpp_password"
    poolSize = 10
  }
  
  kafka {
    bootstrapServers = "localhost:9092"
    groupId = "tpp-consumer-group"
  }
  
  logging {
    level = "INFO"
    format = "json"
  }
}
```

---

## 12. Ограничения и Допущения

- **MVP scope:** один источник валюты (RUB), без конвертации
- **Без авторизации** на первых этапах (JWT добавляется позже)
- **Без дедупликации** запросов (idempotency key — фаза 2)
- **Без реального Kafka consumer** для уведомлений (заглушка на старте)

---

*Версия: 1.0 | Дата: 2026-04-06 | Статус: Draft*
