package com.onlineshopping.catalog.controller;

import com.onlineshopping.catalog.dto.ProductSearchRequest;
import com.onlineshopping.catalog.dto.ProductSearchResponse;
import com.onlineshopping.catalog.service.ProductSearchService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    private final ProductSearchService productSearchService;

    public ProductController(ProductSearchService productSearchService) {
        this.productSearchService = productSearchService;
    }

    @PostMapping("/search")
    public ProductSearchResponse search(@RequestBody ProductSearchRequest request) {
        ProductSearchRequest safeRequest = request == null
                ? new ProductSearchRequest(null, null, null, null, null, null, null)
                : request;
        return productSearchService.search(safeRequest);
    }
}
