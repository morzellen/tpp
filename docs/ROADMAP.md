# План Разработки: Transaction Processing Platform

## Обзор

Этот документ описывает **поэтапный план реализации** TPP проекта. Каждая фаза содержит конкретные задачи, критерии готовности и ожидаемые артефакты.

---

## 📅 Фаза 0: Подготовка и Настройка (Неделя 1)

### Цель
Настроить проект, инфраструктуру и базовую структуру для продуктивной разработки.

### Задачи

| # | Задача | Описание | Приоритет |
|---|---|---|---|
| 0.1 | **Структура проекта** | Создать директорию `src/main/scala/tpp/` с пакетами: `domain`, `application`, `infrastructure` | 🔴 High |
| 0.2 | **build.sbt** | Настроить мульти-модульный sbt с зависимостями: ZIO, http4s, Doobie, Circe, ZIO Kafka | 🔴 High |
| 0.3 | **Docker Compose** | Настроить `docker/docker-compose.yml` с PostgreSQL, Kafka, Zookeeper | 🔴 High |
| 0.4 | **Конфигурация** | Создать `resources/application.conf` (HOCON) + ZIO Config | 🟡 Medium |
| 0.5 | **Logging** | Настроить ZIO Logging со структурированным выводом | 🟡 Medium |
| 0.6 | **CI/CD Pipeline** | GitHub Actions: compile, test, lint | 🟢 Low |

### Критерии Готовности
- ✅ `sbt compile` выполняется без ошибок
- ✅ `docker compose up -d` поднимает PostgreSQL и Kafka
- ✅ Приложение запускается и отвечает на `/health`
- ✅ CI проходит на каждый PR

### Артефакты
- `build.sbt` с зависимостями
- `docker/docker-compose.yml`
- `resources/application.conf`
- `.github/workflows/ci.yml`
- `Main.scala` с базовым ZIO App

---

## 📅 Фаза 1: Domain Layer (Неделя 1-2)

### Цель
Реализовать **чистое доменное ядро** без внешних зависимостей.

### Задачи

| # | Задача | Описание | Приоритет |
|---|---|---|---|
| 1.1 | **Value Objects** | `TransactionId`, `AccountId`, `Amount`, `Currency` с инвариантами | 🔴 High |
| 1.2 | **Модели** | `Transaction`, `Account` с enum статусами | 🔴 High |
| 1.3 | **Domain Events** | `TransactionCreated`, `TransactionCompleted`, `TransactionFailed`, `AccountBalanceUpdated` | 🔴 High |
| 1.4 | **Domain Errors** | ADT ошибок: `InsufficientFunds`, `AccountNotFound`, и т.д. | 🔴 High |
| 1.5 | **Чистая логика** | `TransactionProcessor.create()`, `complete()`, `cancel()` | 🔴 High |
| 1.6 | **Unit тесты** | Тесты на все доменные функции (happy path + error cases) | 🔴 High |
| 1.7 | **Property-Based тесты** | Законы для `Amount`, инварианты моделей | 🟡 Medium |

### Критерии Готовности
- ✅ Все модели компилируются и иммутабельны
- ✅ `TransactionProcessor` — чистые функции (без эффектов)
- ✅ Unit тесты покрывают 90%+ domain слоя
- ✅ 0 внешних зависимостей в `domain/` пакете

### Артефакты
```
src/main/scala/tpp/domain/
├── model/
│   ├── TransactionId.scala
│   ├── AccountId.scala
│   ├── Amount.scala
│   ├── Currency.scala
│   ├── Transaction.scala
│   └── Account.scala
├── event/
│   ├── DomainEvent.scala
│   ├── TransactionCreated.scala
│   ├── TransactionCompleted.scala
│   ├── TransactionFailed.scala
│   └── AccountBalanceUpdated.scala
├── error/
│   └── DomainError.scala
└── service/
    └── TransactionProcessor.scala
```

---

## 📅 Фаза 2: Event Store + Projections (Неделя 2-3)

### Цель
Реализовать **Event Store** в PostgreSQL и **проекции** для ReadModel.

### Задачи

| # | Задача | Описание | Приоритет |
|---|---|---|---|
| 2.1 | **SQL Migrations** | Создать `events`, `transactions`, `accounts` таблицы | 🔴 High |
| 2.2 | **Event Store Port** | Интерфейс `EventStore` (append, getEvents, getNextVersion) | 🔴 High |
| 2.3 | **EventStorePostgres** | Имплементация с Doobie + JSONB сериализация | 🔴 High |
| 2.4 | **TransactionProjection** | Обработчик событий → обновление `transactions` таблицы | 🔴 High |
| 2.5 | **AccountBalanceProjection** | Обработчик событий → обновление балансов | 🔴 High |
| 2.6 | **Repository Ports** | `TransactionRepository`, `AccountRepository` интерфейсы | 🔴 High |
| 2.7 | **Repository Impl** | Doobie имплементации для ReadModel | 🔴 High |
| 2.8 | **Integration тесты** | TestContainers для PostgreSQL | 🟡 Medium |

