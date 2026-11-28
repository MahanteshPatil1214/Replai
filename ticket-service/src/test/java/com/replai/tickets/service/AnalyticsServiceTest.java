package com.replai.tickets.service;

import com.replai.tickets.model.Employee;
import com.replai.tickets.model.EmployeeRole;
import com.replai.tickets.model.Team;
import com.replai.tickets.model.TicketStatus;
import com.replai.tickets.repository.AnalyticsRepository;
import com.replai.tickets.repository.EmployeeRepository;
import com.replai.tickets.repository.TeamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

class AnalyticsServiceTest {

    private AnalyticsRepository analyticsRepository;
    private EmployeeRepository employeeRepository;
    private AnalyticsService analyticsService;

    @BeforeEach
    void setUp() {
        analyticsRepository = Mockito.mock(AnalyticsRepository.class);
        TeamRepository teamRepository = Mockito.mock(TeamRepository.class);
        employeeRepository = Mockito.mock(EmployeeRepository.class);
        analyticsService = new AnalyticsService(analyticsRepository, teamRepository, employeeRepository);

        lenient().when(analyticsRepository.resolvedRows(any())).thenReturn(List.of());
        lenient().when(analyticsRepository.resolvedRows(any(), any())).thenReturn(List.of());
        lenient().when(analyticsRepository.resolvedCountByEmployee(any())).thenReturn(List.of());
        lenient().when(analyticsRepository.resolvedCountByEmployee(any(), any())).thenReturn(List.of());
        lenient().when(analyticsRepository.countUnresolved(any())).thenReturn(0L);
        lenient().when(analyticsRepository.countResolved(any())).thenReturn(0L);

        Team software = new Team("Software Team");
        ReflectionTestUtils.setField(software, "id", 1L);
        Team infrastructure = new Team("Infrastructure Team");
        ReflectionTestUtils.setField(infrastructure, "id", 2L);
        lenient().when(teamRepository.findAll()).thenReturn(List.of(software, infrastructure));
        lenient().when(employeeRepository.findAll()).thenReturn(List.of());
    }

    private Object[] resolvedRow(Long teamId, Long assigneeId, String created, String resolved, String due, boolean breached) {
        return new Object[]{
                teamId,
                assigneeId,
                Instant.parse(created),
                Instant.parse(resolved),
                due == null ? null : Instant.parse(due),
                breached
        };
    }

    @Test
    void summaryAveragesResolutionTimeAndSlaResult() {
        when(analyticsRepository.countUnresolved(TicketStatus.RESOLVED)).thenReturn(3L);
        when(analyticsRepository.countResolved(TicketStatus.RESOLVED)).thenReturn(2L);
        when(analyticsRepository.resolvedRows(TicketStatus.RESOLVED)).thenReturn(List.of(
                resolvedRow(1L, 11L, "2026-01-01T00:00:00Z", "2026-01-01T04:00:00Z", "2026-01-01T06:00:00Z", false),
                resolvedRow(1L, 12L, "2026-01-01T00:00:00Z", "2026-01-01T10:00:00Z", "2026-01-01T06:00:00Z", true)));

        var summary = analyticsService.summary();

        assertThat(summary.open()).isEqualTo(3);
        assertThat(summary.resolved()).isEqualTo(2);
        assertThat(summary.avgResolutionHours()).isEqualTo(7.0);   // (4h + 10h) / 2
        assertThat(summary.slaMetPercent()).isEqualTo(50.0);       // 1 of 2 inside the SLA
    }

    @Test
    void summaryIsSafeWhenNothingIsResolvedYet() {
        var summary = analyticsService.summary();

        assertThat(summary.avgResolutionHours()).isNull();
        assertThat(summary.slaMetPercent()).isNull();
    }

    @Test
    void teamsAlwaysShowEveryTeamEvenWithNoTickets() {
        var teams = analyticsService.teams();

        assertThat(teams).extracting(row -> row.teamName())
                .containsExactlyInAnyOrder("Software Team", "Infrastructure Team");
        assertThat(teams).allSatisfy(row -> {
            assertThat(row.resolved()).isZero();
            assertThat(row.open()).isZero();
        });
    }

