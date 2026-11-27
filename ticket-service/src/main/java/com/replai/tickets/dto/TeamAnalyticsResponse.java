package com.replai.tickets.dto;

/** Per-team row on the analytics dashboard. */
public record TeamAnalyticsResponse(
        Long teamId,
        String teamName,
        long resolved,
        long open,
        Double avgResolutionHours,
        Double slaMetPercent
) {
}
