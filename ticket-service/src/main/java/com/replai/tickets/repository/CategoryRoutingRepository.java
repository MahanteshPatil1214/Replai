package com.replai.tickets.repository;

import com.replai.tickets.model.CategoryRouting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CategoryRoutingRepository extends JpaRepository<CategoryRouting, String> {

    Optional<CategoryRouting> findByCategory(String category);
}
