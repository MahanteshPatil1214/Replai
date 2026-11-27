import pytest

from app import pipeline


KB_CHUNKS = [
    {
        "article_id": 7,
        "chunk_text": "We refund duplicate charges within 1 business day.",
        "title": "Refunds for duplicate charges",
        "score": 0.83,
    },
    {
        "article_id": 7,
        "chunk_text": "Send us the order number and the last four digits.",
        "title": "Refunds for duplicate charges",
        "score": 0.71,
    },
    {
        "article_id": 1,
        "chunk_text": "You can request a refund within 30 days.",
        "title": "Refund policy",
        "score": 0.64,
    },
]


def test_sources_dedupe_by_article_but_keep_retrieval_order():
    sources = pipeline.sources_from_chunks(KB_CHUNKS)

    assert sources == [
        {"articleId": 7, "title": "Refunds for duplicate charges"},
        {"articleId": 1, "title": "Refund policy"},
    ]


def test_sources_ignore_chunks_without_an_article_id():
    assert pipeline.sources_from_chunks([{"chunk_text": "x", "title": None}]) == []


class FakeProducer:
    def __init__(self):
        self.calls = []

    async def publish(self, topic, ticket_id, event):
        self.calls.append((topic, ticket_id, event))


@pytest.mark.asyncio
async def test_retrieved_chunks_are_handed_to_the_llm(monkeypatch):
    captured = {}

    def fake_search(query, limit=3):
        captured["query"] = query
        captured["limit"] = limit
        return KB_CHUNKS

    def fake_analyse(subject, message, chunks):
        captured["chunks"] = chunks
        return {
            "category": "Billing",
            "priority": "HIGH",
            "sentiment": "NEGATIVE",
            "summary": "Double charge.",
            "suggestedReply": "We will refund it.",
        }

    from app import rag

    monkeypatch.setattr(rag, "search_chunks", fake_search)
    monkeypatch.setattr(pipeline.llm, "analyse", fake_analyse)

    fake_producer = FakeProducer()
    monkeypatch.setattr(pipeline, "producer", fake_producer)

    result = await pipeline.enrich_ticket(
        {"ticketId": "5", "subject": "Double charge", "message": "Charged twice please refund"}
    )

    assert captured["query"] == "Double charge\nCharged twice please refund"
    assert captured["limit"] == 3
    assert captured["chunks"] == KB_CHUNKS
    assert result["sources"] == [
        {"articleId": 7, "title": "Refunds for duplicate charges"},
        {"articleId": 1, "title": "Refund policy"},
    ]
    assert result["suggestedReply"] == "We will refund it."
    assert result["confidence"] == 0.83
    assert result["needsHuman"] is False
    assert result["knowledgeGap"] is False


@pytest.mark.asyncio
async def test_enrichment_still_works_when_retrieval_fails(monkeypatch):
    def explode(query, limit=3):
        raise RuntimeError("pg is down")

    from app import rag

    monkeypatch.setattr(rag, "search_chunks", explode)

    fake_producer = FakeProducer()
    monkeypatch.setattr(pipeline, "producer", fake_producer)

    result = await pipeline.enrich_ticket(
        {"ticketId": "6", "subject": "Password", "message": "I cannot log in"}
    )

    assert result["sources"] == []
    assert result["category"] in ("Software", "Infrastructure", "Billing", "Account", "General")
    assert result["needsHuman"] is True
    assert result["knowledgeGap"] is True
    assert fake_producer.calls[0][0] == "ticket.enriched"
