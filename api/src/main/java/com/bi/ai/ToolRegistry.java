package com.bi.ai;

import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class ToolRegistry {

    private final Map<String, AiTool> tools = new LinkedHashMap<>();

    public void register(AiTool tool) {
        tools.put(tool.name(), tool);
    }

    public Optional<AiTool> get(String name) {
        return Optional.ofNullable(tools.get(name));
    }

    public Map<String, AiTool> all() {
        return Collections.unmodifiableMap(tools);
    }

    /**
     * Build the `tools` array for OpenAI function-calling format.
     */
    public List<Map<String, Object>> getFunctionDefinitions() {
        List<Map<String, Object>> defs = new ArrayList<>();
        for (AiTool tool : tools.values()) {
            Map<String, Object> func = new LinkedHashMap<>();
            func.put("name", tool.name());
            func.put("description", tool.description());
            func.put("parameters", tool.parameters());
            defs.add(Map.of("type", "function", "function", func));
        }
        return defs;
    }
}
