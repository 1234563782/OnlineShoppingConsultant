package com.onlineshopping.inventory.service;

import com.onlineshopping.inventory.model.InventoryEntity;
import com.onlineshopping.inventory.repo.InventoryRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class InventoryService {

    private final InventoryRepository inventoryRepository;

    public InventoryService(InventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

    public Optional<InventoryEntity> findBySkuId(String skuId) {
        return inventoryRepository.findById(skuId);
    }
}
