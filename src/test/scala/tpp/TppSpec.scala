package tpp

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Базовые тесты для проверки работоспособности тестового фреймворка.
  *
  * Эти тесты подтверждают, что ScalaTest правильно настроен
  * и тестовая инфраструктура работает.
  */
class TppSpec extends AnyFlatSpec with Matchers:

  "TPP project" should "have a valid version" in {
    val version = "0.1.0-SNAPSHOT"
    version should startWith("0.1.0")
  }

  it should "support Scala 3 features" in {
    // Проверка базовых возможностей Scala 3
    case class Point(x: Int, y: Int)
    val p = Point(1, 2)
    p.x shouldBe 1
    p.y shouldBe 2
  }

  it should "handle collections correctly" in {
    val numbers = List(1, 2, 3, 4, 5)
    numbers should have size 5
    numbers should contain allOf (1, 3, 5)
  }
