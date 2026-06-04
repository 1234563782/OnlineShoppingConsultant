package com.onlineshopping.memory.controller;

import com.onlineshopping.memory.dto.MemoryResponse;
import com.onlineshopping.memory.dto.MemoryUpdateRequest;
import com.onlineshopping.memory.service.UserMemoryService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/memory")
public class MemoryController {

    private final UserMemoryService userMemoryService;

    public MemoryController(UserMemoryService userMemoryService) {
        this.userMemoryService = userMemoryService;
    }

    @GetMapping("/{userId}")
    public MemoryResponse getByUserId(@PathVariable String userId) {
        return userMemoryService.getByUserId(userId);
    }

    @PutMapping("/{userId}")
    public MemoryResponse upsert(
            @PathVariable String userId,
            @Valid @RequestBody MemoryUpdateRequest request
    ) {
        return userMemoryService.mergeUpdate(userId, request.getProfileJson());
    }

    @DeleteMapping("/{userId}")
    public void delete(@PathVariable String userId) {
        userMemoryService.deleteByUserId(userId);
    }
}
