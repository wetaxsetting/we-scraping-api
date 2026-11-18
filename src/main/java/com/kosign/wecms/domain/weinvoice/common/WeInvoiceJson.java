package com.kosign.wecms.domain.weinvoice.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * JSON at the database boundary: the engine's {@code parsed} / {@code list_raw} and the request
 * payloads exchanged with the engine. Keys are the engine's own (snake_case already), so maps
 * and trees are used — the application's naming strategy applies to POJOs only.
 */
@Component
public class WeInvoiceJson {

    private static final TypeReference<LinkedHashMap<String, Object>> MAP = new TypeReference<>() {
    };

    private final ObjectMapper mapper;

    public WeInvoiceJson(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /** Parsed tree; {@code null}, blank or malformed text → a missing node (every path reads as absent). */
    public JsonNode tree(String json) {
        if (json == null || json.isBlank()) return MissingNode.getInstance();
        try {
            return mapper.readTree(json);
        } catch (JsonProcessingException e) {
            return MissingNode.getInstance();
        }
    }

    /** JSON object as a map; {@code null} when absent or not an object. */
    public Map<String, Object> map(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return mapper.readValue(json, MAP);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    public String write(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialise engine request payload", e);
        }
    }

    /** Trimmed text at {@code node.field}; {@code null} when absent, null or blank. */
    public static String text(JsonNode node, String field) {
        JsonNode v = node == null ? null : node.get(field);
        if (v == null || v.isNull() || v.isMissingNode() || v.isContainerNode()) return null;
        String s = v.asText().trim();
        return s.isEmpty() ? null : s;
    }

    /** Decimal at {@code node.field} (number or numeric text); {@code null} when absent or unreadable. */
    public static BigDecimal decimal(JsonNode node, String field) {
        JsonNode v = node == null ? null : node.get(field);
        if (v == null || v.isNull() || v.isMissingNode()) return null;
        if (v.isNumber()) return v.decimalValue();
        try {
            return new BigDecimal(v.asText().trim().replace(",", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Integer at {@code node.field}; {@code null} when absent or unreadable. */
    public static Integer integer(JsonNode node, String field) {
        BigDecimal d = decimal(node, field);
        try {
            return d == null ? null : d.intValueExact();
        } catch (ArithmeticException e) {
            return null;
        }
    }

    public static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) return v.trim();
        }
        return null;
    }
}
