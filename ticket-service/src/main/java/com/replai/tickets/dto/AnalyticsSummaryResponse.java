package com.replai.tickets.dto;

/**
 * One row per screen widget. Every value is a plain number the UI can render.
 */
public record AnalyticsSummaryResponse(
        long open,
        long resolved,
        Double avgResolutionHours,
        Double slaMetPercent
) {
}
