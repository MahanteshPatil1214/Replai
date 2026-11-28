"""Authorization for the knowledge base write endpoints.

ticket-service signs the JWT on login; ai-service verifies it with the same
secret (JWT_SECRET) because the browser talks to both services directly.
Only LEAD and ADMIN may change the knowledge base - reads stay open for
anyone signed in (project.md section 8).
"""

from __future__ import annotations

from typing import Callable

import jwt
from fastapi import HTTPException, Header

from .config import settings

ALGORITHM = "HS256"
LEAD_OR_ADMIN = ("LEAD", "ADMIN")


def _claims(authorization: str | None) -> dict:
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(status_code=401, detail="authentication required - log in first")

    token = authorization.removeprefix("Bearer ").strip()
    try:
        return jwt.decode(token, settings.jwt_secret, algorithms=[ALGORITHM])
    except jwt.PyJWTError as exc:
        raise HTTPException(status_code=401, detail="authentication required - log in first") from exc


def require_role(*roles: str) -> Callable[..., dict]:
    """Dependency factory: valid token, and one of `roles` inside it."""

    def dependency(authorization: str | None = Header(default=None)) -> dict:
        claims = _claims(authorization)
        if claims.get("role") not in roles:
            raise HTTPException(status_code=403, detail="your role does not allow this action")
        return claims

    return dependency


require_lead_or_admin = require_role(*LEAD_OR_ADMIN)
