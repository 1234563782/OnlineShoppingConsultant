package com.onlineshopping.promotion.service;

import com.onlineshopping.promotion.model.PromotionEntity;
import com.onlineshopping.promotion.repo.PromotionRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PromotionService {

    private final PromotionRepository promotionRepository;

    public PromotionService(PromotionRepository promotionRepository) {
        this.promotionRepository = promotionRepository;
    }

    public List<PromotionEntity> findBySkuId(String skuId) {
        return promotionRepository.findBySkuId(skuId);
    }
}
