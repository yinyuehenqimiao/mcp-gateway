package com.mcp.gateway.service.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mcp.gateway.domain.entity.ApiEndpoint;
import com.mcp.gateway.service.openapi.ParsedOperation;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class InputSchemaBuilder {

    private final ObjectMapper objectMapper;

    public InputSchemaBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String build(ApiEndpoint api) {
        if (api.getParametersJson() == null && api.getRequestBodySchema() == null) {
            return "{\"type\":\"object\",\"properties\":{}}";
        }
        try {
            List<ParsedOperation.Param> params = readParams(api.getParametersJson());
            ObjectNode schema = objectMapper.createObjectNode();
            schema.put("type", "object");
            ObjectNode properties = schema.putObject("properties");
            ArrayNode required = objectMapper.createArrayNode();

            for (ParsedOperation.Param parameter : params) {
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

            if (api.getRequestBodySchema() != null && !api.getRequestBodySchema().isBlank()) {
                ObjectNode bodyProperty = properties.putObject("body");
                JsonNode jsonSchema = objectMapper.readTree(api.getRequestBodySchema());
                if (jsonSchema.isObject()) {
                    bodyProperty.setAll((ObjectNode) jsonSchema.deepCopy());
                } else {
                    bodyProperty.put("type", "object");
                }
                if (!bodyProperty.has("type")) {
                    bodyProperty.put("type", "object");
                }
                if (!bodyProperty.has("description")) {
                    bodyProperty.put("description", "JSON request body");
                }
                required.add("body");
            }

            if (!required.isEmpty()) {
                schema.set("required", required);
            }
            return schema.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("构建 inputSchema 失败: " + api.getToolName(), ex);
        }
    }

    public List<ParsedOperation.Param> readParams(String parametersJson) {
        List<ParsedOperation.Param> params = new ArrayList<>();
        if (parametersJson == null || parametersJson.isBlank()) {
            return params;
        }
        try {
            JsonNode array = objectMapper.readTree(parametersJson);
            if (!array.isArray()) {
                return params;
            }
            for (JsonNode node : array) {
                params.add(new ParsedOperation.Param(
                        node.path("name").asText(),
                        node.path("in").asText("query"),
                        node.path("description").asText(null),
                        node.path("required").asBoolean(false),
                        node.path("type").asText("string")
                ));
            }
            return params;
        } catch (Exception ex) {
            throw new IllegalStateException("解析 parameters_json 失败", ex);
        }
    }
}
