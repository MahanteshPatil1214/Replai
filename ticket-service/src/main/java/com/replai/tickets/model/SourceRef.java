package com.replai.tickets.model;

/**
 * A knowledge base article that the AI used to write the reply.
 * Persisted inside the ticket's `sources` JSONB column.
 */
public record SourceRef(Long articleId, String title) {
}
