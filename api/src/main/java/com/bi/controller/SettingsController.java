package com.bi.controller;

import com.bi.model.dto.ApiResponse;
import com.bi.model.dto.LlmConfigRequest;
import com.bi.model.dto.LlmConfigResponse;
import com.bi.service.LlmConfigService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    private final LlmConfigService configService;

    public SettingsController(LlmConfigService configService) {
        this.configService = configService;
    }

    @GetMapping("/llm")
    public ResponseEntity<ApiResponse<LlmConfigResponse>> getLlmConfig() {
        return ResponseEntity.ok(ApiResponse.ok(configService.getConfig()));
    }

    @PutMapping("/llm")
    public ResponseEntity<ApiResponse<LlmConfigResponse>> updateLlmConfig(@RequestBody LlmConfigRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("保存成功", configService.updateConfig(req)));
    }
}
