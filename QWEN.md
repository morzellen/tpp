# TPP (Transaction Processing Platform)

## Project Overview

**TPP** — платформа обработки финансовых транзакций на Scala 3 / ZIO, реализующая гибридную архитектуру (Clean Architecture + Event Store + CQRS). Проект изначально назывался "guiness", но был переименован в "tpp".

### Key Technologies

- **Language:** Scala 3.8.3
- **Build Tool:** sbt 1.12.8
- **IDE:** IntelliJ IDEA (with Scala plugin)
- **JVM-based** application

## Project Structure

```
guiness/
├── build.sbt                 # Main build configuration
├── project/
│   └── build.properties      # sbt version specification
├── src/
│   ├── main/
│   │   └── scala/
│   │       └── main.scala    # Main application entry point
│   └── test/
│       └── scala/            # Test source directory (empty)
├── .gitignore               # Git ignore rules
├── .idea/                   # IntelliJ IDEA configuration
├── .bsp/                    # Build Server Protocol configuration
└── target/                  # Build output directory
```

## Building and Running

### Prerequisites

- Java Development Kit (JDK) 8 or higher
- sbt 1.12.8 (or use the sbt wrapper if available)

### Common Commands

**Run the application:**
```bash
sbt run
```

**Compile the project:**
```bash
sbt compile
```

**Clean build artifacts:**
```bash
sbt clean
```

**Run tests:**
```bash
sbt test
```

**Open sbt interactive console:**
```bash
sbt
```

## Development Conventions

### Code Style

The project uses Scala 3 syntax features:
- `@main` annotation for defining entry points
- `do` keyword for control structures
- String interpolation with `s"..."`
- Modern Scala 3 indentation and control flow

### Testing

The `src/test/scala/` directory exists but currently contains no test files. When adding tests:
- Use a Scala testing framework (e.g., ScalaTest, specs2)
- Place test files in `src/test/scala/`
- Follow the naming convention `*Spec.scala` or `*Test.scala`

### Version Control

The project uses Git with standard ignore patterns for:
- Build output directories (`target/`, `out/`, `bin/`)
- IDE-specific files (`.idea/`, `*.iml`, etc.)
- OS-specific files (`.DS_Store`)

## Current State

This is a minimal Scala project with a single `main.scala` file containing:
- A `@main` annotated function as the entry point
- Basic iteration examples using `foreach` and `for` comprehensions
- IDE-specific hints and debugging suggestions

The project appears to be in early development or is a template for future development.

## Adding Dependencies

To add external libraries, edit `build.sbt`:

```scala
lazy val root = (project in file("."))
  .settings(
    name := "guiness",
    libraryDependencies += "org.example" %% "library-name" % "version"
  )
```

## Notes

- No README file exists in the repository
- No external dependencies are currently configured
- The project is set to version `0.1.0-SNAPSHOT`

## Qwen Added Memories
- Проект: Transaction Processing Platform (TPP) — платформа обработки финансовых транзакций на Scala 3 / ZIO. Архитектура: Гибридная (Clean Architecture + Event Store + CQRS). Модульный монолит в одном sbt проекте. Стек: Scala 3.8.3, ZIO 2.x, http4s, Doobie, PostgreSQL, Kafka, Circe. Документация: docs/SPEC.md (техспека), docs/ARCHITECTURE.md (архитектура), docs/ROADMAP.md (план разработки), README.md (обзор). 7 фаз разработки: 0-Настройка, 1-Domain, 2-Event Store, 3-Application, 4-API, 5-Kafka, 6-Testing, 7-Production.
