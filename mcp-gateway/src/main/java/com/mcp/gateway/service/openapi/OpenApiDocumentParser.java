package com.mcp.gateway.service.openapi;

import com.mcp.gateway.common.exception.BusinessException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 解析 OpenAPI 3.x / Swagger 2.0 JSON，输出统一的 ParsedOperation。
 * requestBody / 参数 schema 会递归展开 components/schemas（含字段 description）。
 */
@Component
public class OpenApiDocumentParser {

    private final ObjectMapper objectMapper;
    private final OpenApiSchemaExpander schemaExpander;

    public OpenApiDocumentParser(ObjectMapper objectMapper, OpenApiSchemaExpander schemaExpander) {
        this.objectMapper = objectMapper;
        this.schemaExpander = schemaExpander;
    }

    public List<ParsedOperation> parse(String documentJson) {
        try {
            JsonNode root = objectMapper.readTree(documentJson);
            if (root.has("swagger") && root.path("swagger").asText().startsWith("2.")) {
                return parseSwagger2(root);
            }
            return parseOpenApi3(root);
        } catch (Exception ex) {
            throw new BusinessException("无法解析 OpenAPI/Swagger 文档: " + ex.getMessage(), ex);
        }
    }

    private List<ParsedOperation> parseOpenApi3(JsonNode root) {
        JsonNode paths = root.path("paths");
        if (!paths.isObject()) {
            throw new BusinessException("OpenAPI 文档缺少 paths");
        }
        List<ParsedOperation> result = new ArrayList<>();
        Iterator<Map.Entry<String, JsonNode>> pathFields = paths.fields();
        while (pathFields.hasNext()) {
            Map.Entry<String, JsonNode> pathEntry = pathFields.next();
            String pathTemplate = pathEntry.getKey();
            JsonNode pathItem = pathEntry.getValue();
            List<ParsedOperation.Param> pathLevelParams = readParameters(root, pathItem.get("parameters"));

            Iterator<Map.Entry<String, JsonNode>> methodFields = pathItem.fields();
            while (methodFields.hasNext()) {
                Map.Entry<String, JsonNode> methodEntry = methodFields.next();
                String method = methodEntry.getKey().toLowerCase(Locale.ROOT);
                if (!isHttpMethod(method)) {
                    continue;
                }
                JsonNode operation = methodEntry.getValue();
                List<ParsedOperation.Param> params = new ArrayList<>(pathLevelParams);
                params.addAll(readParameters(root, operation.get("parameters")));

                boolean hasBody = operation.has("requestBody") && !operation.get("requestBody").isNull();
                String bodySchema = null;
                if (hasBody) {
                    JsonNode schema = operation.path("requestBody")
                            .path("content").path("application/json").path("schema");
                    if (schema.isMissingNode() || schema.isNull()) {
                        // fallback first content type
                        JsonNode content = operation.path("requestBody").path("content");
                        if (content.isObject() && content.fields().hasNext()) {
                            schema = content.fields().next().getValue().path("schema");
                        }
                    }
                    JsonNode expanded = schemaExpander.expand(root, schema);
                    bodySchema = expanded == null || expanded.isMissingNode() || expanded.isNull()
                            ? "{\"type\":\"object\"}"
                            : expanded.toString();
                }

                result.add(toOperation(root, pathTemplate, method, operation, params, bodySchema,
                        hasBody && operation.path("requestBody").path("required").asBoolean(true)));
            }
        }
        return result;
    }

