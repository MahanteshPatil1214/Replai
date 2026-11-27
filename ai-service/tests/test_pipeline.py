import json
from pathlib import Path

import pytest

from app import pipeline
from app.events import TICKET_ENRICHED

CONTRACTS = Path(__file__).resolve().parents[2] / "contracts"


@pytest.fixture(autouse=True)
def no_knowledge_base(monkeypatch):
    """Keep pipeline tests independent of a running Postgres."""

    async def empty(subject, message):
        return []

    monkeypatch.setattr(pipeline, "_retrieve_chunks", empty)


@pytest.fixture
def sent(monkeypatch):
    calls = []

    async def fake_publish(topic, ticket_id, event):
        calls.append((topic, ticket_id, event))

    monkeypatch.setattr(pipeline.producer, "publish", fake_publish)
    return calls


def _event(**overrides):
    event = {
        "eventId": "e-1",
        "ticketId": "123",
        "customerEmail": "user@example.com",
        "subject": "Payment failed",
        "message": "I was charged twice for my order.",
        "createdAt": "2026-11-26T10:00:00Z",
    }
    event.update(overrides)
    return event


@pytest.mark.asyncio
async def test_enrich_publishes_ticket_enriched_with_ticket_id_key(sent):
    result = await pipeline.enrich_ticket(_event())

    assert len(sent) == 1
    topic, key, payload = sent[0]
    assert topic == TICKET_ENRICHED
    assert key == "123"
    assert result["eventId"]
    assert result["ticketId"] == "123"
    assert set(result) == {
        "eventId",
        "ticketId",
        "category",
        "priority",
        "sentiment",
        "summary",
        "suggestedReply",
        "confidence",
        "needsHuman",
        "knowledgeGap",
        "sources",
    }


@pytest.mark.asyncio
async def test_enrich_skips_event_without_ticket_id(sent):
    result = await pipeline.enrich_ticket(_event(ticketId=""))

    assert result == {}
    assert sent == []


@pytest.mark.asyncio
async def test_enrich_propagates_failures_to_the_consumer_wrapper(sent, monkeypatch):
    def boom(*args, **kwargs):
        raise RuntimeError("llm exploded")

    monkeypatch.setattr(pipeline.llm, "analyse", boom)

    with pytest.raises(RuntimeError):
        await pipeline.enrich_ticket(_event())


def test_enriched_event_matches_contract():
    event = pipeline.build_enriched_event(
        "123",
        {
            "category": "Infrastructure",
            "priority": "CRITICAL",
            "sentiment": "NEGATIVE",
            "summary": "Production API is returning 503 errors.",
            "suggestedReply": "Hi, we're sorry about the outage.",
        },
        [{"articleId": 4, "title": "API outage troubleshooting"}],
        {"confidence": 0.82, "needsHuman": False, "knowledgeGap": False},
    )

    contract = json.loads((CONTRACTS / "ticket.enriched.json").read_text(encoding="utf-8"))

    assert set(event) == set(contract)
    assert event["sources"] == contract["sources"]
    assert event["category"] in ("Software", "Infrastructure", "Billing", "Account", "General")
    assert event["priority"] in ("LOW", "MEDIUM", "HIGH", "CRITICAL")
    assert event["confidence"] == 0.82
    assert event["needsHuman"] is False
    assert event["knowledgeGap"] is False


def test_confidence_is_the_best_chunk_similarity():
    chunks = [{"score": 0.31}, {"score": 0.82}, {"score": 0.5}]
    assert pipeline.confidence_from_chunks(chunks) == 0.82
    assert pipeline.confidence_from_chunks([]) == 0.0


def test_low_confidence_hands_the_ticket_to_a_human():
    flags = pipeline.evaluate([{"score": 0.2}], used_fallback=False)

    assert flags["confidence"] == 0.2
    assert flags["knowledgeGap"] is True
    assert flags["needsHuman"] is True


def test_low_confidence_replaces_the_reply_with_an_honest_handoff():
    event = pipeline.build_enriched_event(
        "123",
        {
            "category": "General",
            "priority": "MEDIUM",
            "sentiment": "NEUTRAL",
            "summary": "Nothing matched.",
            "suggestedReply": "I made this up.",
        },
        [],
        {"confidence": 0.1, "needsHuman": True, "knowledgeGap": True},
    )

    assert event["suggestedReply"] == pipeline.HUMAN_FOLLOWUP_REPLY
    assert "human agent will follow up" in event["suggestedReply"]


def test_llm_fallback_always_needs_a_human_even_with_good_chunks():
    flags = pipeline.evaluate([{"score": 0.9}], used_fallback=True)

    assert flags["confidence"] == 0.9
    assert flags["knowledgeGap"] is False
    assert flags["needsHuman"] is True
