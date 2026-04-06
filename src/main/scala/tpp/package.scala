package object tpp:
  /** Transaction Processing Platform (TPP).
    *
    * Платформа обработки финансовых транзакций на Scala 3 / ZIO.
    * Гибридная архитектура: Clean Architecture + Event Store + CQRS.
    *
    * Слои:
    *   - domain/ — чистая бизнес-логика (0 внешних зависимостей)
    *   - application/ — use cases (command/query handlers)
    *   - infrastructure/ — внешние зависимости (DB, Kafka, API)
    */
  ()
