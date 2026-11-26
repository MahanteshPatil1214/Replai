package com.replai.tickets.service;

import com.replai.tickets.dto.CreateTicketRequest;
import com.replai.tickets.dto.TicketResponse;
import com.replai.tickets.model.Ticket;
import com.replai.tickets.model.TicketStatus;
import com.replai.tickets.repository.TicketRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TicketService {

    private static final Logger log = LoggerFactory.getLogger(TicketService.class);

    private final TicketRepository ticketRepository;

    public TicketService(TicketRepository ticketRepository) {
        this.ticketRepository = ticketRepository;
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
        return TicketResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<TicketResponse> findAll() {
        return ticketRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(TicketResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public TicketResponse findById(Long id) {
        return TicketResponse.from(getOrThrow(id));
    }

    @Transactional
    public TicketResponse updateStatus(Long id, String status) {
        Ticket ticket = getOrThrow(id);
        ticket.setStatus(TicketStatus.valueOf(status));
        return TicketResponse.from(ticketRepository.save(ticket));
    }

    protected Ticket getOrThrow(Long id) {
        return ticketRepository.findById(id)
                .orElseThrow(() -> new TicketNotFoundException(id));
    }
}
