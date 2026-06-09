package com.onlineshopping.orchestrator.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.onlineshopping.orchestrator.auth.model.UserAccountEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserAccountMapper extends BaseMapper<UserAccountEntity> {
}
