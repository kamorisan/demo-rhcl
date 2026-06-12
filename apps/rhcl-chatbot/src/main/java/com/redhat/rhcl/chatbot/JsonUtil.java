package com.redhat.rhcl.chatbot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quarkus.arc.Unremovable;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Utility class for JSON parsing and manipulation using Jackson.
 */
@ApplicationScoped
@Unremovable
public class JsonUtil {

    @Inject
    ObjectMapper mapper;

    /**
     * Parse a JSON string into a JsonNode.
     *
     * @param json JSON string
     * @return JsonNode representation
     * @throws Exception if parsing fails
     */
    public JsonNode readTree(String json) throws Exception {
        if (json == null || json.isBlank()) {
            return mapper.createObjectNode();
        }
        return mapper.readTree(json);
    }

    /**
     * Convert a Java object to a JSON string.
     *
     * @param obj Java object
     * @return JSON string
     * @throws Exception if serialization fails
     */
    public String write(Object obj) throws Exception {
        if (obj == null) {
            return "{}";
        }
        return mapper.writeValueAsString(obj);
    }

    /**
     * Create a new empty ObjectNode.
     *
     * @return new ObjectNode
     */
    public ObjectNode obj() {
        return mapper.createObjectNode();
    }

    /**
     * Create a new empty ArrayNode.
     *
     * @return new ArrayNode
     */
    public ArrayNode arr() {
        return mapper.createArrayNode();
    }

    /**
     * Get the underlying ObjectMapper instance.
     *
     * @return ObjectMapper
     */
    public ObjectMapper mapper() {
        return mapper;
    }
}
