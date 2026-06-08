package com.onlineshopping.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.onlineshopping.inventory.model.InventoryEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface InventoryMapper extends BaseMapper<InventoryEntity> {
}
