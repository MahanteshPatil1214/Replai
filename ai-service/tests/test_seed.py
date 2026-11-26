import pytest

from app import seed


def test_load_seed_articles_reads_the_bundled_file():
    articles = seed.load_seed_articles()

    assert len(articles) >= 5
    assert all(a["title"] and a["content"] for a in articles)
    titles = [a["title"] for a in articles]
    assert any("refund" in t.lower() for t in titles)
    assert any("password" in t.lower() for t in titles)
    assert any("shipping" in t.lower() for t in titles)


class FakeStore:
    def __init__(self, existing: int):
        self.existing = existing
        self.created = []

    def count_articles(self) -> int:
        return self.existing

    def create_article(self, title: str, content: str) -> dict:
        self.created.append(title)
        return {"id": len(self.created), "title": title, "content": content}


def test_seed_skips_when_the_knowledge_base_is_not_empty(monkeypatch):
    fake = FakeStore(existing=8)
    monkeypatch.setattr(seed, "store", fake)

    assert seed.seed_if_empty() == 0
    assert fake.created == []


def test_seed_ingests_every_article_when_empty(monkeypatch):
    fake = FakeStore(existing=0)
    monkeypatch.setattr(seed, "store", fake)

    inserted = seed.seed_if_empty()

    assert inserted == len(seed.load_seed_articles())
    assert fake.created == [a["title"] for a in seed.load_seed_articles()]


def test_seed_rejects_a_malformed_file(tmp_path):
    bad = tmp_path / "articles.json"
    bad.write_text('{"title": "not a list"}', encoding="utf-8")

    with pytest.raises(ValueError):
        seed.load_seed_articles(bad)
