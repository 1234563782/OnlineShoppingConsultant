package com.onlineshopping.catalog.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "product_category")
public class CategoryEntity {

    @Id
    @Column(name = "category_id", nullable = false, length = 64)
    private String categoryId;

    @Column(name = "name", nullable = false, length = 64)
    private String name;

    @Column(name = "parent_id", length = 64)
    private String parentId;

    @Column(name = "aliases", length = 512)
    private String aliases;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled = true;

    public String getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(String categoryId) {
        this.categoryId = categoryId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getParentId() {
        return parentId;
    }

    public void setParentId(String parentId) {
        this.parentId = parentId;
    }

    public String getAliases() {
        return aliases;
    }

    public void setAliases(String aliases) {
        this.aliases = aliases;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }
}
