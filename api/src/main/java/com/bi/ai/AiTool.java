package com.bi.ai;

import java.util.Map;

/**
 * A tool that the AI agent can call.
 */
public interface AiTool {
    String name();
    String description();
    /** JSON Schema for parameters */
    Map<String, Object> parameters();
    /** Execute the tool and return a result string */
    String execute(Map<String, Object> args);
}
