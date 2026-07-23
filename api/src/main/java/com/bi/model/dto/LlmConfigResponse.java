package com.bi.model.dto;

import com.bi.model.entity.LlmConfig;

/**
 * Returned to frontend — apiKey is masked.
 */
public class LlmConfigResponse {

    private String baseUrl;
    private String apiKey;    // masked
    private String model;
    private int maxTokens;
    private double temperature;

    public static LlmConfigResponse from(LlmConfig c) {
        LlmConfigResponse r = new LlmConfigResponse();
        r.baseUrl = c.getBaseUrl();
        r.apiKey = mask(c.getApiKey());
        r.model = c.getModel();
        r.maxTokens = c.getMaxTokens();
        r.temperature = c.getTemperature();
        return r;
    }

    private static String mask(String key) {
        if (key == null || key.isEmpty()) return "";
        if (key.length() <= 8) return "****";
        return key.substring(0, 4) + "****" + key.substring(key.length() - 4);
    }

    // --- Getters / Setters ---

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public int getMaxTokens() { return maxTokens; }
    public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }

    public double getTemperature() { return temperature; }
    public void setTemperature(double temperature) { this.temperature = temperature; }
}
