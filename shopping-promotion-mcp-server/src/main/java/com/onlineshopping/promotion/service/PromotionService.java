package com.onlineshopping.promotion.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.onlineshopping.promotion.mapper.PromotionMapper;
import com.onlineshopping.promotion.model.PromotionEntity;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PromotionService {

    private final PromotionMapper promotionMapper;

    public PromotionService(PromotionMapper promotionMapper) {
        this.promotionMapper = promotionMapper;
    }

    public List<PromotionEntity> findBySkuId(String skuId) {
        return promotionMapper.selectList(
                Wrappers.<PromotionEntity>lambdaQuery().eq(PromotionEntity::getSkuId, skuId));
    }
}
