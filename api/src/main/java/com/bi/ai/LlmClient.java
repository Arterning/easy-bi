package com.bi.ai;

import com.bi.service.LlmConfigService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

@Component
public class LlmClient {

    private static final Logger log = LoggerFactory.getLogger(LlmClient.class);

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
    private final ObjectMapper mapper = new ObjectMapper();

    private final LlmConfigService config;

    public LlmClient(LlmConfigService config) {
        this.config = config;
    }

    /**
     * Call the LLM with messages and optional tools. Returns parsed response.
     */
    public LlmResponse chat(List<Map<String, Object>> messages, List<Map<String, Object>> tools) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", config.getModel());
        body.put("messages", messages);
        body.put("max_tokens", config.getMaxTokens());
        body.put("temperature", config.getTemperature());
        if (tools != null && !tools.isEmpty()) {
            body.put("tools", tools);
            body.put("tool_choice", "auto");
        }

        try {
            String json = mapper.writeValueAsString(body);
            log.debug("LLM request ({} chars)", json.length());

            var request = HttpRequest.newBuilder()
                    .uri(URI.create(config.getBaseUrl() + "/chat/completions"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + config.getApiKey())
                    .timeout(Duration.ofSeconds(120))
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.error("LLM error {}: {}", response.statusCode(), response.body());
                throw new RuntimeException("LLM 调用失败: " + response.statusCode() + " " + response.body());
            }

            Map<String, Object> resp = mapper.readValue(response.body(),
                    new TypeReference<Map<String, Object>>() {});

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> choices = (List<Map<String, Object>>) resp.get("choices");
            if (choices == null || choices.isEmpty()) {
                throw new RuntimeException("LLM 返回空结果");
            }

            Map<String, Object> choice = choices.get(0);
            Map<String, Object> message = (Map<String, Object>) choice.get("message");

            LlmResponse result = new LlmResponse();
            result.content = (String) message.get("content");

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> toolCalls = (List<Map<String, Object>>) message.get("tool_calls");
            if (toolCalls != null) {
                result.toolCalls = new ArrayList<>();
                for (Map<String, Object> tc : toolCalls) {
                    LlmResponse.ToolCall call = new LlmResponse.ToolCall();
                    call.id = (String) tc.get("id");
                    Map<String, Object> func = (Map<String, Object>) tc.get("function");
                    call.name = (String) func.get("name");
                    call.arguments = (String) func.get("arguments");
                    result.toolCalls.add(call);
                }
            }

            return result;

        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("LLM 调用失败: " + e.getMessage(), e);
        }
    }

    /** Response from LLM. */
    public static class LlmResponse {
        public String content;
        public List<ToolCall> toolCalls;

        public boolean hasToolCalls() {
            return toolCalls != null && !toolCalls.isEmpty();
        }

        public static class ToolCall {
            public String id;
            public String name;
            public String arguments;
        }
    }
}
