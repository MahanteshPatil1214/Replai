"""Split article content into overlapping chunks (about 300-500 characters)."""

from __future__ import annotations

import re

DEFAULT_CHUNK_SIZE = 400
DEFAULT_OVERLAP = 60


def _split_sentences(text: str) -> list[str]:
    parts = re.split(r"(?<=[.!?])\s+|\n+", text)
    return [part.strip() for part in parts if part.strip()]


def chunk_text(
    text: str,
    size: int = DEFAULT_CHUNK_SIZE,
    overlap: int = DEFAULT_OVERLAP,
) -> list[str]:
    """Sentence-aware chunking with a small overlap between neighbours.

    Falls back to hard slicing when a single "sentence" is longer than size.
    """
    text = (text or "").strip()
    if not text:
        return []
    if size <= 0:
        return [text]
    overlap = max(0, min(overlap, size - 1))

    chunks: list[str] = []
    current = ""

    for sentence in _split_sentences(text):
        while len(sentence) > size:
            # A single huge sentence: cut it and treat the rest as a new one.
            head, sentence = sentence[:size], sentence[size - overlap:]
            if current:
                chunks.append(current)
                current = ""
            chunks.append(head)

        candidate = f"{current} {sentence}".strip() if current else sentence
        if len(candidate) <= size:
            current = candidate
            continue

        chunks.append(current)
        carry = current[-overlap:] if overlap else ""
        current = f"{carry} {sentence}".strip() if carry else sentence

    if current:
        chunks.append(current)

    return chunks
