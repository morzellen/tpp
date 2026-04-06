package object kafka:
  /** Kafka integration — публикация событий и consume уведомлений.
    *
    * Компоненты:
    *   - KafkaProducer — публикация domain events
    *   - NotificationConsumer — обработка уведомлений
    *   - EventPublisher — связь Event Store → Kafka
    */
  ()
