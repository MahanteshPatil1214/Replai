"""Embeddings. The SAME model is used for ingestion and for search."""

from __future__ import annotations

import logging
from typing import Any

from ..config import settings

log = logging.getLogger("ai-service.embeddings")

_model: Any = None


def model_name() -> str:
    return settings.embedding_model


def _get_model():
    global _model
    if _model is None:
        from sentence_transformers import SentenceTransformer  # heavy import, load lazily

        log.info("Loading embedding model %s ...", model_name())
        _model = SentenceTransformer(model_name())
        log.info("Embedding model loaded")
    return _model


def embed_texts(texts: list[str]) -> list[list[float]]:
    """Return one 384-dimension vector per input text."""
    if not texts:
        return []
    vectors = _get_model().encode(texts, normalize_embeddings=True)
    return [list(map(float, vector)) for vector in vectors]


def embed_text(text: str) -> list[float]:
    return embed_texts([text])[0]
