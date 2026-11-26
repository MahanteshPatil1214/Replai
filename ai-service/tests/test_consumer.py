import json

import pytest

from app.consumer import TicketConsumer, parse_event


def test_parse_event_accepts_contract_shape():
    raw = json.dumps(
        {
            "eventId": "uuid",
            "ticketId": "123",
            "customerEmail": "user@example.com",
            "subject": "Payment failed",
            "message": "I was charged twice for my order.",
            "createdAt": "2026-11-26T10:00:00Z",
        }
    ).encode()

    event = parse_event(raw)

    assert event["ticketId"] == "123"
    assert event["subject"] == "Payment failed"


def test_parse_event_rejects_bad_json():
    with pytest.raises(ValueError):
        parse_event(b"not json")


def test_parse_event_rejects_non_object():
    with pytest.raises(ValueError):
        parse_event(b"[1, 2, 3]")


@pytest.mark.asyncio
async def test_bad_message_never_kills_the_consumer():
    handled: list[dict] = []

    async def handler(event):
        handled.append(event)

    consumer = TicketConsumer(handler=handler)

    await consumer._handle_record(b"{ this is broken")
    assert handled == []

    good = json.dumps({"eventId": "1", "ticketId": "7"}).encode()
    await consumer._handle_record(good)
    assert handled == [{"eventId": "1", "ticketId": "7"}]


@pytest.mark.asyncio
async def test_handler_error_is_logged_not_raised():
    async def handler(event):
        raise RuntimeError("boom")

    consumer = TicketConsumer(handler=handler)

    await consumer._handle_record(json.dumps({"eventId": "1"}).encode())
