package com.onlineshopping.catalog.config;

import com.onlineshopping.catalog.model.ProductEntity;
import com.onlineshopping.catalog.repo.ProductRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CatalogDataInitializer implements ApplicationRunner {

    private final ProductRepository productRepository;

    public CatalogDataInitializer(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (productRepository.count() > 0) {
            return;
        }
        productRepository.saveAll(List.of(
                product("SKU1001", "手机", "小米 14", "Xiaomi", 3999, "旗舰直屏，徕卡影像"),
                product("SKU1002", "手机", "iPhone 15", "Apple", 5999, "A16 芯片，生态完善"),
                product("SKU1003", "手机", "荣耀 200", "Honor", 2699, "轻薄长续航"),
                product("SKU2001", "耳机", "索尼 WH-1000XM5", "Sony", 2299, "头戴降噪旗舰"),
                product("SKU2002", "耳机", "AirPods Pro 2", "Apple", 1899, "主动降噪，通透模式"),
                product("SKU2003", "耳机", "漫步者 NeoBuds Pro", "Edifier", 799, "入门降噪"),
                product("SKU3001", "电脑", "联想小新 Pro 14", "Lenovo", 5699, "轻薄高性能"),
                product("SKU3002", "电脑", "MacBook Air M3", "Apple", 8999, "续航优秀"),
                product("SKU3003", "电脑", "机械革命 无界 14X", "MECHREVO", 4299, "高性价比"),
                product("SKU4001", "平板", "iPad Air", "Apple", 4799, "学习办公通用"),
                product("SKU4002", "平板", "小米平板 6S Pro", "Xiaomi", 3299, "大屏娱乐"),
                product("SKU5001", "手表", "华为 WATCH GT 4", "Huawei", 1488, "健康监测"),
                product("SKU5002", "手表", "Apple Watch S9", "Apple", 2999, "iOS 生态搭配")
        ));
    }

    private static ProductEntity product(
            String skuId, String category, String name, String brand, int price, String description
    ) {
        ProductEntity entity = new ProductEntity();
        entity.setSkuId(skuId);
        entity.setCategory(category);
        entity.setName(name);
        entity.setBrand(brand);
        entity.setPrice(price);
        entity.setDescription(description);
        return entity;
    }
}
