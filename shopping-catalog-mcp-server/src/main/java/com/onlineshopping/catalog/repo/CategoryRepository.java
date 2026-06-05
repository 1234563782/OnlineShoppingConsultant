package com.onlineshopping.catalog.repo;

import com.onlineshopping.catalog.model.CategoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<CategoryEntity, String> {

    List<CategoryEntity> findByEnabledTrue();

    Optional<CategoryEntity> findByNameAndEnabledTrue(String name);
}
