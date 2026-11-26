package com.replai.tickets.service;

import com.replai.tickets.kafka.TicketEnrichedEvent;
import com.replai.tickets.model.SourceRef;
import com.replai.tickets.model.Ticket;
import com.replai.tickets.model.TicketStatus;
import com.replai.tickets.repository.TicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TicketEnrichmentServiceTest {

    private TicketRepository repository;
    private TicketEnrichmentService service;
    private Ticket ticket;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(TicketRepository.class);
        service = new TicketEnrichmentService(repository);

        ticket = new Ticket();
        ticket.setCustomerEmail("user@example.com");
        ticket.setSubject("Payment failed");
        ticket.setMessage("Charged twice.");
        when(repository.findById(1L)).thenReturn(Optional.of(ticket));
        when(repository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private TicketEnrichedEvent event() {
        return new TicketEnrichedEvent(
                "evt-1",
                "1",
                "Billing",
                "HIGH",
                "NEGATIVE",
                "Charged twice.",
                "Sorry about that.",
                List.of(new SourceRef(7L, "Refunds for duplicate charges"))
        );
    }

    @Test
    void applyFillsTheAiColumnsAndMarksTheTicketEnriched() {
        boolean applied = service.apply(event());

        assertThat(applied).isTrue();
        assertThat(ticket.getCategory()).isEqualTo("Billing");
        assertThat(ticket.getPriority()).isEqualTo("HIGH");
        assertThat(ticket.getSentiment()).isEqualTo("NEGATIVE");
        assertThat(ticket.getSummary()).isEqualTo("Charged twice.");
        assertThat(ticket.getSuggestedReply()).isEqualTo("Sorry about that.");
        assertThat(ticket.getSources()).hasSize(1);
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.ENRICHED);
    }

    @Test
    void applyIsIdempotentWhenTheSameEventArrivesTwice() {
        service.apply(event());
        service.apply(event());

        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.ENRICHED);
        assertThat(ticket.getSources()).hasSize(1);
        verify(repository, Mockito.times(2)).save(ticket);
    }

    @Test
    void applySkipsUnknownTicket() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        boolean applied = service.apply(
                new TicketEnrichedEvent("e", "99", "Billing", "LOW", "NEUTRAL", "s", "r", List.of()));

        assertThat(applied).isFalse();
        verify(repository, never()).save(any());
    }

    @Test
    void applySkipsUnusableTicketId() {
        boolean applied = service.apply(
                new TicketEnrichedEvent("e", "not-a-number", "Billing", "LOW", "NEUTRAL", "s", "r", List.of()));

        assertThat(applied).isFalse();
        verify(repository, never()).save(any());
    }
}
