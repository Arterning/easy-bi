package com.bi.service;

import com.bi.model.entity.ChatSession;
import com.bi.repository.ChatSessionRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class ChatSessionService {

    private static final Logger log = LoggerFactory.getLogger(ChatSessionService.class);

    private final ChatSessionRepository repo;
    private final ObjectMapper mapper = new ObjectMapper();

    public ChatSessionService(ChatSessionRepository repo) {
        this.repo = repo;
    }

    /** Create an empty session. */
    @Transactional
    public ChatSession create(String id, String title, List<Map<String, Object>> messages) {
        ChatSession s = new ChatSession();
        s.setId(id);
        s.setTitle(title);
        s.setMessages(toJson(messages));
        repo.save(s);
        log.info("Chat session created: id={}", id);
        return s;
    }

    /** Load session messages as List<Map>. Returns null if not found. */
    public List<Map<String, Object>> loadMessages(String sessionId) {
        return repo.findById(sessionId)
                .map(s -> fromJson(s.getMessages()))
                .orElse(null);
    }

    /** Save messages back to DB for an existing session. */
    @Transactional
    public void saveMessages(String sessionId, String title, List<Map<String, Object>> messages) {
        ChatSession s = repo.findById(sessionId).orElse(null);
        if (s == null) {
            s = new ChatSession();
            s.setId(sessionId);
        }
        if (title != null) s.setTitle(title);
        s.setMessages(toJson(messages));
        repo.save(s);
    }

    /** List all sessions (summary only — no messages). */
    public List<ChatSession> list() {
        return repo.findAllByOrderByUpdatedAtDesc();
    }

    /** Get full session with messages. */
    public ChatSession get(String id) {
        return repo.findById(id).orElse(null);
    }

    @Transactional
    public void delete(String id) {
        repo.deleteById(id);
        log.info("Chat session deleted: id={}", id);
    }

    // ---- JSON helpers ----

    String toJson(List<Map<String, Object>> messages) {
        try {
            return mapper.writeValueAsString(messages);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize messages", e);
        }
    }

    List<Map<String, Object>> fromJson(String json) {
        try {
            return mapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize messages", e);
        }
    }
}
