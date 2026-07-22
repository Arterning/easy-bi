package com.bi.ai.tools;

import com.bi.ai.AiTool;
import com.bi.ai.ToolRegistry;
import com.bi.model.entity.Dataset;
import com.bi.repository.DatasetRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class ListDatasetsTool implements AiTool {

    private final DatasetRepository repo;

    public ListDatasetsTool(ToolRegistry registry, DatasetRepository repo) {
        this.repo = repo;
        registry.register(this);
    }

    @Override public String name() { return "list_datasets"; }

    @Override public String description() { return "列出所有已保存的数据集（名称 + ID）"; }

    @Override
    public Map<String, Object> parameters() {
        return Map.of(
            "type", "object",
            "properties", Map.of(),
            "required", List.of()
        );
    }

    @Override
    public String execute(Map<String, Object> args) {
        var list = repo.findAll(PageRequest.of(0, 50));
        if (list.isEmpty()) return "暂无数据集。";
        StringBuilder sb = new StringBuilder();
        for (Dataset ds : list) {
            sb.append("ID: ").append(ds.getId())
              .append(" | ").append(ds.getName()).append("\n");
        }
        return sb.toString();
    }
}
