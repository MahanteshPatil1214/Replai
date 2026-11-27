package com.replai.tickets.dto;

/** Open tickets grouped by priority. */
public record PriorityAnalyticsResponse(String priority, long open) {
}
