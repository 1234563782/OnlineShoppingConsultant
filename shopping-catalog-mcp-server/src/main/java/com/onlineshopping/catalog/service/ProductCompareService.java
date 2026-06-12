package com.onlineshopping.catalog.service;

import com.onlineshopping.catalog.dto.ProductCompareRequest;
import com.onlineshopping.catalog.dto.ProductCompareResponse;
import com.onlineshopping.catalog.model.ProductEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ProductCompareService {

    private static final int MIN_TARGETS = 2;
    private static final int MAX_TARGETS = 4;

    private final CatalogService catalogService;
    private final JdbcTemplate jdbcTemplate;

    public ProductCompareService(CatalogService catalogService, JdbcTemplate jdbcTemplate) {
        this.catalogService = catalogService;
        this.jdbcTemplate = jdbcTemplate;
    }

    public ProductCompareResponse compare(ProductCompareRequest request) {
        List<String> skuIds = normalizeSkuIds(request == null ? null : request.skuIds());
        if (skuIds.size() < MIN_TARGETS) {
            return new ProductCompareResponse(
                    ProductCompareResponse.STATUS_INSUFFICIENT_TARGETS,
                    skuIds,
                    List.of(),
                    List.of(),
                    false,
                    "至少需要 2 个有效 skuId 才能对比"
            );
        }
        if (skuIds.size() > MAX_TARGETS) {
            skuIds = skuIds.subList(0, MAX_TARGETS);
        }

        List<ProductEntity> products = catalogService.findBySkuIdsPreserveOrder(skuIds);
        if (products.isEmpty()) {
            return new ProductCompareResponse(
                    ProductCompareResponse.STATUS_SKU_NOT_FOUND,
                    skuIds,
                    List.of(),
                    List.of(),
                    false,
                    "未找到可对比的商品"
            );
        }
        if (products.size() < MIN_TARGETS) {
            List<String> found = products.stream().map(ProductEntity::getSkuId).toList();
            return new ProductCompareResponse(
                    ProductCompareResponse.STATUS_INSUFFICIENT_TARGETS,
                    found,
                    List.of(),
                    List.of(),
                    false,
                    "仅找到 %d 个有效商品，无法完成对比".formatted(products.size())
            );
        }

        Set<String> categoryIds = products.stream()
                .map(ProductEntity::getCategoryId)
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        boolean crossCategory = categoryIds.size() > 1;

        Map<String, Map<String, Object>> inventoryBySku = loadInventory(skuIds);
        Map<String, List<Map<String, Object>>> promotionsBySku = loadPromotions(skuIds);

        List<Map<String, Object>> comparedProducts = new ArrayList<>();
        for (ProductEntity product : products) {
            comparedProducts.add(toCompareProduct(product, inventoryBySku, promotionsBySku));
        }

        List<String> dimensions = buildDimensions(request == null ? null : request.focusDimensions());
        String message = crossCategory
                ? "对比商品属于不同品类，结论仅供参考"
                : null;

        return new ProductCompareResponse(
                ProductCompareResponse.STATUS_OK,
                products.stream().map(ProductEntity::getSkuId).toList(),
                comparedProducts,
                dimensions,
                crossCategory,
                message
        );
    }

    private List<String> normalizeSkuIds(List<String> rawSkuIds) {
        if (rawSkuIds == null || rawSkuIds.isEmpty()) {
            return List.of();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String skuId : rawSkuIds) {
            if (skuId == null || skuId.isBlank()) {
                continue;
            }
            normalized.add(skuId.trim());
        }
        return new ArrayList<>(normalized);
    }

    private Map<String, Object> toCompareProduct(
            ProductEntity product,
            Map<String, Map<String, Object>> inventoryBySku,
            Map<String, List<Map<String, Object>>> promotionsBySku
    ) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("skuId", product.getSkuId());
        item.put("categoryId", product.getCategoryId());
        item.put("categoryName", product.getCategoryName());
        item.put("name", product.getName());
        item.put("brand", product.getBrand());
        item.put("price", product.getPrice());
        item.put("description", product.getDescription());
        item.put("inventory", inventoryBySku.getOrDefault(product.getSkuId(), defaultInventory(product.getSkuId())));
        item.put("promotions", promotionsBySku.getOrDefault(product.getSkuId(), List.of()));
        item.put("effectivePriceHint", effectivePriceHint(product.getPrice(), promotionsBySku.get(product.getSkuId())));
        return item;
    }

    private Integer effectivePriceHint(Integer price, List<Map<String, Object>> promotions) {
        if (price == null || promotions == null || promotions.isEmpty()) {
            return price;
        }
        int discount = 0;
        for (Map<String, Object> promotion : promotions) {
            Object rawDiscount = promotion.get("discount");
            if (rawDiscount instanceof Number number) {
                discount += number.intValue();
            }
        }
        return Math.max(0, price - discount);
    }

    private Map<String, Object> defaultInventory(String skuId) {
        Map<String, Object> inventory = new LinkedHashMap<>();
        inventory.put("skuId", skuId);
        inventory.put("available", false);
        inventory.put("quantity", 0);
        return inventory;
    }

    private Map<String, Map<String, Object>> loadInventory(List<String> skuIds) {
        if (skuIds.isEmpty()) {
            return Map.of();
        }
        String placeholders = skuIds.stream().map(id -> "?").collect(Collectors.joining(","));
        String sql = "SELECT sku_id, quantity FROM product_inventory WHERE sku_id IN (" + placeholders + ")";
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, skuIds.toArray());
            Map<String, Map<String, Object>> result = new LinkedHashMap<>();
            for (Map<String, Object> row : rows) {
                String skuId = stringValue(row.get("sku_id"));
                int quantity = row.get("quantity") instanceof Number number ? number.intValue() : 0;
                Map<String, Object> inventory = new LinkedHashMap<>();
                inventory.put("skuId", skuId);
                inventory.put("available", quantity > 0);
                inventory.put("quantity", quantity);
                result.put(skuId, inventory);
            }
            return result;
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private Map<String, List<Map<String, Object>>> loadPromotions(List<String> skuIds) {
        if (skuIds.isEmpty()) {
            return Map.of();
        }
        String placeholders = skuIds.stream().map(id -> "?").collect(Collectors.joining(","));
        String sql = "SELECT sku_id, type, label, discount, discount_rate FROM product_promotion WHERE sku_id IN ("
                + placeholders + ")";
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, skuIds.toArray());
            Map<String, List<Map<String, Object>>> result = new LinkedHashMap<>();
            for (Map<String, Object> row : rows) {
                String skuId = stringValue(row.get("sku_id"));
                Map<String, Object> promotion = new LinkedHashMap<>();
                promotion.put("type", stringValue(row.get("type")));
                promotion.put("label", stringValue(row.get("label")));
                if (row.get("discount") instanceof Number discount) {
                    promotion.put("discount", discount.intValue());
                }
                if (row.get("discount_rate") instanceof Number rate) {
                    promotion.put("discountRate", rate.doubleValue());
                }
                result.computeIfAbsent(skuId, key -> new ArrayList<>()).add(promotion);
            }
            return result;
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private List<String> buildDimensions(List<String> focusDimensions) {
        List<String> defaults = List.of("price", "brand", "description", "inventory", "promotions");
        if (focusDimensions == null || focusDimensions.isEmpty()) {
            return defaults;
        }
        List<String> normalized = new ArrayList<>();
        for (String dimension : focusDimensions) {
            if (dimension == null || dimension.isBlank()) {
                continue;
            }
            normalized.add(dimension.trim().toLowerCase(Locale.ROOT));
        }
        return normalized.isEmpty() ? defaults : normalized;
    }

    private String stringValue(Object value) {
        return value == null ? null : value.toString();
    }
}
