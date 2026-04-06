# Transaction Processing Platform (TPP)

> **Платформа обработки финансовых транзакций на Scala 3 / ZIO**  
> Гибридная архитектура: Clean Architecture + Event Store + CQRS

[![Scala 3](https://img.shields.io/badge/Scala-3.8.3-red.svg)](https://www.scala-lang.org/)
[![ZIO](https://img.shields.io/badge/ZIO-2.x-green.svg)](https://zio.dev/)
[![sbt](https://img.shields.io/badge/sbt-1.12.8-orange.svg)](https://www.scala-sbt.org/)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

---

## 📋 О Проекте

TPP — это **enterprise-grade платформа** для приёма, валидации и обработки финансовых транзакций, реализующая лучшие практики функционального программирования и событийно-ориентированной архитектуры.

### Зачем этот проект?

- 🎓 **Обучение:** эталонный пример Scala 3 + ZIO в enterprise-контексте
- 🏗️ **Архитектура:** гибридный подход (Clean Architecture + Event Store)
- 💰 **Финтех-домен:** полный цикл транзакций с гарантией сохранности данных
- 🔧 **Production-ready:** Docker Compose, CI/CD, тестирование, логирование

### Ключевые Возможности

| Функция | Описание |
|---|---|
| **Создание транзакций** | Переводы между счетами с валидацией |
| **Event Store** | Append-only хранение всех событий для аудита |
| **CQRS** | Разделение команд записи и запросов чтения |
| **Асинхронность** | ZIO + Kafka для неблокирующей обработки |
| **Fault Tolerance** | Retry, Circuit Breaker, graceful shutdown |
| **Полный аудит** | Воспроизводимость любого состояния системы |

---

## 🚀 Быстрый Старт

### Предварительные Требования

- **JDK:** 17+
- **sbt:** 1.12.8+
- **Docker & Docker Compose:** для локальной инфраструктуры

### Запуск за 3 Команды

```bash
# 1. Поднять инфраструк (PostgreSQL, Kafka)
docker compose -f docker/docker-compose.yml up -d

# 2. Скомпилировать и запустить приложение
sbt run

# 3. Проверить работоспособность
curl http://localhost:8080/health
```

### Первый Запрос

```bash
# Создать транзакцию
curl -X POST http://localhost:8080/api/v1/transactions \
  -H "Content-Type: application/json" \
  -d '{
    "fromAccountId": "550e8400-e29b-41d4-a716-446655440000",
    "toAccountId": "6ba7b810-9dad-11d1-80b4-00c04fd430c8",
    "amount": 1500.00,
    "currency": "RUB"
  }'

# Получить транзакцию
curl http://localhost:8080/api/v1/transactions/{id}
```

---

## 📐 Архитектура

```
┌─────────────────────────────────────────────────┐
│              API Layer (http4s)                  │
│    Routes → DTOs → Validation → Response        │
├─────────────────────────────────────────────────┤
│        Application Layer (ZIO Services)          │
│  Command Handlers │ Query Handlers │ Projections │
├─────────────────────────────────────────────────┤
│            Domain Layer (Pure Scala)             │
│   Models │ Events │ Errors │ Business Rules      │
├─────────────────────────────────────────────────┤
│         Infrastructure Layer (Adapters)          │
│  PostgreSQL │ Kafka │ Config │ Logging           │
└─────────────────────────────────────────────────┘
```

### Подробная Документация

- [📄 Техническая спецификация](docs/SPEC.md)
- [🏛️ Архитектурные решения](docs/ARCHITECTURE.md)
- [🗺️ План разработки](docs/ROADMAP.md)

---

## 🛠️ Технологии

| Категория | Технология |
|---|---|
| **Язык** | Scala 3.8.3 |
| **Effect System** | ZIO 2.x |
| **HTTP** | http4s |
| **БД** | PostgreSQL 15+ (Doobie) |
| **Message Broker** | Apache Kafka 3.x |
| **Serialization** | Circe |
| **Testing** | ZIO Test + ScalaCheck |
| **Containerization** | Docker Compose |
| **CI/CD** | GitHub Actions |
| **Logging** | ZIO Logging + Slf4j |

---

## 📁 Структура Проекта

```
tpp/
├── src/main/scala/tpp/
│   ├── domain/           # ← ЯДРО: модели, события, правила
│   ├── application/      # ← USE CASES: command/query handlers
│   ├── infrastructure/   # ← ВНЕШНИЕ ЗАВИСИМОСТИ: DB, Kafka, API
│   └── Main.scala        # ← ТОЧКА ВХОДА (ZIO App)
├── src/test/scala/tpp/   # ← Тесты (unit, property-based, integration)
├── docker/               # ← Docker Compose + конфиги
├── docs/                 # ← Документация
├── build.sbt             # ← Build configuration
└── resources/            # ← application.conf, migrations
```

---

## 🧪 Тестирование

```bash
# Запустить все тесты
sbt test

# Запустить только unit тесты
sbt "testOnly *domain.*"

# Запустить интеграционные тесты
sbt "testOnly *infrastructure.*"

# Property-based тесты
sbt "testOnly *PropertySpec*"
```

---

## 🐳 Docker Compose

```bash
# Поднять всю инфраструктуру
docker compose -f docker/docker-compose.yml up -d

# Остановить
docker compose -f docker/docker-compose.yml down

# Очистить volumes
docker compose -f docker/docker-compose.yml down -v
```

**Сервисы:**
- **PostgreSQL:** `localhost:5432` (tpp / tpp_user / tpp_password)
- **Kafka:** `localhost:9092`
- **Zookeeper:** `localhost:2181`

---

## 📖 Документация

| Документ | Описание |
|---|---|
| [SPEC.md](docs/SPEC.md) | Полная техническая спецификация |
| [ARCHITECTURE.md](docs/ARCHITECTURE.md) | Архитектурные решения и диаграммы |
| [ROADMAP.md](docs/ROADMAP.md) | План разработки по фазам |

---

## 🤝 Contributing

1. Fork репозиторий
2. Создай ветку для фичи (`git checkout -b feature/amazing-feature`)
3. Закоммить изменения (`git commit -m 'Add amazing feature'`)
4. Push в ветку (`git push origin feature/amazing-feature`)
5. Открой Pull Request

---

## 📄 License

MIT License. См. файл [LICENSE](LICENSE) для деталей.

---

*Создано с ❤️ для изучения Scala 3, ZIO и enterprise архитектуры*
