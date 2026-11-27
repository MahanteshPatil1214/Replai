package com.replai.tickets.repository;

import com.replai.tickets.model.SlaPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SlaPolicyRepository extends JpaRepository<SlaPolicy, String> {

    Optional<SlaPolicy> findByPriority(String priority);
}
