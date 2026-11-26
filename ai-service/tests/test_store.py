from app.rag import store


class FakeDB:
    def __init__(self):
        self.all_results = []
        self.one_result = None
        self.all_calls = []
        self.one_calls = []

    def fetch_all(self, sql, params=()):
        self.all_calls.append((sql, params))
        return list(self.all_results)

    def fetch_one(self, sql, params=()):
        self.one_calls.append((sql, params))
        return self.one_result


def test_vector_format_is_pgvector_text_form():
    assert store._vector([0.5, -1.0]) == "[0.500000,-1.000000]"


def test_search_returns_empty_when_the_kb_is_empty(monkeypatch):
    fake = FakeDB()
    fake.one_result = {"n": 0}
    monkeypatch.setattr(store, "db", fake)

    assert store.search_chunks("how do refunds work") == []


def test_search_skips_empty_queries(monkeypatch):
    fake = FakeDB()
    monkeypatch.setattr(store, "db", fake)

    assert store.search_chunks("   ") == []
    assert fake.all_calls == []


def test_search_returns_closest_chunks(monkeypatch):
    fake = FakeDB()
    fake.one_result = {"n": 3}
    fake.all_results = [
        {"article_id": 7, "chunk_text": "Refunds take 5 days.", "title": "Refund policy", "score": 0.81},
        {"article_id": 2, "chunk_text": "Contact your bank.", "title": "Failed payments", "score": 0.62},
    ]
    monkeypatch.setattr(store, "db", fake)
    monkeypatch.setattr(store, "embed_text", lambda text: [0.1] * 384)

    results = store.search_chunks("refund", limit=2)

    assert [r["article_id"] for r in results] == [7, 2]
    assert results[0]["score"] == 0.81
    assert "ORDER BY c.embedding <=>" in fake.all_calls[0][0]


def test_list_articles_renames_columns_for_the_api(monkeypatch):
    fake = FakeDB()
    fake.all_results = [
        {"id": 1, "title": "Refunds", "content": "c", "created_at": "2026-11-26", "chunk_count": 3},
    ]
    monkeypatch.setattr(store, "db", fake)

    [article] = store.list_articles()

    assert article["createdAt"] == "2026-11-26"
    assert article["chunkCount"] == 3
    assert "created_at" not in article


def test_delete_article_reports_whether_something_was_removed(monkeypatch):
    fake = FakeDB()
    monkeypatch.setattr(store, "db", fake)

    fake.one_result = {"id": 5}
    assert store.delete_article(5) is True

    fake.one_result = None
    assert store.delete_article(6) is False


def test_search_joins_the_article_title_so_sources_can_be_returned(monkeypatch):
    fake = FakeDB()
    fake.one_result = {"n": 1}
    fake.all_results = [{"article_id": 9, "chunk_text": "t", "title": "T", "score": 1.0}]
    monkeypatch.setattr(store, "db", fake)
    monkeypatch.setattr(store, "embed_text", lambda text: [0.2] * 384)

    store.search_chunks("anything")

    sql, params = fake.all_calls[0]
    assert "JOIN kb_articles" in sql
    assert len(params) == 3  # embedding used twice (score + order) plus limit
