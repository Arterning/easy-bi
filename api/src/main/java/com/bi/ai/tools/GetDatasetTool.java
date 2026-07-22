package com.bi.ai.tools;

import com.bi.ai.AiTool;
import com.bi.ai.ToolRegistry;
import com.bi.model.entity.Dataset;
import com.bi.repository.DatasetRepository;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class GetDatasetTool implements AiTool {

    private final DatasetRepository repo;

    public GetDatasetTool(ToolRegistry registry, DatasetRepository repo) {
        this.repo = repo;
        registry.register(this);
    }

    @Override public String name() { return "get_dataset"; }

    @Override public String description() { return "获取指定数据集的详情（名称 + SQL + 描述）"; }

    @Override
    public Map<String, Object> parameters() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "datasetId", Map.of("type", "integer", "description", "数据集 ID")
            ),
            "required", List.of("datasetId")
        );
    }

    @Override
    public String execute(Map<String, Object> args) {
        long id = ((Number) args.get("datasetId")).longValue();
        Dataset ds = repo.findById(id).orElse(null);
        if (ds == null) return "数据集不存在: id=" + id;
        return "名称: " + ds.getName() + "\nSQL: " + ds.getSql() +
               "\n描述: " + (ds.getDescription() != null ? ds.getDescription() : "(无)");
    }
}
