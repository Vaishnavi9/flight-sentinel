# Backend Architecture & Folder Guide

This document explains the structure of the backend for **Flight Sentinel**: what each folder does, how services interact, and the conventions to follow when adding code.

## Repository Map (backend-relevant)

```
flight-sentinel/
  ingestion/
    replayer/                 # CSV -> Kafka publisher (simulated live feed)
  services/
    delay-api/                # Quarkus microservice (consumer + REST APIs)
    ml-service/               # (later) Python FastAPI for predictions
  infra/
    docker-compose.yml        # Local infra: Kafka, ZK, Postgres, Redis (Grafana later)
    k8s/                      # (later) K8s manifests
    grafana/                  # (later) Dashboards & provisioning
  data/
    samples/                  # Example CSVs for local runs
  docs/
    hld.md
    lld.md
    runbook.md
    backend-architecture.md   # ← this file
    diagrams/
```

---

## Architectural Overview (backend)

- **Ingestion** layer publishes *raw* flight events to Kafka (`flights.raw`).
- **Processing/API** layer (Quarkus) consumes, normalizes, computes delay, persists to Postgres, caches hot data in Redis, and exposes REST.
- **Optional ML** sidecar provides `/predict` to estimate delay probability. The API composes results.
- **Infra** is declarative and reproducible (Docker Compose for dev; K8s later).

---

## Folder-by-Folder

### `ingestion/replayer/`
**Purpose:** Simulate real-time events from CSVs.

**Contents (expected):**
- `replayer.py` — reads CSV and publishes JSON to Kafka topic `flights.raw`.
- `requirements.txt` — Python deps.
- `.env` (optional) — override broker/topic/path.

**Conventions:**
- Do **not** transform domain semantics here; publish the **raw** CSV fields (normalized ISO timestamps are OK).
- Keep rate control via `SLEEP_SECS` env.
- One job: read → validate minimally → publish.

---

### `services/delay-api/` (Quarkus)
**Purpose:** Core backend microservice — stream processor + REST API.

**High-level modules (recommended Java packages):**
```
com.flight.sentinel
  ├─ api/            # JAX-RS resources (controllers)
  ├─ service/        # business logic (delay calc, caching, orchestration)
  ├─ stream/         # Kafka consumer/producer, DLQ, deserialization
  ├─ repository/     # Postgres via Panache/JPA or JDBC
  ├─ domain/         # Entities, value objects, enums
  ├─ dto/            # Request/response payloads
  ├─ cache/          # Redis accessors
  ├─ config/         # Config, CDI producers (ObjectMapper, Kafka props)
  ├─ observability/  # Micrometer meters, logging filters
  └─ util/           # Small helpers (time, keys)
```

**Key responsibilities:**
- **Stream consume:** Read `flights.raw`, validate, normalize (uppercase IATA, ISO 8601), compute `delay_ms`.
- **Persistence:** Upsert by natural key `(flight_id, scheduled_dep)` to ensure idempotency.
- **Caching:** Maintain 24h hot sets for frequent queries (`/delays/recent?airport=…`) in Redis.
- **APIs:** REST endpoints + OpenAPI; return JSON only.
- **Observability:** Metrics for throughput, lag, latency, cache hit ratio.

**Config (application.properties examples):**
```
# HTTP
quarkus.http.port=8080

# Kafka
kafka.bootstrap.servers=${KAFKA_BROKER:localhost:9092}
app.topic.raw=flights.raw
app.topic.dlq=flights.dlq
app.consumer.group=delay-api

# Postgres
quarkus.datasource.jdbc.url=jdbc:postgresql://localhost:5432/flights
quarkus.datasource.username=flights
quarkus.datasource.password=flights
quarkus.hibernate-orm.database.generation=update

# Redis
quarkus.redis.hosts=redis://localhost:6379

# OpenAPI
quarkus.smallrye-openapi.path=/q/openapi
```

**API sketch:**
- `GET /health`
- `GET /flights/{id}`
- `GET /delays/recent?airport=AMS&since=PT24H`
- `GET /stats/airports?date=YYYY-MM-DD`
- `GET /flights/{id}/predict` *(later; proxied to ml-service)*

---

### `services/ml-service/` (Python FastAPI) — later
**Purpose:** Serve a small model to estimate probability of delay.

**Contents (expected):**
- `app.py` (FastAPI)
- `model.pkl` or `model.onnx`
- `requirements.txt`
- `Dockerfile`

**Contract:**
- `POST /predict` with simple features → `{ probability_delay_over_30min: 0.xx }`
- Stateless; version the model; log feature drift notes.

---

### `infra/`
**Purpose:** Local orchestration and, later, K8s deployment.

- `docker-compose.yml`  
  Brings up **Zookeeper, Kafka, Postgres, Redis** (and later Prometheus/Grafana).
- `k8s/` (later)
    - `delay-api-deployment.yaml`, `service.yaml`, `configmap.yaml`, `secret.yaml`
    - Topic creation handled by ops or init job (don’t hardcode in app).
- `grafana/` (later)
    - `dashboards/*.json` (API latency, consumer lag, cache hit)
    - Provisioning files

**Conventions:**
- Compose is for developer parity; don’t bake secrets into Git.
- Keep manifests minimal and environment-driven.

---

### `data/samples/`
**Purpose:** Example files for repeatable local runs.

