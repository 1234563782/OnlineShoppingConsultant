package com.onlineshopping.orchestrator.service;

import org.springframework.stereotype.Service;

@Service
public class SmallTalkReplyBuilder {

    public String build(String message) {
        if (message == null || message.isBlank()) {
            return "你好，我是导购助手。告诉我你想买的品类、预算和使用场景，我就能给你推荐具体款式。";
        }
        String text = message.trim();
        if (containsAny(text, "谢谢", "辛苦了")) {
            return "不客气。你可以继续告诉我预算、场景或品牌偏好，我会继续帮你筛选。";
        }
        if (containsAny(text, "再见", "拜拜")) {
            return "好的，随时来找我，我可以继续帮你选购。";
        }
        return "你好，我在。你想买什么品类？可以直接说预算、使用场景和偏好。";
    }

    private boolean containsAny(String text, String... words) {
        for (String word : words) {
            if (text.contains(word)) {
                return true;
            }
        }
        return false;
    }
}
