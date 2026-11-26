import sys
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))


@pytest.fixture(scope="session", autouse=True)
def _close_db_pool():
    """Make sure no psycopg pool threads outlive the test run."""
    yield
    from app import db

    db.close_pool()
