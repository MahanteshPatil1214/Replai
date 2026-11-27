"""The AI flow: ticket.created -> RAG retrieval -> analyse -> ticket.enriched."""

from __future__ import annotations

import asyncio
import logging
import uuid
from typing import Any

from . import llm
from .config import settings
from .events import TICKET_ENRICHED, producer

log = logging.getLogger("ai-service.pipeline")

# What the customer sees when the knowledge base cannot back an answer.
HUMAN_FOLLOWUP_REPLY = (
    "Thanks for reaching out. We could not find a confident answer in our "
    "knowledge base, so a human agent will follow up with you shortly."
)


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


def confidence_from_chunks(chunks: list[dict[str, Any]]) -> float:
    """Confidence = similarity of the best retrieved chunk (0 to 1)."""
    scores = [float(chunk.get("score") or 0.0) for chunk in chunks]
    best = max(scores, default=0.0)
    return round(min(max(best, 0.0), 1.0), 4)


def evaluate(chunks: list[dict[str, Any]], used_fallback: bool = False) -> dict[str, Any]:
    """Confidence, needsHuman and knowledgeGap flags (project.md section 9)."""
    confidence = confidence_from_chunks(chunks)
    knowledge_gap = confidence < settings.confidence_threshold
    return {
        "confidence": confidence,
        "needsHuman": bool(used_fallback or knowledge_gap),
        "knowledgeGap": knowledge_gap,
    }


def build_enriched_event(
    ticket_id: str,
    result: dict[str, Any],
    sources: list[dict[str, Any]] | None = None,
    evaluation: dict[str, Any] | None = None,
) -> dict[str, Any]:
    """Shape must match contracts/ticket.enriched.json exactly."""
    flags = evaluation or evaluate([], used_fallback=bool(result.get("_fallback")))
    suggested_reply = result["suggestedReply"]
    if flags["knowledgeGap"]:
        # No guessing: the AI answer is replaced by an honest handoff.
        suggested_reply = HUMAN_FOLLOWUP_REPLY

    return {
        "eventId": str(uuid.uuid4()),
        "ticketId": ticket_id,
        "category": result["category"],
        "priority": result["priority"],
        "sentiment": result["sentiment"],
        "summary": result["summary"],
        "suggestedReply": suggested_reply,
        "confidence": flags["confidence"],
        "needsHuman": flags["needsHuman"],
        "knowledgeGap": flags["knowledgeGap"],
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
    used_fallback = bool(result.pop("_fallback", False))
    flags = evaluate(chunks, used_fallback)
    enriched = build_enriched_event(ticket_id, result, sources, flags)

    await producer.publish(TICKET_ENRICHED, ticket_id, enriched)
    log.info(
        "Enriched ticket %s -> %s/%s/%s (confidence=%.2f, needsHuman=%s, gap=%s)",
        ticket_id,
        result["category"],
        result["priority"],
        result["sentiment"],
        flags["confidence"],
        flags["needsHuman"],
        flags["knowledgeGap"],
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
