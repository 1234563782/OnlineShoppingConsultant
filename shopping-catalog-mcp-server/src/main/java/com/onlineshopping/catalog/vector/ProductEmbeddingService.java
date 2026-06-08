package com.onlineshopping.catalog.vector;

import com.onlineshopping.catalog.model.ProductEntity;
import com.onlineshopping.catalog.mapper.ProductMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
@Conditional(VectorSearchEnabledCondition.class)
public class ProductEmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(ProductEmbeddingService.class);

    private final ProductMapper productMapper;
    private final ProductEmbeddingStore embeddingStore;
    private final DashScopeEmbeddingClient embeddingClient;
    private final VectorStoreProperties properties;

    public ProductEmbeddingService(
            ProductMapper productMapper,
            ProductEmbeddingStore embeddingStore,
            DashScopeEmbeddingClient embeddingClient,
            VectorStoreProperties properties
    ) {
        this.productMapper = productMapper;
        this.embeddingStore = embeddingStore;
        this.embeddingClient = embeddingClient;
        this.properties = properties;
    }

    /**
     * Re-index all products from MySQL into PostgreSQL (calls DashScope per row).
     */
    public int rebuildAll() {
        if (!embeddingClient.hasApiKey()) {
            log.warn("Skipping embedding rebuild: SPRING_AI_DASHSCOPE_API_KEY is empty");
            return 0;
        }
        int count = 0;
        for (ProductEntity p : productMapper.selectList(null)) {
            if (indexOne(p)) {
                count++;
            }
        }
        return count;
    }

    /**
     * @return true if a row was written or updated
     */
    public boolean indexOne(ProductEntity product) {
        if (!embeddingClient.hasApiKey()) {
            return false;
        }
        if (product.getSkuId() == null || product.getCategoryId() == null || product.getCategoryId().isBlank()) {
            return false;
        }
        try {
            String doc = ProductEmbeddingText.buildDocument(product);
            if (doc.isBlank()) {
                return false;
            }
            String hash = ProductEmbeddingText.sha256Hex(doc);
            if (embeddingStore.findContentHash(product.getSkuId()).orElse("").equals(hash)) {
                int n = embeddingStore.updateScalarsIfChanged(
                        product.getSkuId(),
                        product.getCategoryId(),
                        Objects.requireNonNullElse(product.getPrice(), 0)
                );
                return n > 0;
            }
            float[] vec = embeddingClient.embed(
                    properties.getEmbeddingModel(),
                    doc,
                    properties.getEmbeddingDimensions()
            );
            if (vec == null || vec.length == 0) {
                log.warn("No embedding for sku {}", product.getSkuId());
                return false;
            }
            embeddingStore.upsert(
                    product.getSkuId(),
                    product.getCategoryId(),
                    Objects.requireNonNullElse(product.getPrice(), 0),
                    vec,
                    hash,
                    properties.getEmbeddingModel()
            );
            return true;
        } catch (Exception e) {
            log.warn("Failed to index sku {}: {}", product.getSkuId(), e.getMessage());
            return false;
        }
    }

    /**
     * Ordered sku ids from pgvector (Plan A), or empty if query embedding failed.
     */
    public List<String> searchNearestSkuIds(
            String categoryId,
            Double minPrice,
            Double maxPrice,
            String semanticQuery,
            int limit
    ) {
        if (!embeddingClient.hasApiKey() || semanticQuery == null || semanticQuery.isBlank()) {
            return List.of();
        }
        if (categoryId == null || categoryId.isBlank()) {
            return List.of();
        }
        float[] queryVec = embeddingClient.embed(
                properties.getEmbeddingModel(),
                semanticQuery.trim(),
                properties.getEmbeddingDimensions()
        );
        if (queryVec == null || queryVec.length == 0) {
            return List.of();
        }
        try {
            return embeddingStore.findNearestSkuIds(categoryId, minPrice, maxPrice, queryVec, limit);
        } catch (Exception e) {
            log.warn("Vector search failed for categoryId={}: {}", categoryId, e.getMessage());
            return List.of();
        }
    }
}
