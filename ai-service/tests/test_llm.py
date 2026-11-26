import json

import pytest

from app.llm import (
    analyse,
    build_prompt,
    extract_json,
    fallback_result,
    normalise,
)


def test_extract_json_plain_object():
    assert extract_json('{"category": "Billing"}') == {"category": "Billing"}


def test_extract_json_strips_code_fence():
    raw = '```json\n{"category": "Technical", "priority": "HIGH"}\n```'
    assert extract_json(raw) == {"category": "Technical", "priority": "HIGH"}


def test_extract_json_ignores_surrounding_prose():
    raw = 'Sure! Here is the JSON you asked for:\n{"priority": "LOW"}\nHope that helps.'
    assert extract_json(raw) == {"priority": "LOW"}


def test_extract_json_raises_on_garbage():
    with pytest.raises(ValueError):
        extract_json("I could not answer that.")


def test_normalise_fills_defaults_and_normalises_case():
    out = normalise(
        {"category": "billing", "priority": "urgent", "summary": "  double charge "},
        fallback_result("s", "m", []),
    )

    assert out["category"] == "Billing"          # case fixed, valid value kept
    assert out["priority"] == "MEDIUM"            # invalid value -> fallback
    assert out["sentiment"] == "NEUTRAL"          # missing -> fallback
    assert out["summary"] == "double charge"
    assert out["suggestedReply"]


def test_build_prompt_includes_chunks_and_ticket():
    prompt = build_prompt(
        "Refund?",
        "I want my money back",
        [{"title": "Refund policy", "chunk_text": "Refunds take 5 days."}],
    )

    assert "Refund policy" in prompt
    assert "Refunds take 5 days." in prompt
    assert "I want my money back" in prompt


def test_build_prompt_without_chunks():
    prompt = build_prompt("s", "m", [])
    assert "no matching articles found" in prompt


def test_analyse_without_api_key_uses_fallback():
    out = analyse("Payment failed", "charged twice", [])

    assert out["category"] == "General"
    assert out["priority"] == "MEDIUM"
    assert out["sentiment"] == "NEUTRAL"
    assert out["summary"]
    assert out["suggestedReply"]


def test_analyse_reply_comes_from_knowledge_base_when_available():
    chunks = [{"articleId": 7, "title": "Refunds for duplicate charges", "chunk_text": "We refund within 5 days."}]

    out = analyse("charged twice", "please refund", chunks)

    assert "Refunds for duplicate charges" in out["suggestedReply"]
    assert "human agent will follow up" in out["suggestedReply"]


def test_contract_shape_is_json_serialisable():
    out = analyse("subject", "message", [])
    assert set(out) == {"category", "priority", "sentiment", "summary", "suggestedReply"}
    json.dumps(out)
