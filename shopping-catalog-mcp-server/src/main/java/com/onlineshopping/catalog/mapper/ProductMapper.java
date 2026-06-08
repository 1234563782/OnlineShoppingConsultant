package com.onlineshopping.catalog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.onlineshopping.catalog.model.ProductEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ProductMapper extends BaseMapper<ProductEntity> {

    @Select("""
            SELECT sku_id, category, category_id, category_name, name, brand, price, description
            FROM product
            WHERE (#{categoryId} IS NULL OR category_id = #{categoryId})
              AND (#{kw} = ''
                OR LOWER(CONCAT(COALESCE(category_name, category), ' ', name, ' ', brand, ' ', description))
                   LIKE CONCAT('%', #{kw}, '%'))
              AND (#{minPrice} IS NULL OR price >= #{minPrice})
              AND (#{maxPrice} IS NULL OR price <= #{maxPrice})
            ORDER BY price ASC
            LIMIT #{limit}
            """)
    List<ProductEntity> searchByFilters(
            @Param("categoryId") String categoryId,
            @Param("kw") String kw,
            @Param("minPrice") Double minPrice,
            @Param("maxPrice") Double maxPrice,
            @Param("limit") int limit
    );
}
