"""Environment driven configuration for ai-service."""

from __future__ import annotations

import os
from dataclasses import dataclass


def _env(name: str, default: str = "") -> str:
    return os.environ.get(name, default).strip()


@dataclass(frozen=True)
class Settings:
    kafka_bootstrap_servers: str = "localhost:9092"
    kafka_group_id: str = "ai-service"
    postgres_host: str = "localhost"
    postgres_port: str = "5433"
    postgres_db: str = "replai"
    postgres_user: str = "replai"
    postgres_password: str = "replai"
    llm_api_key: str = ""
    llm_model: str = "gpt-4o-mini"
    llm_base_url: str = ""
    embedding_model: str = "all-MiniLM-L6-v2"
    embedding_dim: int = 384
    consumer_enabled: bool = True
    # Same secret ticket-service signs its login tokens with (project.md section 8).
    jwt_secret: str = "replai-local-dev-secret-change-me-0123456789"
    # Below this similarity the AI does not guess: hand the ticket to a human.
    confidence_threshold: float = 0.45

    @property
    def postgres_dsn(self) -> str:
        return (
            f"host={self.postgres_host} port={self.postgres_port} "
            f"dbname={self.postgres_db} user={self.postgres_user} "
            f"password={self.postgres_password}"
        )


def load_settings() -> Settings:
    return Settings(
        kafka_bootstrap_servers=_env("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092"),
        kafka_group_id=_env("KAFKA_GROUP_ID", "ai-service"),
        postgres_host=_env("POSTGRES_HOST", "postgres" if _env("POSTGRES_HOST") else "localhost"),
        postgres_port=_env("POSTGRES_PORT", "5433"),
        postgres_db=_env("POSTGRES_DB", "replai"),
        postgres_user=_env("POSTGRES_USER", "replai"),
        postgres_password=_env("POSTGRES_PASSWORD", "replai"),
        llm_api_key=_env("LLM_API_KEY"),
        llm_model=_env("LLM_MODEL", "gpt-4o-mini"),
        llm_base_url=_env("LLM_BASE_URL"),
        embedding_model=_env("EMBEDDING_MODEL", "all-MiniLM-L6-v2"),
        embedding_dim=int(_env("EMBEDDING_DIM", "384")),
        consumer_enabled=_env("KAFKA_CONSUMER_ENABLED", "true").lower() != "false",
        confidence_threshold=float(_env("CONFIDENCE_THRESHOLD", "0.45")),
        jwt_secret=_env("JWT_SECRET", "replai-local-dev-secret-change-me-0123456789"),
    )


settings = load_settings()
