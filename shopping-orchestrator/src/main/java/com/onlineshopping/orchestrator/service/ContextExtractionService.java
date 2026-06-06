package com.onlineshopping.orchestrator.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    public ContextExtractionService(ChatModel chatModel, ObjectMapper objectMapper) {
        this.chatClient = ChatClient.builder(chatModel).build();
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> extractPatch(String userMessage, Map<String, Object> currentSessionContext) {
        String prompt = """
                你是电商导购系统的上下文抽取器，只输出严格 JSON，不要 markdown，不要解释。

                请根据用户本轮输入和已有会话上下文，抽取“本轮明确表达的新信息或修正信息”。
                如果用户没有提及某字段，该字段返回 null 或空数组，不要猜。
                如果用户表达“不确定/先看看/随便看看”，设置 userUncertain=true。
                如果用户只是寒暄，intentType=small_talk。
                如果用户明显不是购物相关，intentType=non_shopping。
                如果用户有购买/选购/对比/推荐诉求，intentType=shopping。

                JSON schema:
                {
                  "intentType":"shopping|small_talk|non_shopping",
                  "categoryRaw":"用户本轮明确说出的商品/品类原词，如智能电视/运动手表/降噪耳机；未知为null；不要归一化成系统类目",
                  "budget":{"min":number|null,"max":number|null,"certainty":"STRICT|FLEXIBLE|UNKNOWN"},
                  "scene":"string|null",
                  "brandPreferences":["string"],
                  "dislikes":["string"],
                  "mustHave":["string"],
                  "notes":"string|null",
                  "userUncertain":boolean,
                  "longTermMemoryPatch":{
                    "brandPreferences":["用户明确长期喜欢/偏好的品牌"],
                    "dislikes":["用户明确长期不喜欢/排斥的品牌或特征"],
                    "notes":["稳定偏好或长期注意事项；本次预算、本次品类、本次临时场景不要写入"]
                  }
                }
                字段写入规则：
                - 用户本轮明确说「喜欢/要/偏好/想要 X」时，必须把 X 写入 mustHave（本次选购硬性要求）。
                - 若用户同时表达稳定长期偏好（如「以后都/平时/一直」），才额外写入 longTermMemoryPatch。
                - 若用户明确推翻旧偏好（如以前排斥入耳式、现在说喜欢入耳式），longTermMemoryPatch.notes 写入新的正向偏好；不要保留已被推翻的 dislikes。
                长期画像写入规则：
                - 只有用户明确表达“我喜欢/我常用/我不要/以后都按这个/我比较在意”等稳定偏好，才写入 longTermMemoryPatch。
                - 本次想买什么、本次预算、本次临时使用场景，只属于当前会话上下文，不要写入 longTermMemoryPatch。
                - 不确定时 longTermMemoryPatch 返回空对象 {}。

                已有会话上下文：
                %s

                用户本轮输入：
                %s
                """.formatted(currentSessionContext, userMessage);
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
        String prompt = """
                你是电商导购系统的追问回答抽取器，只输出严格 JSON，不要 markdown，不要解释。

                系统上一轮正在等待用户补充字段：%s。
                你需要判断用户本轮输入是否回答了这个字段。pendingField 只是解释上下文，不能强行把答非所问写入该字段。

                通用规则：
                - 如果用户回答了 pendingField，answeredPendingField=true，并提取对应字段。
                - 如果用户没回答 pendingField，answeredPendingField=false，对应字段返回 null。
                - 即使没回答 pendingField，也要抽取本轮明确表达的其它有效购物信息，如预算、换品类、品牌偏好、排斥项。
                - 如果用户表示“不确定/先看看/随便/都可以”，userUncertain=true，shouldKeepPending=false。
                - 如果用户答非所问但仍在购物上下文，shouldKeepPending=true。
                - 如果用户只是寒暄，intentType=small_talk，answeredPendingField=false，shouldKeepPending=true。
                - 如果用户明显不是购物相关，intentType=non_shopping，answeredPendingField=false，shouldKeepPending=true。
                - categoryRaw 只填用户本轮明确说出的商品/品类原词，不要归一化成系统类目。
                - scene 可以保留用户原话的自然表达，例如“日常办公”“孩子上网课”“客厅看电影”，不要映射成固定枚举。

                JSON schema:
                {
                  "intentType":"shopping|small_talk|non_shopping",
                  "answeredPendingField":boolean,
                  "shouldKeepPending":boolean,
                  "categoryRaw":"string|null",
                  "budget":{"min":number|null,"max":number|null,"certainty":"STRICT|FLEXIBLE|UNKNOWN"},
                  "scene":"string|null",
                  "brandPreferences":["string"],
                  "dislikes":["string"],
                  "mustHave":["string"],
                  "notes":"string|null",
                  "userUncertain":boolean,
                  "longTermMemoryPatch":{
                    "brandPreferences":["用户明确长期喜欢/偏好的品牌"],
                    "dislikes":["用户明确长期不喜欢/排斥的品牌或特征"],
                    "notes":["稳定偏好或长期注意事项；本次预算、本次品类、本次临时场景不要写入"]
                  }
                }
                字段写入规则：
                - 用户本轮明确说「喜欢/要/偏好/想要 X」时，必须把 X 写入 mustHave（本次选购硬性要求）。
                - 若用户明确推翻旧偏好（如以前排斥入耳式、现在说喜欢入耳式），longTermMemoryPatch.notes 写入新的正向偏好；不要保留已被推翻的 dislikes。

                已有会话上下文：
                %s

                用户本轮输入：
                %s
                """.formatted(pendingField, currentSessionContext, userMessage);
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
