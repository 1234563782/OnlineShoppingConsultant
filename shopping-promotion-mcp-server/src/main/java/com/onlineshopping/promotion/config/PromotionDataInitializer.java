package com.onlineshopping.promotion.config;

import com.onlineshopping.promotion.model.PromotionEntity;
import com.onlineshopping.promotion.repo.PromotionRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PromotionDataInitializer implements ApplicationRunner {

    private final PromotionRepository promotionRepository;

    public PromotionDataInitializer(PromotionRepository promotionRepository) {
        this.promotionRepository = promotionRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (promotionRepository.count() > 0) {
            return;
        }
        promotionRepository.saveAll(List.of(
                promo("SKU1001", "coupon", "满3000减200", 200, null),
                promo("SKU1002", "installment", "12期免息", 0, null),
                promo("SKU2001", "coupon", "满2000减150", 150, null),
                promo("SKU2002", "member", "会员95折", null, 0.95),
                promo("SKU3001", "coupon", "满5000减300", 300, null),
                promo("SKU4002", "coupon", "满3000减100", 100, null)
        ));
    }

    private static PromotionEntity promo(
            String skuId, String type, String label, Integer discount, Double discountRate
    ) {
        PromotionEntity entity = new PromotionEntity();
        entity.setSkuId(skuId);
        entity.setType(type);
        entity.setLabel(label);
        entity.setDiscount(discount);
        entity.setDiscountRate(discountRate);
        return entity;
    }
}
