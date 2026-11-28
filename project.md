# Replai: AI Customer Support Platform

A hybrid microservice project using **Java**, **Python**, **Kafka**, **GenAI**, **React**, and **Docker**.

A customer submits a ticket. The system automatically **prioritizes** it, **routes** it to the right team (Software, Infrastructure, Billing, and so on), starts an **SLA counter**, drafts a **knowledge-base-grounded reply**, and tracks **which team and employee resolve the most issues**.

> Positioning: open-source support AI that shows its sources, admits when it doesn't know, and learns from your agents.

---

## 1. What Makes Replai Different

| Feature | What it does |
|---|---|
| **Trustworthy AI** | Every reply shows the knowledge base articles it used ("Based on") and a confidence score |
| **Honest handoff** | Low confidence means no guessing. The ticket goes to a human triage queue |
| **Knowledge gap detection** | Tickets with no matching article are flagged, so you know which help article to write next |
| **Learns from agents** | Tracks whether agents accept, edit, or reject each AI draft, and shows the acceptance rate |
| **Auto triage and routing** | Priority and team are decided automatically from the ticket text |
| **SLA counters** | Every ticket has a deadline based on priority, with live "days open" and breach tracking |
| **Performance analytics** | Resolution counts and times per team and per employee |

---

## 2. Architecture

```
┌──────────┐     ┌────────────────┐     ┌───────┐     ┌──────────────┐
│  Web UI  │────►│ ticket-service │────►│ Kafka │────►│  ai-service  │
│ (React)  │◄────│    (Java)      │◄────│       │◄────│   (Python)   │
└──────────┘     └───────┬────────┘     └───────┘     └──────┬───────┘
                         │                                   │
                    ┌────▼─────────────────────────────┐ ┌───▼─────┐
                    │ Postgres + pgvector              │ │ LLM API │
                    │ tickets, teams, employees, KB    │ └─────────┘
                    └──────────────────────────────────┘
```

### End-to-end flow

1. Customer creates a ticket in the UI
2. `ticket-service` saves it (status `OPEN`) and publishes `ticket.created`
3. `ai-service` consumes it and searches the knowledge base (RAG)
4. `ai-service` calls the LLM to get category, priority, sentiment, summary, and a draft reply
5. `ai-service` computes a **confidence score** and a **knowledge gap** flag
6. `ai-service` publishes `ticket.enriched`
7. `ticket-service` consumes it and **routes** the ticket:
   - Confidence is high: map category to a team, pick an employee, set status `ASSIGNED`
   - Confidence is low or `needsHuman`: put it in the **Triage queue** (status `NEEDS_TRIAGE`)
8. `ticket-service` sets the **SLA deadline** from the priority
9. The employee works the ticket and resolves it, choosing **Accept**, **Edit**, or **Reject** for the AI draft
10. Analytics update from the resolved ticket

> If the AI service is down, a scheduled job moves tickets stuck in `OPEN` for more than 2 minutes to the Triage queue with priority `MEDIUM`, so nothing is lost.

---

## 3. Services

| Service | Language | Responsibility |
|---|---|---|
| `ticket-service` | Java (Spring Boot) | REST API, tickets, teams, employees, routing, assignment, SLA, analytics, Kafka producer and consumer |
| `ai-service` | Python (FastAPI + Kafka consumer) | Consumes tickets, RAG retrieval, LLM call, confidence score, publishes results, manages KB articles |
| `web` | React (Vite) | Customer form, team queues, triage board, ticket detail, analytics, knowledge base |

**Infrastructure (Docker):** Kafka (KRaft mode), Kafka UI, Postgres with pgvector (`pgvector/pgvector:pg16`)

---

## 4. Kafka Topics

| Topic | Producer | Consumer | Purpose |
|---|---|---|---|
| `ticket.created` | ticket-service | ai-service | New ticket needs analysis |
| `ticket.enriched` | ai-service | ticket-service | AI results ready for routing |

Use `ticketId` as the message key so events for one ticket stay in order.

### Event: `ticket.created`

```json
{
  "eventId": "uuid",
  "ticketId": "123",
  "customerEmail": "user@example.com",
  "subject": "Server is down",
  "message": "Our production API returns 503 errors since morning.",
  "createdAt": "2026-11-26T10:00:00Z"
}
```

### Event: `ticket.enriched`