    private List<ParsedOperation> parseSwagger2(JsonNode root) {
        JsonNode paths = root.path("paths");
        if (!paths.isObject()) {
            throw new BusinessException("Swagger 文档缺少 paths");
        }
        List<ParsedOperation> result = new ArrayList<>();
        Iterator<Map.Entry<String, JsonNode>> pathFields = paths.fields();
        while (pathFields.hasNext()) {
            Map.Entry<String, JsonNode> pathEntry = pathFields.next();
            String pathTemplate = pathEntry.getKey();
            JsonNode pathItem = pathEntry.getValue();
            List<ParsedOperation.Param> pathLevelParams = readParameters(root, pathItem.get("parameters"));

            Iterator<Map.Entry<String, JsonNode>> methodFields = pathItem.fields();
            while (methodFields.hasNext()) {
                Map.Entry<String, JsonNode> methodEntry = methodFields.next();
                String method = methodEntry.getKey().toLowerCase(Locale.ROOT);
                if (!isHttpMethod(method)) {
                    continue;
                }
                JsonNode operation = methodEntry.getValue();
                List<ParsedOperation.Param> params = new ArrayList<>(pathLevelParams);
                params.addAll(readParameters(root, operation.get("parameters")));

                String bodySchema = null;
                boolean bodyRequired = false;
                for (ParsedOperation.Param param : List.copyOf(params)) {
                    if ("body".equalsIgnoreCase(param.in())) {
                        bodyRequired = param.required();
                        // Swagger2 body schema is stored separately via original node; rebuild below
                    }
                }
                JsonNode parameters = operation.get("parameters");
                if (parameters != null && parameters.isArray()) {
                    for (JsonNode parameterNode : parameters) {
                        if ("body".equalsIgnoreCase(textOrNull(parameterNode.get("in")))) {
                            JsonNode expanded = schemaExpander.expand(root, parameterNode.path("schema"));
                            bodySchema = expanded == null || expanded.isMissingNode() || expanded.isNull()
                                    ? "{\"type\":\"object\"}"
                                    : expanded.toString();
                            bodyRequired = parameterNode.path("required").asBoolean(true);
                            params.removeIf(p -> "body".equalsIgnoreCase(p.in()));
                            break;
                        }
                    }
                }

                result.add(toOperation(root, pathTemplate, method, operation, params, bodySchema, bodyRequired));
            }
        }
        return result;
    }

    private ParsedOperation toOperation(
            JsonNode root,
            String pathTemplate,
            String method,
            JsonNode operation,
            List<ParsedOperation.Param> params,
            String bodySchema,
            boolean bodyRequired) {
        String operationId = textOrNull(operation.get("operationId"));
        String summary = textOrNull(operation.get("summary"));
        String description = textOrNull(operation.get("description"));
        if (summary == null || summary.isBlank()) {
            summary = description;
        }
        if (summary == null || summary.isBlank()) {
            summary = method.toUpperCase(Locale.ROOT) + " " + pathTemplate;
        }

        String toolName = (operationId == null || operationId.isBlank())
                ? sanitize(method + "_" + pathTemplate)
                : sanitize(operationId);

        String parametersJson;
        String inputSchemaJson;
        try {
            parametersJson = objectMapper.writeValueAsString(params);
            inputSchemaJson = buildInputSchema(params, bodySchema, bodyRequired);
        } catch (Exception ex) {
            throw new IllegalStateException("构建参数 schema 失败", ex);
        }

        return new ParsedOperation(
                summary,
                toolName,
                description == null || description.isBlank() ? summary : description,
                method.toUpperCase(Locale.ROOT),
                pathTemplate,
                operationId,
                params,
                bodySchema,
                parametersJson,
                inputSchemaJson
        );
    }

    private List<ParsedOperation.Param> readParameters(JsonNode root, JsonNode parameterNodes) {
        List<ParsedOperation.Param> params = new ArrayList<>();
        if (parameterNodes == null || !parameterNodes.isArray()) {
            return params;
        }
        for (JsonNode parameterNode : parameterNodes) {
            JsonNode resolved = parameterNode;
            String ref = textOrNull(parameterNode.get("$ref"));
            if (ref != null) {
                resolved = resolveRef(root, ref);
            }
            String name = textOrNull(resolved.get("name"));
            String in = textOrNull(resolved.get("in"));
            if (name == null || in == null) {
                continue;
            }
            if (!"path".equalsIgnoreCase(in) && !"query".equalsIgnoreCase(in)
                    && !"header".equalsIgnoreCase(in) && !"body".equalsIgnoreCase(in)) {
                continue;
            }
            boolean required = resolved.path("required").asBoolean("path".equalsIgnoreCase(in));
            String description = textOrNull(resolved.get("description"));
            JsonNode schemaNode = resolved.has("schema") ? resolved.get("schema") : resolved;
            JsonNode expandedSchema = schemaExpander.expand(root, schemaNode);
            String type = expandedSchema.path("type").asText(null);
            if (type == null || type.isBlank()) {
                type = "string";
            }
            // 复杂 query/header 对象：把展开后的 schema 摘要写进 description，便于人工查看
            if (("object".equals(type) || "array".equals(type)) && description != null) {
                description = description + " | schema=" + compactSchemaHint(expandedSchema);
            } else if (("object".equals(type) || "array".equals(type)) && description == null) {
                description = compactSchemaHint(expandedSchema);
            }
            params.add(new ParsedOperation.Param(
                    name,
                    in.toLowerCase(Locale.ROOT),
                    description == null ? name : description,
                    required,
                    type
            ));
        }
        return params;
    }

