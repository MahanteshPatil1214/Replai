# Replai — AI Customer Support Platform

A beginner-friendly, event-driven support platform built with **Java (Spring Boot)**, **Python (FastAPI)**, **Kafka**, **Postgres + pgvector (RAG)**, an **LLM**, and **Docker**.

A customer submits a ticket → the system classifies it with an LLM, detects priority/sentiment, drafts a reply grounded in the knowledge base, and shows the result in the UI within a couple of seconds.

```
 Browser ──> web (nginx + React)
                │  /api/*                    /kb/*
                v                             v
        ticket-service (Java)  <──Kafka──>  ai-service (Python)
                │                             │
                └────────> Postgres <─────────┘
                        (tickets + pgvector KB)
```

### Event flow

1. User creates a ticket in the UI.
2. `ticket-service` saves it to Postgres and publishes `ticket.created` (keyed by `ticketId`).
3. `ai-service` consumes the event, retrieves the most relevant knowledge base articles (pgvector, `all-MiniLM-L6-v2` embeddings), and asks the LLM for **JSON only**: category, priority, sentiment and a suggested reply.
4. `ai-service` publishes `ticket.enriched` (with the `sources` it used, keyed by `ticketId`).
5. `ticket-service` consumes it idempotently and updates the ticket.
6. The UI shows the category, priority, sentiment, suggested reply, and the KB articles it was based on.

Only two topics exist: `ticket.created` and `ticket.enriched`. Consumers tolerate duplicates and never crash on a bad message.

## Quick start

```bash
docker compose up --build
```

Then open:

| URL | What |
|---|---|
| http://localhost:3000 | Web UI (create a ticket, inbox, ticket detail, knowledge base) |
| http://localhost:8080 | Kafka UI |
| http://localhost:8081 | ticket-service REST API (`/api/tickets`) |
| http://localhost:8001 | ai-service health (`/health`) + KB (`/kb/articles`, `/kb/search`) |

The first build downloads the embedding model (`all-MiniLM-L6-v2`) and bakes it into the ai-service image, so it is never fetched at runtime. The knowledge base is seeded with 8 starter articles on first start.

> Host ports 5433 (Postgres) and 8001 (ai-service) are used instead of 5432/8000 because they were taken by another local project. Inside the Docker network the services still use `postgres:5432` and `ai-service:8000`.

### Optional: a real LLM

Copy `.env.example` to `.env` and set:

```bash
LLM_API_KEY=sk-...
LLM_MODEL=gpt-4o-mini
LLM_BASE_URL=            # only for an OpenAI-compatible proxy
```

Without a key the service falls back to a deterministic local classifier (`General` / `MEDIUM` / `NEUTRAL`) that still drafts a reply from the retrieved chunks, so the whole flow works offline.

## Project layout

```
infra/            Postgres init (pgvector)
contracts/        JSON Schemas for the Kafka events
ticket-service/   Java: REST API + Kafka producer/consumer (controller -> service -> repository)
ai-service/       Python: consumer.py, llm.py, pipeline.py, rag/, kb_api.py, seed/
web/              React + Vite + TS + Tailwind + TanStack Query (API calls live in web/src/api/)
docker-compose.yml
```

## Local development (without Docker for the app services)

```bash
docker compose up -d postgres kafka kafka-ui     # infra only

# terminal 1 — Java API on :8081
mvn -f ticket-service/pom.xml spring-boot:run

# terminal 2 — Python AI service on :8001
cd ai-service
python -m venv .venv && .venv\Scripts\pip install -r requirements.txt   # pip install -r requirements.txt on macOS/Linux
KAFKA_BOOTSTRAP_SERVERS=localhost:9092 POSTGRES_PORT=5433 .venv\Scripts\python -m uvicorn app.kb_api:app --port 8001

# terminal 3 — web on :3000 (proxies /api and /kb to the services above)
cd web && npm install && npm run dev
```

## Tests

```bash
mvn -f ticket-service/pom.xml test     # 8 Java tests
cd ai-service && python -m pytest      # 42 Python tests
cd web && npm run build                # type-check + production build
```

## Configuration

All settings come from environment variables — see `.env.example`. **Never commit secrets**; `.env` is git-ignored.

| Variable | Used by | Default |
|---|---|---|
| `POSTGRES_USER` / `POSTGRES_PASSWORD` / `POSTGRES_DB` | postgres, both services | `replai` / `replai` / `replai` |
| `KAFKA_BOOTSTRAP_SERVERS` | both services | `kafka:9092` (compose) / `localhost:9092` (local) |
| `LLM_API_KEY` / `LLM_MODEL` / `LLM_BASE_URL` | ai-service | empty (offline fallback) / `gpt-4o-mini` / empty |

If the architecture, APIs, events or schema change, update `project.md` (and `agent.md`) in the same commit.
