# Flight Sentinel (Backend Starter)

This starter follows a **Clean Architecture** with simple layers:
- `api/` (controllers) — HTTP only, thin
- `usecase/` — application logic (use cases)
- `domain/` — core entities and rules
- `ports/` — interfaces to the outside world
- `infrastructure/` — adapters implementing ports (DB, Kafka, Redis)

## Getting started
- Start dependencies:
  ```bash
  cd deployment && docker compose up -d
  ```
- Build & run API (requires Java 21 & Maven):
  ```bash
  cd services/delay-api
  ./mvnw quarkus:dev
  ```
- Swagger UI: http://localhost:8080/docs
- OpenAPI JSON/YAML: http://localhost:8080/openapi

## Why this architecture?
- **Separation of concerns** → easier testing & maintenance
- **Framework-agnostic domain** → swap adapters without touching business logic
- **Testability** → use cases testable without Kafka/DB
- **Scalability** → clear boundaries help teams move independently
- **Evolvability** → add endpoints or adapters without rewiring core logic

See `docs/openapi/flight-sentinel.yaml` for the initial contract.
