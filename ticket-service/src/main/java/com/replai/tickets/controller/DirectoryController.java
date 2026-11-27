package com.replai.tickets.controller;

import com.replai.tickets.dto.EmployeeResponse;
import com.replai.tickets.dto.TeamResponse;
import com.replai.tickets.service.DirectoryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** GET /api/teams and GET /api/employees. */
@RestController
@RequestMapping("/api")
public class DirectoryController {

    private final DirectoryService directoryService;

    public DirectoryController(DirectoryService directoryService) {
        this.directoryService = directoryService;
    }

    @GetMapping("/teams")
    public List<TeamResponse> teams() {
        return directoryService.listTeams();
    }

    @GetMapping("/employees")
    public List<EmployeeResponse> employees(@RequestParam(required = false) Long team) {
        return directoryService.listEmployees(team);
    }
}
