package com.replai.tickets.repository;

import com.replai.tickets.model.Employee;
import com.replai.tickets.model.EmployeeRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    List<Employee> findByTeamIdOrderByRoleAscNameAsc(Long teamId);

    List<Employee> findAllByOrderByTeamIdAscNameAsc();

    Optional<Employee> findByEmail(String email);

    List<Employee> findByRole(EmployeeRole role);
}
