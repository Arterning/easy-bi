package com.bi.ai;

import com.bi.model.dto.ApiResponse;
import com.bi.model.entity.ChatSession;
import com.bi.service.ChatSessionService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.*;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AgentService agentService;
    private final ChatSessionService sessionService;

    public AiController(AgentService agentService, ChatSessionService sessionService) {
        this.agentService = agentService;
        this.sessionService = sessionService;
    }

    // ==================== Chat (SSE) ====================

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@RequestBody Map<String, String> body) {
        String message = body.get("message");
        String sessionId = body.get("sessionId");
        boolean createNew = Boolean.parseBoolean(body.getOrDefault("createNew", "false"));

        if (sessionId == null || sessionId.isBlank()) {
            sessionId = UUID.randomUUID().toString();
        }

        if (message == null || message.isBlank()) {
            SseEmitter err = new SseEmitter();
            err.completeWithError(new IllegalArgumentException("message is required"));
            return err;
        }

        // Derive title from first N chars of user message
        String title = message.length() > 50 ? message.substring(0, 50) : message;

        SseEmitter emitter = new SseEmitter(300_000L); // 5 min timeout

        agentService.run(sessionId, message, title, createNew, new AgentService.AgentCallback() {
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

    // ==================== Session management ====================

    @GetMapping("/sessions")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> listSessions() {
        List<ChatSession> sessions = sessionService.list();
        List<Map<String, Object>> result = new ArrayList<>();
        for (ChatSession s : sessions) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", s.getId());
            item.put("title", s.getTitle());
            item.put("createdAt", s.getCreatedAt());
            item.put("updatedAt", s.getUpdatedAt());
            result.add(item);
        }
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @GetMapping("/sessions/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSession(@PathVariable String id) {
        ChatSession s = sessionService.get(id);
        if (s == null) return ResponseEntity.ok(ApiResponse.ok(Map.of("id", id, "messages", List.of())));

        List<Map<String, Object>> messages = sessionService.loadMessages(id);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", s.getId());
        result.put("title", s.getTitle());
        result.put("messages", messages != null ? messages : List.of());
        result.put("createdAt", s.getCreatedAt());
        result.put("updatedAt", s.getUpdatedAt());
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @DeleteMapping("/sessions/{id}")
    public ResponseEntity<ApiResponse<Map<String, String>>> deleteSession(@PathVariable String id) {
        sessionService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok("ok", Map.of("status", "ok")));
    }
}
