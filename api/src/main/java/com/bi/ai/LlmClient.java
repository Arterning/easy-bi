package com.bi.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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

    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final int maxTokens;
    private final double temperature;

    public LlmClient(
            @Value("${ai.llm.base-url}") String baseUrl,
            @Value("${ai.llm.api-key:}") String apiKey,
            @Value("${ai.llm.model}") String model,
            @Value("${ai.llm.max-tokens:4096}") int maxTokens,
            @Value("${ai.llm.temperature:0.1}") double temperature) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.model = model;
        this.maxTokens = maxTokens;
        this.temperature = temperature;
    }

    /**
     * Call the LLM with messages and optional tools. Returns parsed response.
     */
    public LlmResponse chat(List<Map<String, Object>> messages, List<Map<String, Object>> tools) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("max_tokens", maxTokens);
        body.put("temperature", temperature);
        if (tools != null && !tools.isEmpty()) {
            body.put("tools", tools);
            body.put("tool_choice", "auto");
        }

        try {
            String json = mapper.writeValueAsString(body);
            log.debug("LLM request ({} chars)", json.length());

            var request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/chat/completions"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
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
