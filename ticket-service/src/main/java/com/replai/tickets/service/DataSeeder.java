package com.replai.tickets.service;

import com.replai.tickets.model.CategoryRouting;
import com.replai.tickets.model.Employee;
import com.replai.tickets.model.EmployeeRole;
import com.replai.tickets.model.SlaPolicy;
import com.replai.tickets.model.Team;
import com.replai.tickets.repository.CategoryRoutingRepository;
import com.replai.tickets.repository.EmployeeRepository;
import com.replai.tickets.repository.SlaPolicyRepository;
import com.replai.tickets.repository.TeamRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Seeds the reference data the spec requires on startup: 4 teams, 8 employees,
 * the category routing table and the SLA policies. Everything is idempotent, so
 * restarting the service never duplicates a row.
 */
@Component
public class DataSeeder {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final TeamRepository teamRepository;
    private final EmployeeRepository employeeRepository;
    private final CategoryRoutingRepository categoryRoutingRepository;
    private final SlaPolicyRepository slaPolicyRepository;

    public DataSeeder(
            TeamRepository teamRepository,
            EmployeeRepository employeeRepository,
            CategoryRoutingRepository categoryRoutingRepository,
            SlaPolicyRepository slaPolicyRepository) {
        this.teamRepository = teamRepository;
        this.employeeRepository = employeeRepository;
        this.categoryRoutingRepository = categoryRoutingRepository;
        this.slaPolicyRepository = slaPolicyRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seed() {
        seedTeamsAndEmployees();
        seedCategoryRouting();
        seedSlaPolicies();
    }

    private void seedTeamsAndEmployees() {
        if (teamRepository.count() > 0) {
            return;
        }

        Team software = teamRepository.save(new Team("Software Team"));
        Team infrastructure = teamRepository.save(new Team("Infrastructure Team"));
        Team billing = teamRepository.save(new Team("Billing Team"));
        Team support = teamRepository.save(new Team("Support Team"));

        List<Employee> employees = List.of(
                new Employee("Asha Kumar", "asha.kumar@replai.dev", software.getId(), EmployeeRole.LEAD),
                new Employee("Ravi Sharma", "ravi.sharma@replai.dev", software.getId(), EmployeeRole.AGENT),
                new Employee("Meera Patel", "meera.patel@replai.dev", infrastructure.getId(), EmployeeRole.LEAD),
                new Employee("Vikram Rao", "vikram.rao@replai.dev", infrastructure.getId(), EmployeeRole.AGENT),
                new Employee("Sneha Iyer", "sneha.iyer@replai.dev", billing.getId(), EmployeeRole.LEAD),
                new Employee("Arjun Nair", "arjun.nair@replai.dev", billing.getId(), EmployeeRole.AGENT),
                new Employee("Priya Das", "priya.das@replai.dev", support.getId(), EmployeeRole.LEAD),
                new Employee("Karan Mehta", "karan.mehta@replai.dev", support.getId(), EmployeeRole.AGENT));
        employeeRepository.saveAll(employees);

        log.info("Seeded 4 teams and {} employees", employees.size());
    }

    private void seedCategoryRouting() {
        if (categoryRoutingRepository.count() > 0) {
            return;
        }

        categoryRoutingRepository.save(new CategoryRouting("Software", teamId("Software Team")));
        categoryRoutingRepository.save(new CategoryRouting("Infrastructure", teamId("Infrastructure Team")));
        categoryRoutingRepository.save(new CategoryRouting("Billing", teamId("Billing Team")));
        categoryRoutingRepository.save(new CategoryRouting("Account", teamId("Support Team")));
        categoryRoutingRepository.save(new CategoryRouting("General", teamId("Support Team")));

        log.info("Seeded category routing");
    }

    private void seedSlaPolicies() {
        if (slaPolicyRepository.count() > 0) {
            return;
        }

        slaPolicyRepository.save(new SlaPolicy("CRITICAL", 4));
        slaPolicyRepository.save(new SlaPolicy("HIGH", 24));
        slaPolicyRepository.save(new SlaPolicy("MEDIUM", 72));
        slaPolicyRepository.save(new SlaPolicy("LOW", 168));

        log.info("Seeded SLA policies");
    }

    private Long teamId(String name) {
        return teamRepository.findByName(name)
                .map(Team::getId)
                .orElseThrow(() -> new IllegalStateException("team missing during seed: " + name));
    }
}
