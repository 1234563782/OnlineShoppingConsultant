package com.onlineshopping.catalog.controller;

import com.onlineshopping.catalog.dto.ProductCompareRequest;
import com.onlineshopping.catalog.dto.ProductCompareResponse;
import com.onlineshopping.catalog.service.ProductCompareService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/products")
public class ProductCompareController {

    private final ProductCompareService productCompareService;

    public ProductCompareController(ProductCompareService productCompareService) {
        this.productCompareService = productCompareService;
    }

    @PostMapping("/compare")
    public ProductCompareResponse compare(@RequestBody ProductCompareRequest request) {
        return productCompareService.compare(request);
    }
}
