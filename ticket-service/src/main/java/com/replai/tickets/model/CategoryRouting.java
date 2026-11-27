package com.replai.tickets.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Maps an AI category to a team. Lives in the database so routing can be
 * changed without redeploying (project.md section 5).
 */
@Entity
@Table(name = "category_routing")
public class CategoryRouting {

    @Id
    @Column(nullable = false, length = 40)
    private String category;

    @Column(name = "team_id", nullable = false)
    private Long teamId;

    public CategoryRouting() {
    }

    public CategoryRouting(String category, Long teamId) {
        this.category = category;
        this.teamId = teamId;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Long getTeamId() {
        return teamId;
    }

    public void setTeamId(Long teamId) {
        this.teamId = teamId;
    }
}
