package com.replai.tickets.controller;

import com.replai.tickets.dto.AssignRequest;
import com.replai.tickets.dto.CreateTicketRequest;
import com.replai.tickets.dto.ResolveTicketRequest;
import com.replai.tickets.dto.TicketResponse;
import com.replai.tickets.dto.UpdateStatusRequest;
import com.replai.tickets.security.CurrentUser;
import com.replai.tickets.service.TicketService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/tickets")
public class TicketController {

    private final TicketService ticketService;

    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    /** Public: the customer form does not log in. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TicketResponse create(@Valid @RequestBody CreateTicketRequest request) {
        return ticketService.create(request);
    }

    @GetMapping
    public List<TicketResponse> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long team,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) Long assignee) {
        return ticketService.findAll(status, team, priority, assignee);
    }

    @GetMapping("/{id}")
    public TicketResponse get(@PathVariable Long id) {
        return ticketService.findById(id);
    }

    /** Restricted to LEAD and ADMIN in SecurityConfig (the triage board). */
    @PatchMapping("/{id}/assign")
    public TicketResponse assign(@PathVariable Long id, @Valid @RequestBody AssignRequest request) {
        return ticketService.assign(id, request.teamId(), request.assigneeId());
    }

    @PostMapping("/{id}/resolve")
    public TicketResponse resolve(
            @PathVariable Long id,
            @Valid @RequestBody ResolveTicketRequest request,
            @AuthenticationPrincipal CurrentUser user) {
        return ticketService.resolve(id, request, user);
    }

    @PatchMapping("/{id}/status")
    public TicketResponse updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateStatusRequest request,
            @AuthenticationPrincipal CurrentUser user) {
        return ticketService.updateStatus(id, request.status(), user);
    }
}
