# Reconciler

A web application that ingests an orders export and a payment-processor export,
reconciles them with a deterministic engine, and presents the result as a dashboard
that someone responsible for a store's revenue could act on.

An LLM layer explains discrepancies in plain language. It never decides whether two
records match.

## Status

Early scaffold. The application boots, connects to PostgreSQL, and runs its Liquibase
migrations. Feature work follows in subsequent commits.

## Tech

- Java 21, Spring Boot 4.1
- PostgreSQL with Liquibase migrations
- Thymeleaf, server-rendered (htmx for interactivity, added later)

## Running locally

**With Docker** — runs against a throwaway PostgreSQL container, nothing else to install:

```
./gradlew bootTestRun
```

**With your own database** — set the connection details (see `.env.example`) and:

```
./gradlew bootRun
```

Health check: <http://localhost:8080/actuator/health>

## Tests

```
./gradlew test
```

Integration tests use Testcontainers and require Docker to be running.
