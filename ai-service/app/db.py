"""Small helper around a psycopg connection pool."""

from __future__ import annotations

import logging
from typing import Any, Sequence

from psycopg import Connection
from psycopg.rows import dict_row
from psycopg_pool import ConnectionPool

from .config import settings

log = logging.getLogger("ai-service.db")

_pool: ConnectionPool | None = None


def get_pool() -> ConnectionPool:
    global _pool
    if _pool is None:
        _pool = ConnectionPool(
            conninfo=settings.postgres_dsn,
            min_size=1,
            max_size=5,
            open=True,
            kwargs={"row_factory": dict_row},
        )
        log.info("Postgres connection pool opened (%s)", settings.postgres_host)
    return _pool


def close_pool() -> None:
    global _pool
    if _pool is not None:
        _pool.close()
        _pool = None
        log.info("Postgres connection pool closed")


def fetch_all(sql: str, params: Sequence[Any] = ()) -> list[dict[str, Any]]:
    with get_pool().connection() as conn:
        with conn.cursor() as cur:
            cur.execute(sql, params)
            return list(cur.fetchall())


def fetch_one(sql: str, params: Sequence[Any] = ()) -> dict[str, Any] | None:
    with get_pool().connection() as conn:
        with conn.cursor() as cur:
            cur.execute(sql, params)
            return cur.fetchone()


def execute(sql: str, params: Sequence[Any] = ()) -> None:
    """Run a statement inside its own transaction (committed on success)."""
    with get_pool().connection() as conn:
        with conn.cursor() as cur:
            cur.execute(sql, params)


def transaction(conn: Connection) -> Connection:
    """Exposed for code paths that need several statements in one transaction."""
    return conn
