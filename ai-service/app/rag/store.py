"""Schema and SQL for the knowledge base (owned by ai-service)."""

from __future__ import annotations

import logging
from typing import Any

from .. import db
from ..config import settings
from .chunking import chunk_text
from .embeddings import embed_text, embed_texts

log = logging.getLogger("ai-service.store")

DIM = settings.embedding_dim

SCHEMA = f"""
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS kb_articles (
    id          BIGSERIAL PRIMARY KEY,
    title       VARCHAR(300) NOT NULL,
    content     TEXT NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS kb_chunks (
    id          BIGSERIAL PRIMARY KEY,
    article_id  BIGINT NOT NULL REFERENCES kb_articles(id) ON DELETE CASCADE,
    chunk_text  TEXT NOT NULL,
    embedding   VECTOR({DIM})
);

CREATE INDEX IF NOT EXISTS kb_chunks_article_idx ON kb_chunks (article_id);
CREATE INDEX IF NOT EXISTS kb_chunks_embedding_idx
    ON kb_chunks USING hnsw (embedding vector_cosine_ops);
"""

ARTICLE_COLUMNS = "a.id, a.title, a.content, a.created_at"


def _vector(value: list[float]) -> str:
    """pgvector accepts its text form '[0.1,0.2,...]' - no driver plugin needed."""
    return "[" + ",".join(f"{number:.6f}" for number in value) + "]"


def create_tables() -> None:
    db.execute(SCHEMA)
    log.info("knowledge base schema ensured (%d-dimension vectors)", DIM)


def create_article(title: str, content: str) -> dict[str, Any]:
    """Insert an article, chunk it and embed each chunk in one transaction."""
    title = title.strip()
    content = content.strip()

    chunks = chunk_text(content)
    vectors = embed_texts(chunks) if chunks else []

    with db.get_pool().connection() as conn:
        with conn.cursor() as cur:
            cur.execute(
                "INSERT INTO kb_articles (title, content) VALUES (%s, %s) "
                "RETURNING id, title, content, created_at",
                (title, content),
            )
            article = dict(cur.fetchone())
            for text, vector in zip(chunks, vectors):
                cur.execute(
                    "INSERT INTO kb_chunks (article_id, chunk_text, embedding) "
                    "VALUES (%s, %s, %s::vector)",
                    (article["id"], text, _vector(vector)),
                )

    article["chunkCount"] = len(chunks)
    log.info("Stored article %s with %d chunks", article["id"], len(chunks))
    return article


def list_articles() -> list[dict[str, Any]]:
    rows = db.fetch_all(
        f"""
        SELECT {ARTICLE_COLUMNS},
               (SELECT count(*) FROM kb_chunks c WHERE c.article_id = a.id) AS chunk_count
        FROM kb_articles a
        ORDER BY a.id DESC
        """
    )
    for row in rows:
        row["createdAt"] = row.pop("created_at")
        row["chunkCount"] = row.pop("chunk_count")
    return rows


def get_article(article_id: int) -> dict[str, Any] | None:
    row = db.fetch_one(
        "SELECT id, title, content, created_at FROM kb_articles WHERE id = %s",
        (article_id,),
    )
    if row:
        row["createdAt"] = row.pop("created_at")
    return row


def delete_article(article_id: int) -> bool:
    row = db.fetch_one(
        "DELETE FROM kb_articles WHERE id = %s RETURNING id",
        (article_id,),
    )
    if row:
        log.info("Deleted article %s (its chunks cascade)", article_id)
    return row is not None


def count_articles() -> int:
    row = db.fetch_one("SELECT count(*) AS n FROM kb_articles")
    return int(row["n"]) if row else 0


def search_chunks(query: str, limit: int = 3) -> list[dict[str, Any]]:
    """Closest chunks for a free-text query (cosine distance)."""
    query = (query or "").strip()
    if not query:
        return []
    if count_articles() == 0:
        return []

    embedding = _vector(embed_text(query))
    rows = db.fetch_all(
        """
        SELECT c.article_id, c.chunk_text, a.title,
               1 - (c.embedding <=> %s::vector) AS score
        FROM kb_chunks c
        JOIN kb_articles a ON a.id = c.article_id
        ORDER BY c.embedding <=> %s::vector
        LIMIT %s
        """,
        (embedding, embedding, int(limit)),
    )
    for row in rows:
        row["score"] = float(row["score"])
    return rows