    private String compactSchemaHint(JsonNode schema) {
        if (schema == null || !schema.isObject()) {
            return "object";
        }
        if ("array".equals(schema.path("type").asText())) {
            JsonNode items = schema.path("items");
            if (items.has("properties") && items.get("properties").isObject()) {
                return "array<" + String.join(",", iterableNames(items.get("properties"))) + ">";
            }
            return "array<" + items.path("type").asText("object") + ">";
        }
        if (schema.has("properties") && schema.get("properties").isObject()) {
            return "object{" + String.join(",", iterableNames(schema.get("properties"))) + "}";
        }
        return schema.path("type").asText("object");
    }

    private static Iterable<String> iterableNames(JsonNode properties) {
        List<String> names = new ArrayList<>();
        properties.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private String buildInputSchema(List<ParsedOperation.Param> parameters, String bodySchema, boolean bodyRequired)
            throws Exception {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");
        ArrayNode required = objectMapper.createArrayNode();

        for (ParsedOperation.Param parameter : parameters) {
            if ("body".equalsIgnoreCase(parameter.in())) {
                continue;
            }
            ObjectNode property = properties.putObject(parameter.name());
            property.put("type", parameter.type() == null ? "string" : parameter.type());
            if (parameter.description() != null) {
                property.put("description", parameter.description());
            }
            if (parameter.required()) {
                required.add(parameter.name());
            }
        }

        if (bodySchema != null && !bodySchema.isBlank()) {
            ObjectNode bodyProperty = properties.putObject("body");
            JsonNode jsonSchema = objectMapper.readTree(bodySchema);
            if (jsonSchema.isObject()) {
                bodyProperty.setAll((ObjectNode) jsonSchema.deepCopy());
            } else {
                bodyProperty.put("type", "object");
            }
            if (!bodyProperty.has("type")) {
                bodyProperty.put("type", "object");
            }
            // 把 DTO 标题/说明提升到 body 描述，方便 Agent 理解
            if (!bodyProperty.has("description")) {
                String title = bodyProperty.path("title").asText(null);
                String desc = bodyProperty.path("description").asText(null);
                if (desc != null && !desc.isBlank()) {
                    bodyProperty.put("description", desc);
                } else if (title != null && !title.isBlank()) {
                    bodyProperty.put("description", title);
                } else {
                    bodyProperty.put("description", "JSON request body（已展开 schemas 字段）");
                }
            }
            if (bodyRequired) {
                required.add("body");
            }
        }

        if (!required.isEmpty()) {
            schema.set("required", required);
        }
        return schema.toString();
    }

    private JsonNode resolveRef(JsonNode root, String ref) {
        return schemaExpander.expand(root, objectMapper.createObjectNode().put("$ref", ref));
    }

    private static boolean isHttpMethod(String method) {
        return switch (method) {
            case "get", "post", "put", "delete", "patch" -> true;
            default -> false;
        };
    }

    private static String textOrNull(JsonNode node) {
        return node == null || node.isNull() ? null : node.asText();
    }

    public static String sanitize(String raw) {
        String cleaned = raw.replaceAll("[^a-zA-Z0-9_\\-.]", "_");
        if (cleaned.isBlank()) {
            return "tool";
        }
        if (Character.isDigit(cleaned.charAt(0))) {
            return "tool_" + cleaned;
        }
        if (cleaned.length() > 128) {
            return cleaned.substring(0, 128);
        }
        return cleaned;
    }
}
