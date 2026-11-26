from app.rag.chunking import chunk_text


def test_empty_text_makes_no_chunks():
    assert chunk_text("") == []
    assert chunk_text("   ") == []


def test_short_text_is_a_single_chunk():
    chunks = chunk_text("Refunds take 5 business days.")

    assert len(chunks) == 1
    assert chunks[0] == "Refunds take 5 business days."


def test_long_text_is_split_into_chunks_under_the_limit():
    sentences = [f"Refund number {i} takes five business days to process." for i in range(60)]
    chunks = chunk_text(" ".join(sentences))

    assert len(chunks) > 1
    assert all(len(chunk) <= 400 for chunk in chunks)


def test_chunks_keep_the_original_sentences():
    text = "First sentence here. Second sentence here. Third sentence here."
    chunks = chunk_text(text, size=60, overlap=10)

    joined = " ".join(chunks)
    assert "First sentence here." in joined
    assert "Third sentence here." in joined


def test_very_long_single_sentence_is_cut():
    chunks = chunk_text("x" * 1000, size=300, overlap=30)

    assert len(chunks) >= 3
    assert all(len(chunk) <= 300 for chunk in chunks)


def test_paragraph_breaks_are_kept_as_boundaries():
    import re

    text = "Para one.\n\nPara two.\n\nPara three."
    chunks = chunk_text(text, size=15, overlap=0)

    assert len(chunks) >= 3
    assert all(len(chunk) <= 15 for chunk in chunks)
    # no content is lost, whitespace only
    assert re.sub(r"\s+", "", "".join(chunks)) == re.sub(r"\s+", "", text)
