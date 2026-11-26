"""FastAPI app for ai-service.

It also hosts the Kafka consumer, so one `uvicorn` process runs both
the knowledge-base REST API and the event-driven AI flow.
"""

from __future__ import annotations

import asyncio
import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI

from .config import settings
from .consumer import TicketConsumer
from .events import producer
from .pipeline import enrich_ticket

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)-7s %(name)s - %(message)s",
)
log = logging.getLogger("ai-service")

_consumer_task: asyncio.Task | None = None
_consumer: TicketConsumer | None = None


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
