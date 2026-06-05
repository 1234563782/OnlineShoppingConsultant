package com.onlineshopping.catalog.service;

import com.onlineshopping.catalog.model.ProductEntity;
import com.onlineshopping.catalog.repo.ProductRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class CatalogService {

    private final ProductRepository productRepository;

    public CatalogService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public List<ProductEntity> search(String keyword, Double minPrice, Double maxPrice, int limit) {
        String kw = keyword == null ? "" : keyword.toLowerCase(Locale.ROOT);
        return productRepository.findAll().stream()
                .filter(p -> kw.isBlank() || searchableText(p).contains(kw))
                .filter(p -> minPrice == null || p.getPrice() >= minPrice)
                .filter(p -> maxPrice == null || p.getPrice() <= maxPrice)
                .limit(limit)
                .collect(Collectors.toList());
    }

    public Optional<ProductEntity> findBySkuId(String skuId) {
        return productRepository.findById(skuId);
    }

    private String searchableText(ProductEntity product) {
        return (product.getCategory() + " " + product.getName() + " " + product.getBrand())
                .toLowerCase(Locale.ROOT);
    }
}
