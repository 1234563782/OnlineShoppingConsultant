package com.onlineshopping.inventory.config;

import com.onlineshopping.inventory.model.InventoryEntity;
import com.onlineshopping.inventory.repo.InventoryRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class InventoryDataInitializer implements ApplicationRunner {

    private final InventoryRepository inventoryRepository;

    public InventoryDataInitializer(InventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (inventoryRepository.count() > 0) {
            return;
        }
        inventoryRepository.saveAll(List.of(
                stock("SKU1001", 32),
                stock("SKU1002", 8),
                stock("SKU1003", 0),
                stock("SKU2001", 15),
                stock("SKU2002", 26),
                stock("SKU2003", 57),
                stock("SKU3001", 5),
                stock("SKU3002", 2),
                stock("SKU3003", 0),
                stock("SKU4001", 9),
                stock("SKU4002", 23),
                stock("SKU5001", 11),
                stock("SKU5002", 3)
        ));
    }

    private static InventoryEntity stock(String skuId, int quantity) {
        InventoryEntity entity = new InventoryEntity();
        entity.setSkuId(skuId);
        entity.setQuantity(quantity);
        return entity;
    }
}
