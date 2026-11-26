# Replai: AI Customer Support Platform

A beginner-friendly hybrid microservice project using **Java**, **Python**, **Kafka**, **GenAI**, and **Docker**.

A customer submits a support ticket. The system uses an LLM to classify it, detect its priority, and draft a reply. The result appears in the UI.

---

## 1. Goals

- Learn how Java and Python services work together
- Learn event-driven communication with Kafka
- Integrate an LLM into a real workflow
- Run everything with a single `docker compose up`

---

## 2. Architecture

```
┌──────────┐     ┌────────────────┐     ┌───────┐     ┌──────────────┐
│  Web UI  │────►│ ticket-service │────►│ Kafka │────►│  ai-service  │
│ (React)  │◄────│    (Java)      │◄────│       │◄────│   (Python)   │
└──────────┘     └───────┬────────┘     └───────┘     └──────┬───────┘
                         │                                   │
                    ┌────▼─────┐                        ┌────▼────┐
                    │ Postgres │                        │ LLM API │
                    └──────────┘                        └─────────┘
```

### Flow

1. User creates a ticket in the UI
2. `ticket-service` saves it to Postgres and publishes `ticket.created`
3. `ai-service` consumes the event
4. `ai-service` searches the **knowledge base** (pgvector) for the most relevant articles (RAG)
5. `ai-service` calls the LLM with the ticket plus the retrieved articles
6. `ai-service` publishes `ticket.enriched` (including the sources used)
7. `ticket-service` consumes it and updates the ticket
8. UI shows category, priority, sentiment, the suggested reply, and the knowledge base articles it was based on

> The knowledge base lives in the same Postgres instance, using the **pgvector** extension. No extra service is needed.

---

## 3. Services

| Service | Language | Responsibility |
|---|---|---|
| `ticket-service` | Java (Spring Boot) | REST API, ticket storage, Kafka producer and consumer |
| `ai-service` | Python (FastAPI + Kafka consumer) | Consumes tickets, retrieves knowledge base articles (RAG), calls the LLM, publishes results, manages KB articles |
| `web` | React (Vite) | Modern UI for creating and viewing tickets and managing the knowledge base |

**Infrastructure (Docker):** Kafka (KRaft mode), Kafka UI, Postgres with the pgvector extension (use the `pgvector/pgvector:pg16` image)

---

## 4. Kafka Topics

| Topic | Producer | Consumer | Purpose |
|---|---|---|---|
| `ticket.created` | ticket-service | ai-service | New ticket needs analysis |
| `ticket.enriched` | ai-service | ticket-service | AI results ready |

Use `ticketId` as the message key so events for one ticket stay in order.

### Event: `ticket.created`

```json
{
  "eventId": "uuid",
  "ticketId": "123",
  "customerEmail": "user@example.com",
  "subject": "Payment failed",
  "message": "I was charged twice for my order.",
  "createdAt": "2026-11-26T10:00:00Z"
}
```

### Event: `ticket.enriched`

```json
{
  "eventId": "uuid",
  "ticketId": "123",
  "category": "Billing",
  "priority": "HIGH",
  "sentiment": "NEGATIVE",
  "summary": "Customer was charged twice for one order.",
  "suggestedReply": "Hi, we're sorry about the double charge...",
  "sources": [
    { "articleId": 7, "title": "Refunds for duplicate charges" }
  ]
}
```

---

## 5. Database (Postgres)

**Table: `tickets`**

| Column | Type |
|---|---|
| id | BIGSERIAL (PK) |
| customer_email | VARCHAR |
| subject | VARCHAR |
| message | TEXT |
| status | VARCHAR (`OPEN`, `ENRICHED`, `RESOLVED`) |
| category | VARCHAR |
| priority | VARCHAR |
| sentiment | VARCHAR |
| summary | TEXT |
| suggested_reply | TEXT |
| sources | JSONB (articles used for the reply) |
| created_at | TIMESTAMP |

**Table: `kb_articles`** (owned by `ai-service`)

| Column | Type |
|---|---|
| id | BIGSERIAL (PK) |
| title | VARCHAR |
| content | TEXT |
| created_at | TIMESTAMP |

**Table: `kb_chunks`** (owned by `ai-service`)

| Column | Type |
|---|---|
| id | BIGSERIAL (PK) |
| article_id | BIGINT (FK to `kb_articles`) |
| chunk_text | TEXT |
| embedding | VECTOR(384) |

Enable the extension once: `CREATE EXTENSION IF NOT EXISTS vector;`

Search the closest chunks with:

```sql
SELECT article_id, chunk_text
FROM kb_chunks
ORDER BY embedding <=> :query_embedding
LIMIT 3;
```

---

## 6. REST API (ticket-service)

| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/tickets` | Create a ticket |
| GET | `/api/tickets` | List all tickets |
| GET | `/api/tickets/{id}` | Get a single ticket |
| PATCH | `/api/tickets/{id}/status` | Update status |

## 6.1 REST API (ai-service, knowledge base)

| Method | Endpoint | Description |
|---|---|---|
| POST | `/kb/articles` | Add an article (chunks and embeds it automatically) |
| GET | `/kb/articles` | List all articles |
| DELETE | `/kb/articles/{id}` | Delete an article and its chunks |
| GET | `/kb/search?q=...` | Test search, returns the closest chunks |

---

## 7. AI Service Logic

One prompt returns structured JSON, grounded in knowledge base articles:

```
You are a customer support assistant.
Use ONLY the knowledge base articles below to write the reply.
If the articles don't cover the question, say a human agent will follow up.

Knowledge base articles:
{retrieved_chunks}

Ticket:
{subject}
{message}

