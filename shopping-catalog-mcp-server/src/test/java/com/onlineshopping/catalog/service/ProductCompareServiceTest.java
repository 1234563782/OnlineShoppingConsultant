package com.onlineshopping.catalog.service;

import com.onlineshopping.catalog.dto.ProductCompareRequest;
import com.onlineshopping.catalog.dto.ProductCompareResponse;
import com.onlineshopping.catalog.model.ProductEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductCompareServiceTest {

    @Mock
    private CatalogService catalogService;

    @Mock
    private JdbcTemplate jdbcTemplate;

    private ProductCompareService productCompareService;

    @BeforeEach
    void setUp() {
        productCompareService = new ProductCompareService(catalogService, jdbcTemplate);
    }

    @Test
    void returnsInsufficientWhenLessThanTwoSkuIds() {
        ProductCompareResponse response = productCompareService.compare(
                new ProductCompareRequest(List.of("SKU1001"), List.of())
        );

        assertEquals(ProductCompareResponse.STATUS_INSUFFICIENT_TARGETS, response.status());
    }

    @Test
    void comparesTwoProductsSuccessfully() {
        ProductEntity first = product("SKU1001", "cat_phone", "小米 14", 3999);
        ProductEntity second = product("SKU1002", "cat_phone", "iPhone 15", 5999);
        when(catalogService.findBySkuIdsPreserveOrder(List.of("SKU1001", "SKU1002")))
                .thenReturn(List.of(first, second));
        when(jdbcTemplate.queryForList(any(String.class), any(Object[].class))).thenReturn(List.of());

        ProductCompareResponse response = productCompareService.compare(
                new ProductCompareRequest(List.of("SKU1001", "SKU1002"), List.of("价格"))
        );

        assertEquals(ProductCompareResponse.STATUS_OK, response.status());
        assertEquals(2, response.products().size());
        assertEquals(false, response.crossCategory());
    }

    private ProductEntity product(String skuId, String categoryId, String name, int price) {
        ProductEntity entity = new ProductEntity();
        entity.setSkuId(skuId);
        entity.setCategoryId(categoryId);
        entity.setCategoryName("手机");
        entity.setName(name);
        entity.setBrand("brand");
        entity.setPrice(price);
        entity.setDescription("desc");
        return entity;
    }
}
