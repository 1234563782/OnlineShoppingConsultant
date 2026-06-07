package com.onlineshopping.catalog.config;

import com.onlineshopping.catalog.model.ProductEntity;
import com.onlineshopping.catalog.model.CategoryEntity;
import com.onlineshopping.catalog.repo.CategoryRepository;
import com.onlineshopping.catalog.repo.ProductRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class CatalogDataInitializer implements ApplicationRunner {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    public CatalogDataInitializer(CategoryRepository categoryRepository, ProductRepository productRepository) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        seedCategoriesIfNeeded();
        backfillProductCategoryIds();
        if (productRepository.count() > 0) {
            return;
        }
        productRepository.saveAll(List.of(
                product("SKU1001", "cat_phone", "手机", "小米 14", "Xiaomi", 3999, "旗舰直屏，徕卡影像"),
                product("SKU1002", "cat_phone", "手机", "iPhone 15", "Apple", 5999, "A16 芯片，生态完善"),
                product("SKU1003", "cat_phone", "手机", "荣耀 200", "Honor", 2699, "轻薄长续航"),
                product("SKU2001", "cat_headphone", "耳机", "索尼 WH-1000XM5", "Sony", 2299, "头戴降噪旗舰"),
                product("SKU2002", "cat_headphone", "耳机", "AirPods Pro 2", "Apple", 1899, "主动降噪，通透模式"),
                product("SKU2003", "cat_headphone", "耳机", "漫步者 NeoBuds Pro", "Edifier", 799, "入门降噪"),
                product("SKU3001", "cat_computer", "电脑", "联想小新 Pro 14", "Lenovo", 5699, "轻薄高性能"),
                product("SKU3002", "cat_computer", "电脑", "MacBook Air M3", "Apple", 8999, "续航优秀"),
                product("SKU3003", "cat_computer", "电脑", "机械革命 无界 14X", "MECHREVO", 4299, "高性价比"),
                product("SKU4001", "cat_tablet", "平板", "iPad Air", "Apple", 4799, "学习办公通用"),
                product("SKU4002", "cat_tablet", "平板", "小米平板 6S Pro", "Xiaomi", 3299, "大屏娱乐"),
                product("SKU5001", "cat_watch", "手表", "华为 WATCH GT 4", "Huawei", 1488, "健康监测"),
                product("SKU5002", "cat_watch", "手表", "Apple Watch S9", "Apple", 2999, "iOS 生态搭配")
        ));
    }

    private static ProductEntity product(
            String skuId, String categoryId, String categoryName, String name, String brand, int price, String description
    ) {
        ProductEntity entity = new ProductEntity();
        entity.setSkuId(skuId);
        entity.setCategory(categoryName);
        entity.setCategoryId(categoryId);
        entity.setCategoryName(categoryName);
        entity.setName(name);
        entity.setBrand(brand);
        entity.setPrice(price);
        entity.setDescription(description);
        return entity;
    }

    private void seedCategoriesIfNeeded() {
        if (categoryRepository.count() > 0) {
            return;
        }
        categoryRepository.saveAll(List.of(
                category("cat_phone", "手机", "智能手机,安卓手机,iPhone,苹果手机"),
                category("cat_headphone", "耳机", "蓝牙耳机,降噪耳机,头戴耳机,无线耳机,入耳式,TWS,真无线"),
                category("cat_computer", "电脑", "笔记本,笔记本电脑,轻薄本,游戏本,台式机"),
                category("cat_tablet", "平板", "平板电脑,iPad,安卓平板"),
                category("cat_watch", "手表", "智能手表,运动手表,Apple Watch,华为手表"),
                category("cat_tv", "电视", "电视机,智能电视,大屏电视,客厅电视")
        ));
    }

    private CategoryEntity category(String categoryId, String name, String aliases) {
        CategoryEntity entity = new CategoryEntity();
        entity.setCategoryId(categoryId);
        entity.setName(name);
        entity.setAliases(aliases);
        entity.setEnabled(true);
        return entity;
    }

    private void backfillProductCategoryIds() {
        Map<String, CategoryEntity> byName = categoryRepository.findByEnabledTrue().stream()
                .collect(java.util.stream.Collectors.toMap(CategoryEntity::getName, category -> category));
        List<ProductEntity> changed = productRepository.findAll().stream()
                .filter(product -> product.getCategoryId() == null || product.getCategoryName() == null)
                .peek(product -> {
                    CategoryEntity category = byName.get(product.getCategory());
                    if (category != null) {
                        product.setCategoryId(category.getCategoryId());
                        product.setCategoryName(category.getName());
                    }
                })
                .filter(product -> product.getCategoryId() != null && product.getCategoryName() != null)
                .toList();
        if (!changed.isEmpty()) {
            productRepository.saveAll(changed);
        }
    }
}
