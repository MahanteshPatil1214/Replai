package com.replai.tickets.service;

import com.replai.tickets.dto.AiAnalyticsResponse;
import com.replai.tickets.dto.AnalyticsSummaryResponse;
import com.replai.tickets.dto.EmployeeAnalyticsResponse;
import com.replai.tickets.dto.PriorityAnalyticsResponse;
import com.replai.tickets.dto.TeamAnalyticsResponse;
import com.replai.tickets.dto.TicketResponse;
import com.replai.tickets.model.Employee;
import com.replai.tickets.model.Team;
import com.replai.tickets.model.TicketStatus;
import com.replai.tickets.repository.AnalyticsRepository;
import com.replai.tickets.repository.EmployeeRepository;
import com.replai.tickets.repository.TeamRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Everything the analytics dashboard shows. Numbers come from the tickets
 * table only (project.md section 10): counts are aggregated in SQL with
 * GROUP BY, durations and SLA results are averaged from the resolved rows.
 */
@Service
public class AnalyticsService {

    private static final TicketStatus RESOLVED = TicketStatus.RESOLVED;
    private static final List<String> PRIORITIES = List.of("CRITICAL", "HIGH", "MEDIUM", "LOW");

    private final AnalyticsRepository analyticsRepository;
    private final TeamRepository teamRepository;
    private final EmployeeRepository employeeRepository;

    public AnalyticsService(
            AnalyticsRepository analyticsRepository,
            TeamRepository teamRepository,
            EmployeeRepository employeeRepository) {
        this.analyticsRepository = analyticsRepository;
        this.teamRepository = teamRepository;
        this.employeeRepository = employeeRepository;
    }

    @Transactional(readOnly = true)
    public AnalyticsSummaryResponse summary() {
        List<Object[]> rows = analyticsRepository.resolvedRows(RESOLVED);
        return new AnalyticsSummaryResponse(
                analyticsRepository.countUnresolved(RESOLVED),
                analyticsRepository.countResolved(RESOLVED),
                averageResolutionHours(rows),
                slaMetPercent(rows));
    }

    @Transactional(readOnly = true)
    public List<TeamAnalyticsResponse> teams() {
        Map<Long, Long> openByTeam = toMap(analyticsRepository.openCountByTeam(RESOLVED));

        Map<Long, List<Object[]>> resolvedByTeam = analyticsRepository.resolvedRows(RESOLVED)
                .stream()
                .filter(row -> row[0] != null)
                .collect(Collectors.groupingBy(row -> (Long) row[0]));

        List<TeamAnalyticsResponse> result = new ArrayList<>();
        for (Team team : teamRepository.findAll()) {
            List<Object[]> rows = resolvedByTeam.getOrDefault(team.getId(), List.of());
            result.add(new TeamAnalyticsResponse(
                    team.getId(),
                    team.getName(),
                    rows.size(),
                    openByTeam.getOrDefault(team.getId(), 0L),
                    averageResolutionHours(rows),
                    slaMetPercent(rows)));
        }

        result.sort(Comparator.comparing(TeamAnalyticsResponse::resolved).reversed()
                .thenComparing(TeamAnalyticsResponse::teamName));
        return result;
    }

    /**
     * Leaderboard for a range: `7d`, `30d` or `all` (project.md analytics).
     */
    @Transactional(readOnly = true)
    public List<EmployeeAnalyticsResponse> employees(String range) {
        Instant cutoff = cutoffFor(range);
        // Postgres cannot type a null cutoff parameter, so each case is its own query.
        Map<Long, Long> solvedByEmployee = cutoff == null
                ? toMap(analyticsRepository.resolvedCountByEmployee(RESOLVED))
                : toMap(analyticsRepository.resolvedCountByEmployee(RESOLVED, cutoff));

        List<Object[]> employeeRows = cutoff == null
                ? analyticsRepository.resolvedRows(RESOLVED)
                : analyticsRepository.resolvedRows(RESOLVED, cutoff);
        Map<Long, List<Object[]>> rowsByEmployee = employeeRows.stream()
                .filter(row -> row[1] != null)
                .collect(Collectors.groupingBy(row -> (Long) row[1]));

        Map<Long, Team> teamById = new HashMap<>();
        for (Team team : teamRepository.findAll()) {
            if (team.getId() != null) {
                teamById.put(team.getId(), team);
            }
        }

        List<EmployeeAnalyticsResponse> result = new ArrayList<>();
        for (Employee employee : employeeRepository.findAll()) {
            long solved = solvedByEmployee.getOrDefault(employee.getId(), 0L);
            if (solved == 0) {
                continue;
            }
            String teamName = teamById.containsKey(employee.getTeamId())
                    ? teamById.get(employee.getTeamId()).getName()
                    : null;
            result.add(new EmployeeAnalyticsResponse(
                    employee.getId(),
                    employee.getName(),
                    teamName,
                    solved,
                    averageResolutionHours(rowsByEmployee.getOrDefault(employee.getId(), List.of()))));
        }

        result.sort(Comparator.comparingLong(EmployeeAnalyticsResponse::resolved).reversed()
                .thenComparing(Comparator.comparing(
                        (EmployeeAnalyticsResponse row) -> row.avgResolutionHours() == null
                                ? Double.MAX_VALUE
                                : row.avgResolutionHours())));
        return result;
    }

