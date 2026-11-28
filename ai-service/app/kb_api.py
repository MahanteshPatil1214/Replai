"""FastAPI app for ai-service: knowledge base REST API + Kafka consumer."""

from __future__ import annotations

import asyncio
import logging
from contextlib import asynccontextmanager

from fastapi import Depends, FastAPI, HTTPException, Query
from pydantic import BaseModel, Field

from . import db
from .auth import require_lead_or_admin
from .config import settings
from .consumer import TicketConsumer
from .events import producer
from .pipeline import enrich_ticket
from .rag import chunking, store
from .seed import seed_if_empty

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)-7s %(name)s - %(message)s",
)
log = logging.getLogger("ai-service")

_consumer_task: asyncio.Task | None = None
_consumer: TicketConsumer | None = None


class CreateArticleRequest(BaseModel):
    title: str = Field(min_length=1, max_length=300)
    content: str = Field(min_length=1)


class SearchResponse(BaseModel):
    query: str
    results: list[dict]


async def _ensure_schema(retries: int = 10) -> None:
    """The Postgres container may still be initialising."""
    for attempt in range(1, retries + 1):
        try:
            await asyncio.to_thread(store.create_tables)
            return
        except Exception as exc:  # noqa: BLE001
            log.warning("schema init failed (%s/%s): %s", attempt, retries, exc)
            await asyncio.sleep(2)
    raise RuntimeError("could not connect to Postgres to create the knowledge base schema")


async def _run_with_retry(consumer: TicketConsumer) -> None:
    """A Kafka blip at startup must not take the service down permanently."""
    delay = 2.0
    while True:
        try:
            await consumer.run()
            return
        except asyncio.CancelledError:
            raise
        except Exception:  # noqa: BLE001
            log.exception("Kafka consumer failed, retrying in %ss", delay)
            await asyncio.sleep(delay)
            delay = min(delay * 2, 30.0)


async def _start_consumer() -> None:
    global _consumer, _consumer_task
    if not settings.consumer_enabled:
        log.info("Kafka consumer disabled (KAFKA_CONSUMER_ENABLED=false)")
        return
    if _consumer_task and not _consumer_task.done():
        return

    try:
        await producer.start()
    except Exception:  # noqa: BLE001 - publish() retries the connection lazily
        log.exception("Kafka producer not reachable yet, will retry on publish")

    _consumer = TicketConsumer(handler=enrich_ticket)
    _consumer_task = asyncio.create_task(_run_with_retry(_consumer))
    log.info("consumer task scheduled")


async def _stop_consumer() -> None:
    global _consumer_task
    if _consumer is not None:
        try:
            await _consumer.stop()
        except Exception:  # noqa: BLE001
            log.exception("error while stopping the consumer")
    if _consumer_task is not None:
        try:
            await asyncio.wait_for(asyncio.shield(_consumer_task), timeout=10)
        except Exception:  # noqa: BLE001 - shutdown should never fail loudly
            _consumer_task.cancel()
    _consumer_task = None
    try:
        await producer.stop()
    except Exception:  # noqa: BLE001
        log.exception("error while stopping the producer")
    log.info("consumer task stopped")


async def _seed_knowledge_base() -> None:
    try:
        seeded = await asyncio.to_thread(seed_if_empty)
        if seeded:
            log.info("Loaded %s seed articles into the knowledge base", seeded)
    except Exception:  # noqa: BLE001 - an empty KB still lets the service run
        log.exception("Could not seed the knowledge base")


@asynccontextmanager
async def lifespan(app: FastAPI):
    await _ensure_schema()
    await _seed_knowledge_base()
    await _start_consumer()
    try:
        yield
    finally:
        await _stop_consumer()
        await asyncio.to_thread(db.close_pool)


app = FastAPI(
    title="Replai ai-service",
    version="0.1.0",
    lifespan=lifespan,
)


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok", "service": "ai-service"}


# --- knowledge base -------------------------------------------------------


# Writes need a LEAD or ADMIN token (project.md section 8); reads are open
# to anyone who is signed in.

# Sync `def` endpoints: FastAPI runs them in a thread pool, so the blocking
# Postgres and embedding calls here never stall the Kafka consumer.


@app.post("/kb/articles", status_code=201, dependencies=[Depends(require_lead_or_admin)])
def add_article(payload: CreateArticleRequest) -> dict:
    """Adds an article and chunks + embeds it automatically."""
    return store.create_article(payload.title, payload.content)


@app.get("/kb/articles")
def articles() -> list[dict]:
    return store.list_articles()


@app.get("/kb/articles/{article_id}")
def article(article_id: int) -> dict:
    found = store.get_article(article_id)
    if found is None:
        raise HTTPException(status_code=404, detail=f"Article {article_id} not found")
    return found


@app.delete("/kb/articles/{article_id}", status_code=204, dependencies=[Depends(require_lead_or_admin)])
def remove_article(article_id: int) -> None:
    if not store.delete_article(article_id):
        raise HTTPException(status_code=404, detail=f"Article {article_id} not found")


@app.get("/kb/search", response_model=SearchResponse)
def search(q: str = Query(min_length=1), limit: int = Query(default=3, ge=1, le=20)) -> SearchResponse:
    """Test search: returns the closest chunks, i.e. what the AI would see."""
    return SearchResponse(query=q, results=store.search_chunks(q, limit))


@app.get("/kb/chunk-stats")
def chunk_stats() -> dict:
    """Small helper for the UI: how many articles/chunks exist."""
    articles = store.list_articles()
    return {
        "articles": len(articles),
        "chunks": sum(int(a.get("chunkCount") or 0) for a in articles),
        "chunkSize": chunking.DEFAULT_CHUNK_SIZE,
        "overlap": chunking.DEFAULT_OVERLAP,
    }


if __name__ == "__main__":  # pragma: no cover
    import uvicorn

    uvicorn.run("app.kb_api:app", host="0.0.0.0", port=8001)