```json
{
  "eventId": "uuid",
  "ticketId": "123",
  "category": "Infrastructure",
  "priority": "CRITICAL",
  "sentiment": "NEGATIVE",
  "summary": "Production API is returning 503 errors.",
  "suggestedReply": "Hi, we're sorry about the outage. Our infrastructure team is investigating...",
  "confidence": 0.82,
  "needsHuman": false,
  "knowledgeGap": false,
  "sources": [
    { "articleId": 4, "title": "API outage troubleshooting" }
  ]
}
```

---

## 5. Triage and Routing Rules

### Priority levels

| Priority | Examples |
|---|---|
| `CRITICAL` | Production down, data loss, security issue, many users affected |
| `HIGH` | Major feature broken, payment failed, blocked customer |
| `MEDIUM` | Bug with a workaround, account problem |
| `LOW` | Questions, feature requests, minor issues |

### Category to team

| AI category | Team |
|---|---|
| Software | Software Team |
| Infrastructure | Infrastructure Team |
| Billing | Billing Team |
| Account | Support Team |
| General | Support Team |

Stored in a `category_routing` table so it can be changed without code.

### Assignment inside a team

1. Pick the employee in that team with the **fewest open tickets**
2. Queues are ordered by priority first (`CRITICAL` to `LOW`), then oldest first

### Triage queue

Tickets go to `NEEDS_TRIAGE` when:
- `needsHuman` is true (confidence below the threshold)
- The AI service did not answer in time
- The category is unknown

A team lead assigns these manually from the Triage board.

---

## 6. SLA and Counters

| Priority | Resolve within |
|---|---|
| `CRITICAL` | 4 hours |
| `HIGH` | 1 day |
| `MEDIUM` | 3 days |
| `LOW` | 7 days |

Stored in an `sla_policies` table.

**Counters shown in the UI:**
- **Open tickets:** "Day 2 of 3" and time remaining until `sla_due_at`
- **Resolved tickets:** time taken to resolve (for example "1 day 4 hours")
- **Breached:** red badge when `now > sla_due_at` and the ticket is not resolved

A scheduled job runs every minute and sets `sla_breached = true` on overdue tickets.

---

## 7. Database (Postgres)

### `tickets` (ticket-service)

| Column | Type |
|---|---|
| id | BIGSERIAL (PK) |
| customer_email | VARCHAR |
| subject | VARCHAR |
| message | TEXT |
| status | VARCHAR (`OPEN`, `NEEDS_TRIAGE`, `ASSIGNED`, `IN_PROGRESS`, `RESOLVED`) |
| category | VARCHAR |
| priority | VARCHAR |
| sentiment | VARCHAR |
| summary | TEXT |
| team_id | BIGINT (FK) |
| assignee_id | BIGINT (FK, employee) |
| suggested_reply | TEXT |
| final_reply | TEXT |
| draft_action | VARCHAR (`ACCEPTED`, `EDITED`, `REJECTED`) |
| confidence | DOUBLE |
| needs_human | BOOLEAN |
| knowledge_gap | BOOLEAN |
| sources | JSONB |
| sla_due_at | TIMESTAMP |
| sla_breached | BOOLEAN |
| created_at | TIMESTAMP |
| assigned_at | TIMESTAMP |
| resolved_at | TIMESTAMP |
| resolved_by | BIGINT (FK, employee) |

### Supporting tables (ticket-service)

| Table | Columns |
|---|---|
| `teams` | id, name (Software Team, Infrastructure Team, Billing Team, Support Team) |
| `employees` | id, name, email, team_id, role (`AGENT`, `LEAD`, `ADMIN`), password_hash (BCrypt) |
| `category_routing` | category, team_id |
| `sla_policies` | priority, resolve_within_hours |

Seed 4 teams and 9 employees on startup (one lead and one agent per team, plus an admin), give every employee without a password the `SEED_PASSWORD` hash, and create the admin account on a database that predates login.

### Knowledge base (ai-service)

**`kb_articles`:** id, title, content, created_at

**`kb_chunks`:** id, article_id (FK), chunk_text, embedding `VECTOR(384)`

Enable the extension once: `CREATE EXTENSION IF NOT EXISTS vector;`

```sql
SELECT article_id, chunk_text, 1 - (embedding <=> :query_embedding) AS similarity
FROM kb_chunks
ORDER BY embedding <=> :query_embedding
LIMIT 3;
```

---

## 8. REST API

