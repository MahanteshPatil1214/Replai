package com.replai.tickets.service;

import com.replai.tickets.dto.CreateTicketRequest;
import com.replai.tickets.dto.TicketResponse;
import com.replai.tickets.model.Ticket;
import com.replai.tickets.model.TicketStatus;
import com.replai.tickets.repository.TicketRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class TicketServiceTest {

    private final TicketRepository repository = Mockito.mock(TicketRepository.class);
    private final TicketService service = new TicketService(repository);

    @Test
    void createSavesAnOpenTicket() {
        when(repository.save(any(Ticket.class))).thenAnswer(invocation -> {
            Ticket ticket = invocation.getArgument(0);
            return ticket;
        });

        TicketResponse response = service.create(
                new CreateTicketRequest("user@example.com", "Payment failed", "Charged twice."));

        assertThat(response.status()).isEqualTo(TicketStatus.OPEN.name());
        assertThat(response.customerEmail()).isEqualTo("user@example.com");
        assertThat(response.subject()).isEqualTo("Payment failed");
        assertThat(response.category()).isNull();
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
        when(repository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TicketResponse response = service.updateStatus(1L, "RESOLVED");

        assertThat(response.status()).isEqualTo("RESOLVED");
    }
}
