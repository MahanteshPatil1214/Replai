"""Load the starter knowledge base articles (idempotent)."""

from __future__ import annotations

import json
import logging
from pathlib import Path
from typing import Any

from .rag import store

log = logging.getLogger("ai-service.seed")

SEED_FILE = Path(__file__).resolve().parents[1] / "seed" / "articles.json"


def load_seed_articles(path: Path = SEED_FILE) -> list[dict[str, Any]]:
    with path.open(encoding="utf-8") as handle:
        articles = json.load(handle)
    if not isinstance(articles, list):
        raise ValueError(f"{path} must contain a JSON array")
    return articles


def seed_if_empty(path: Path = SEED_FILE) -> int:
    """Ingest every seed article only when the knowledge base is empty."""
    if store.count_articles() > 0:
        log.info("Knowledge base already has articles, skipping the seed")
        return 0

    articles = load_seed_articles(path)
    for article in articles:
        store.create_article(article["title"], article["content"])

    log.info("Seeded %d knowledge base articles", len(articles))
    return len(articles)
