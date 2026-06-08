package com.onlineshopping.catalog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.onlineshopping.catalog.model.CategoryEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CategoryMapper extends BaseMapper<CategoryEntity> {
}