- `flights_sample.csv` — tiny dataset to prove E2E.
- Larger samples OK, but keep one **small** file for quick smoke tests.

**Conventions:**
- Don’t commit large proprietary datasets.
- CSV headers must match the replayer schema.

---

### `docs/`
**Purpose:** Explain design and operations.

- `hld.md` — high-level design; context, NFRs, dataflow.
- `lld.md` — modules/classes, sequence diagrams, key algorithms.
- `runbook.md` — how to start, verify, monitor, recover.
- `backend-architecture.md` — this file.
- `diagrams/` — Excalidraw/PNG/SVG of architecture and flows.

---

## Component Integration (How it fits together)

```
          CSV (data/samples)          API Clients
                 │                         │
                 ▼                         │ HTTP/JSON
         Ingestion/Replayer ───────────────┼──────────────▶ delay-api (REST)
                 │ Kafka (flights.raw)     │
                 ▼                         │
            Kafka Broker  ◀───────────── delay-api (consumer)
                 │                          │
                 │                          ├─ Postgres (history, queries)
                 │                          └─ Redis (hot cache)
                 │
                 └──────▶ (optional) ml-service (FastAPI) ◀─ delay-api (call-out)
```

---

## Topic & Keying Strategy

- **Topics**
    - `flights.raw` — raw/normalized flight events from replayer/provider.
    - `flights.dlq` — dead letters for poison messages.

- **Partitioning**  
  Start with 1 partition (dev). Later:
    - By `airport_code` to scale read patterns per airport, or
    - By `flight_id` to group updates for the same flight.

- **Idempotency Key**  
  Natural key: `(flight_id, scheduled_dep)`  
  Upsert on this pair to avoid duplicates during replays.

---

## Data Model (DB sketch)

Table: `flights`
```
flight_id         TEXT        not null
carrier           TEXT        not null
origin            TEXT        not null  -- IATA
destination       TEXT        not null  -- IATA
scheduled_dep     TIMESTAMP   not null
actual_dep        TIMESTAMP   null
delay_ms          BIGINT      null
PRIMARY KEY (flight_id, scheduled_dep)
INDEX idx_by_origin_dest_date(origin, destination, scheduled_dep)
INDEX idx_by_airport_date(destination, scheduled_dep)
```

---

## Error Handling & DLQ

- **Validation errors** (missing fields, bad timestamp): send to `flights.dlq` with reason code.
- **Transient errors** (DB down): retry with backoff; log and meter failures.
- **Poison pill** detection: after N retries → DLQ.

---

## Configuration & Environments

- Prefer **env vars** or `application.properties` overrides for:
    - Kafka brokers, group id, topics
    - DB URL/user/pass
    - Redis hosts
    - Feature flags (enable ML)

- Keep **local defaults** in repo; externalize secrets in real envs.

---

## Observability (baseline)

- Micrometer registry → Prometheus scrape (`/q/metrics`)
- Suggested metrics:
    - `fs_events_processed_total`
    - `fs_consumer_lag`
    - `fs_api_latency_seconds` (histogram)
    - `fs_cache_hit_ratio`
    - `fs_db_write_latency_seconds`
- Correlation ID middleware for request logs.

---

## Testing Strategy

- **Unit:** domain calc (delay), normalizers, key generation.
- **Stream integration:** embedded Kafka test or Testcontainers.
- **API integration:** Quarkus DevServices or Testcontainers for Postgres/Redis.
- **E2E (local):** `docker compose up`, run replayer, cURL smoke tests.

---

## Coding Conventions

- **Java/Quarkus**
    - JAX-RS for REST, DTOs separate from entities.
    - Service layer: no DB or Kafka code; keep business logic here.
    - Repository layer: DB access only.
    - Avoid static state; use CDI injection.

- **Python (ml-service)**
    - FastAPI, pydantic models, typed endpoints.
    - Pin model & deps with explicit versions.

- **Message contracts**
    - Start JSON; document schema; move to Avro when stable.
    - Never break compatibility without version bump.

---

## How to Add a New Feature (example: “cancelled flights”)

1. **Contract** — Extend raw event schema with `status` (scheduled, departed, cancelled).
2. **Stream** — Update consumer to handle `cancelled`: compute delay as `null`, set status.
3. **DB** — Add `status` column; backfill strategy for old rows.
4. **API** — Add filter `status=cancelled` and reflect in responses.
5. **Cache** — Include `status` in Redis keys/set members for recent views.
6. **Docs** — Update HLD/LLD, OpenAPI, diagrams, runbook.
7. **Tests** — Unit + integration for new status handling.

---

## Definition of Done (backend change)

- Code + tests ✅
- DB migration (if any) ✅
- Metrics added/updated ✅
- Docs (HLD/LLD/OpenAPI) updated ✅
- Smoke test via replayer + cURL ✅

---

## Glossary (quick)

- **Kafka**: distributed log for events (decouples producers/consumers).
- **DLQ**: dead-letter queue for messages that consistently fail processing.
- **Idempotent upsert**: write logic that ignores duplicates (same key).
- **Redis**: in-memory store for low-latency reads.
- **Micrometer/Prometheus**: metrics and scraping for monitoring.

---

**Last updated:** Week 1 baseline.  
**Owner:** Vaishnavi Dhanwade.
