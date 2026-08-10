package com.bi.ai.tools;

import com.bi.ai.AiTool;
import com.bi.ai.ToolRegistry;
import com.bi.model.entity.Dataset;
import com.bi.repository.DatasetRepository;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class UpdateDatasetTool implements AiTool {

    private final DatasetRepository repo;

    public UpdateDatasetTool(ToolRegistry registry, DatasetRepository repo) {
        this.repo = repo;
        registry.register(this);
    }

    @Override public String name() { return "update_dataset"; }

    @Override public String description() { return "修改已有数据集的名称、SQL 或描述"; }

    @Override
    public Map<String, Object> parameters() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "dataset_id", Map.of("type", "integer", "description", "要修改的数据集 ID（从 create_dataset 的返回值或 get_dataset 的查询结果中获得）"),
                "name", Map.of("type", "string", "description", "新的数据集名称（可选，不填则不修改）"),
                "sql", Map.of("type", "string", "description", "新的 SQL 查询语句（可选，不填则不修改）"),
                "description", Map.of("type", "string", "description", "新的数据集描述（可选，不填则不修改）")
            ),
            "required", List.of("dataset_id")
        );
    }

    @Override
    public String execute(Map<String, Object> args) {
        Long id = args.get("dataset_id") instanceof Number n ? n.longValue() : null;
        if (id == null) return "错误: dataset_id 不能为空";

        Dataset ds = repo.findById(id).orElse(null);
        if (ds == null) return "数据集不存在: ID=" + id;

        String name = (String) args.get("name");
        String sql = (String) args.get("sql");
        String desc = (String) args.get("description");

        StringBuilder changes = new StringBuilder();

        if (name != null && !name.isBlank()) {
            ds.setName(name);
            changes.append("名称 → ").append(name).append(" ");
        }
        if (sql != null && !sql.isBlank()) {
            ds.setSql(sql);
            changes.append("SQL 已更新 ");
        }
        if (desc != null && !desc.isBlank()) {
            ds.setDescription(desc);
            changes.append("描述已更新 ");
        }

        if (changes.isEmpty()) {
            return "没有需要修改的字段。数据集 ID=" + id + " 未变更。";
        }

        repo.save(ds);
        return "数据集已更新: ID=" + id + ", " + changes.toString().trim();
    }
}
