"""FastAPI app for ai-service.

It also hosts the Kafka consumer, so one `uvicorn` process runs both
the knowledge-base REST API and the event-driven AI flow.
"""

from __future__ import annotations

import asyncio
import json
import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI

from .config import settings
from .consumer import TicketConsumer, log_ticket_created

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)-7s %(name)s - %(message)s",
)
log = logging.getLogger("ai-service")

_consumer_task: asyncio.Task | None = None
_consumer: TicketConsumer | None = None


async def _start_consumer() -> None:
    global _consumer, _consumer_task
    if not settings.consumer_enabled:
        log.info("Kafka consumer disabled (KAFKA_CONSUMER_ENABLED=false)")
        return
    if _consumer_task and not _consumer_task.done():
        return

    _consumer = TicketConsumer(handler=log_ticket_created)
    _consumer_task = asyncio.create_task(_consumer.run())
    log.info("consumer task scheduled")


async def _stop_consumer() -> None:
    global _consumer_task
    if _consumer is not None:
        await _consumer.stop()
    if _consumer_task is not None:
        await asyncio.wait_for(_consumer_task, timeout=10)
    _consumer_task = None
    log.info("consumer task stopped")


@asynccontextmanager
async def lifespan(app: FastAPI):
    await _start_consumer()
    try:
        yield
    finally:
        await _stop_consumer()


app = FastAPI(
    title="Replai ai-service",
    version="0.1.0",
    lifespan=lifespan,
)


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok", "service": "ai-service"}


@app.get("/demo/ticket-created")
def demo_ticket_created() -> dict[str, str]:
    """Helper: shape of the event this service consumes (contracts/ticket.created.json)."""
    return {
        "eventId": "uuid",
        "ticketId": "123",
        "customerEmail": "user@example.com",
        "subject": "Payment failed",
        "message": "I was charged twice for my order.",
        "createdAt": "2026-11-26T10:00:00Z",
    }


if __name__ == "__main__":  # pragma: no cover
    import uvicorn

    uvicorn.run("app.kb_api:app", host="0.0.0.0", port=8001)