### Критерии Готовности
- ✅ События сохраняются в PostgreSQL (append-only)
- ✅ Проекции корректно обновляют ReadModel
- ✅ Оптимистичная блокировка через `version` работает
- ✅ Интеграционные тесты проходят

### Артефакты
```
src/main/scala/tpp/infrastructure/db/
├── EventStore.scala              # Port
├── EventStorePostgres.scala      # Adapter
├── TransactionRepository.scala   # Port
├── TransactionRepositoryPostgres.scala
├── AccountRepository.scala       # Port
└── AccountRepositoryPostgres.scala

src/main/scala/tpp/application/projection/
├── TransactionProjection.scala
└── AccountBalanceProjection.scala

resources/migrations/
└── V1__initial_schema.sql
```

---

## 📅 Фаза 3: Application Layer — Command/Query Handlers (Неделя 3)

### Цель
Реализовать **бизнес-use cases** с интеграцией Event Store.

### Задачи

| # | Задача | Описание | Приоритет |
|---|---|---|---|
| 3.1 | **Command модели** | `CreateTransaction`, `CancelTransaction` | 🔴 High |
| 3.2 | **Query модели** | `GetTransactionById`, `ListTransactionsByAccount` | 🔴 High |
| 3.3 | **CreateTxHandler** | Полный цикл: загрузить счета → валидация → события → сохранить | 🔴 High |
| 3.4 | **CancelTxHandler** | Отмена транзакции с генерацией `TransactionCancelled` | 🟡 Medium |
| 3.5 | **GetTxHandler** | Простой запрос к ReadModel | 🔴 High |
| 3.6 | **ListTxsHandler** | Пагинация, фильтрация по accountId | 🟡 Medium |
| 3.7 | **Application тесты** | Моки для EventStore + Repository | 🟡 Medium |

### Критерии Готовности
- ✅ `CreateTransaction` проходит полный цикл
- ✅ Ошибки домена корректно обрабатываются
- ✅ События публикуются после сохранения
- ✅ Тесты с моканными зависимостями

### Артефакты
```
src/main/scala/tpp/application/
├── command/
│   ├── CreateTransaction.scala
│   ├── CancelTransaction.scala
│   ├── CreateTransactionHandler.scala
│   └── CancelTransactionHandler.scala
├── query/
│   ├── GetTransactionById.scala
│   ├── ListTransactionsByAccount.scala
│   ├── GetTransactionHandler.scala
│   └── ListTransactionsHandler.scala
└── eventstore/
    └── EventPublisher.scala
```

---

## 📅 Фаза 4: API Layer — http4s Routes (Неделя 3-4)

### Цель
Реализовать **REST API** согласно спецификации.

### Задачи

| # | Задача | Описание | Приоритет |
|---|---|---|---|
| 4.1 | **DTO модели** | `CreateTransactionRequest`, `TransactionResponse`, `ErrorResponse` | 🔴 High |
| 4.2 | **Circe Codecs** | Сериализация/десериализация для всех DTO | 🔴 High |
| 4.3 | **TransactionRoutes** | POST/GET эндпоинты для транзакций | 🔴 High |
| 4.4 | **AccountRoutes** | GET эндпоинты для счетов | 🟡 Medium |
| 4.5 | **HealthRoute** | `/health` endpoint | 🔴 High |
| 4.6 | **Error Handling** | Global error handler → HTTP статусы | 🔴 High |
| 4.7 | **CORS** | Настройка CORS для dev | 🟢 Low |
| 4.8 | **API тесты** | http4s client для интеграционных тестов | 🟡 Medium |

### Критерии Готовности
- ✅ Все эндпоинты из SPEC.md реализованы
- ✅ Валидация входных данных
- ✅ Корректные HTTP статусы (201, 400, 404, 422, 500)
- ✅ JSON responses соответствуют спецификации

### Артефакты
```
src/main/scala/tpp/infrastructure/api/
├── dto/
│   ├── CreateTransactionRequest.scala
│   ├── TransactionResponse.scala
│   ├── AccountResponse.scala
│   └── ErrorResponse.scala
├── routes/
│   ├── TransactionRoutes.scala
│   ├── AccountRoutes.scala
│   └── HealthRoutes.scala
└── error/
    └── HttpErrorHandler.scala
```

---

## 📅 Фаза 5: Kafka Integration (Неделя 4-5)

### Цель
Настроить **event publishing** и **notification consumer**.

