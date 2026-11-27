package com.replai.tickets.service;

import com.replai.tickets.model.Employee;
import com.replai.tickets.model.SlaPolicy;
import com.replai.tickets.model.Ticket;
import com.replai.tickets.model.TicketStatus;
import com.replai.tickets.repository.CategoryRoutingRepository;
import com.replai.tickets.repository.EmployeeRepository;
import com.replai.tickets.repository.SlaPolicyRepository;
import com.replai.tickets.repository.TicketRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Auto triage: decide whether a ticket goes to a team or to the triage queue,
 * pick the least-loaded employee, and start the SLA counter (project.md 5-6).
 */
@Service
public class RoutingService {

    private static final Logger log = LoggerFactory.getLogger(RoutingService.class);

    /** Statuses that count as "on someone's desk" when balancing the load. */
    private static final List<TicketStatus> OPEN_STATUSES =
            List.of(TicketStatus.ASSIGNED, TicketStatus.IN_PROGRESS);

    private final CategoryRoutingRepository categoryRoutingRepository;
    private final EmployeeRepository employeeRepository;
    private final SlaPolicyRepository slaPolicyRepository;
    private final TicketRepository ticketRepository;

    public RoutingService(
            CategoryRoutingRepository categoryRoutingRepository,
            EmployeeRepository employeeRepository,
            SlaPolicyRepository slaPolicyRepository,
            TicketRepository ticketRepository) {
        this.categoryRoutingRepository = categoryRoutingRepository;
        this.employeeRepository = employeeRepository;
        this.slaPolicyRepository = slaPolicyRepository;
        this.ticketRepository = ticketRepository;
    }

    /**
     * Route a freshly enriched ticket: SLA first (always set from priority),
     * then either the triage queue or the least-loaded employee of the team.
     */
    public void route(Ticket ticket) {
        Instant now = Instant.now();
        ticket.setSlaDueAt(slaDueAt(ticket.getPriority(), now));

        if (needsHuman(ticket)) {
            sendToTriage(ticket, "needsHuman or low confidence");
            return;
        }

        Optional<Long> teamId = teamForCategory(ticket.getCategory());
        if (teamId.isEmpty()) {
            sendToTriage(ticket, "unknown category '" + ticket.getCategory() + "'");
            return;
        }

        Employee employee = leastLoadedEmployee(teamId.get());
        if (employee == null) {
            sendToTriage(ticket, "no employees in team " + teamId.get());
            return;
        }

        ticket.setTeamId(teamId.get());
        ticket.setAssigneeId(employee.getId());
        ticket.setStatus(TicketStatus.ASSIGNED);
        ticket.setAssignedAt(now);
        log.info("Ticket {} routed to {} (team {}, confidence {})",
                ticket.getId(), employee.getName(), teamId.get(), ticket.getConfidence());
    }

    /**
     * Manual assignment from the triage board: a team lead picks the team and
     * the employee. Returns false when either id does not exist.
     */
    public boolean assign(Ticket ticket, Long teamId, Long employeeId) {
        if (teamId == null || employeeId == null) {
            return false;
        }
        boolean exists = employeeRepository.findById(employeeId)
                .map(employee -> employee.getTeamId().equals(teamId))
                .orElse(false);
        if (!exists) {
            return false;
        }

        ticket.setTeamId(teamId);
        ticket.setAssigneeId(employeeId);
        ticket.setStatus(TicketStatus.ASSIGNED);
        ticket.setAssignedAt(Instant.now());
        if (ticket.getSlaDueAt() == null) {
            ticket.setSlaDueAt(slaDueAt(ticket.getPriority(), Instant.now()));
        }
        return true;
    }

    /** Deadline = now + resolve_within_hours for the priority (unknown -> 3 days). */
    public Instant slaDueAt(String priority, Instant from) {
        Duration within = slaPolicyRepository.findByPriority(priority)
                .map(SlaPolicy::getResolveWithinHours)
                .map(hours -> Duration.ofHours(Math.max(hours, 1)))
                .orElse(Duration.ofHours(72));
        return from.plus(within);
    }

    /**
     * Safety net when ai-service is down: a ticket still OPEN after the timeout
     * goes to the triage queue with MEDIUM priority and a fresh SLA deadline,
     * so nothing is ever lost (project.md end-to-end flow).
     */
    public void handStuckTicketToTriage(Ticket ticket) {
        ticket.setPriority("MEDIUM");
        ticket.setSlaDueAt(slaDueAt("MEDIUM", Instant.now()));
        sendToTriage(ticket, "stuck in OPEN without an AI answer");
    }

    private boolean needsHuman(Ticket ticket) {
        return Boolean.TRUE.equals(ticket.getNeedsHuman());
    }

    private void sendToTriage(Ticket ticket, String reason) {
        ticket.setTeamId(null);
        ticket.setAssigneeId(null);
        ticket.setStatus(TicketStatus.NEEDS_TRIAGE);
        ticket.setAssignedAt(null);
        log.info("Ticket {} sent to triage: {}", ticket.getId(), reason);
    }

    private Optional<Long> teamForCategory(String category) {
        if (category == null || category.isBlank()) {
            return Optional.empty();
        }
        return categoryRoutingRepository.findByCategory(category.trim()).map(routing -> routing.getTeamId());
    }

    /**
     * Employee of the team with the fewest open tickets. Ties are broken by
     * role (lead first) and then by name, so the choice is deterministic.
     */
    public Employee leastLoadedEmployee(Long teamId) {
        List<Employee> employees = employeeRepository.findByTeamIdOrderByRoleAscNameAsc(teamId);
        if (employees.isEmpty()) {
            return null;
        }

        Map<Long, Long> openCounts = new HashMap<>();
        for (Object[] row : ticketRepository.countOpenTicketsByAssignee(OPEN_STATUSES)) {
            openCounts.put((Long) row[0], (Long) row[1]);
        }

        return employees.stream()
                .min((left, right) -> {
                    long leftLoad = openCounts.getOrDefault(left.getId(), 0L);
                    long rightLoad = openCounts.getOrDefault(right.getId(), 0L);
                    return Long.compare(leftLoad, rightLoad);
                })
                .orElse(null);
    }
}