    /** Open tickets per priority, always in CRITICAL -> LOW order. */
    @Transactional(readOnly = true)
    public List<PriorityAnalyticsResponse> priorities() {
        Map<String, Long> open = new HashMap<>();
        for (Object[] row : analyticsRepository.openCountByPriority(RESOLVED)) {
            open.put(String.valueOf(row[0]), (Long) row[1]);
        }

        return PRIORITIES.stream()
                .map(priority -> new PriorityAnalyticsResponse(priority, open.getOrDefault(priority, 0L)))
                .toList();
    }

    /** What agents did with each AI draft, plus the acceptance rate. */
    @Transactional(readOnly = true)
    public AiAnalyticsResponse ai() {
        Map<String, Long> counts = new HashMap<>();
        for (Object[] row : analyticsRepository.countByDraftAction()) {
            counts.put(String.valueOf(row[0]), (Long) row[1]);
        }

        long accepted = counts.getOrDefault("ACCEPTED", 0L);
        long edited = counts.getOrDefault("EDITED", 0L);
        long rejected = counts.getOrDefault("REJECTED", 0L);
        long total = accepted + edited + rejected;
        Double rate = total == 0 ? null : round1(accepted * 100.0 / total);

        return new AiAnalyticsResponse(accepted, edited, rejected, rate);
    }

    /** Tickets the knowledge base could not answer: write an article next. */
    @Transactional(readOnly = true)
    public List<TicketResponse> knowledgeGaps() {
        return analyticsRepository.findKnowledgeGaps().stream().map(TicketResponse::from).toList();
    }

    /** `7d` -> 7 days ago, `30d` -> 30 days ago, `all`/blank -> no cutoff. */
    public Instant cutoffFor(String range) {
        if (range == null || range.isBlank()) {
            return null;
        }
        String value = range.trim().toLowerCase();
        if (value.equals("all")) {
            return null;
        }
        if (value.matches("\\d+d")) {
            return Instant.now().minus(Duration.ofDays(Long.parseLong(value.substring(0, value.length() - 1))));
        }
        if (value.matches("\\d+")) {
            return Instant.now().minus(Duration.ofDays(Long.parseLong(value)));
        }
        throw new IllegalArgumentException("unknown range '" + range + "' - use 7d, 30d or all");
    }

    private static Double averageResolutionHours(List<Object[]> rows) {
        List<Double> hours = new ArrayList<>();
        for (Object[] row : rows) {
            Instant createdAt = (Instant) row[2];
            Instant resolvedAt = (Instant) row[3];
            if (createdAt != null && resolvedAt != null && !resolvedAt.isBefore(createdAt)) {
                hours.add(Duration.between(createdAt, resolvedAt).toSeconds() / 3600.0);
            }
        }
        if (hours.isEmpty()) {
            return null;
        }
        return round1(hours.stream().mapToDouble(Double::doubleValue).average().orElse(0));
    }

    private static Double slaMetPercent(List<Object[]> rows) {
        long total = 0;
        long met = 0;
        for (Object[] row : rows) {
            total++;
            Instant resolvedAt = (Instant) row[3];
            Instant slaDueAt = (Instant) row[4];
            boolean breached = Boolean.TRUE.equals(row[5]);
            boolean withinSla = slaDueAt == null || (resolvedAt != null && !resolvedAt.isAfter(slaDueAt));
            if (withinSla && !breached) {
                met++;
            }
        }
        return total == 0 ? null : round1(met * 100.0 / total);
    }

    private static Map<Long, Long> toMap(List<Object[]> rows) {
        Map<Long, Long> result = new HashMap<>();
        for (Object[] row : rows) {
            if (row[0] != null) {
                result.put((Long) row[0], (Long) row[1]);
            }
        }
        return result;
    }

    private static Double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
