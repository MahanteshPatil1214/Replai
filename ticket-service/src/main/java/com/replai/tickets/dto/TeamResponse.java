package com.replai.tickets.dto;

import com.replai.tickets.model.Team;

/**
 * Response body for GET /api/teams.
 */
public record TeamResponse(Long id, String name) {

    public static TeamResponse from(Team team) {
        return new TeamResponse(team.getId(), team.getName());
    }
}
