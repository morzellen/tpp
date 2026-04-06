# Contributing Guidelines

Спасибо, что хочешь внести вклад в **Transaction Processing Platform (TPP)**! 🎉

## 🚀 Быстрый Старт

1. **Fork** репозиторий
2. **Clone** свою копию:
   ```bash
   git clone https://github.com/your-username/tpp.git
   cd tpp
   ```
3. **Создай ветку** для фичи:
   ```bash
   git checkout -b feature/amazing-feature
   ```
4. **Внеси изменения** и убедись, что тесты проходят:
   ```bash
   sbt test
   ```
5. **Отформатируй код:**
   ```bash
   sbt scalafmtAll
   ```
6. **Закоммить и push:**
   ```bash
   git commit -m "feat: add amazing feature"
   git push origin feature/amazing-feature
   ```
7. **Создай Pull Request**

---

## 📐 Соглашения по Коду

### Именование

| Элемент | Стиль | Пример |
|---|---|---|
| Классы / Traits / Enums | PascalCase | `TransactionService` |
| Объекты | PascalCase | `DomainError` |
| Методы / Функции | camelCase | `createTransaction` |
| Value Objects | camelCase | `transactionId` |
| Константы | UPPER_SNAKE_CASE | `MAX_RETRY_COUNT` |
| Пакеты | lowercase | `domain.model` |

### Структура Файла

```scala
// 1. Импорты (сгруппированные)
import scala.concurrent.duration.*
import zio.*

// 2. Пакет и импорты из домена
import tpp.domain.model._
import tpp.domain.error._

// 3. Основной код
trait TransactionService:
  def create(cmd: CreateTransaction): IO[DomainError, Transaction]

object TransactionServiceImpl:
  // Имплементация
```

### Комментарии

- Пиши **зачем**, а не **что**
- Документируй публичные API через Scaladoc:
  ```scala
  /** Создать новую транзакцию между счетами.
    *
    * @param cmd команда создания транзакции
    * @return созданная транзакция или ошибка домена
    */
  def create(cmd: CreateTransaction): IO[DomainError, Transaction]
  ```

### Форматирование

- Используем **scalafmt** (конфиг `.scalafmt.conf` в корне)
- 2 пробела для отступов
- Максимальная длина строки: 100 символов

---

## 🧪 Тестирование

### Правила

- **Новая фича = новые тесты**
- Domain слой: **90%+ coverage**
- Application слой: **80%+ coverage**
- Название теста: описание сценария, не реализации

```scala
// ✅ Хорошо
test("fail when fromAccount has insufficient funds")

// ❌ Плохо
test("test create transaction")
```

### Запуск

```bash
# Все тесты
sbt test

# Конкретный тест
sbt "testOnly *TransactionSpec"

# Integration тесты
sbt "testOnly *IntegrationSpec"
```

---

## 📝 Коммиты

Используем **Conventional Commits**:

```
<type>(<scope>): <description>

[optional body]
```

### Типы

| Тип | Описание |
|---|---|
| `feat` | Новая фича |
| `fix` | Исправление бага |
| `docs` | Изменения в документации |
| `style` | Форматирование (без логики) |
| `refactor` | Рефакторинг (без изменений поведения) |
| `test` | Добавление/изменение тестов |
| `chore` | Рутина (CI, конфиги, зависимости) |

### Примеры

```
feat(domain): add TransactionCreated event

fix(api): return 404 for nonexistent transactions

docs: update ARCHITECTURE.md with sequence diagrams

test(application): add property-based tests for Amount

chore(deps): upgrade ZIO to 2.1.0
```

---

## 🐛 Баг-Репорты

При создании issue с багом укажи:

1. **Scala версия:** `3.8.3`
2. **Шаги воспроизведения**
3. **Ожидаемое поведение**
4. **Фактическое поведение**
5. **Логи / Stacktrace** (если есть)

---

## 💡 Предложения Фич

Для proposal:

1. Опиши **проблему**, которую решаешь
2. Предложи **решение**
3. Укажи **альтернативы**, которые рассматривал

---

## 📚 Ресурсы

- [Техническая спецификация](docs/SPEC.md)
- [Архитектура](docs/ARCHITECTURE.md)
- [План разработки](docs/ROADMAP.md)
- [ZIO Documentation](https://zio.dev/)
- [http4s Documentation](https://http4s.org/)

---

*Спасибо за вклад! 💜*
