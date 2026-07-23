package com.bi.service;

import com.bi.model.dto.LlmConfigRequest;
import com.bi.model.dto.LlmConfigResponse;
import com.bi.model.entity.LlmConfig;
import com.bi.repository.LlmConfigRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manages LLM configuration with DB persistence and env-var fallback.
 * Priority: user-saved DB config > environment variables > application.yml defaults.
 *
 * Thread safety: the config holder is replaced atomically via volatile.
 */
@Service
public class LlmConfigService {

    private static final Logger log = LoggerFactory.getLogger(LlmConfigService.class);

    private final LlmConfigRepository repo;

    // Env/yml defaults (used when no user config exists)
    private final String defaultBaseUrl;
    private final String defaultApiKey;
    private final String defaultModel;
    private final int defaultMaxTokens;
    private final double defaultTemperature;

    /** Current effective config — replaced atomically. */
    private volatile LlmConfig current;

    public LlmConfigService(LlmConfigRepository repo,
                            @Value("${ai.llm.base-url}") String defaultBaseUrl,
                            @Value("${ai.llm.api-key:}") String defaultApiKey,
                            @Value("${ai.llm.model}") String defaultModel,
                            @Value("${ai.llm.max-tokens:4096}") int defaultMaxTokens,
                            @Value("${ai.llm.temperature:0.1}") double defaultTemperature) {
        this.repo = repo;
        this.defaultBaseUrl = defaultBaseUrl;
        this.defaultApiKey = defaultApiKey;
        this.defaultModel = defaultModel;
        this.defaultMaxTokens = defaultMaxTokens;
        this.defaultTemperature = defaultTemperature;
    }

    @PostConstruct
    void init() {
        LlmConfig db = repo.findAll().stream().findFirst().orElse(null);
        if (db != null) {
            log.info("LLM config loaded from DB: model={}, baseUrl={}", db.getModel(), db.getBaseUrl());
            current = db;
        } else {
            current = createDefault();
            repo.save(current);
            log.info("LLM config initialized from env defaults: model={}", current.getModel());
        }
    }

    private LlmConfig createDefault() {
        LlmConfig c = new LlmConfig();
        c.setBaseUrl(defaultBaseUrl);
        c.setApiKey(defaultApiKey);
        c.setModel(defaultModel);
        c.setMaxTokens(defaultMaxTokens);
        c.setTemperature(defaultTemperature);
        return c;
    }

    // ---- Thread-safe readers (no lock needed — volatile read) ----

    public String getBaseUrl()      { return current.getBaseUrl(); }
    public String getApiKey()       { return current.getApiKey(); }
    public String getModel()        { return current.getModel(); }
    public int getMaxTokens()       { return current.getMaxTokens(); }
    public double getTemperature()  { return current.getTemperature(); }

    // ---- API ----

    /** Return config with apiKey masked for frontend display. */
    public LlmConfigResponse getConfig() {
        return LlmConfigResponse.from(current);
    }

    /** Update config. If apiKey is blank, keep the existing one. */
    @Transactional
    public LlmConfigResponse updateConfig(LlmConfigRequest req) {
        LlmConfig c = repo.findAll().stream().findFirst().orElseGet(this::createDefault);

        c.setBaseUrl(req.getBaseUrl());
        c.setModel(req.getModel());
        c.setMaxTokens(req.getMaxTokens());
        c.setTemperature(req.getTemperature());

        // Only update apiKey when the user provides a new one (not masked placeholder)
        if (req.getApiKey() != null && !req.getApiKey().isBlank() && !req.getApiKey().contains("****")) {
            c.setApiKey(req.getApiKey());
        }

        repo.save(c);
        current = c; // atomic publish
        log.info("LLM config updated: model={}, baseUrl={}", c.getModel(), c.getBaseUrl());
        return LlmConfigResponse.from(c);
    }
}
