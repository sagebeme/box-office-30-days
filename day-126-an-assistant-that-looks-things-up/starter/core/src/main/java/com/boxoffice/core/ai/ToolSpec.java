package com.boxoffice.core.ai;

import java.util.List;
import java.util.Map;

/**
 * A lookup Claude may ask for: its name, what it's for, and what it takes.
 * The description is for Claude, so it says when to use the tool, not how
 * it works inside.
 *
 * @param properties each input's JSON Schema, e.g. "code" -> {"type": "string"}
 * @param required   the inputs Claude must always give
 */
public record ToolSpec(String name, String description, Map<String, Map<String, Object>> properties,
                       List<String> required) {

    public ToolSpec {
        properties = Map.copyOf(properties);
        required = List.copyOf(required);
    }
}
