package com.replai.tickets.service;

import com.replai.tickets.model.CategoryRouting;
import com.replai.tickets.model.Employee;
import com.replai.tickets.model.EmployeeRole;
import com.replai.tickets.model.SlaPolicy;
import com.replai.tickets.model.Ticket;
import com.replai.tickets.model.TicketStatus;
import com.replai.tickets.repository.CategoryRoutingRepository;
import com.replai.tickets.repository.EmployeeRepository;
import com.replai.tickets.repository.SlaPolicyRepository;
import com.replai.tickets.repository.TicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

class RoutingServiceTest {

    private CategoryRoutingRepository categoryRoutingRepository;
    private EmployeeRepository employeeRepository;
    private SlaPolicyRepository slaPolicyRepository;
    private TicketRepository ticketRepository;
    private RoutingService routingService;

    @BeforeEach
    void setUp() {
        categoryRoutingRepository = Mockito.mock(CategoryRoutingRepository.class);
        employeeRepository = Mockito.mock(EmployeeRepository.class);
        slaPolicyRepository = Mockito.mock(SlaPolicyRepository.class);
        ticketRepository = Mockito.mock(TicketRepository.class);
        routingService = new RoutingService(
                categoryRoutingRepository, employeeRepository, slaPolicyRepository, ticketRepository);

        lenient().when(slaPolicyRepository.findByPriority(any()))
                .thenReturn(Optional.of(new SlaPolicy("MEDIUM", 72)));
        lenient().when(ticketRepository.countOpenTicketsByAssignee(anyList())).thenReturn(List.of());
    }

    private Employee employee(long id, String name, EmployeeRole role) {
        Employee employee = new Employee(name, name.replace(' ', '.') + "@replai.dev", 3L, role);
        ReflectionTestUtils.setField(employee, "id", id);
        return employee;
    }

    private Ticket confidentTicket() {
        Ticket ticket = new Ticket();
        ticket.setCustomerEmail("user@example.com");
        ticket.setSubject("Payment failed");
        ticket.setMessage("Charged twice.");
        ticket.setCategory("Billing");
        ticket.setPriority("HIGH");
        ticket.setConfidence(0.82);
        ticket.setNeedsHuman(false);
        ticket.setKnowledgeGap(false);
        return ticket;
    }

    @Test
    void confidentTicketGoesToTheLeastLoadedEmployeeOfTheTeam() {
        when(categoryRoutingRepository.findByCategory("Billing"))
                .thenReturn(Optional.of(new CategoryRouting("Billing", 3L)));
        when(employeeRepository.findByTeamIdOrderByRoleAscNameAsc(3L))
                .thenReturn(List.of(employee(11L, "Sneha Iyer", EmployeeRole.LEAD),
                        employee(12L, "Arjun Nair", EmployeeRole.AGENT)));
        when(ticketRepository.countOpenTicketsByAssignee(anyList()))
                .thenReturn(List.of(new Object[]{11L, 4L}, new Object[]{12L, 1L}));

        Ticket ticket = confidentTicket();
        routingService.route(ticket);

        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.ASSIGNED);
        assertThat(ticket.getTeamId()).isEqualTo(3L);
        assertThat(ticket.getAssigneeId()).isEqualTo(12L);   // only 1 open ticket
        assertThat(ticket.getAssignedAt()).isNotNull();
    }

    @Test
    void lowConfidenceGoesToTheTriageQueue() {
        Ticket ticket = confidentTicket();
        ticket.setNeedsHuman(true);

        routingService.route(ticket);

        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.NEEDS_TRIAGE);
        assertThat(ticket.getTeamId()).isNull();
        assertThat(ticket.getAssigneeId()).isNull();
        // the SLA counter still starts, so a triaged ticket cannot sit forever
        assertThat(ticket.getSlaDueAt()).isNotNull();
    }

    @Test
    void unknownCategoryGoesToTheTriageQueue() {
        when(categoryRoutingRepository.findByCategory("Nonsense")).thenReturn(Optional.empty());

        Ticket ticket = confidentTicket();
        ticket.setCategory("Nonsense");

        routingService.route(ticket);

        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.NEEDS_TRIAGE);
    }

    @Test
    void teamWithoutEmployeesGoesToTheTriageQueue() {
        when(categoryRoutingRepository.findByCategory("Billing"))
                .thenReturn(Optional.of(new CategoryRouting("Billing", 3L)));
        when(employeeRepository.findByTeamIdOrderByRoleAscNameAsc(3L)).thenReturn(List.of());

        Ticket ticket = confidentTicket();
        routingService.route(ticket);

        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.NEEDS_TRIAGE);
    }

    @Test
    void slaDeadlineComesFromThePriorityPolicy() {
        when(slaPolicyRepository.findByPriority("CRITICAL"))
                .thenReturn(Optional.of(new SlaPolicy("CRITICAL", 4)));
        Instant from = Instant.parse("2026-01-01T00:00:00Z");

        assertThat(routingService.slaDueAt("CRITICAL", from)).isEqualTo(from.plus(Duration.ofHours(4)));
        assertThat(routingService.slaDueAt("unknown", from)).isEqualTo(from.plus(Duration.ofHours(72)));
    }

    @Test
    void handStuckTicketToTriageUsesMediumPriorityAndALastDeadline() {
        when(slaPolicyRepository.findByPriority("MEDIUM"))
                .thenReturn(Optional.of(new SlaPolicy("MEDIUM", 72)));

        Ticket ticket = new Ticket();   // never got an AI answer

        routingService.handStuckTicketToTriage(ticket);

        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.NEEDS_TRIAGE);
        assertThat(ticket.getPriority()).isEqualTo("MEDIUM");
        assertThat(ticket.getSlaDueAt()).isNotNull();
        assertThat(ticket.getSlaDueAt()).isAfter(Instant.now());
    }

    @Test
    void manualAssignmentChecksThatTheEmployeeBelongsToTheTeam() {
        Employee employee = employee(12L, "Arjun Nair", EmployeeRole.AGENT);
        when(employeeRepository.findById(12L)).thenReturn(Optional.of(employee));

        Ticket ticket = new Ticket();
        assertThat(routingService.assign(ticket, 3L, 12L)).isTrue();
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.ASSIGNED);
        assertThat(ticket.getTeamId()).isEqualTo(3L);
        assertThat(ticket.getSlaDueAt()).isNotNull();

        assertThat(routingService.assign(ticket, 4L, 12L)).isFalse();
        assertThat(routingService.assign(ticket, 3L, 999L)).isFalse();
    }
}
