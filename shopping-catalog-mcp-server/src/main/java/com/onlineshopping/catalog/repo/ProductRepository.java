package com.onlineshopping.catalog.repo;

import com.onlineshopping.catalog.model.ProductEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductRepository extends JpaRepository<ProductEntity, String> {

    /**
     * categoryId 命中标准类目；kw 在类目名 / 商品名 / 品牌 / 描述上做补充匹配。
     * categoryId 和 kw 都为空时，只按价格过滤并返回替代候选。
     */
    @Query("""
            SELECT p FROM ProductEntity p
            WHERE (:categoryId IS NULL OR p.categoryId = :categoryId)
              AND (:kw = ''
                OR LOWER(CONCAT(CONCAT(CONCAT(CONCAT(COALESCE(p.categoryName, p.category), ' '), p.name), CONCAT(' ', p.brand)), CONCAT(' ', p.description)))
                   LIKE CONCAT(CONCAT('%', :kw), '%'))
              AND (:minPrice IS NULL OR p.price >= :minPrice)
              AND (:maxPrice IS NULL OR p.price <= :maxPrice)
            ORDER BY p.price ASC
            """)
    List<ProductEntity> searchByFilters(
            @Param("categoryId") String categoryId,
            @Param("kw") String kw,
            @Param("minPrice") Double minPrice,
            @Param("maxPrice") Double maxPrice,
            Pageable pageable
    );
}
