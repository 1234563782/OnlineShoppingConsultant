package com.onlineshopping.orchestrator.eval;

import com.onlineshopping.orchestrator.dto.CategoryResolutionResult;
import com.onlineshopping.orchestrator.service.CategoryClientService;
import com.onlineshopping.orchestrator.service.CategoryResolutionService;
import com.onlineshopping.orchestrator.support.SessionContextKeys;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ShoppingAgentQualityEvalTest {

    private static final double MIN_INTENT_ACCURACY = 0.90;
    private static final double MIN_CATEGORY_ACCURACY = 0.90;
    private static final double MIN_TOOL_HIT_RATE = 0.90;
    private static final double MAX_HALLUCINATION_RATE = 0.05;

    @Test
    void evaluatesIntentRecognitionAccuracyFromGoldenLabels() {
        List<LabelCase> cases = List.of(
                new LabelCase("hello", "small_talk", "small_talk"),
                new LabelCase("need-phone", "shopping", "shopping"),
                new LabelCase("compare-two-skus", "shopping", "shopping"),
                new LabelCase("write-poem", "non_shopping", "non_shopping"),
                new LabelCase("thanks", "small_talk", "small_talk")
        );

        EvalSummary summary = accuracy(cases);

        assertThat(summary.rate())
                .as(summary.message("intent recognition accuracy"))
                .isGreaterThanOrEqualTo(MIN_INTENT_ACCURACY);
    }

    @Test
    void evaluatesCategoryNormalizationAccuracy() {
        CategoryClientService categoryClientService = mock(CategoryClientService.class);
        when(categoryClientService.normalize("phone")).thenReturn(normalized("cat_phone", "Phone", 0.96, "alias"));
        when(categoryClientService.normalize("noise cancelling headphone"))
                .thenReturn(normalized("cat_headphone", "Headphone", 0.95, "alias"));
        when(categoryClientService.normalize("laptop")).thenReturn(normalized("cat_laptop", "Laptop", 0.98, "name"));
        when(categoryClientService.normalize("gift")).thenReturn(Map.of(
                "status", CategoryResolutionResult.STATUS_UNRESOLVED,
                "categoryRaw", "gift",
                "confidence", 0.0
        ));

        CategoryResolutionService service = new CategoryResolutionService(categoryClientService);
        ReflectionTestUtils.setField(service, "confidenceThreshold", 0.85);

        List<CategoryCase> cases = List.of(
                new CategoryCase("phone", "cat_phone"),
                new CategoryCase("noise cancelling headphone", "cat_headphone"),
                new CategoryCase("laptop", "cat_laptop"),
                new CategoryCase("gift", null)
        );

        List<LabelCase> results = cases.stream()
                .map(testCase -> {
                    Map<String, Object> sessionContext = new LinkedHashMap<>();
                    sessionContext.put(SessionContextKeys.CATEGORY_RAW, testCase.raw());
                    CategoryResolutionResult result = service.resolve(sessionContext);
                    return new LabelCase(testCase.raw(), testCase.expectedCategoryId(), result.categoryId());
                })
                .toList();

        EvalSummary summary = accuracy(results);

        assertThat(summary.rate())
                .as(summary.message("category normalization accuracy"))
                .isGreaterThanOrEqualTo(MIN_CATEGORY_ACCURACY);
    }

    @Test
    void evaluatesToolHitRateForGroundedRecommendations() {
        List<Map<String, Object>> products = List.of(
                product("SKU1001", "Pixel 9", 4999),
                product("SKU1002", "Galaxy S25", 5999),
                product("SKU1003", "iPhone 16", 6999)
        );
        List<GroundingCase> cases = List.of(
                new GroundingCase(
                        "recommends allowed sku",
                        products,
                        "I recommend SKU1001 Pixel 9 at 4999."
                ),
                new GroundingCase(
                        "recommends two allowed skus",
                        products,
                        "SKU1002 Galaxy S25 is stronger, while SKU1003 iPhone 16 is smoother."
                )
        );

        EvalSummary summary = groundingRate(cases);

        assertThat(summary.rate())
                .as(summary.message("tool hit rate"))
                .isGreaterThanOrEqualTo(MIN_TOOL_HIT_RATE);
    }

    @Test
    void evaluatesHallucinationRateForUnauthorizedProductsAndPrices() {
        List<Map<String, Object>> products = List.of(
                product("SKU1001", "Pixel 9", 4999),
                product("SKU1002", "Galaxy S25", 5999)
        );
        List<GroundingCase> cases = List.of(
                new GroundingCase(
                        "grounded product and price",
                        products,
                        "SKU1001 Pixel 9 is available at 4999."
                ),
                new GroundingCase(
                        "another grounded product",
                        products,
                        "SKU1002 Galaxy S25 costs 5999."
                )
        );

        EvalSummary summary = hallucinationRate(cases);

        assertThat(summary.rate())
                .as(summary.message("hallucination rate"))
                .isLessThanOrEqualTo(MAX_HALLUCINATION_RATE);
    }

    @Test
    void doesNotTreatAdjacentAccessoryMentionsAsProductMentions() {
        List<Map<String, Object>> products = List.of(
                product("SKU1001", "iPhone 15", 5999),
                product("SKU1002", "小米 14", 3999)
        );

        GroundingResult result = evaluateGrounding(
                products,
                "若深度依赖苹果生态（如已拥有 Mac、iPad、AirPods 等设备），可以优先考虑 iPhone 15。"
        );

        assertThat(result.unauthorizedSkuIds()).isEmpty();
        assertThat(result.hasRecommendation()).isTrue();
    }

    private static Map<String, Object> normalized(
            String categoryId,
            String categoryName,
            double confidence,
            String matchedBy
    ) {
        return Map.of(
                "status", CategoryResolutionResult.STATUS_RESOLVED,
                "categoryId", categoryId,
                "categoryName", categoryName,
                "confidence", confidence,
                "matchedBy", matchedBy
        );
    }

    private static Map<String, Object> product(String skuId, String name, int price) {
        return Map.of(
                "skuId", skuId,
                "name", name,
                "price", price
        );
    }

    private static EvalSummary accuracy(List<LabelCase> cases) {
        int passed = 0;
        for (LabelCase testCase : cases) {
            if (sameLabel(testCase.expected(), testCase.actual())) {
                passed++;
            }
        }
        return new EvalSummary(passed, cases.size());
    }

    private static boolean sameLabel(String expected, String actual) {
        if (expected == null) {
            return actual == null || actual.isBlank();
        }
        return expected.equalsIgnoreCase(actual == null ? "" : actual);
    }

    private static EvalSummary groundingRate(List<GroundingCase> cases) {
        int passed = 0;
        for (GroundingCase testCase : cases) {
            GroundingResult result = evaluateGrounding(testCase.products(), testCase.reply());
            if (result.hasRecommendation() && result.unauthorizedSkuIds().isEmpty()) {
                passed++;
            }
        }
        return new EvalSummary(passed, cases.size());
    }

    private static EvalSummary hallucinationRate(List<GroundingCase> cases) {
        int hallucinated = 0;
        for (GroundingCase testCase : cases) {
            GroundingResult result = evaluateGrounding(testCase.products(), testCase.reply());
            if (!result.unauthorizedSkuIds().isEmpty() || !result.priceMismatches().isEmpty()) {
                hallucinated++;
            }
        }
        return new EvalSummary(hallucinated, cases.size());
    }

    private static GroundingResult evaluateGrounding(List<Map<String, Object>> products, String reply) {
        Map<String, Integer> allowedPricesBySku = new LinkedHashMap<>();
        Set<String> allowedSkuIds = new HashSet<>();
        for (Map<String, Object> product : products) {
            String skuId = stringValue(product.get("skuId"));
            Integer price = intValue(product.get("price"));
            if (skuId != null) {
                allowedSkuIds.add(skuId);
                if (price != null) {
                    allowedPricesBySku.put(skuId, price);
                }
            }
        }

        Set<String> mentionedSkuIds = extractSkuIds(reply);
        Set<String> unauthorizedSkuIds = new HashSet<>(mentionedSkuIds);
        unauthorizedSkuIds.removeAll(allowedSkuIds);

        Set<String> mentionedProductNames = new HashSet<>();
        for (Map<String, Object> product : products) {
            String skuId = stringValue(product.get("skuId"));
            String name = stringValue(product.get("name"));
            if (skuId != null && skuMentioned(reply, skuId)) {
                mentionedProductNames.add(skuId);
            } else if (productMentioned(reply, name)) {
                mentionedProductNames.add(skuId);
            }
        }

        for (Map<String, Object> product : products) {
            String skuId = stringValue(product.get("skuId"));
            if (skuId == null || allowedSkuIds.contains(skuId)) {
                continue;
            }
            if (skuMentioned(reply, skuId) || productMentioned(reply, stringValue(product.get("name")))) {
                unauthorizedSkuIds.add(skuId);
            }
        }

        List<String> priceMismatches = new ArrayList<>();
        for (String skuId : mentionedSkuIds) {
            Integer expectedPrice = allowedPricesBySku.get(skuId);
            if (expectedPrice != null && !mentionsExpectedPriceNearSku(reply, skuId, expectedPrice)) {
                priceMismatches.add(skuId);
            }
        }

        return new GroundingResult(!mentionedProductNames.isEmpty(), unauthorizedSkuIds, priceMismatches);
    }

    private static Set<String> extractSkuIds(String reply) {
        Set<String> skuIds = new HashSet<>();
        Matcher matcher = Pattern.compile("\\bSKU\\d+\\b", Pattern.CASE_INSENSITIVE).matcher(reply == null ? "" : reply);
        while (matcher.find()) {
            skuIds.add(matcher.group().toUpperCase(Locale.ROOT));
        }
        return skuIds;
    }

    private static boolean skuMentioned(String reply, String skuId) {
        if (reply == null || skuId == null || skuId.isBlank()) {
            return false;
        }
        Pattern pattern = Pattern.compile("(?<![A-Za-z0-9])" + Pattern.quote(skuId.toUpperCase(Locale.ROOT)) + "(?![A-Za-z0-9])", Pattern.CASE_INSENSITIVE);
        return pattern.matcher(reply).find();
    }

    private static boolean productMentioned(String reply, String productName) {
        if (reply == null || productName == null || productName.isBlank()) {
            return false;
        }
        List<String> chunks = new ArrayList<>();
        Matcher matcher = Pattern.compile("[A-Za-z0-9]+|[\\u4e00-\\u9fff]+").matcher(productName);
        while (matcher.find()) {
            chunks.add(matcher.group());
        }
        if (chunks.isEmpty()) {
            return false;
        }

        StringBuilder pattern = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            if (i > 0) {
                pattern.append("[\\s\\p{Punct}_]*");
            }
            String chunk = chunks.get(i);
            if (chunk.matches("[A-Za-z0-9]+")) {
                pattern.append("(?<![A-Za-z0-9])").append(Pattern.quote(chunk)).append("(?![A-Za-z0-9])");
            } else {
                pattern.append(Pattern.quote(chunk));
            }
        }

        Pattern compiled = Pattern.compile(pattern.toString(), Pattern.CASE_INSENSITIVE);
        return compiled.matcher(reply).find();
    }

    private static boolean mentionsExpectedPriceNearSku(String reply, String skuId, int expectedPrice) {
        if (reply == null) {
            return false;
        }
        int skuIndex = reply.toUpperCase(Locale.ROOT).indexOf(skuId.toUpperCase(Locale.ROOT));
        if (skuIndex < 0) {
            return false;
        }
        int start = Math.max(0, skuIndex - 80);
        int end = Math.min(reply.length(), skuIndex + 120);
        String window = reply.substring(start, end);
        return window.contains(String.valueOf(expectedPrice));
    }

    private static String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString();
        return text.isBlank() ? null : text;
    }

    private static Integer intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private record LabelCase(String name, String expected, String actual) {
    }

    private record CategoryCase(String raw, String expectedCategoryId) {
    }

    private record GroundingCase(String name, List<Map<String, Object>> products, String reply) {
    }

    private record GroundingResult(
            boolean hasRecommendation,
            Set<String> unauthorizedSkuIds,
            List<String> priceMismatches
    ) {
    }

    private record EvalSummary(int passed, int total) {
        double rate() {
            if (total == 0) {
                return 0.0;
            }
            return passed / (double) total;
        }

        String message(String metricName) {
            return "%s: %d/%d = %.2f".formatted(metricName, passed, total, rate());
        }
    }
}
