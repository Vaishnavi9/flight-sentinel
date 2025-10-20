# flight-sentinel

# Flight Sentinel
Real-time flight delay monitoring with Kafka + Quarkus.

## Quick start
```bash
docker compose -f infra/docker-compose.yml up -d
python ingestion/replayer/replayer.py   # emits to flights.raw
cd services/delay-api && ./mvnw quarkus:dev
