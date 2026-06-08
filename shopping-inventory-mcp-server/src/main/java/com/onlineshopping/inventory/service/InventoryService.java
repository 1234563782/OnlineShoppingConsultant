package com.onlineshopping.inventory.service;

import com.onlineshopping.inventory.mapper.InventoryMapper;
import com.onlineshopping.inventory.model.InventoryEntity;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class InventoryService {

    private final InventoryMapper inventoryMapper;

    public InventoryService(InventoryMapper inventoryMapper) {
        this.inventoryMapper = inventoryMapper;
    }

    public Optional<InventoryEntity> findBySkuId(String skuId) {
        return Optional.ofNullable(inventoryMapper.selectById(skuId));
    }
}