Return ONLY JSON with:
category (Billing | Technical | Account | General),
priority (LOW | MEDIUM | HIGH),
sentiment (POSITIVE | NEUTRAL | NEGATIVE),
summary (one sentence),
suggestedReply (polite, short).
```

Steps:
1. Consume `ticket.created`
2. Embed the ticket text (subject + message)
3. Search `kb_chunks` for the top 3 closest chunks (RAG retrieval)
4. Build the prompt with the retrieved chunks and call the LLM
5. Parse the JSON safely (strip code fences, handle errors)
6. Publish `ticket.enriched` with the `sources` used

### Knowledge Base and RAG

**What RAG means here:** instead of letting the LLM guess, the AI first looks up your company's own help articles and writes the reply based on them.

**Adding an article (ingestion):**
1. `POST /kb/articles` with a title and content
2. Split the content into chunks (about 300 to 500 characters, with a small overlap)
3. Create an embedding for each chunk
4. Store the chunk text and embedding in `kb_chunks`

**Answering a ticket (retrieval):**
1. Embed the ticket text with the same embedding model
2. Run the similarity query on `kb_chunks` (see the SQL in the database section)
3. Pass the top 3 chunks to the LLM prompt
4. Return the matching article titles as `sources`

**Embedding model:** use `sentence-transformers` with `all-MiniLM-L6-v2`. It is free, runs locally in the container, and produces 384-dimension vectors (matching `VECTOR(384)`). The same model must be used for both ingestion and search.

**Seed data:** start with 5 to 10 short articles such as refund policy, password reset, shipping times, and payment failures.

---

## 8. UI

**Stack:** React (Vite), TypeScript, Tailwind CSS, React Router, TanStack Query

**Typography-led design (not a generic admin template):**
- Headings: serif (Fraunces or Instrument Serif)
- Body: Geist or Inter Tight
- IDs and timestamps: JetBrains Mono
- Warm off-white background, near-black text, one accent color for AI-generated content
- Hairline borders, generous spacing, light and dark mode

**Screens:**
1. **New ticket form:** clean customer-facing form
2. **Inbox:** list of tickets with priority and category tags
3. **Ticket detail:** original message plus an AI panel (summary, sentiment, suggested reply with a Copy button, and a "Based on" list showing the knowledge base articles used)
4. **Knowledge base:** list, add, and delete articles, with a small search box to test what the AI would find

---

## 9. Folder Structure

```
replai/
├── docker-compose.yml
├── project.md
├── contracts/              # shared event JSON examples
├── ticket-service/         # Java Spring Boot
│   ├── src/
│   ├── pom.xml
│   └── Dockerfile
├── ai-service/             # Python
│   ├── app/
│   │   ├── consumer.py     # Kafka consumer and producer
│   │   ├── llm.py          # LLM call and JSON parsing
│   │   ├── rag/            # chunking, embeddings, vector search
│   │   └── kb_api.py       # knowledge base endpoints
│   ├── seed/               # starter knowledge base articles
│   ├── requirements.txt
│   └── Dockerfile
└── web/                    # React (Vite)
    └── Dockerfile
```

---

## 10. Tech Stack

| Area | Tool |
|---|---|
| Java service | Spring Boot, Spring Web, Spring Data JPA, Spring Kafka |
| Python service | `confluent-kafka` or `aiokafka`, an LLM SDK (Anthropic / OpenAI), FastAPI |
| RAG | `sentence-transformers` (embeddings), `pgvector` (vector search), `psycopg` |
| Messaging | Apache Kafka (KRaft) |
| Database | PostgreSQL with pgvector (`pgvector/pgvector:pg16`) |
| Frontend | React (Vite), TypeScript, Tailwind, React Router |
| Containers | Docker, Docker Compose |
| Debugging | Kafka UI (`http://localhost:8080`) |

---

## 11. Build Order

1. **Infra:** `docker-compose.yml` with Kafka, Kafka UI, Postgres
2. **ticket-service:** create and list tickets, save to Postgres
3. **Kafka producer:** publish `ticket.created` after saving
4. **ai-service:** consume the event and print it
5. **LLM call:** classify, set priority, draft a reply
6. **Publish result:** send `ticket.enriched`
7. **Kafka consumer in ticket-service:** update the ticket
8. **Knowledge base:** enable pgvector, create `kb_articles` and `kb_chunks`, add the `/kb/articles` endpoints
9. **Ingestion:** chunk and embed articles, load the seed articles
10. **RAG in the AI flow:** retrieve the top 3 chunks and add them to the prompt, then return `sources`
11. **UI:** form, inbox, ticket detail (with "Based on" sources), knowledge base screen
12. **Dockerize everything** and run with one command

---

## 12. How to Run (target)

```bash
# 1. Add your LLM key
echo "LLM_API_KEY=your_key_here" > .env

# 2. Start everything
docker compose up --build

# 3. Open
# UI:       http://localhost:3000
# API:      http://localhost:8081/api/tickets
# Kafka UI: http://localhost:8080
```

---

## 13. Future Improvements (optional)

- Retry topic and dead-letter topic for failed LLM calls
- Idempotency check using `eventId`
- Real-time UI updates with SSE or WebSocket
- Outbox pattern for reliable publishing
- Better RAG: re-ranking, hybrid keyword + vector search, and PDF or URL article import
- Feedback loop: track whether agents accept or edit the suggested reply
- API gateway, authentication, Redis caching
- Prometheus and Grafana monitoring
- Scale `ai-service` with multiple replicas (Kafka consumer groups)

---

## 14. What You'll Learn

- Event-driven architecture with Kafka
- Java and Python communicating without direct coupling
- Prompting an LLM for structured output
- Building RAG: chunking, embeddings, and vector search with pgvector
- Dockerizing a multi-service app
- Building a polished, typography-focused UI