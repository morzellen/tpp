package tpp

import zio.*

/** Точка входа приложения TPP (Transaction Processing Platform).
  *
  * Запускает приложение и настраивает
  * базовую инфраструктуру (логирование, конфигурацию).
  */
object Main extends ZIOAppDefault:

  /** Основная логика приложения.
    *
    * На текущем этапе (Фаза 0) только логирует запуск
    * и ожидает завершения работы.
    */
  val run: ZIO[Any, Nothing, Unit] =
    for
      _ <- ZIO.logInfo("=== TPP (Transaction Processing Platform) ===")
      _ <- ZIO.logInfo("Фаза 0: Подготовка и Настройка")
      _ <- ZIO.logInfo("Приложение запущено и готово к работе")
      // TODO Фаза 4: заменить ZIO.never на binding HTTP-сервера
      // или на Promise-based graceful shutdown signal
      _ <- ZIO.never
    yield ()
