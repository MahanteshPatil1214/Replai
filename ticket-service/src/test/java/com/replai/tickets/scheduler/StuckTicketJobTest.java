package com.replai.tickets.scheduler;

import com.replai.tickets.model.Ticket;
import com.replai.tickets.model.TicketStatus;
import com.replai.tickets.repository.TicketRepository;
import com.replai.tickets.service.RoutingService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StuckTicketJobTest {

    private final TicketRepository repository = Mockito.mock(TicketRepository.class);
    private final RoutingService routingService = Mockito.mock(RoutingService.class);
    private final StuckTicketJob job = new StuckTicketJob(repository, routingService, 2);

    @Test
    void movesStuckOpenTicketsToTriageWithMediumPriority() {
        Ticket stuck = new Ticket();
        stuck.setStatus(TicketStatus.OPEN);
        when(repository.findOlderThan(Mockito.eq(TicketStatus.OPEN), any())).thenReturn(List.of(stuck));

        job.rescueStuckTickets();

        verify(routingService).handStuckTicketToTriage(stuck);
        verify(repository).saveAll(List.of(stuck));
    }

    @Test
    void doesNothingWhenNoTicketIsStuck() {
        when(repository.findOlderThan(Mockito.eq(TicketStatus.OPEN), any())).thenReturn(List.of());

        job.rescueStuckTickets();

        verify(routingService, never()).handStuckTicketToTriage(any());
        verify(repository, never()).saveAll(any());
    }
}