    @Test
    void teamsRankByResolvedCount() {
        when(analyticsRepository.openCountByTeam(TicketStatus.RESOLVED))
                .thenReturn(List.of(new Object[]{1L, 2L}, new Object[]{2L, 9L}));
        when(analyticsRepository.resolvedRows(TicketStatus.RESOLVED)).thenReturn(List.of(
                resolvedRow(2L, 21L, "2026-01-01T00:00:00Z", "2026-01-01T02:00:00Z", null, false),
                resolvedRow(2L, 22L, "2026-01-01T00:00:00Z", "2026-01-01T03:00:00Z", null, false),
                resolvedRow(1L, 11L, "2026-01-01T00:00:00Z", "2026-01-01T01:00:00Z", null, false)));

        var teams = analyticsService.teams();

        assertThat(teams.get(0).teamName()).isEqualTo("Infrastructure Team");
        assertThat(teams.get(0).resolved()).isEqualTo(2);
        assertThat(teams.get(0).open()).isEqualTo(9);
        assertThat(teams.get(1).resolved()).isEqualTo(1);
    }

    @Test
    void leaderboardOrdersBySolvedCountAndSkipsEmployeesWithNothing() {
        Employee lead = new Employee("Asha Kumar", "asha@replai.dev", 1L, EmployeeRole.LEAD);
        ReflectionTestUtils.setField(lead, "id", 11L);
        Employee idle = new Employee("Nobody", "nobody@replai.dev", 1L, EmployeeRole.AGENT);
        ReflectionTestUtils.setField(idle, "id", 13L);
        when(employeeRepository.findAll()).thenReturn(List.of(lead, idle));

        when(analyticsRepository.resolvedCountByEmployee(TicketStatus.RESOLVED))
                .thenReturn(List.<Object[]>of(new Object[]{11L, 5L}));
        when(analyticsRepository.resolvedRows(TicketStatus.RESOLVED)).thenReturn(List.<Object[]>of(
                resolvedRow(1L, 11L, "2026-01-01T00:00:00Z", "2026-01-01T05:00:00Z", null, false)));

        var leaderboard = analyticsService.employees("all");

        assertThat(leaderboard).hasSize(1);
        assertThat(leaderboard.get(0).name()).isEqualTo("Asha Kumar");
        assertThat(leaderboard.get(0).resolved()).isEqualTo(5);
        assertThat(leaderboard.get(0).avgResolutionHours()).isEqualTo(5.0);
        assertThat(leaderboard.get(0).team()).isEqualTo("Software Team");
    }

    @Test
    void priorityBreakdownAlwaysListsAllFourPriorities() {
        when(analyticsRepository.openCountByPriority(TicketStatus.RESOLVED))
                .thenReturn(List.of(new Object[]{"CRITICAL", 2L}, new Object[]{"LOW", 7L}));

        var priorities = analyticsService.priorities();

        assertThat(priorities).extracting(row -> row.priority())
                .containsExactly("CRITICAL", "HIGH", "MEDIUM", "LOW");
        assertThat(priorities.get(0).open()).isEqualTo(2);
        assertThat(priorities.get(1).open()).isZero();
        assertThat(priorities.get(3).open()).isEqualTo(7);
    }

    @Test
    void aiQualityCountsDraftActionsAndAcceptanceRate() {
        when(analyticsRepository.countByDraftAction()).thenReturn(List.of(
                new Object[]{"ACCEPTED", 3L},
                new Object[]{"EDITED", 1L},
                new Object[]{"REJECTED", 1L}));

        var ai = analyticsService.ai();

        assertThat(ai.accepted()).isEqualTo(3);
        assertThat(ai.edited()).isEqualTo(1);
        assertThat(ai.rejected()).isEqualTo(1);
        assertThat(ai.acceptanceRate()).isEqualTo(60.0);
    }

    @Test
    void aiQualityIsUnknownBeforeAnyDraftWasReviewed() {
        when(analyticsRepository.countByDraftAction()).thenReturn(List.of());

        assertThat(analyticsService.ai().acceptanceRate()).isNull();
    }

    @Test
    void rangesAreParsedAndInvalidOnesRejected() {
        assertThat(analyticsService.cutoffFor("all")).isNull();
        assertThat(analyticsService.cutoffFor(null)).isNull();
        assertThat(analyticsService.cutoffFor("7d")).isBefore(Instant.now().minusSeconds(6 * 86400));
        assertThat(analyticsService.cutoffFor("30d")).isBefore(Instant.now().minusSeconds(29 * 86400));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> analyticsService.cutoffFor("yesterday"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("yesterday");
    }
}
