package com.onlineshopping.catalog.service;

import com.onlineshopping.catalog.model.ProductEntity;
import com.onlineshopping.catalog.repo.ProductRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class CatalogService {

    private final ProductRepository productRepository;

    public CatalogService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public List<ProductEntity> search(String categoryId, String keyword, Double minPrice, Double maxPrice, int limit) {
        String raw = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        String kw = raw.isBlank() ? "" : raw.replace("%", "").replace("_", "");
        return productRepository.searchByFilters(
                normalizeCategoryId(categoryId),
                kw,
                minPrice,
                maxPrice,
                PageRequest.of(0, limit)
        );
    }

    public Optional<ProductEntity> findBySkuId(String skuId) {
        return productRepository.findById(skuId);
    }

    private String normalizeCategoryId(String categoryId) {
        return categoryId == null || categoryId.isBlank() ? null : categoryId.trim();
    }
}
