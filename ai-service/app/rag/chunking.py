"""Split article content into overlapping chunks (about 300-500 characters)."""

from __future__ import annotations

import re

DEFAULT_CHUNK_SIZE = 400
DEFAULT_OVERLAP = 60


def _split_sentences(text: str) -> list[str]:
    parts = re.split(r"(?<=[.!?])\s+|\n+", text)
    return [part.strip() for part in parts if part.strip()]


def _overlap_tail(text: str, start: int, overlap: int) -> str:
    """The `overlap` characters before index `start`.

    If the cut lands inside a word, the half word is dropped so the next chunk
    never begins with something like "atement,".
    """
    if overlap <= 0:
        return ""
    begin = max(0, start - overlap)
    tail = text[begin:start]
    if begin > 0 and not text[begin - 1].isspace():
        space = tail.find(" ")
        tail = tail[space + 1 :] if space != -1 else ""
    return tail.strip()


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
            head, remainder = sentence[:size], sentence[size:]
            sentence = f"{_overlap_tail(sentence, size, overlap)} {remainder}".strip()
            if current:
                chunks.append(current)
                current = ""
            chunks.append(head)

        candidate = f"{current} {sentence}".strip() if current else sentence
        if len(candidate) <= size:
            current = candidate
            continue

        chunks.append(current)
        carry = _overlap_tail(current, len(current), overlap)
        current = f"{carry} {sentence}".strip() if carry else sentence

    if current:
        chunks.append(current)

    return chunks
