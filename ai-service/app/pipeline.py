"""The AI flow: ticket.created -> analyse -> ticket.enriched."""

from __future__ import annotations

import logging
import uuid
from typing import Any

from . import llm
from .events import TICKET_ENRICHED, producer

log = logging.getLogger("ai-service.pipeline")


def build_enriched_event(
    ticket_id: str,
    result: dict[str, str],
    sources: list[dict[str, Any]] | None = None,
) -> dict[str, Any]:
    """Shape must match contracts/ticket.enriched.json exactly."""
    return {
        "eventId": str(uuid.uuid4()),
        "ticketId": ticket_id,
        "category": result["category"],
        "priority": result["priority"],
        "sentiment": result["sentiment"],
        "summary": result["summary"],
        "suggestedReply": result["suggestedReply"],
        "sources": sources or [],
    }


async def enrich_ticket(event: dict[str, Any]) -> dict[str, Any]:
    """Handler for ticket.created. Never raises: a bad event is logged and skipped."""
    ticket_id = str(event.get("ticketId") or "").strip()
    if not ticket_id:
        log.error("ticket.created without ticketId, ignoring: %s", event.get("eventId"))
        return {}

    subject = str(event.get("subject") or "")
    message = str(event.get("message") or "")

    # Step 8/10 add the knowledge base retrieval here (RAG).
    chunks = await _retrieve_chunks(subject, message)
    sources = [
        {"articleId": chunk.get("article_id"), "title": chunk.get("title")}
        for chunk in chunks
        if chunk.get("article_id") is not None
    ]

    result = llm.analyse(subject, message, chunks)
    enriched = build_enriched_event(ticket_id, result, sources)

    await producer.publish(TICKET_ENRICHED, ticket_id, enriched)
    log.info(
        "Enriched ticket %s -> %s/%s/%s (%d sources)",
        ticket_id,
        result["category"],
        result["priority"],
        result["sentiment"],
        len(sources),
    )
    return enriched


async def _retrieve_chunks(subject: str, message: str) -> list[dict[str, Any]]:
    """RAG retrieval. Returns [] until the knowledge base exists (step 8)."""
    try:
        from .rag import search_chunks  # imported lazily so early steps stay light
    except ImportError:
        return []
    try:
        return await search_chunks(f"{subject}\n{message}".strip())
    except Exception as exc:  # noqa: BLE001 - retrieval failure must not kill the flow
        log.error("Knowledge base search failed: %s", exc)
        return []