### Задачи

| # | Задача | Описание | Приоритет |
|---|---|---|---|
| 5.1 | **Kafka Producer** | ZIO Kafka producer для domain events | 🔴 High |
| 5.2 | **Topic Config** | Настройка topics в Docker Compose | 🔴 High |
| 5.3 | **EventPublisher** | Публикация событий после append в Event Store | 🔴 High |
| 5.4 | **Notification Consumer** | Kafka consumer для `notifications.send` | 🟡 Medium |
| 5.5 | **Dead Letter Queue** | Обработка failed messages | 🟡 Medium |
| 5.6 | **Integration тесты** | Embedded Kafka для тестов | 🟡 Medium |

### Критерии Готовности
- ✅ События публикуются в Kafka после сохранения
- ✅ Consumer обрабатывает уведомления
- ✅ Dead Letter Queue для ошибочных сообщений
- ✅ Тесты с Embedded Kafka

### Артефакты
```
src/main/scala/tpp/infrastructure/kafka/
├── KafkaConfig.scala
├── KafkaProducer.scala
├── EventPublisher.scala
└── NotificationConsumer.scala
```

---

## 📅 Фаза 6: Testing & Quality (Неделя 5-6)

### Цель
Обеспечить **надёжность** через тестирование.

### Задачи

| # | Задача | Описание | Приоритет |
|---|---|---|---|
| 6.1 | **Unit тесты** | Domain + Application (mock infra) | 🔴 High |
| 6.2 | **Property-Based** | ScalaCheck для Amount, Currency, инвариантов | 🔴 High |
| 6.3 | **Integration тесты** | TestContainers (Postgres + Kafka) | 🔴 High |
| 6.4 | **E2E тесты** | Полный сценарий через http4s client | 🟡 Medium |
| 6.5 | **Coverage** | Настроить scoverage (min 80%) | 🟡 Medium |
| 6.6 | **Linting** | scalafmt + scalafix в CI | 🟡 Medium |

### Критерии Готовности
- ✅ Domain: 90%+ coverage
- ✅ Application: 80%+ coverage
- ✅ Integration: основные сценарии покрыты
- ✅ CI проходит на каждый PR

---

## 📅 Фаза 7: Production Readiness (Неделя 6-7)

### Цель
Подготовить проект к **реальной эксплуатации**.

### Задачи

| # | Задача | Описание | Приоритет |
|---|---|---|---|
| 7.1 | **Metrics** | ZIO Metrics + Prometheus endpoint | 🟡 Medium |
| 7.2 | **Tracing** | OpenTelemetry (опционально) | 🟢 Low |
| 7.3 | **Graceful Shutdown** | Закрытие пулов, flush Kafka | 🔴 High |
| 7.4 | **Health Checks** | DB connectivity, Kafka status | 🔴 High |
| 7.5 | **Docker Image** | Dockerfile для приложения | 🔴 High |
| 7.6 | **Documentation** | Обновить README, API docs (OpenAPI) | 🟡 Medium |

### Критерии Готовности
- ✅ `/health` показывает статус зависимостей
- ✅ Graceful shutdown работает без потерь данных
- ✅ Docker image билдится и запускается
- ✅ Документация актуальна

---

## 📊 Сводная Диаграмма

```
Фаза 0  ██░░░░░░░░  Настройка
Фаза 1  ████░░░░░░  Domain
Фаза 2  ██████░░░░  Event Store
Фаза 3  ████████░░  Application
Фаза 4  ██████████  API
Фаза 5  ██████████  Kafka
Фаза 6  ████████░░  Testing
Фаза 7  ██████░░░░  Production
        ────────────────────────
        Недели: 1  2  3  4  5  6  7
```

---

## 🎯 Критерии Готовности Всего Проекта

- ✅ Все 7 фаз завершены
- ✅ 500+ строк кода (минимум)
- ✅ 80%+ test coverage
- ✅ CI/CD pipeline зелёный
- ✅ Приложение запускается через Docker Compose
- ✅ API полностью документировано
- ✅ README актуален

---

## 🔄 Backlog (После MVP)

| Фича | Описание | Приоритет |
|---|---|---|
| **JWT Auth** | Авторизация запросов | 🟡 |
| **Idempotency Key** | Защита от дублей | 🟡 |
| **Currency Conversion** | Мультивалютные транзакции | 🟡 |
| **Event Replay** | Восстановение состояния из событий | 🟢 |
| **Snapshotting** | Оптимизация для больших агрегатов | 🟢 |
| **Admin Panel** | UI для мониторинга | 🟢 |
| **Performance Tests** | Gatling/K6 нагрузочное тестирование | 🟢 |

---

*План является ориентировочным и может корректироваться по ходу разработки*
