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

class TicketRepositoryTest extends RepositoryTest {

    @Autowired
    private TicketRepository repository;

    @BeforeEach
    void cleanUp() {
        repository.deleteAll();
    }

    private Ticket ticket(String subject, String priority, TicketStatus status, Long teamId, Instant createdAt) {
        Ticket ticket = new Ticket();
        ticket.setCustomerEmail("user@example.com");
        ticket.setSubject(subject);
        ticket.setMessage("message");
        ticket.setPriority(priority);
        ticket.setStatus(status);
        ticket.setTeamId(teamId);
        ticket.setCreatedAt(createdAt);
        return repository.save(ticket);
    }

    @Test
    void searchOrdersByPriorityThenOldestFirst() {
        Instant base = Instant.parse("2026-01-01T00:00:00Z");
        ticket("low", "LOW", TicketStatus.ASSIGNED, 1L, base);
        ticket("critical", "CRITICAL", TicketStatus.ASSIGNED, 1L, base.plusSeconds(60));
        ticket("high", "HIGH", TicketStatus.ASSIGNED, 1L, base.plusSeconds(30));
        ticket("medium", "MEDIUM", TicketStatus.ASSIGNED, 1L, base.plusSeconds(90));

        List<String> subjects = repository.search(TicketStatus.ASSIGNED, 1L, null, null).stream()
                .map(Ticket::getSubject)
                .toList();

        assertThat(subjects).containsExactly("critical", "high", "medium", "low");
    }

    @Test
    void searchAppliesEveryFilter() {
        Instant base = Instant.parse("2026-01-01T00:00:00Z");
        ticket("mine", "HIGH", TicketStatus.ASSIGNED, 1L, base);
        ticket("other team", "HIGH", TicketStatus.ASSIGNED, 2L, base);
        ticket("other priority", "LOW", TicketStatus.ASSIGNED, 1L, base);
        ticket("other status", "HIGH", TicketStatus.NEEDS_TRIAGE, 1L, base);

        List<String> subjects = repository.search(TicketStatus.ASSIGNED, 1L, "HIGH", null).stream()
                .map(Ticket::getSubject)
                .toList();

        assertThat(subjects).containsExactly("mine");

        // an assignee that owns nothing filters the rest away
        assertThat(repository.search(TicketStatus.ASSIGNED, 1L, "HIGH", 7L)).isEmpty();
    }

    @Test
    void searchWithoutFiltersReturnsEverythingByPriority() {
        Instant base = Instant.parse("2026-01-01T00:00:00Z");
        ticket("a", "LOW", TicketStatus.OPEN, null, base);
        ticket("b", "CRITICAL", TicketStatus.OPEN, null, base);

        assertThat(repository.search(null, null, null, null)).hasSize(2);
    }

    @Test
    void countOpenTicketsByAssigneeGroupsByEmployee() {
        Instant now = Instant.now();
        Ticket first = ticket("a", "LOW", TicketStatus.ASSIGNED, 1L, now);
        first.setAssigneeId(11L);
        repository.save(first);
        ticket("b", "LOW", TicketStatus.ASSIGNED, 1L, now).setAssigneeId(11L);
        Ticket resolved = ticket("c", "LOW", TicketStatus.RESOLVED, 1L, now);
        resolved.setAssigneeId(11L);
        repository.save(resolved);
        Ticket other = ticket("d", "LOW", TicketStatus.IN_PROGRESS, 1L, now);
        other.setAssigneeId(12L);
        repository.save(other);

        Map<Long, Long> counts = repository.countOpenTicketsByAssignee(
                        List.of(TicketStatus.ASSIGNED, TicketStatus.IN_PROGRESS)).stream()
                .collect(java.util.stream.Collectors.toMap(
                        row -> (Long) row[0], row -> (Long) row[1]));

        assertThat(counts).containsEntry(11L, 2L).containsEntry(12L, 1L);
    }
}
