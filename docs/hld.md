# Flight Sentinel — Technical Overview

## Purpose
Flight Sentinel is a **real-time event-driven system** for monitoring and predicting flight delays.  
It ingests raw flight events, normalizes and enriches them, computes delay metrics, stores historical records, and serves APIs for querying both real-time and aggregated data.  
The architecture demonstrates **scalability, resilience, and observability** principles used in production systems.

---

## Architecture Components

### 1. Ingestion — **Replayer**
- Reads CSV datasets (historical flight data).
- Publishes each record as a JSON message to Kafka topic `flights.raw`.
- Acts as a **simulator for live event streams** during development.

### 2. Messaging Backbone — **Kafka**
- Central message bus for all flight events.
- Provides **decoupling** between producers (replayer or future live feeds) and consumers (Delay API, analytics jobs).
- Configured with **partitioning** for parallelism and **consumer groups** for horizontal scaling.
- Retains messages for replay and fault recovery.

### 3. Processing & API Layer — **Delay API (Quarkus service)**
- **Consumes** messages from Kafka → validates, normalizes (ISO 8601 times, uppercase IATA codes).
- **Computes delay** = `actual_dep - scheduled_dep`.
- **Writes normalized data** into Postgres (persistent storage).
- **Caches hot data** (last 24h, recent queries) in Redis.
- **Exposes REST/JSON APIs**:
    - `GET /flights/{id}` → flight details
    - `GET /delays/recent?airport=AMS` → recent delays
    - `GET /stats/airports?date=2025-01-05` → aggregated stats
- Integrated with **Micrometer metrics** and **OpenAPI** spec.

### 4. Storage
- **PostgreSQL**: Authoritative store for all historical flight events. Enables analytical queries, joins, and persistence beyond cache lifetime.
- **Redis**: In-memory store for low-latency retrieval of recent flight delays.

### 5. Observability
- **Prometheus** scrapes API and consumer metrics (lag, throughput, error rate).
- **Grafana** dashboards visualize ingestion rate, consumer lag, API response times, and cache hit ratio.
- Structured logging (JSON) with correlation IDs for traceability.

### 6. (Optional) ML Sidecar — **Python FastAPI**
- Trains a lightweight model on historical delays (features: airport, carrier, hour of day, weekday).
- Exposes `/predict` endpoint for Delay API to call.
- Demonstrates **ML integration in microservice architecture** without coupling inference to the main pipeline.

### 7. Infrastructure
- **Docker Compose** for local orchestration of Kafka, Zookeeper, Postgres, Redis, Prometheus, Grafana.
- **Kubernetes manifests** (Deployments, Services, ConfigMaps) for container orchestration and scaling.
- **Runbook.md** documents startup, health checks, and operational procedures.

---

## Data Flow

1. **Replayer → Kafka**
    - CSV events published to `flights.raw`.

2. **Kafka → Delay API**
    - Consumer reads, normalizes, computes delay.
    - Failed events routed to `flights.dlq`.

3. **Delay API → Storage**
    - Writes normalized data to Postgres.
    - Writes hot data (24h window) to Redis.

4. **API Clients → Delay API**
    - Query APIs for flight status, recent delays, or aggregated stats.
    - Delay API serves from Redis (fast path) or Postgres (historical).

5. **Observability**
    - Prometheus scrapes metrics; Grafana visualizes.
    - Alerts defined for lag, API latency, DB errors.

6. **(Optional) ML**
    - Delay API calls ML sidecar → gets probability of delay.
    - ML predictions can be added to API responses.

---

## Key Design Principles

- **Event-driven architecture:** Kafka buffers and decouples producers from consumers.
- **Idempotency:** Writes keyed on `(flight_id, scheduled_dep)` prevent duplicates.
- **Resilience:** DLQ for poison messages; consumer groups for recovery; retries with exponential backoff.
- **Performance:** Redis for sub-50ms recent lookups; Postgres indexes for historical queries.
- **Scalability:** Kafka partitions by `airport_code` or `flight_id`; consumers can be scaled horizontally.
- **Observability:** Metrics, logging, and dashboards included from the start.
- **Extensibility:** ML sidecar is optional, isolated, and replaceable.

---

## Example Queries

- **API Call**:  
  `GET /delays/recent?airport=AMS`  
  **Response**:
  ```json
  [
    {"flight_id":"LH123","delay_minutes":32},
    {"flight_id":"KL456","delay_minutes":5}
  ]
