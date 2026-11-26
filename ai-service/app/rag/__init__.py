"""RAG: chunking, embeddings and pgvector search."""

from .chunking import chunk_text
from .embeddings import embed_texts, model_name
from .store import (
    create_article,
    create_tables,
    delete_article,
    list_articles,
    search_chunks,
)

__all__ = [
    "chunk_text",
    "embed_texts",
    "model_name",
    "create_article",
    "create_tables",
    "delete_article",
    "list_articles",
    "search_chunks",
]