### Authentication (login and roles)

Everyone except the customer signs in with an email and a password.

| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/auth/login` | Body `{email, password}` → `{token, employee}` |
| GET | `/api/auth/me` | The signed-in employee |

- The token is an **HS256 JWT** signed with `JWT_SECRET` and valid for `JWT_EXPIRY_MINUTES` (480 minutes by default). Claims: `sub` (employee id), `name`, `email`, `role`.
- Passwords are stored as **BCrypt** hashes in `employees.password_hash`. Every seeded account uses `SEED_PASSWORD` (default `replai123`).
- The web client sends `Authorization: Bearer <token>` with every call except login. A `401` clears the session and returns to `/login`.
- `ai-service` verifies the same token with the same `JWT_SECRET`, because the browser calls `/kb` directly through the nginx / vite proxy.

**Roles**

| Action | AGENT | LEAD | ADMIN |
|---|---|---|---|
| Create a ticket (customer form) and read tickets, teams, employees, KB | yes | yes | yes |
| Change status or resolve a ticket | only tickets assigned to them | any ticket | any ticket |
| Assign from the triage board (`PATCH .../assign`) | no | yes | yes |
| Analytics and knowledge gaps (`/api/analytics/**`) | no | yes | yes |
| Add or delete knowledge base articles | no | yes | yes |

Enforcement lives in two places: `SecurityConfig` in ticket-service (endpoint level, with JSON `401`/`403` responses) and `TicketService.assertCanWorkOn` (per-ticket ownership). The React app mirrors it: the Analytics nav item is hidden from agents, and the triage assign control plus the knowledge base edit buttons only render for leads and admins.

### ticket-service

All endpoints need a token except `POST /api/tickets`.

| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/tickets` | Create a ticket (customer, public) |
| GET | `/api/tickets` | List tickets. Filters: `status`, `team`, `priority`, `assignee` |
| GET | `/api/tickets/{id}` | Ticket detail with AI results and SLA info |
| PATCH | `/api/tickets/{id}/assign` | Assign to a team and employee (used for triage, LEAD/ADMIN) |
| PATCH | `/api/tickets/{id}/status` | Move to `IN_PROGRESS` and so on |
| POST | `/api/tickets/{id}/resolve` | Resolve with `finalReply` and `draftAction`. `resolvedBy` comes from the token, not the body |
| GET | `/api/teams` | List teams |
| GET | `/api/employees` | List employees (filter by team) |

**Analytics**

| Method | Endpoint | Description |
|---|---|---|
| GET | `/api/analytics/summary` | Open, resolved, average resolution time, SLA met % |
| GET | `/api/analytics/teams` | Per team: solved, open, average resolution time, SLA met % |
| GET | `/api/analytics/employees?range=7d` | Leaderboard: solved count and average resolution time per employee |
| GET | `/api/analytics/priority` | Open tickets by priority |
| GET | `/api/analytics/ai` | Accepted, edited, rejected counts and acceptance rate |
| GET | `/api/analytics/knowledge-gaps` | Tickets the knowledge base could not answer |

### ai-service

| Method | Endpoint | Description |
|---|---|---|
| POST | `/kb/articles` | Add an article (chunks and embeds it) - LEAD/ADMIN |
| GET | `/kb/articles` | List articles |
| GET | `/kb/articles/{id}` | One article |
| DELETE | `/kb/articles/{id}` | Delete an article and its chunks - LEAD/ADMIN |
| GET | `/kb/search?q=...` | Test search, returns the closest chunks |

> The customer form (`POST /api/tickets`) is the only endpoint anyone can call without signing in.

---

## 9. AI Service Logic

### Prompt (one call returns JSON)

```
You are a customer support triage assistant.
Use ONLY the knowledge base articles below to write the reply.
If the articles don't cover the question, say a human agent will follow up.

Knowledge base articles:
{retrieved_chunks}

Ticket:
{subject}
{message}

Return ONLY JSON with:
category (Software | Infrastructure | Billing | Account | General),
priority (LOW | MEDIUM | HIGH | CRITICAL),
sentiment (POSITIVE | NEUTRAL | NEGATIVE),
summary (one sentence),
suggestedReply (polite, short).
```

### Steps

1. Consume `ticket.created`
2. Embed the ticket text (subject + message)
3. Retrieve the top 3 closest chunks from `kb_chunks`
4. Call the LLM with the ticket and the chunks
5. Parse the JSON safely (strip code fences, handle invalid output)
6. Compute **confidence** and flags (below)
7. Publish `ticket.enriched` with `sources`

### Confidence and knowledge gaps

- `confidence` = the similarity score of the best retrieved chunk (0 to 1)
- If `confidence < 0.45` (configurable): `needsHuman = true` and `knowledgeGap = true`, and the suggested reply is replaced with a "a human agent will follow up" message
- Safe fallback if the LLM returns bad JSON: category `General`, priority `MEDIUM`, `needsHuman = true`

### RAG ingestion

1. `POST /kb/articles` with a title and content
2. Split into chunks (about 300 to 500 characters, small overlap)
3. Embed each chunk with `sentence-transformers` (`all-MiniLM-L6-v2`, 384 dimensions)
4. Store in `kb_chunks`

Use the same embedding model for ingestion and search. Seed 8 to 10 short articles (refund policy, password reset, API errors, server outage, and so on).

---

## 10. Analytics: What the Dashboard Shows

- **Overview:** open tickets, resolved tickets, average resolution time, SLA met %
- **Team performance:** tickets solved per team, with average resolution time
- **Employee leaderboard:** who solved the most tickets (7 days, 30 days, all time)
- **Open by priority:** how many `CRITICAL`, `HIGH`, `MEDIUM`, `LOW` tickets are waiting
- **SLA:** breached tickets and the oldest open tickets
- **AI quality:** acceptance rate of drafts (accepted, edited, rejected)
- **Knowledge gaps:** topics customers ask about that have no article yet

All numbers come from the `tickets` table, so no extra services are needed.

---

## 11. UI

**Stack:** React (Vite), TypeScript, Tailwind CSS, React Router, TanStack Query

**Typography-led design (not a generic admin template):**
- Headings: serif (Fraunces or Instrument Serif)
- Body: Geist or Inter Tight
- IDs, timestamps, and counters: JetBrains Mono
- Warm off-white background, near-black text, one accent color used only for AI-generated content
- Hairline borders, generous spacing, light and dark mode

**Screens:**
0. **Sign in:** email and password, with the seeded accounts listed underneath
1. **New ticket form:** clean customer-facing form
2. **Team queue:** tickets for one team, sorted by priority, with SLA counters
3. **Triage board:** `NEEDS_TRIAGE` tickets with an assign-to-team-and-employee control
4. **Ticket detail:** original message, AI panel (summary, sentiment, confidence badge, "Based on" sources, suggested reply), SLA countdown, and **Accept / Edit / Reject** with a Resolve button
5. **Analytics:** overview, team and employee leaderboards, priority breakdown, AI acceptance rate
6. **Knowledge gaps:** list of unanswered topics with a button to create an article from a ticket
7. **Knowledge base:** list, add, and delete articles, plus a test search box

---

## 12. Folder Structure

```
replai/
├── docker-compose.yml
├── project.md
├── agent.md
├── contracts/              # shared event JSON examples
├── ticket-service/         # Java Spring Boot
│   ├── src/main/java/.../
│   │   ├── controller/
│   │   ├── service/        # routing, assignment, SLA, analytics, auth
│   │   ├── repository/
│   │   ├── security/       # JWT filter, token service, RBAC rules
│   │   ├── kafka/          # producer and consumer
│   │   └── scheduler/      # SLA breach job, stuck-ticket job
│   ├── src/main/resources/ # application.yml, seed data
│   ├── pom.xml
│   └── Dockerfile
├── ai-service/             # Python
│   ├── app/
│   │   ├── consumer.py     # Kafka consumer and producer
│   │   ├── llm.py          # LLM call and JSON parsing
│   │   ├── auth.py         # verifies the login token (KB writes)
│   │   ├── rag/            # chunking, embeddings, vector search, confidence
│   │   └── kb_api.py       # knowledge base endpoints
│   ├── seed/               # starter knowledge base articles
│   ├── requirements.txt
│   └── Dockerfile
└── web/                    # React (Vite)
    ├── src/
    │   ├── api/
    │   ├── lib/            # auth session, formatting, SLA helpers
    │   ├── pages/
    │   └── components/
    └── Dockerfile
```

---

## 13. Tech Stack

| Area | Tool |
|---|---|
| Java service | Spring Boot, Spring Web, Spring Data JPA, Spring Kafka, Spring Scheduling |
| Python service | `confluent-kafka` or `aiokafka`, an LLM SDK (Anthropic / OpenAI), FastAPI |
| RAG | `sentence-transformers`, `pgvector`, `psycopg` |
| Messaging | Apache Kafka (KRaft) |
| Database | PostgreSQL with pgvector |
| Frontend | React (Vite), TypeScript, Tailwind, React Router, TanStack Query, a chart library (Recharts) |
| Containers | Docker, Docker Compose |
| Debugging | Kafka UI (`http://localhost:8080`) |

---

## 14. Build Order (phases)

**Phase 1: Core loop**
1. `docker-compose.yml` with Kafka, Kafka UI, Postgres (pgvector)
2. ticket-service: create and list tickets
3. Publish `ticket.created`
4. ai-service: consume, call the LLM, publish `ticket.enriched`
5. ticket-service: consume `ticket.enriched` and update the ticket

**Phase 2: Routing and SLA**
6. Add `teams`, `employees`, `category_routing`, `sla_policies` with seed data
7. Route by category, assign the least-loaded employee
8. Set `sla_due_at`, add the breach scheduler and the stuck-ticket job
9. Triage queue and manual assign endpoint

**Phase 3: Trustworthy AI**
10. Knowledge base tables, `/kb` endpoints, ingestion, seed articles
11. RAG retrieval, `sources`, confidence score, `needsHuman`, `knowledgeGap`

**Phase 4: Learning from agents and analytics**
12. Resolve endpoint with `draftAction` and `finalReply`
13. Analytics endpoints (summary, teams, employees, priority, AI, knowledge gaps)

**Phase 5: UI and polish**
14. Customer form, team queue, triage board, ticket detail
15. Analytics dashboard and knowledge gaps screens
16. Dockerize everything and run with one command

---

## 15. How to Run (target)

```bash
# 1. Add your LLM key
echo "LLM_API_KEY=your_key_here" > .env

# 2. Start everything
docker compose up --build

# 3. Open
# UI:       http://localhost:3000
# API:      http://localhost:8081/api/tickets
# AI API:   http://localhost:8000
# Kafka UI: http://localhost:8080
```

---

## 16. Future Improvements (optional)

- Retry topic and dead-letter topic for failed LLM calls
- Idempotency check using `eventId`
- Real-time UI updates with SSE or WebSocket
- Outbox pattern for reliable publishing
- Local LLM support (Ollama) for fully private deployments
- Feed edited replies back into the knowledge base
- Better RAG: re-ranking, hybrid keyword and vector search, PDF or URL import
- Email notifications for SLA warnings
- Redis caching, API gateway, Prometheus and Grafana
- Scale `ai-service` with multiple replicas (Kafka consumer groups)

---

## 17. Later: Making It Sellable (after the MVP works)

Not part of the MVP. Build these only after phases 1 to 5 are done.

**Target customer:** internal IT helpdesks at companies with 50 to 500 employees, and small IT service providers handling tickets for several clients.

**Validate first:** talk to about 10 people who run support or IT at small companies before building more.

| Item | Why it's needed |
|---|---|
| Password reset and invites | Login and roles (admin, lead, agent) exist; recovery does not |
| Multi-tenancy | Many companies on one system, each with separate data (`tenant_id` on every table) |
| Email-to-ticket and embeddable chat widget | More ways for customers to raise tickets |
| Billing (Stripe or Razorpay) | Charge per agent per month |
| Security basics | HTTPS, audit logs (passwords are already hashed with BCrypt) |
| Reliability | Backups, retries, dead-letter topic, monitoring |
| Easy onboarding | A new company set up in minutes |

**Designed now to make this easier later:**
- Tickets, teams, and employees stay in separate tables
- Routing and SLA rules live in database tables, not in code
- All configuration comes from environment variables

**Positioning:** an open-source, self-hostable support platform for small teams, with transparent AI.

**Rollout path:** finish the MVP, add multi-tenancy, add email-in and the widget, run 2 or 3 free pilots, then start charging.

---

## 18. What You'll Learn

- Event-driven architecture with Kafka
- Java and Python communicating without direct coupling
- Prompting an LLM for structured output
- Building RAG with pgvector
- Rule-based routing, assignment, and SLA tracking
- Writing analytics queries (group by team and employee)
- Dockerizing a multi-service app
- Building a polished, typography-focused UI