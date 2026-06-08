package com.onlineshopping.catalog.vector;

import com.pgvector.PGvector;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
@Conditional(VectorSearchEnabledCondition.class)
public class ProductEmbeddingStore {

    private final JdbcTemplate vectorJdbcTemplate;

    public ProductEmbeddingStore(@Qualifier("vectorJdbcTemplate") JdbcTemplate vectorJdbcTemplate) {
        this.vectorJdbcTemplate = vectorJdbcTemplate;
    }

    public Optional<String> findContentHash(String skuId) {
        List<String> rows = vectorJdbcTemplate.query(
                "SELECT content_hash FROM embedding_product WHERE sku_id = ?",
                (rs, rowNum) -> rs.getString(1),
                skuId
        );
        return rows.isEmpty() ? Optional.empty() : Optional.ofNullable(rows.get(0));
    }

    public void upsert(
            String skuId,
            String categoryId,
            int price,
            float[] embedding,
            String contentHash,
            String embeddingModel
    ) {
        String sql = """
                INSERT INTO embedding_product (sku_id, category_id, price, embedding, content_hash, embedding_model, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, now())
                ON CONFLICT (sku_id) DO UPDATE SET
                    category_id = EXCLUDED.category_id,
                    price = EXCLUDED.price,
                    embedding = EXCLUDED.embedding,
                    content_hash = EXCLUDED.content_hash,
                    embedding_model = EXCLUDED.embedding_model,
                    updated_at = now()
                """;
        vectorJdbcTemplate.update(sql, ps -> {
            ps.setString(1, skuId);
            ps.setString(2, categoryId);
            ps.setInt(3, price);
            ps.setObject(4, new PGvector(embedding));
            ps.setString(5, contentHash);
            ps.setString(6, embeddingModel);
        });
    }

    /**
     * When document text unchanged but MySQL category/price drifted, keep PG filter columns in sync (no re-embed).
     */
    public int updateScalarsIfChanged(String skuId, String categoryId, int price) {
        return vectorJdbcTemplate.update(
                """
                        UPDATE embedding_product
                        SET category_id = ?, price = ?, updated_at = now()
                        WHERE sku_id = ?
                          AND (category_id IS DISTINCT FROM ? OR price IS DISTINCT FROM ?)
                        """,
                categoryId, price, skuId, categoryId, price
        );
    }

    /**
     * Plan A: filter by category + optional price, order by cosine distance to query embedding.
     */
    public List<String> findNearestSkuIds(
            String categoryId,
            Double minPrice,
            Double maxPrice,
            float[] queryEmbedding,
            int limit
    ) {
        StringBuilder sql = new StringBuilder("""
                SELECT sku_id FROM embedding_product
                WHERE category_id = ?
                  AND embedding IS NOT NULL
                """);
        List<Object> args = new ArrayList<>();
        args.add(categoryId);
        if (minPrice != null) {
            sql.append(" AND price >= ?");
            args.add(minPrice.intValue());
        }
        if (maxPrice != null) {
            sql.append(" AND price <= ?");
            args.add(maxPrice.intValue());
        }
        sql.append(" ORDER BY embedding <=> ? LIMIT ?");
        args.add(new PGvector(queryEmbedding));
        args.add(limit);
        return vectorJdbcTemplate.query(
                sql.toString(),
                (rs, rowNum) -> rs.getString(1),
                args.toArray()
        );
    }
}
