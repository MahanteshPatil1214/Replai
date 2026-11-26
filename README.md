<div align="center">

# Replai

**An event-driven AI customer support platform**

Classify, prioritize and draft replies to support tickets with an LLM and RAG — over Kafka, in Java and Python, in one `docker compose up`.

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Docker Compose](https://img.shields.io/badge/Docker_Compose-2-2496ED?logo=docker&logoColor=white)](https://docs.docker.com/compose/)
[![Java](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.4-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Python](https://img.shields.io/badge/Python-3.11-3776AB?logo=python&logoColor=white)](https://www.python.org/)
[![FastAPI](https://img.shields.io/badge/FastAPI-009688?logo=fastapi&logoColor=white)](https://fastapi.tiangolo.com/)
[![Apache Kafka](https://img.shields.io/badge/Apache_Kafka-3.9-231F20?logo=apachekafka&logoColor=white)](https://kafka.apache.org/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16_%2B_pgvector-4169E1?logo=postgresql&logoColor=white)](https://github.com/pgvector/pgvector)
[![React](https://img.shields.io/badge/React-19-61DAFB?logo=react&logoColor=black)](https://react.dev/)
[![Vite](https://img.shields.io/badge/Vite-7-646CFF?logo=vite&logoColor=white)](https://vite.dev/)

</div>

---

## Table of contents

- [What it does](#what-it-does)
- [Features](#features)
- [Architecture](#architecture)
- [Tech stack](#tech-stack)
- [Quick start](#quick-start)
- [Configuration](#configuration)
- [API reference](#api-reference)
- [Event contracts](#event-contracts)
- [How the AI pipeline works](#how-the-ai-pipeline-works)
- [Project structure](#project-structure)
- [Testing](#testing)
- [Local development](#local-development)
- [Roadmap](#roadmap)
- [Contributing](#contributing)
- [License](#license)

---

## What it does

A customer submits a support ticket. Within a couple of seconds the platform has:

1. stored the ticket in Postgres,
2. searched a **vector knowledge base** (pgvector) for the most relevant help articles,
3. asked an **LLM** to classify the ticket and draft a reply **grounded in those articles**,
4. pushed the result back and rendered it in the UI — with the sources it used.

Everything between the two services happens **asynchronously over Kafka**, so each side can be scaled, restarted or redeployed independently.

## Features

- ✅ **Event-driven core** — two topics only (`ticket.created`, `ticket.enriched`), messages keyed by `ticketId`, JSON Schema contracts in [`contracts/`](contracts/)
- ✅ **RAG over pgvector** — sentence-aware chunking, `all-MiniLM-L6-v2` (384-d) embeddings, cosine similarity search, sources returned with every enrichment
- ✅ **Strict JSON LLM output** — the model must return `{category, priority, sentiment, suggestedReply}`; any bad output falls back to a safe default (`General` / `MEDIUM` / `NEUTRAL`)
- ✅ **Works offline** — no API key needed: a deterministic local classifier takes over and still drafts a reply from the retrieved chunks
- ✅ **Duplicate-tolerant consumers** — idempotent enrichment by `eventId`; malformed messages are logged and skipped, never fatal
- ✅ **Clean layering** — Java `controller → service → repository` with request/response DTOs; Python `consumer.py` / `llm.py` / `rag/` / `kb_api.py`
- ✅ **Single origin, zero CORS** — nginx proxies `/api/*` and `/kb/*`; the browser only ever talks to one host
- ✅ **Seed knowledge base** — 8 starter articles ingested and embedded automatically on first boot
- ✅ **Modern UI** — React + Vite + TypeScript + Tailwind v4 + TanStack Query: new ticket, inbox, ticket detail with AI panel, KB browser with retrieval tester

## Architecture

```
                     ┌────────────┐   /api/*    ┌────────────────────┐
   ┌──────────┐      │            │ ──────────► │                    │
   │  Browser │ ───► │  web       │             │  ticket-service    │
   │  (React) │      │  nginx      │             │  Java · Spring Boot│
   └──────────┘      │            │             │                    │
                     └─────┬──────┘             └─────────┬──────────┘
                           │ /kb/*                        │ ticket.created
                           ▼                              │ ticket.enriched
                     ┌────────────────────┐               │ (key: ticketId)
                     │  ai-service        │ ◄─────────────┤
                     │  Python · FastAPI  │               │
                     │  consumer · RAG    │               │
                     │  llm · kb_api      │               │
                     └─────────┬──────────┘               │
                               │                          │
                               ▼                          ▼
                     ┌──────────────────────────────────────────────┐
                     │  Postgres 16 + pgvector   │   Kafka (KRaft)  │
                     │  tickets · KB · embeddings│   + Kafka UI     │
                     └──────────────────────────────────────────────┘
                                            ▲
                                            │ (optional)
                                       ┌────┴─────┐
                                       │ OpenAI-  │
                                       │ compat.  │
                                       │ LLM API  │
                                       └──────────┘
```

**Event flow**

1. The UI creates a ticket → `POST /api/tickets`.
2. `ticket-service` saves it as `OPEN` and publishes **`ticket.created`** (keyed by `ticketId`).
3. `ai-service` consumes the event, embeds the message and retrieves the top knowledge base chunks from pgvector.
4. The chunks are injected into a prompt that demands **JSON only** → `{category, priority, sentiment, suggestedReply, sources}`.
5. `ai-service` publishes **`ticket.enriched`** (again keyed by `ticketId`).
6. `ticket-service` consumes it **idempotently**, writes the result and flips the ticket to `ENRICHED`.
7. The UI polls via TanStack Query and shows category, priority, sentiment, the drafted reply and the source articles.

## Tech stack

| Layer | Technology |
|---|---|
| Frontend | React 19, Vite 7, TypeScript, Tailwind CSS v4, TanStack Query |
| Backend (tickets) | Java 21, Spring Boot 3.4, Spring Data JPA, Spring Kafka, PostgreSQL |
| Backend (AI) | Python 3.11, FastAPI, aiokafka, psycopg3, sentence-transformers, `openai` SDK |
| Events | Apache Kafka 3.9 (KRaft, no ZooKeeper) + Kafka UI |
| Data | PostgreSQL 16 + **pgvector** (384-dim vectors) |
| LLM | Any OpenAI-compatible endpoint — or the built-in offline fallback |
| Packaging | Docker multi-stage builds, docker-compose, nginx reverse proxy |

## Quick start

```bash
git clone https://github.com/MahanteshPatil1214/Replai.git
cd Replai
docker compose up --build
```

Open **http://localhost:3000**, create a ticket, and watch it flip from `OPEN` to `ENRICHED` with sources.

| URL | Service |
|---|---|
| http://localhost:3000 | Web UI |
| http://localhost:8080 | Kafka UI |
| http://localhost:8081 | `ticket-service` REST API |
| http://localhost:8001 | `ai-service` (`/health`, `/kb/*`) |
| localhost:5433 | Postgres (host port) |

> **Ports:** `5433` and `8001` are used on the host because `5432`/`8000` were already taken by another local project — inside the Docker network the services still use `postgres:5432` and `ai-service:8000`.

The first build downloads the embedding model and bakes it into the ai-service image, so containers never fetch anything at runtime. The knowledge base is seeded with 8 articles on first start (wiped with `docker compose down -v`).

## Configuration

Copy the template and edit as needed — **never commit secrets** (`.env` is git-ignored):

```bash
cp .env.example .env
```

| Variable | Used by | Default | Notes |
|---|---|---|---|
| `LLM_API_KEY` | ai-service | *(empty)* | Empty ⇒ deterministic offline fallback classifier |
| `LLM_MODEL` | ai-service | `gpt-4o-mini` | Any chat model your endpoint supports |
| `LLM_BASE_URL` | ai-service | *(empty)* | OpenAI-compatible proxy (OpenRouter, Groq, Ollama, vLLM…) |
| `POSTGRES_USER` / `POSTGRES_PASSWORD` / `POSTGRES_DB` | postgres, both services | `replai` | |
| `KAFKA_BOOTSTRAP_SERVERS` | both services | `kafka:9092` / `localhost:9092` | |
| `POSTGRES_URL` | ticket-service | `jdbc:postgresql://postgres:5432/replai` | |

## API reference

### ticket-service — `:8081`

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/tickets` | Create a ticket → publishes `ticket.created` |
| `GET` | `/api/tickets` | List tickets (newest first) |
| `GET` | `/api/tickets/{id}` | Ticket with AI enrichment + sources |
| `PATCH` | `/api/tickets/{id}/status` | Move a ticket `OPEN → ENRICHED → RESOLVED` |

```bash
curl -X POST http://localhost:8081/api/tickets \
  -H "Content-Type: application/json" \
  -d '{"customerEmail":"ana@example.com","subject":"Charged twice","message":"My card was charged two times for order 8891."}'
```

### ai-service — `:8001`

| Method | Path | Description |
|---|---|---|
| `GET` | `/health` | Liveness probe |
| `POST` | `/kb/articles` | Create an article (chunked + embedded on write) |
| `GET` | `/kb/articles` | List articles |
| `GET` | `/kb/articles/{id}` | One article |
| `DELETE` | `/kb/articles/{id}` | Remove an article |
| `GET` | `/kb/search?q=…&limit=5` | Vector similarity search |
| `GET` | `/kb/chunk-stats` | Chunking/embedding statistics |

```bash
curl "http://localhost:8001/kb/search?q=charged+twice+refund&limit=3"
```

## Event contracts

Schemas live in [`contracts/`](contracts/) and are validated in both services.

**`ticket.created`** → produced by `ticket-service`, consumed by `ai-service`

```json
{
  "eventId": "5f0c9e52-3a1f-4a37-9d5f-1b4f2b2e77aa",
  "occurredAt": "2026-11-26T14:38:00Z",
  "ticketId": 4,
  "subject": "Charged twice for one order",
  "message": "My card was charged two times for order 8891.",
  "customerEmail": "ana@example.com"
}
```

**`ticket.enriched`** → produced by `ai-service`, consumed by `ticket-service`

```json
{
  "eventId": "8c952d85-e551-495e-bf02-51eae18aa1b9",
  "occurredAt": "2026-11-26T14:38:05Z",
  "ticketId": 4,
  "category": "Billing",
  "priority": "MEDIUM",
  "sentiment": "NEGATIVE",
  "suggestedReply": "Thanks for getting in touch…",
  "sources": [
    { "articleId": 2, "title": "Refunds for duplicate charges" }
  ]
}
```

Rules both sides follow: `ticketId` is the message key, `eventId` is a UUID, and consumers are **at-least-once tolerant** — a duplicate `eventId` is a no-op, and a malformed payload is logged and skipped without crashing.

## How the AI pipeline works

1. **Chunk** — article/ticket text is split into ~400-character, sentence-aware chunks with a 60-character overlap that never cuts a word in half.
2. **Embed** — each chunk becomes a 384-dimension vector with `all-MiniLM-L6-v2`; the *same* model is used for ingestion and query search.
3. **Retrieve** — the ticket message is embedded and matched by cosine similarity against pgvector.
4. **Prompt** — top chunks are injected into a system prompt that forces **JSON only** and lists the categories the model may choose.
5. **Validate** — `extract_json` parses the answer; anything unusable falls back to `General` / `MEDIUM` / `NEUTRAL` with a generic reply, so the pipeline never fails.
6. **Publish** — the result, plus deduped `{articleId, title}` sources, goes out as `ticket.enriched`.

## Project structure

```
Replai
├── docker-compose.yml        # postgres · kafka · kafka-ui · ticket-service · ai-service · web
├── infra/init.sql             # pgvector extension + tables
├── contracts/                 # JSON Schemas for both events
├── ticket-service/            # Java 21 · Spring Boot
│   └── src/main/java/.../{controller,service,repository,dto,kafka}
├── ai-service/                # Python 3.11 · FastAPI
│   └── app/{consumer,llm,pipeline,events,kb_api,db}.py  ·  app/rag/  ·  seed/
├── web/                       # React + Vite + TS + Tailwind
│   └── src/{api,pages,components}/
├── project.md                 # full project spec (source of truth)
└── agent.md                   # implementation guidelines
```

## Testing

```bash
# Java — 8 tests (validation, producer keys, idempotent enrichment)
mvn -f ticket-service/pom.xml test

# Python — 42 tests (LLM JSON parsing, consumer resilience, RAG, chunking, seed)
cd ai-service && python -m pytest

# Frontend — type-check + production build
cd web && npm run build
```

## Local development

Run the infrastructure in Docker and the apps natively for fast feedback:

```bash
docker compose up -d postgres kafka kafka-ui

# terminal 1 — Java API on :8081
mvn -f ticket-service/pom.xml spring-boot:run

# terminal 2 — AI service on :8001
cd ai-service
python -m venv .venv
pip install -r requirements.txt
# Windows: .venv\Scripts\activate
KAFKA_BOOTSTRAP_SERVERS=localhost:9092 POSTGRES_PORT=5433 python -m uvicorn app.kb_api:app --port 8001

# terminal 3 — web on :3000 (Vite proxies /api and /kb)
cd web && npm install && npm run dev
```

## Roadmap

- [ ] Re-ranking + hybrid keyword/vector search
- [ ] PDF and URL article import
- [ ] Streaming LLM responses and token-level cost metrics
- [ ] Ticket status transitions in the UI (`OPEN → ENRICHED → RESOLVED`)
- [ ] GitHub Actions CI with the build badge

## Contributing

Issues and pull requests are welcome.

1. Fork the repo and create a feature branch: `git checkout -b feature/my-change`
2. Keep to the conventions in [`agent.md`](agent.md) (two Kafka topics only, DTO validation, JSON-only LLM output, tests for new behaviour)
3. Make sure all three test suites pass (see [Testing](#testing))
4. If you change architecture, APIs, events or schema, update `project.md` in the same commit
5. Open a PR with a short description of the change

## License

Distributed under the [MIT License](LICENSE).
