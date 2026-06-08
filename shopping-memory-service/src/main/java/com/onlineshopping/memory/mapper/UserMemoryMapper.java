package com.onlineshopping.memory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.onlineshopping.memory.model.UserMemoryEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMemoryMapper extends BaseMapper<UserMemoryEntity> {
}
