package com.bi.ai.tools;

import com.bi.ai.AiTool;
import com.bi.ai.ToolRegistry;
import com.bi.model.entity.Dataset;
import com.bi.repository.DatasetRepository;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class CreateDatasetTool implements AiTool {

    private final DatasetRepository repo;

    public CreateDatasetTool(ToolRegistry registry, DatasetRepository repo) {
        this.repo = repo;
        registry.register(this);
    }

    @Override public String name() { return "create_dataset"; }

    @Override public String description() { return "将 SQL 查询保存为数据集，方便后续复用"; }

    @Override
    public Map<String, Object> parameters() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "name", Map.of("type", "string", "description", "数据集名称"),
                "sql", Map.of("type", "string", "description", "SQL 查询语句"),
                "description", Map.of("type", "string", "description", "数据集描述（可选）")
            ),
            "required", List.of("name", "sql")
        );
    }

    @Override
    public String execute(Map<String, Object> args) {
        String name = (String) args.get("name");
        String sql = (String) args.get("sql");
        String desc = (String) args.get("description");

        Dataset ds = new Dataset();
        ds.setName(name);
        ds.setSql(sql);
        ds.setDescription(desc);
        ds = repo.save(ds);

        return "数据集已创建: ID=" + ds.getId() + ", 名称=" + ds.getName();
    }
}
