package com.petito.jsontransformer.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Service responsible for transforming arbitrary JSON payloads according to a dynamic mapping model.
 * <p>
 * Supports key renaming, structural nesting, list iterations, fallback resolution to the root payload,
 * and primitive type conversions (e.g., string-to-boolean normalization).
 * </p>
 */
@Service
public class JsonTransformerService {

    /**
     * Reusable Jackson ObjectMapper for parsing input JSON strings into Map structures.
     */
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Declarative mapping definition defining target field aliases and nested object transformations.
     */
    private final Map<String, Object> mapping;

    /**
     * Injects the configured mapping model bean into the transformation engine.
     *
     * @param mapping The loaded transformation rule set (injected via @Qualifier("mappingModel")).
     */
    public JsonTransformerService(@Qualifier("mappingModel") Map<String, Object> mapping) {
        // Fallback to an empty map to ensure null-safety if the injected bean is null
        this.mapping = mapping != null ? mapping : Collections.emptyMap();
    }

    /**
     * Entry point for transforming a raw JSON string into the targeted schema.
     *
     * @param jsonInput The source JSON payload as a raw String.
     * @return A LinkedHashMap containing the transformed JSON structure preserving entry order.
     * @throws RuntimeException If JSON parsing or traversal fails.
     */
    public Map<String, Object> transformJson(String jsonInput) {
        try {
            // Parse incoming JSON into a generic nested Map representation
            Map<String, Object> inputMap = mapper.readValue(jsonInput, new TypeReference<>() {});
            
            // Execute transformation passing the parsed map as both local context and root reference
            return transform(inputMap, mapping, inputMap);
        } catch (Exception e) {
            throw new RuntimeException("Errore durante la trasformazione del JSON", e);
        }
    }

    /**
     * Root-level transformation loop iterating over defined mapping instructions.
     *
     * @param input     The current input map (at root level).
     * @param mapping   The schema mapping model rules.
     * @param rootInput Reference to the entire payload for cross-level field lookups.
     * @return Map containing transformed key-value pairs.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> transform(Map<String, Object> input, Map<String, Object> mapping, Map<String, Object> rootInput) {
        if (input == null || mapping == null) return Collections.emptyMap();

        // LinkedHashMap preserves insertion order as declared in the mapping definition
        Map<String, Object> output = new LinkedHashMap<>();

        for (Map.Entry<String, Object> entry : mapping.entrySet()) {
            String key = entry.getKey();
            Object mapVal = entry.getValue();

            // Case 1: Direct 1-to-1 field rename (e.g., "oldKey": "newKey")
            if (!(mapVal instanceof Map)) {
                String targetKey = String.valueOf(mapVal);
                if (input.containsKey(key)) {
                    output.put(targetKey, input.get(key));
                }
                continue;
            }

            // Case 2: Complex nested object, list, or custom target directive
            Map<String, Object> nestedMap = (Map<String, Object>) mapVal;
            
            // Resolve the output key name (defaults to source key if "_target" directive is omitted)
            String targetKey = nestedMap.get("_target") instanceof String ? (String) nestedMap.get("_target") : key;
            Object value = input.get(key);
            
            // Recursively process the nested value (object, list, or scalar)
            Object transformed = transformValue(value, nestedMap, rootInput, input.containsKey(key));
            
            // Retain the key if transformation produced data or the key was explicitly present in input
            if (transformed != null || input.containsKey(key)) {
                output.put(targetKey, transformed);
            }
        }

        return output;
    }

    /**
     * Recursively transforms a value based on mapping specifications.
     * Handles primitive values, nested Maps, and collections/arrays.
     *
     * @param value                  The source data element to transform.
     * @param mapping                The transformation specification for this node.
     * @param rootInput              Reference to the root payload for fallback variable resolution.
     * @param fieldExplicitlyPresent Flag indicating whether the source field existed in the parent payload.
     * @return Transformed object, list, or map.
     */
    @SuppressWarnings("unchecked")
    private Object transformValue(Object value, Object mapping, Map<String, Object> rootInput, boolean fieldExplicitlyPresent) {
        // If the mapping rule is not a specification map, return raw value untouched
        if (!(mapping instanceof Map)) return value;

        Map<String, Object> mapMapping = (Map<String, Object>) mapping;

        // --- ARRAY / LIST HANDLING ---
        if (value instanceof List) {
            List<Object> transformedList = new ArrayList<>();
            // Apply the mapping template recursively to each element within the array
            for (Object item : (List<?>) value) {
                Object transformedItem = transformValue(item, mapping, rootInput, true);
                if (transformedItem != null) transformedList.add(transformedItem);
            }
            return transformedList.isEmpty() && !fieldExplicitlyPresent ? null : transformedList;
        }

        // --- OBJECT / MAP HANDLING ---
        Map<String, Object> nestedOutput = new LinkedHashMap<>();
        Map<String, Object> sourceMap = (value instanceof Map) ? (Map<String, Object>) value : Collections.emptyMap();

        for (Map.Entry<String, Object> e : mapMapping.entrySet()) {
            String key = e.getKey();
            
            // "_target" is metadata for renaming the parent node, not a field to extract
            if ("_target".equals(key)) continue;

            Object targetSpec = e.getValue();
            
            // Check existence in local scope first; if absent, fallback to rootInput
            boolean keyPresent = sourceMap.containsKey(key) || rootInput.containsKey(key);
            Object fieldValue = sourceMap.containsKey(key) ? sourceMap.get(key) : rootInput.get(key);
            
            if (!keyPresent) continue;

            if (targetSpec instanceof Map) {
                // Recursively traverse inner complex object
                Object childTransformed = transformValue(fieldValue, targetSpec, rootInput, keyPresent);
                String childTarget = ((Map<String, Object>) targetSpec).get("_target") instanceof String ? 
                                     (String) ((Map<String, Object>) targetSpec).get("_target") : key;
                
                if (childTransformed != null || keyPresent) {
                    nestedOutput.put(childTarget, childTransformed);
                }
            } else if (targetSpec instanceof String) {
                // Terminal field: rename and apply scalar type conversion
                nestedOutput.put((String) targetSpec, convertValue(fieldValue));
            }
        }

        // Avoid returning empty containers when the source field was not explicitly provided
        return nestedOutput.isEmpty() && !fieldExplicitlyPresent ? null : nestedOutput;
    }

    /**
     * Converts raw scalar values to appropriate Java types.
     * <p>
     * Preserves native Booleans and Numbers, converts String literals "true"/"false" 
     * to boolean primitives, and falls back to string serialization.
     * </p>
     *
     * @param value The raw input object.
     * @return Type-converted object representation.
     */
    private Object convertValue(Object value) {
        if (value instanceof Boolean || value instanceof Number) {
            return value;
        } else if (value instanceof String s) {
            // Normalize case-insensitive string flags to native Boolean
            if ("true".equalsIgnoreCase(s) || "false".equalsIgnoreCase(s)) {
                return Boolean.parseBoolean(s);
            }
            return s;
        }
        return value != null ? value.toString() : null;
    }
}