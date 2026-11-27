package com.replai.tickets.service;

import com.replai.tickets.dto.EmployeeResponse;
import com.replai.tickets.dto.TeamResponse;
import com.replai.tickets.repository.EmployeeRepository;
import com.replai.tickets.repository.TeamRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Read side for teams and employees (the UI needs them for routing controls). */
@Service
public class DirectoryService {

    private final TeamRepository teamRepository;
    private final EmployeeRepository employeeRepository;

    public DirectoryService(TeamRepository teamRepository, EmployeeRepository employeeRepository) {
        this.teamRepository = teamRepository;
        this.employeeRepository = employeeRepository;
    }

    @Transactional(readOnly = true)
    public List<TeamResponse> listTeams() {
        return teamRepository.findAll().stream().map(TeamResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<EmployeeResponse> listEmployees(Long teamId) {
        var employees = teamId == null
                ? employeeRepository.findAllByOrderByTeamIdAscNameAsc()
                : employeeRepository.findByTeamIdOrderByRoleAscNameAsc(teamId);
        return employees.stream().map(EmployeeResponse::from).toList();
    }
}
