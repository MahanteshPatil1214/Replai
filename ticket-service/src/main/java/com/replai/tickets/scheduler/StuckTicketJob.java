package com.replai.tickets.scheduler;

import com.replai.tickets.model.TicketStatus;
import com.replai.tickets.repository.TicketRepository;
import com.replai.tickets.service.RoutingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Safety net for a dead or slow ai-service: a ticket that is still OPEN after
 * two minutes is moved to the triage queue with MEDIUM priority, so a customer
 * is never left waiting on an event that never arrives (project.md flow).
 */
@Component
public class StuckTicketJob {

    private static final Logger log = LoggerFactory.getLogger(StuckTicketJob.class);

    private final TicketRepository ticketRepository;
    private final RoutingService routingService;
    private final int stuckAfterMinutes;

    public StuckTicketJob(
            TicketRepository ticketRepository,
            RoutingService routingService,
            @Value("${replai.stuck-after-minutes:2}") int stuckAfterMinutes) {
        this.ticketRepository = ticketRepository;
        this.routingService = routingService;
        this.stuckAfterMinutes = stuckAfterMinutes;
    }

    @Scheduled(fixedDelayString = "${replai.stuck-ticket-ms:60000}")
    public void rescueStuckTickets() {
        Instant cutoff = Instant.now().minus(stuckAfterMinutes, ChronoUnit.MINUTES);
        var stuck = ticketRepository.findOlderThan(TicketStatus.OPEN, cutoff);
        if (stuck.isEmpty()) {
            return;
        }

        stuck.forEach(routingService::handStuckTicketToTriage);
        ticketRepository.saveAll(stuck);
        log.warn("Moved {} ticket(s) stuck in OPEN for over {} minutes to the triage queue",
                stuck.size(), stuckAfterMinutes);
    }
}
