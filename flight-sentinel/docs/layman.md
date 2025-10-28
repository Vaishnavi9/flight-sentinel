# Flight Sentinel — In Plain English

## What is this application?
**Flight Sentinel** is a small system that watches flights and tells you if they’re running late.  
Think of it like a live scoreboard for flights: it reads flight updates, calculates how late each one is, and exposes simple screens/APIs so people or other software can check the current situation and basic stats.

---

## Why does it exist?
Airlines, airports, and travelers all care about delays. But raw flight data arrives as a stream of events (lots of tiny updates).  
This app:
1) **Collects** those updates,
2) **Cleans & stores** them,
3) **Calculates delay**, and
4) **Serves** the results quickly.

It’s built to be **reliable**, **scalable**, and **easy to understand**, so it’s useful as both a demo and a foundation for a real product.

---

## Real-world analogy
Imagine a **post office** that receives letters (flight updates) all day:
- The **mail slot** where letters arrive is **Kafka** (a mailbox for data).
- The **sorting room** (that opens, reads, and files letters) is the **Delay API service**.
- The **archive cabinet** that keeps all letters for years is **PostgreSQL** (history).
- The **desk tray** with today’s most important letters on top is **Redis** (fast, recent lookups).
- The **wall dashboard** with counters (letters/hour, average delay) is **Prometheus/Grafana** (observability).
- A **little clerk** that occasionally predicts “this flight might be late” is the **ML service** (optional).

---

## The moving parts (components)

### 1) **Replayer** (CSV → Kafka)
- **What it does:** Reads a simple CSV file with flight records and sends each row as a tiny message into Kafka (our mailbox).
- **Why it matters:** Real providers send live data. While we’re developing, the replayer **simulates** that live feed so we can test end-to-end.

### 2) **Kafka** (the mailbox / conveyor belt)
- **What it does:** Safely holds and streams incoming flight messages to whoever needs them.
- **Why it matters:** It lets us handle **lots of updates**, **retry** if something breaks, and **scale** later without losing data.

### 3) **Delay API** (Quarkus service)
- **What it does:** Listens to Kafka, cleans/normalizes each message, calculates **delay** (actual minus scheduled), stores results, and exposes **HTTP endpoints** (URLs) for queries.
- **Why it matters:** It turns raw events into **useful information** you can ask for: “show me recent delays at AMS today.”

### 4) **PostgreSQL** (historical storage)
- **What it does:** Stores normalized flight records and their delays.
- **Why it matters:** We can run queries like “average delay by airport last week” and **keep history** beyond today.

### 5) **Redis** (hot cache)
- **What it does:** Keeps **recent** flight delay results in memory for super-fast answers.
- **Why it matters:** “What’s happening right now?” should feel instant. Redis is our “top of the pile” tray.

### 6) **(Optional) ML Service** (tiny Python app)
- **What it does:** Uses a simple model to **estimate** the chance a flight will be late based on past patterns.
- **Why it matters:** It’s a small taste of “AI-assisted operations” without making the system depend on it.

### 7) **Observability: Prometheus + Grafana**
- **What it does:** Measures health (message rate, errors, response times) and shows graphs.
- **Why it matters:** When something slows or breaks, we know **what** and **why**.

---

## How data flows (end-to-end)

1) **Replayer reads CSV** and creates a message like:
   ```json
   {
     "flight_id": "LH123",
     "carrier": "LH",
     "origin": "FRA",
     "destination": "AMS",
     "scheduled_dep": "2025-01-05T10:10:00Z",
     "actual_dep": "2025-01-05T10:42:00Z"
   }
