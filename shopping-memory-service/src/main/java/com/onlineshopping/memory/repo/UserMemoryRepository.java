package com.onlineshopping.memory.repo;

import com.onlineshopping.memory.model.UserMemoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserMemoryRepository extends JpaRepository<UserMemoryEntity, String> {
}
