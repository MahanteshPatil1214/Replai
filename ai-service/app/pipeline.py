"""The AI flow: ticket.created -> RAG retrieval -> analyse -> ticket.enriched."""

from __future__ import annotations

import asyncio
import logging
import uuid
from typing import Any

from . import llm
from .events import TICKET_ENRICHED, producer

log = logging.getLogger("ai-service.pipeline")


def sources_from_chunks(chunks: list[dict[str, Any]]) -> list[dict[str, Any]]:
    """One entry per article, in retrieval order (a chunk may repeat an article)."""
    sources: list[dict[str, Any]] = []
    seen: set[Any] = set()
    for chunk in chunks:
        article_id = chunk.get("article_id")
        if article_id is None or article_id in seen:
            continue
        seen.add(article_id)
        sources.append({"articleId": article_id, "title": chunk.get("title")})
    return sources


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

    # RAG: embed the ticket and pull the closest knowledge base chunks.
    chunks = await _retrieve_chunks(subject, message)
    sources = sources_from_chunks(chunks)

    result = llm.analyse(subject, message, chunks)
    enriched = build_enriched_event(ticket_id, result, sources)

    await producer.publish(TICKET_ENRICHED, ticket_id, enriched)
    log.info(
        "Enriched ticket %s -> %s/%s/%s (%d chunks, %d sources)",
        ticket_id,
        result["category"],
        result["priority"],
        result["sentiment"],
        len(chunks),
        len(sources),
    )
    return enriched


async def _retrieve_chunks(subject: str, message: str) -> list[dict[str, Any]]:
    """Top-3 knowledge base chunks for the ticket. Failures degrade to no RAG."""
    try:
        from .rag import search_chunks
    except ImportError:
        return []
    try:
        return await asyncio.to_thread(search_chunks, f"{subject}\n{message}".strip(), 3)
    except Exception as exc:  # noqa: BLE001 - retrieval failure must not kill the flow
        log.error("Knowledge base search failed: %s", exc)
        return []
