package com.replai.tickets.dto;

/** AI quality: what agents did with each draft, and the acceptance rate. */
public record AiAnalyticsResponse(
        long accepted,
        long edited,
        long rejected,
        Double acceptanceRate
) {
}
