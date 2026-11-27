package com.replai.tickets.scheduler;

import com.replai.tickets.model.TicketStatus;
import com.replai.tickets.repository.TicketRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Runs every minute and flags tickets that blew past their SLA deadline
 * (project.md section 6). The UI turns `slaBreached` into a red badge.
 */
@Component
public class SlaBreachJob {

    private static final Logger log = LoggerFactory.getLogger(SlaBreachJob.class);

    private final TicketRepository ticketRepository;

    public SlaBreachJob(TicketRepository ticketRepository) {
        this.ticketRepository = ticketRepository;
    }

    @Scheduled(fixedDelayString = "${replai.sla-breach-ms:60000}")
    public void flagBreachedTickets() {
        var overdue = ticketRepository.findSlaBreaches(Instant.now(), TicketStatus.RESOLVED);
        if (overdue.isEmpty()) {
            return;
        }

        overdue.forEach(ticket -> ticket.setSlaBreached(true));
        ticketRepository.saveAll(overdue);
        log.info("SLA breached on {} ticket(s): {}", overdue.size(),
                overdue.stream().map(ticket -> "#" + ticket.getId()).toList());
    }
}
