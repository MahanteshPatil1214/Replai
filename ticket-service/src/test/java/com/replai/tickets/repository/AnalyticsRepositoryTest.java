package com.replai.tickets.repository;

import com.replai.tickets.RepositoryTest;
import com.replai.tickets.model.Ticket;
import com.replai.tickets.model.TicketStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

class AnalyticsRepositoryTest extends RepositoryTest {

    @Autowired
    private AnalyticsRepository analyticsRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @BeforeEach
    void cleanUp() {
        ticketRepository.deleteAll();
    }

    private Ticket ticket(TicketStatus status, Long teamId, Long assigneeId) {
        Ticket ticket = new Ticket();
        ticket.setCustomerEmail("user@example.com");
        ticket.setSubject("subject");
        ticket.setMessage("message");
        ticket.setStatus(status);
        ticket.setTeamId(teamId);
        ticket.setAssigneeId(assigneeId);
        ticket.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        return ticketRepository.save(ticket);
    }

    @Test
    void countsAreGroupedByTeamEmployeePriorityAndDraftAction() {
        Ticket resolved = ticket(TicketStatus.RESOLVED, 1L, 11L);
        resolved.setResolvedAt(Instant.parse("2026-01-01T03:00:00Z"));
        resolved.setDraftAction("ACCEPTED");
        ticketRepository.save(resolved);

        Ticket edited = ticket(TicketStatus.RESOLVED, 1L, 12L);
        edited.setResolvedAt(Instant.parse("2026-01-01T05:00:00Z"));
        edited.setDraftAction("EDITED");
        ticketRepository.save(edited);

        Ticket open = ticket(TicketStatus.ASSIGNED, 2L, 11L);
        open.setPriority("CRITICAL");
        ticketRepository.save(open);

        assertThat(analyticsRepository.countResolved(TicketStatus.RESOLVED)).isEqualTo(2);
        assertThat(analyticsRepository.countUnresolved(TicketStatus.RESOLVED)).isEqualTo(1);

        Map<Long, Long> openByTeam = analyticsRepository.openCountByTeam(TicketStatus.RESOLVED).stream()
                .collect(java.util.stream.Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));
        assertThat(openByTeam).contains(entry(2L, 1L));

        Map<Long, Long> solvedByEmployee = analyticsRepository
                .resolvedCountByEmployee(TicketStatus.RESOLVED, null).stream()
                .collect(java.util.stream.Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));
        assertThat(solvedByEmployee).contains(entry(11L, 1L), entry(12L, 1L));

        Map<String, Long> openByPriority = analyticsRepository
                .openCountByPriority(TicketStatus.RESOLVED).stream()
                .collect(java.util.stream.Collectors.toMap(row -> String.valueOf(row[0]), row -> (Long) row[1]));
        assertThat(openByPriority).contains(entry("CRITICAL", 1L));

        Map<String, Long> draftActions = analyticsRepository.countByDraftAction().stream()
                .collect(java.util.stream.Collectors.toMap(row -> String.valueOf(row[0]), row -> (Long) row[1]));
        assertThat(draftActions).contains(entry("ACCEPTED", 1L), entry("EDITED", 1L));
    }

    @Test
    void resolvedRowsRespectTheRangeCutoff() {
        Ticket inRange = ticket(TicketStatus.RESOLVED, 1L, 11L);
        inRange.setResolvedAt(Instant.parse("2026-01-01T04:00:00Z"));
        ticketRepository.save(inRange);

        Ticket outOfRange = ticket(TicketStatus.RESOLVED, 1L, 11L);
        outOfRange.setResolvedAt(Instant.parse("2025-11-01T04:00:00Z"));
        ticketRepository.save(outOfRange);

        assertThat(analyticsRepository.resolvedRows(TicketStatus.RESOLVED, null)).hasSize(2);

        Instant cutoff = Instant.parse("2025-12-01T00:00:00Z");
        List<Object[]> rows = analyticsRepository.resolvedRows(TicketStatus.RESOLVED, cutoff);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)[3]).isEqualTo(Instant.parse("2026-01-01T04:00:00Z"));
        assertThat(analyticsRepository.resolvedCountByEmployee(TicketStatus.RESOLVED, cutoff))
                .hasSize(1);
    }

    @Test
    void knowledgeGapsOnlyReturnsTicketsTheCouldNotAnswer() {
        Ticket gap = ticket(TicketStatus.ASSIGNED, 1L, 11L);
        gap.setKnowledgeGap(true);
        ticketRepository.save(gap);

        Ticket answered = ticket(TicketStatus.ASSIGNED, 1L, 11L);
        answered.setKnowledgeGap(false);
        ticketRepository.save(answered);

        assertThat(analyticsRepository.findKnowledgeGaps()).hasSize(1);
    }
}
