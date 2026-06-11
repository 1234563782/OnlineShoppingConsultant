package com.onlineshopping.prompt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PromptTemplateService {

    private static final Pattern INCLUDE_PATTERN = Pattern.compile("\\{\\{>\\s*([^}]+?)\\s*\\}\\}");
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{([a-zA-Z][a-zA-Z0-9_]*)\\}\\}");

    private final ObjectMapper jsonMapper = new ObjectMapper();
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    private String manifestVersion = "unknown";
    private Map<String, PromptDefinition> prompts = Map.of();
    private Map<String, String> fragmentPaths = Map.of();
    private Map<String, List<RoutingRule>> routingRules = Map.of();

    public PromptTemplateService() {
        try {
            loadManifest();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load prompt-manifest.yml", e);
        }
    }

    private void loadManifest() throws IOException {
        try (InputStream input = new ClassPathResource("prompt-manifest.yml").getInputStream()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> manifest = yamlMapper.readValue(input, Map.class);
            manifestVersion = stringValue(manifest.get("version"), "unknown");
            prompts = parsePrompts(manifest.get("prompts"));
            fragmentPaths = parseFragmentPaths(manifest.get("fragments"));
            routingRules = parseRouting(manifest.get("routing"));
        }
    }

    public String manifestVersion() {
        return manifestVersion;
    }

    public RenderedPrompt render(String promptId, Map<String, Object> variables) {
        PromptDefinition definition = prompts.get(promptId);
        if (definition == null) {
            throw new IllegalArgumentException("Unknown prompt id: " + promptId);
        }
        String raw = readClasspath(definition.path());
        if (definition.stripFrontmatter()) {
            raw = stripFrontmatter(raw);
        }
        String expanded = expandIncludes(raw, new LinkedHashSet<>());
        String rendered = substituteVariables(expanded, variables == null ? Map.of() : variables);
        return new RenderedPrompt(
                promptId,
                manifestVersion,
                rendered,
                shortHash(rendered)
        );
    }

    public RenderedPrompt renderRouted(String routingKey, Map<String, Object> context) {
        List<RoutingRule> rules = routingRules.get(routingKey);
        if (rules == null || rules.isEmpty()) {
            throw new IllegalArgumentException("Unknown routing key: " + routingKey);
        }
        for (RoutingRule rule : rules) {
            if (matches(rule.condition(), context)) {
                return render(rule.promptId(), context);
            }
        }
        throw new IllegalStateException("No routing rule matched for: " + routingKey);
    }

    private boolean matches(String condition, Map<String, Object> context) {
        if (condition == null || "default".equals(condition)) {
            return true;
        }
        if ("prefetched_ok".equals(condition)) {
            Object prefetched = context.get("prefetchedSearch");
            if (prefetched instanceof PrefetchedSearchView view) {
                return view.isUsable();
            }
            return Boolean.TRUE.equals(context.get("prefetchedOk"));
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private Map<String, PromptDefinition> parsePrompts(Object raw) {
        if (!(raw instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, PromptDefinition> parsed = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!(entry.getValue() instanceof Map<?, ?> value)) {
                continue;
            }
            String path = stringValue(value.get("path"), null);
            if (path == null) {
                continue;
            }
            boolean stripFrontmatter = Boolean.TRUE.equals(value.get("stripFrontmatter"));
            parsed.put(entry.getKey().toString(), new PromptDefinition(path, stripFrontmatter));
        }
        return Map.copyOf(parsed);
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> parseFragmentPaths(Object raw) {
        if (!(raw instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, String> parsed = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            parsed.put(entry.getKey().toString(), entry.getValue().toString());
        }
        return Map.copyOf(parsed);
    }

    @SuppressWarnings("unchecked")
    private Map<String, List<RoutingRule>> parseRouting(Object raw) {
        if (!(raw instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, List<RoutingRule>> parsed = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!(entry.getValue() instanceof Map<?, ?> routingMap)) {
                continue;
            }
            Object when = routingMap.get("when");
            if (!(when instanceof List<?> rules)) {
                continue;
            }
            List<RoutingRule> parsedRules = new ArrayList<>();
            for (Object item : rules) {
                if (item instanceof Map<?, ?> ruleMap) {
                    parsedRules.add(new RoutingRule(
                            stringValue(ruleMap.get("condition"), "default"),
                            stringValue(ruleMap.get("prompt"), null)
                    ));
                }
            }
            parsed.put(entry.getKey().toString(), List.copyOf(parsedRules));
        }
        return Map.copyOf(parsed);
    }

    private String expandIncludes(String content, Set<String> visited) {
        Matcher matcher = INCLUDE_PATTERN.matcher(content);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String includeRef = matcher.group(1).trim();
            String includePath = resolveIncludePath(includeRef);
            if (!visited.add(includePath)) {
                throw new IllegalStateException("Circular prompt include detected: " + includePath);
            }
            String included = readClasspath(includePath);
            String expandedIncluded = expandIncludes(included, visited);
            visited.remove(includePath);
            matcher.appendReplacement(out, Matcher.quoteReplacement(expandedIncluded));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private String resolveIncludePath(String includeRef) {
        String key = includeRef;
        if (key.startsWith("fragments/")) {
            key = key.substring("fragments/".length());
        }
        if (key.endsWith(".md")) {
            key = key.substring(0, key.length() - 3);
        }
        String fromManifest = fragmentPaths.get(key);
        if (fromManifest != null) {
            return normalizeClasspath(fromManifest);
        }
        if (includeRef.startsWith("classpath:")) {
            return normalizeClasspath(includeRef);
        }
        return "prompts/" + includeRef;
    }

    private String substituteVariables(String template, Map<String, Object> variables) {
        Matcher matcher = VARIABLE_PATTERN.matcher(template);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String name = matcher.group(1);
            String replacement = formatVariable(variables.get(name));
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private String formatVariable(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof String text) {
            return text;
        }
        try {
            return jsonMapper.writeValueAsString(value);
        } catch (Exception e) {
            return value.toString();
        }
    }

    private String readClasspath(String path) {
        String normalized = normalizeClasspath(path);
        try {
            ClassPathResource resource = new ClassPathResource(normalized);
            if (!resource.exists()) {
                throw new IllegalStateException("Prompt resource not found: " + normalized);
            }
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read prompt resource: " + normalized, e);
        }
    }

    private String normalizeClasspath(String path) {
        if (path == null) {
            return "";
        }
        return path.startsWith("classpath:") ? path.substring("classpath:".length()) : path;
    }

    private String stripFrontmatter(String content) {
        if (content == null || !content.startsWith("---")) {
            return content == null ? "" : content;
        }
        int end = content.indexOf("\n---", 3);
        if (end < 0) {
            return content;
        }
        int bodyStart = content.indexOf('\n', end + 4);
        if (bodyStart < 0) {
            return "";
        }
        return content.substring(bodyStart + 1).trim();
    }

    private String shortHash(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 4);
        } catch (NoSuchAlgorithmException e) {
            return "00000000";
        }
    }

    private String stringValue(Object value, String defaultValue) {
        return value == null ? defaultValue : value.toString();
    }

    private record PromptDefinition(String path, boolean stripFrontmatter) {
    }

    private record RoutingRule(String condition, String promptId) {
    }

    /** Minimal view for routing without coupling to orchestrator DTOs. */
    public interface PrefetchedSearchView {
        boolean isUsable();
    }
}
