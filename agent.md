# agent.md

Instructions for AI coding agents working on **Replai**. Read `project.md` first for the full design.

## Project summary

Replai is an AI customer support platform built from 3 services plus infrastructure:

| Part | Tech | Folder |
|---|---|---|
| ticket-service | Java, Spring Boot | `ticket-service/` |
| ai-service | Python, FastAPI, Kafka consumer | `ai-service/` |
| web | React (Vite), TypeScript, Tailwind | `web/` |
| Infra | Kafka (KRaft), Kafka UI, Postgres + pgvector | `docker-compose.yml` |

## Flow (do not change without asking)

1. UI calls `POST /api/tickets` on ticket-service
2. ticket-service saves the ticket and publishes `ticket.created`
3. ai-service consumes it, retrieves knowledge base chunks (RAG), calls the LLM, publishes `ticket.enriched`
4. ticket-service consumes `ticket.enriched` and updates the ticket
5. UI shows the AI results and the sources used

## Rules

### General
- Keep it beginner-friendly. Prefer simple, readable code over clever abstractions.
- Do not add new services, frameworks, or infrastructure (gateway, Redis, Prometheus, etc.) unless asked.
- Make small, focused changes. Do not refactor unrelated code.
- Never commit secrets. Read keys from environment variables (`.env`), and keep `.env` out of git.
- Update `project.md` if you change the architecture, APIs, events, or schema.

### Login and roles (RBAC)
- Roles are `AGENT`, `LEAD`, `ADMIN` on `employees.role`. Passwords are BCrypt hashes in `employees.password_hash`.
- `POST /api/tickets` is the only public endpoint (the customer form). Everything else needs `Authorization: Bearer <token>` from `POST /api/auth/login`.
- Ticket-service owns login and signs HS256 JWTs with `JWT_SECRET`; ai-service verifies the same token with the same secret (`app/auth.py`) because the browser calls `/kb` directly. Keep the two defaults identical.
- Endpoint rules live in `security/SecurityConfig.java`, per-ticket ownership in `TicketService.assertCanWorkOn` (agents only touch tickets assigned to them). Do not trust anything in the request body for identity - `resolvedBy` comes from the token.
- The web app mirrors the rules for looks only (`lib/auth.ts`, `RequireRole` in `App.tsx`); the API is what actually enforces them.

### Kafka and events
- Topics: `ticket.created` and `ticket.enriched` only.
- Use `ticketId` as the message key.
- Every event has an `eventId` (UUID). Event shapes live in `contracts/` and `project.md`. Java and Python must match them exactly.
- Consumers must tolerate duplicate messages (check `eventId` or make updates idempotent).
- Never crash the consumer on one bad message. Log the error and continue.

### Java (ticket-service)
- Spring Boot, Spring Web, Spring Data JPA, Spring Kafka.
- Layers: `controller` → `service` → `repository`. Keep controllers thin.
- Use DTOs for request and response bodies. Do not expose JPA entities directly.
- Validate input with `jakarta.validation` annotations.
- Config goes in `application.yml`, with values overridable by environment variables.

### Python (ai-service)
- Python 3.11+, type hints on public functions.
- Structure: `consumer.py` (Kafka), `llm.py` (LLM call and JSON parsing), `rag/` (chunking, embeddings, search), `kb_api.py` (FastAPI routes).
- The LLM must return JSON only. Strip code fences and handle invalid JSON with a safe fallback (category `General`, priority `MEDIUM`).
- Use the same embedding model (`all-MiniLM-L6-v2`, 384 dimensions) for both ingestion and search.
- Reply only from retrieved knowledge base content. If nothing relevant is found, say a human agent will follow up.

### React (web)
- React + Vite + TypeScript. Functional components and hooks only.
- Styling with Tailwind. Follow the design direction: serif headings (Fraunces or Instrument Serif), Geist or Inter Tight for body text, JetBrains Mono for IDs and timestamps.
- Warm off-white and near-black palette, one accent color reserved for AI-generated content, hairline borders, light and dark mode.
- Do not use generic admin-dashboard templates or default component-library looks.
- Fetch data with TanStack Query. Keep API calls in a single `api/` folder.

### Database
- Postgres with the pgvector extension (`pgvector/pgvector:pg16`).
- Tables: `tickets` (ticket-service), `kb_articles` and `kb_chunks` (ai-service).
- Each service only writes to its own tables.

## Commands

```bash
# Run everything
docker compose up --build

# Stop and remove containers
docker compose down

# Java service (local)
cd ticket-service && ./mvnw spring-boot:run
cd ticket-service && ./mvnw test

# Python service (local)
cd ai-service && pip install -r requirements.txt
cd ai-service && uvicorn app.kb_api:app --reload --port 8001
cd ai-service && pytest

# Web (local)
cd web && npm install
cd web && npm run dev
```

## Local URLs

| What | URL |
|---|---|
| Web UI | http://localhost:3000 |
| ticket-service API | http://localhost:8081/api/tickets |
| ai-service API | http://localhost:8001 |
| Kafka UI | http://localhost:8080 |
| Postgres | localhost:5433 (user `replai`, db `replai`) |

> Host port 5432 and 8000 are already taken by another local project, so Replai uses
> **5433** for Postgres and **8001** for ai-service. Inside the Docker network the
> ports stay canonical: `postgres:5432` and `ai-service:8000`.

## Environment variables

```
LLM_API_KEY=
KAFKA_BOOTSTRAP_SERVERS=kafka:9092
POSTGRES_URL=
POSTGRES_USER=
POSTGRES_PASSWORD=
JWT_SECRET=            # shared by ticket-service and ai-service (>= 32 characters)
JWT_EXPIRY_MINUTES=480
SEED_PASSWORD=         # password given to the seeded employees
```

## Definition of done

Before finishing a task, check that:
- [ ] The code builds and the relevant tests pass
- [ ] `docker compose up --build` still starts everything
- [ ] A ticket created in the UI gets enriched and shows AI results
- [ ] Event formats still match `project.md` and `contracts/`
- [ ] No secrets or large generated files were added
- [ ] `project.md` is updated if behavior or design changed

## When unsure

Ask before changing the architecture, the event contracts, or the database schema. For anything else, pick the simplest option that matches `project.md`.