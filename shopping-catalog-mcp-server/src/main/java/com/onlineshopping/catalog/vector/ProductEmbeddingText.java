package com.onlineshopping.catalog.vector;

import com.onlineshopping.catalog.model.ProductEntity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

final class ProductEmbeddingText {

    private ProductEmbeddingText() {
    }

    static String buildDocument(ProductEntity product) {
        String category = product.getCategoryName();
        if (category == null || category.isBlank()) {
            category = product.getCategory() == null ? "" : product.getCategory();
        }
        String name = product.getName() == null ? "" : product.getName();
        String brand = product.getBrand() == null ? "" : product.getBrand();
        String description = product.getDescription() == null ? "" : product.getDescription();
        String combined = (category + " " + name + " " + brand + " " + description).trim().replaceAll("\\s+", " ");
        return combined;
    }

    static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
