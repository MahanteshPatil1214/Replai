package com.replai.tickets.service;

import com.replai.tickets.dto.CreateTicketRequest;
import com.replai.tickets.dto.TicketResponse;
import com.replai.tickets.kafka.TicketCreatedEvent;
import com.replai.tickets.kafka.TicketEventProducer;
import com.replai.tickets.model.Ticket;
import com.replai.tickets.model.TicketStatus;
import com.replai.tickets.repository.TicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TicketServiceTest {

    private TicketRepository repository;
    private TicketEventProducer producer;
    private TicketService service;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(TicketRepository.class);
        producer = Mockito.mock(TicketEventProducer.class);
        service = new TicketService(repository, producer);

        when(repository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createSavesAnOpenTicketAndPublishesTicketCreated() {
        TicketResponse response = service.create(
                new CreateTicketRequest("user@example.com", "Payment failed", "Charged twice."));

        assertThat(response.status()).isEqualTo(TicketStatus.OPEN.name());
        assertThat(response.customerEmail()).isEqualTo("user@example.com");
        assertThat(response.category()).isNull();

        ArgumentCaptor<TicketCreatedEvent> captor = ArgumentCaptor.forClass(TicketCreatedEvent.class);
        verify(producer).publishTicketCreated(captor.capture());
        assertThat(captor.getValue().subject()).isEqualTo("Payment failed");
        assertThat(captor.getValue().eventId()).isNotBlank();
        assertThat(captor.getValue().ticketId()).isNotBlank();
    }

    @Test
    void createDoesNotPublishWhenSaveFails() {
        when(repository.save(any(Ticket.class))).thenThrow(new RuntimeException("db down"));

        assertThatThrownBy(() -> service.create(new CreateTicketRequest("a@b.com", "s", "m")))
                .isInstanceOf(RuntimeException.class);

        verify(producer, never()).publishTicketCreated(any());
    }

    @Test
    void findByIdThrowsWhenMissing() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(99L))
                .isInstanceOf(TicketNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void updateStatusPersistsNewStatus() {
        Ticket ticket = new Ticket();
        ticket.setCustomerEmail("a@b.com");
        ticket.setSubject("s");
        ticket.setMessage("m");
        when(repository.findById(1L)).thenReturn(Optional.of(ticket));

        TicketResponse response = service.updateStatus(1L, "RESOLVED");

        assertThat(response.status()).isEqualTo("RESOLVED");
    }
}
