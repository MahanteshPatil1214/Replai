package com.replai.tickets.scheduler;

import com.replai.tickets.model.Ticket;
import com.replai.tickets.model.TicketStatus;
import com.replai.tickets.repository.TicketRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SlaBreachJobTest {

    private final TicketRepository repository = Mockito.mock(TicketRepository.class);
    private final SlaBreachJob job = new SlaBreachJob(repository);

    @Test
    void flagsOverdueTicketsAndSavesThem() {
        Ticket overdue = new Ticket();
        overdue.setSlaBreached(false);
        when(repository.findSlaBreaches(any(Instant.class), Mockito.eq(TicketStatus.RESOLVED)))
                .thenReturn(List.of(overdue));

        job.flagBreachedTickets();

        assertThat(overdue.getSlaBreached()).isTrue();
        verify(repository).saveAll(List.of(overdue));
    }

    @Test
    void doesNothingWhenEverythingIsInsideTheSla() {
        when(repository.findSlaBreaches(any(Instant.class), Mockito.eq(TicketStatus.RESOLVED)))
                .thenReturn(List.of());

        job.flagBreachedTickets();

        verify(repository, never()).saveAll(any());
    }
}
