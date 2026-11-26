"""Kafka producer for ai-service (ticket.enriched)."""

from __future__ import annotations

import json
import logging
from typing import Any

from aiokafka import AIOKafkaProducer

from .config import settings

log = logging.getLogger("ai-service.events")

TICKET_ENRICHED = "ticket.enriched"


class TicketEventProducer:
    """The AIOKafkaProducer is created lazily: it needs a running event loop."""

    def __init__(self, bootstrap_servers: str | None = None) -> None:
        self._bootstrap_servers = bootstrap_servers or settings.kafka_bootstrap_servers
        self._producer: AIOKafkaProducer | None = None

    async def start(self) -> None:
        if self._producer is not None:
            return
        producer = AIOKafkaProducer(
            bootstrap_servers=self._bootstrap_servers,
            key_serializer=lambda k: k.encode("utf-8") if k is not None else None,
            value_serializer=lambda v: json.dumps(v).encode("utf-8"),
        )
        await producer.start()
        self._producer = producer
        log.info("ai-service producer started")

    async def stop(self) -> None:
        if self._producer is None:
            return
        await self._producer.stop()
        self._producer = None
        log.info("ai-service producer stopped")

    async def publish(self, topic: str, ticket_id: str, event: dict[str, Any]) -> None:
        """The ticketId is the message key so events for one ticket stay in order."""
        await self.start()
        assert self._producer is not None
        await self._producer.send_and_wait(topic, key=ticket_id, value=event)
        log.info("Published %s for ticket %s (eventId=%s)", topic, ticket_id, event.get("eventId"))


producer = TicketEventProducer()
