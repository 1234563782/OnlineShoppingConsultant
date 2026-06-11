package com.onlineshopping.orchestrator.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlineshopping.prompt.PromptTemplateService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ContextExtractionService {

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final PromptTemplateService promptTemplateService;

    public ContextExtractionService(
            ChatModel chatModel,
            ObjectMapper objectMapper,
            PromptTemplateService promptTemplateService
    ) {
        this.chatClient = ChatClient.builder(chatModel).build();
        this.objectMapper = objectMapper;
        this.promptTemplateService = promptTemplateService;
    }

    public Map<String, Object> extractPatch(String userMessage, Map<String, Object> currentSessionContext) {
        String prompt = promptTemplateService.render(
                "context-extraction",
                Map.of(
                        "sessionContext", currentSessionContext,
                        "userMessage", userMessage
                )
        ).content();
        try {
            String content = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();
            return parseJsonObject(content);
        } catch (Exception ignored) {
            return fallbackPatch(userMessage);
        }
    }

    public Map<String, Object> extractPendingFieldPatch(
            String pendingField,
            String userMessage,
            Map<String, Object> currentSessionContext
    ) {
        String prompt = promptTemplateService.render(
                "pending-field-extraction",
                Map.of(
                        "pendingField", pendingField,
                        "sessionContext", currentSessionContext,
                        "userMessage", userMessage
                )
        ).content();
        try {
            String content = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();
            return parseJsonObject(content);
        } catch (Exception ignored) {
            Map<String, Object> patch = fallbackPatch(userMessage);
            patch.put("answeredPendingField", false);
            patch.put("shouldKeepPending", true);
            return patch;
        }
    }

    private Map<String, Object> parseJsonObject(String content) throws Exception {
        if (content == null || content.isBlank()) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(content, new TypeReference<>() {
            });
        } catch (Exception first) {
            Matcher matcher = Pattern.compile("\\{[\\s\\S]*\\}").matcher(content);
            if (matcher.find()) {
                return objectMapper.readValue(matcher.group(), new TypeReference<>() {
                });
            }
            throw first;
        }
    }

    private Map<String, Object> fallbackPatch(String userMessage) {
        Map<String, Object> patch = new HashMap<>();
        patch.put("intentType", "shopping");
        patch.put("categoryRaw", null);
        Map<String, Object> budget = new HashMap<>();
        budget.put("min", null);
        budget.put("max", null);
        budget.put("certainty", "UNKNOWN");
        patch.put("budget", budget);
        patch.put("scene", null);
        patch.put("brandPreferences", java.util.List.of());
        patch.put("dislikes", java.util.List.of());
        patch.put("mustHave", java.util.List.of());
        patch.put("notes", userMessage);
        patch.put("userUncertain", false);
        patch.put("longTermMemoryPatch", Map.of());
        return patch;
    }
}
