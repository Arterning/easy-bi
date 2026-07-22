package com.bi.ai;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AgentService agentService;

    public AiController(AgentService agentService) {
        this.agentService = agentService;
    }

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@RequestBody Map<String, String> body) {
        String message = body.get("message");
        String sessionId = body.getOrDefault("sessionId", UUID.randomUUID().toString());

        if (message == null || message.isBlank()) {
            SseEmitter err = new SseEmitter();
            err.completeWithError(new IllegalArgumentException("message is required"));
            return err;
        }

        SseEmitter emitter = new SseEmitter(300_000L); // 5 min timeout

        agentService.run(sessionId, message, new AgentService.AgentCallback() {
            @Override
            public void onThinking(String text) {
                send(emitter, "thinking", text);
            }

            @Override
            public void onToolCall(String toolName, String arguments) {
                send(emitter, "tool_call", Map.of("tool", toolName, "args", arguments));
            }

            @Override
            public void onToolResult(String toolName, String result) {
                send(emitter, "tool_result", Map.of("tool", toolName, "result", result));
            }

            @Override
            public void onMessage(String text) {
                send(emitter, "message", text);
            }

            @Override
            public void onError(String error) {
                send(emitter, "error", error);
                emitter.complete();
            }

            @Override
            public void onDone() {
                send(emitter, "done", "{}");
                emitter.complete();
            }

            private void send(SseEmitter e, String event, Object data) {
                try {
                    e.send(SseEmitter.event().name(event).data(data));
                } catch (IOException ex) {
                    e.completeWithError(ex);
                }
            }
        });

        return emitter;
    }

    @DeleteMapping("/session/{sessionId}")
    public Map<String, String> clearSession(@PathVariable String sessionId) {
        agentService.clearSession(sessionId);
        return Map.of("status", "ok");
    }
}
