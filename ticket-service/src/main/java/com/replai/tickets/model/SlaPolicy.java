package com.replai.tickets.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * How long a ticket of a given priority may stay open (project.md section 6).
 */
@Entity
@Table(name = "sla_policies")
public class SlaPolicy {

    @Id
    @Column(nullable = false, length = 20)
    private String priority;

    @Column(name = "resolve_within_hours", nullable = false)
    private int resolveWithinHours;

    public SlaPolicy() {
    }

    public SlaPolicy(String priority, int resolveWithinHours) {
        this.priority = priority;
        this.resolveWithinHours = resolveWithinHours;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public int getResolveWithinHours() {
        return resolveWithinHours;
    }

    public void setResolveWithinHours(int resolveWithinHours) {
        this.resolveWithinHours = resolveWithinHours;
    }
}
