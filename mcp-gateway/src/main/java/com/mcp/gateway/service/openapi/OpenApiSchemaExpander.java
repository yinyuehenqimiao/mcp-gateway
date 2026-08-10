package com.mcp.gateway.service.openapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Map;

/**
 * 递归展开 OpenAPI/Swagger 的 $ref（含 components/schemas），保留字段 description/type/format/enum。
 */
@Component
public class OpenApiSchemaExpander {

    private static final int MAX_DEPTH = 12;

    private final ObjectMapper objectMapper;

    public OpenApiSchemaExpander(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public JsonNode expand(JsonNode documentRoot, JsonNode schemaNode) {
        return expand(documentRoot, schemaNode, new ArrayDeque<>(), 0);
    }

    private JsonNode expand(JsonNode root, JsonNode node, Deque<String> stack, int depth) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return objectMapper.createObjectNode().put("type", "object");
        }
        if (depth > MAX_DEPTH) {
            return objectMapper.createObjectNode()
                    .put("type", "object")
                    .put("description", "schema 嵌套过深，已截断");
        }

        String ref = text(node.get("$ref"));
        if (ref != null && !ref.isBlank()) {
            if (stack.contains(ref)) {
                ObjectNode cycle = objectMapper.createObjectNode();
                cycle.put("type", "object");
                cycle.put("description", "循环引用: " + ref);
                return cycle;
            }
            JsonNode target = resolveRef(root, ref);
            stack.push(ref);
            JsonNode expanded = expand(root, target, stack, depth + 1);
            stack.pop();
            // 保留引用处额外字段（如 description）
            if (node.isObject() && node.size() > 1 && expanded.isObject()) {
                ObjectNode merged = expanded.deepCopy();
                Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> entry = fields.next();
                    if ("$ref".equals(entry.getKey())) {
                        continue;
                    }
                    merged.set(entry.getKey(), expand(root, entry.getValue(), stack, depth + 1));
                }
                return merged;
            }
            return expanded;
        }

        if (node.isArray()) {
            ArrayNode array = objectMapper.createArrayNode();
            for (JsonNode item : node) {
                array.add(expand(root, item, stack, depth + 1));
            }
            return array;
        }

        if (!node.isObject()) {
            return node.deepCopy();
        }

        // allOf：合并为一份 object schema，便于 MCP 使用
        if (node.has("allOf") && node.get("allOf").isArray()) {
            ObjectNode merged = objectMapper.createObjectNode();
            merged.put("type", "object");
            ObjectNode properties = objectMapper.createObjectNode();
            ArrayNode required = objectMapper.createArrayNode();
            for (JsonNode part : node.get("allOf")) {
                JsonNode expandedPart = expand(root, part, stack, depth + 1);
                mergeObjectSchema(merged, properties, required, expandedPart);
            }
            if (!properties.isEmpty()) {
                merged.set("properties", properties);
            }
            if (!required.isEmpty()) {
                merged.set("required", required);
            }
            copyAnnotations(node, merged);
            return merged;
        }

        ObjectNode result = objectMapper.createObjectNode();
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String key = entry.getKey();
            JsonNode value = entry.getValue();
            if ("properties".equals(key) && value.isObject()) {
                ObjectNode props = result.putObject("properties");
                Iterator<Map.Entry<String, JsonNode>> propFields = value.fields();
                while (propFields.hasNext()) {
                    Map.Entry<String, JsonNode> prop = propFields.next();
                    props.set(prop.getKey(), expand(root, prop.getValue(), stack, depth + 1));
                }
            } else if ("items".equals(key) || "additionalProperties".equals(key)
                    || "not".equals(key) || "schema".equals(key)) {
                result.set(key, expand(root, value, stack, depth + 1));
            } else if ("oneOf".equals(key) || "anyOf".equals(key)) {
                result.set(key, expand(root, value, stack, depth + 1));
            } else {
                result.set(key, value.deepCopy());
            }
        }
        return result;
    }

    private void mergeObjectSchema(ObjectNode merged, ObjectNode properties, ArrayNode required, JsonNode part) {
        if (part == null || !part.isObject()) {
            return;
        }
        if (part.has("description") && !merged.has("description")) {
            merged.set("description", part.get("description").deepCopy());
        }
        if (part.has("title") && !merged.has("title")) {
            merged.set("title", part.get("title").deepCopy());
        }
        JsonNode props = part.get("properties");
        if (props != null && props.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = props.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                properties.set(entry.getKey(), entry.getValue().deepCopy());
            }
        }
        JsonNode req = part.get("required");
        if (req != null && req.isArray()) {
            for (JsonNode item : req) {
                required.add(item.deepCopy());
            }
        }
    }

    private void copyAnnotations(JsonNode source, ObjectNode target) {
        for (String key : new String[]{"description", "title", "example", "deprecated"}) {
            if (source.has(key) && !target.has(key)) {
                target.set(key, source.get(key).deepCopy());
            }
        }
    }

    private JsonNode resolveRef(JsonNode root, String ref) {
        if (ref == null || !ref.startsWith("#/")) {
            ObjectNode fallback = objectMapper.createObjectNode();
            fallback.put("type", "object");
            fallback.put("description", "无法解析外部引用: " + ref);
            return fallback;
        }
        JsonNode current = root;
        for (String rawPart : ref.substring(2).split("/")) {
            String part = rawPart.replace("~1", "/").replace("~0", "~");
            // SpringDoc 偶发 URL 编码
            try {
                part = java.net.URLDecoder.decode(part, java.nio.charset.StandardCharsets.UTF_8);
            } catch (Exception ignored) {
                // keep original
            }
            current = current.path(part);
        }
        if (current.isMissingNode() || current.isNull()) {
            ObjectNode missing = objectMapper.createObjectNode();
            missing.put("type", "object");
            missing.put("description", "未找到 schema 引用: " + ref);
            return missing;
        }
        return current;
    }

    private static String text(JsonNode node) {
        return node == null || node.isNull() ? null : node.asText();
    }
}
