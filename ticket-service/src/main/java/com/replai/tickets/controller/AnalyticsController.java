package com.replai.tickets.controller;

import com.replai.tickets.dto.AiAnalyticsResponse;
import com.replai.tickets.dto.AnalyticsSummaryResponse;
import com.replai.tickets.dto.EmployeeAnalyticsResponse;
import com.replai.tickets.dto.PriorityAnalyticsResponse;
import com.replai.tickets.dto.TeamAnalyticsResponse;
import com.replai.tickets.dto.TicketResponse;
import com.replai.tickets.service.AnalyticsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** GET /api/analytics/* - every number on the dashboard (project.md section 8). */
@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/summary")
    public AnalyticsSummaryResponse summary() {
        return analyticsService.summary();
    }

    @GetMapping("/teams")
    public List<TeamAnalyticsResponse> teams() {
        return analyticsService.teams();
    }

    @GetMapping("/employees")
    public List<EmployeeAnalyticsResponse> employees(
            @RequestParam(defaultValue = "7d") String range) {
        return analyticsService.employees(range);
    }

    @GetMapping("/priority")
    public List<PriorityAnalyticsResponse> priority() {
        return analyticsService.priorities();
    }

    @GetMapping("/ai")
    public AiAnalyticsResponse ai() {
        return analyticsService.ai();
    }

    @GetMapping("/knowledge-gaps")
    public List<TicketResponse> knowledgeGaps() {
        return analyticsService.knowledgeGaps();
    }
}
