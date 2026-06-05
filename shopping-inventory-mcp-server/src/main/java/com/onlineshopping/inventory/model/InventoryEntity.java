package com.onlineshopping.inventory.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "product_inventory")
public class InventoryEntity {

    @Id
    @Column(name = "sku_id", nullable = false, length = 32)
    private String skuId;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    public String getSkuId() {
        return skuId;
    }

    public void setSkuId(String skuId) {
        this.skuId = skuId;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }
}
