package com.onlineshopping.inventory.repo;

import com.onlineshopping.inventory.model.InventoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryRepository extends JpaRepository<InventoryEntity, String> {
}
