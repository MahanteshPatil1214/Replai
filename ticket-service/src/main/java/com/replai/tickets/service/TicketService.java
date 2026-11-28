package com.replai.tickets.service;

import com.replai.tickets.dto.CreateTicketRequest;
import com.replai.tickets.dto.ResolveTicketRequest;
import com.replai.tickets.dto.TicketResponse;
import com.replai.tickets.kafka.TicketCreatedEvent;
import com.replai.tickets.kafka.TicketEventProducer;
import com.replai.tickets.model.Ticket;
import com.replai.tickets.model.TicketStatus;
import com.replai.tickets.repository.TicketRepository;
import com.replai.tickets.security.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class TicketService {

    private static final Logger log = LoggerFactory.getLogger(TicketService.class);

    private final TicketRepository ticketRepository;
    private final TicketEventProducer eventProducer;
    private final RoutingService routingService;

    public TicketService(
            TicketRepository ticketRepository,
            TicketEventProducer eventProducer,
            RoutingService routingService) {
        this.ticketRepository = ticketRepository;
        this.eventProducer = eventProducer;
        this.routingService = routingService;
    }

    @Transactional
    public TicketResponse create(CreateTicketRequest request) {
        Ticket ticket = new Ticket();
        ticket.setCustomerEmail(request.customerEmail().trim());
        ticket.setSubject(request.subject().trim());
        ticket.setMessage(request.message().trim());
        ticket.setStatus(TicketStatus.OPEN);

        Ticket saved = ticketRepository.save(ticket);
        log.info("Created ticket {}", saved.getId());

        eventProducer.publishTicketCreated(new TicketCreatedEvent(
                UUID.randomUUID().toString(),
                String.valueOf(saved.getId()),
                saved.getCustomerEmail(),
                saved.getSubject(),
                saved.getMessage(),
                saved.getCreatedAt()
        ));

        return TicketResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<TicketResponse> findAll(String status, Long team, String priority, Long assignee) {
        return ticketRepository.search(
                        parseStatus(status),
                        team,
                        priority == null || priority.isBlank() ? null : priority.trim().toUpperCase(),
                        assignee)
                .stream()
                .map(TicketResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public TicketResponse findById(Long id) {
        return TicketResponse.from(getOrThrow(id));
    }

    /**
     * Manual assignment from the triage board: the ticket keeps its AI result
     * but moves to a team and an employee chosen by a lead.
     */
    @Transactional
    public TicketResponse assign(Long id, Long teamId, Long employeeId) {
        Ticket ticket = getOrThrow(id);
        if (!routingService.assign(ticket, teamId, employeeId)) {
            throw new InvalidAssignmentException(teamId, employeeId);
        }
        return TicketResponse.from(ticketRepository.save(ticket));
    }

    @Transactional
    public TicketResponse resolve(Long id, ResolveTicketRequest request, CurrentUser user) {
        Ticket ticket = getOrThrow(id);
        assertCanWorkOn(ticket, user);
        Instant now = Instant.now();

        ticket.setStatus(TicketStatus.RESOLVED);
        ticket.setFinalReply(request.finalReply().trim());
        ticket.setDraftAction(request.draftAction());
        // The owner comes from the JWT, never from the request body.
        ticket.setResolvedBy(user.id());
        ticket.setResolvedAt(now);

        return TicketResponse.from(ticketRepository.save(ticket));
    }

    @Transactional
    public TicketResponse updateStatus(Long id, String status, CurrentUser user) {
        Ticket ticket = getOrThrow(id);
        assertCanWorkOn(ticket, user);
        ticket.setStatus(TicketStatus.valueOf(status));
        return TicketResponse.from(ticketRepository.save(ticket));
    }

    /**
     * RBAC rule for ticket work: an AGENT may only touch a ticket that is
     * assigned to them. Leads and admins can work any ticket.
     */
    private void assertCanWorkOn(Ticket ticket, CurrentUser user) {
        if (user.isLeadOrAdmin()) {
            return;
        }
        if (ticket.getAssigneeId() != null && ticket.getAssigneeId().equals(user.id())) {
            return;
        }
        throw new ForbiddenException("agents can only work tickets assigned to them");
    }

    private TicketStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return TicketStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "unknown status '" + status + "' - use OPEN, NEEDS_TRIAGE, ASSIGNED, IN_PROGRESS or RESOLVED");
        }
    }

    protected Ticket getOrThrow(Long id) {
        return ticketRepository.findById(id)
                .orElseThrow(() -> new TicketNotFoundException(id));
    }
}
