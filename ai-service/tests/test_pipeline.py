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
            "category": "Billing",
            "priority": "HIGH",
            "sentiment": "NEGATIVE",
            "summary": "Customer was charged twice for one order.",
            "suggestedReply": "Hi, we're sorry about the double charge...",
        },
        [{"articleId": 7, "title": "Refunds for duplicate charges"}],
    )

    contract = json.loads((CONTRACTS / "ticket.enriched.json").read_text(encoding="utf-8"))

    assert set(event) == set(contract)
    assert event["sources"] == contract["sources"]
    assert event["category"] in ("Billing", "Technical", "Account", "General")
    assert event["priority"] in ("LOW", "MEDIUM", "HIGH")
