"""LLM call and safe JSON parsing.

One prompt returns structured JSON. Anything unexpected (no key, network error,
broken JSON) falls back to safe defaults so a ticket is never left un-enriched.
"""

from __future__ import annotations

import json
import logging
import re
from typing import Any

from .config import settings

log = logging.getLogger("ai-service.llm")

CATEGORIES = ("Billing", "Technical", "Account", "General")
PRIORITIES = ("LOW", "MEDIUM", "HIGH")
SENTIMENTS = ("POSITIVE", "NEUTRAL", "NEGATIVE")

PROMPT_TEMPLATE = """You are a customer support assistant.
Use ONLY the knowledge base articles below to write the reply.
If the articles don't cover the question, say a human agent will follow up.

Knowledge base articles:
{retrieved_chunks}

Ticket:
{subject}
{message}

Return ONLY JSON with:
category (Billing | Technical | Account | General),
priority (LOW | MEDIUM | HIGH),
sentiment (POSITIVE | NEUTRAL | NEGATIVE),
summary (one sentence),
suggestedReply (polite, short)."""


def build_prompt(subject: str, message: str, chunks: list[dict[str, Any]]) -> str:
    """chunks: [{article_id, title, chunk_text}]"""
    if chunks:
        rendered = "\n\n".join(
            f"[{index + 1}] {chunk.get('title') or 'Untitled'}: {chunk.get('chunk_text', '').strip()}"
            for index, chunk in enumerate(chunks)
        )
    else:
        rendered = "(no matching articles found)"
    return PROMPT_TEMPLATE.format(
        retrieved_chunks=rendered,
        subject=subject.strip(),
        message=message.strip(),
    )


def extract_json(raw: str) -> dict[str, Any]:
    """Pull a JSON object out of an LLM response.

    Handles ```json fences, leading prose, and trailing text.
    Raises ValueError when nothing usable is found.
    """
    text = raw.strip()

    fence = re.search(r"```(?:json)?\s*(.*?)```", text, re.DOTALL | re.IGNORECASE)
    if fence:
        text = fence.group(1).strip()

    try:
        parsed = json.loads(text)
        if isinstance(parsed, dict):
            return parsed
    except json.JSONDecodeError:
        pass

    start = text.find("{")
    end = text.rfind("}")
    if start != -1 and end > start:
        try:
            parsed = json.loads(text[start : end + 1])
            if isinstance(parsed, dict):
                return parsed
        except json.JSONDecodeError as exc:
            raise ValueError(f"not valid JSON: {exc}") from exc

    raise ValueError("no JSON object found in LLM response")


def _one_of(value: Any, allowed: tuple[str, ...], default: str) -> str:
    if isinstance(value, str):
        cleaned = value.strip().upper()
        for option in allowed:
            if cleaned == option.upper():
                return option
    return default


def normalise(result: dict[str, Any], fallback: dict[str, Any]) -> dict[str, str]:
    """Coerce a loose LLM object into the exact contract shape."""
    return {
        "category": _one_of(result.get("category"), CATEGORIES, fallback["category"]),
        "priority": _one_of(result.get("priority"), PRIORITIES, fallback["priority"]),
        "sentiment": _one_of(result.get("sentiment"), SENTIMENTS, fallback["sentiment"]),
        "summary": str(result.get("summary") or fallback["summary"]).strip(),
        "suggestedReply": str(result.get("suggestedReply") or fallback["suggestedReply"]).strip(),
    }


def fallback_result(subject: str, message: str, chunks: list[dict[str, Any]]) -> dict[str, str]:
    """Safe defaults: category General, priority MEDIUM (per agent.md)."""
    if chunks:
        title = chunks[0].get("title") or "our help centre"
        reply = (
            "Thanks for getting in touch. Based on our article "
            f"\u201c{title}\u201d, here is what we know: "
            f"{chunks[0].get('chunk_text', '').strip()} "
            "If that doesn't solve it, a human agent will follow up."
        )
    else:
        reply = (
            "Thanks for reaching out. We couldn't find a relevant help article, "
            "so a human agent will follow up with you shortly."
        )

    return {
        "category": "General",
        "priority": "MEDIUM",
        "sentiment": "NEUTRAL",
        "summary": (message.strip() or subject.strip() or "Support request")[:200],
        "suggestedReply": reply.strip(),
    }


def _call_llm(prompt: str) -> str:
    """OpenAI-compatible chat completion. Raises on any problem."""
    from openai import OpenAI  # imported lazily so tests don't need the SDK

    client = OpenAI(
        api_key=settings.llm_api_key,
        base_url=settings.llm_base_url or None,
        timeout=60.0,
    )

    messages = [{"role": "user", "content": prompt}]
    try:
        response = client.chat.completions.create(
            model=settings.llm_model,
            messages=messages,
            temperature=0,
            response_format={"type": "json_object"},
        )
    except Exception:  # noqa: BLE001 - not every compatible server supports response_format
        log.warning("response_format unsupported, retrying without it", exc_info=True)
        response = client.chat.completions.create(
            model=settings.llm_model,
            messages=messages,
            temperature=0,
        )

    content = response.choices[0].message.content
    if not content:
        raise ValueError("empty LLM response")
    return content


def analyse(subject: str, message: str, chunks: list[dict[str, Any]] | None = None) -> dict[str, str]:
    """Full step: prompt -> LLM -> parsed JSON, with a safe fallback."""
    chunks = chunks or []
    fallback = fallback_result(subject, message, chunks)

    if not settings.llm_api_key:
        log.warning("LLM_API_KEY not set - using the local fallback classifier")
        return fallback

    prompt = build_prompt(subject, message, chunks)
    try:
        raw = _call_llm(prompt)
        parsed = extract_json(raw)
        return normalise(parsed, fallback)
    except Exception as exc:  # noqa: BLE001 - the ticket must still be enriched
        log.error("LLM analysis failed (%s) - using fallback", exc)
        return fallback
