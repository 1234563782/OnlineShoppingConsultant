package com.onlineshopping.promotion.repo;

import com.onlineshopping.promotion.model.PromotionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PromotionRepository extends JpaRepository<PromotionEntity, Long> {

    List<PromotionEntity> findBySkuId(String skuId);
}
