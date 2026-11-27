package com.replai.tickets.repository;

import com.replai.tickets.model.Employee;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    List<Employee> findByTeamIdOrderByRoleAscNameAsc(Long teamId);

    List<Employee> findAllByOrderByTeamIdAscNameAsc();
}
