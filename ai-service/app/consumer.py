"""Kafka consumer for ticket.created.

For now it only parses and logs the event; later steps add the RAG/LLM work
and publish ticket.enriched.
"""

from __future__ import annotations

import asyncio
import json
import logging
from typing import Any, Awaitable, Callable

from aiokafka import AIOKafkaConsumer

from .config import settings

log = logging.getLogger("ai-service.consumer")

# topic -> handler(event dict) -> coroutine
Handler = Callable[[dict[str, Any]], Awaitable[None]]

TICKET_CREATED = "ticket.created"


def parse_event(raw: bytes | str) -> dict[str, Any]:
    """Parse a Kafka value into a dict. Raises ValueError on bad JSON."""
    if isinstance(raw, bytes):
        raw = raw.decode("utf-8")
    event = json.loads(raw)
    if not isinstance(event, dict):
        raise ValueError("event is not a JSON object")
    return event


class TicketConsumer:
    """Long-lived consumer loop. One bad message never kills the loop."""

    def __init__(
        self,
        handler: Handler,
        bootstrap_servers: str | None = None,
        group_id: str | None = None,
        topics: tuple[str, ...] = (TICKET_CREATED,),
    ) -> None:
        self._handler = handler
        self._topics = topics
        self._consumer = AIOKafkaConsumer(
            *topics,
            bootstrap_servers=bootstrap_servers or settings.kafka_bootstrap_servers,
            group_id=group_id or settings.kafka_group_id,
            enable_auto_commit=True,
            auto_offset_reset="earliest",
            value_deserializer=lambda v: v,  # we parse manually so errors stay local
        )

    async def run(self) -> None:
        await self._consumer.start()
        log.info("ai-service consumer started for topics %s", ", ".join(self._topics))
        try:
            async for record in self._consumer:
                await self._handle_record(record.value)
        finally:
            await self._consumer.stop()
            log.info("ai-service consumer stopped")

    async def _handle_record(self, value: bytes) -> None:
        try:
            event = parse_event(value)
        except (ValueError, UnicodeDecodeError) as exc:
            log.error("Skipping unparseable message: %s", exc)
            return

        try:
            await self._handler(event)
        except Exception:  # noqa: BLE001 - a consumer must survive bad messages
            log.exception("Failed to handle event %s", event.get("eventId"))

    async def stop(self) -> None:
        await self._consumer.stop()


async def log_ticket_created(event: dict[str, Any]) -> None:
    """Step-4 handler: consume the event and print it."""
    log.info(
        "ticket.created | ticketId=%s subject=%r from=%s",
        event.get("ticketId"),
        event.get("subject"),
        event.get("customerEmail"),
    )
    log.debug("full event: %s", json.dumps(event, indent=2))


async def _noop() -> None:  # pragma: no cover - keeps asyncio import meaningful
    await asyncio.sleep(0)
