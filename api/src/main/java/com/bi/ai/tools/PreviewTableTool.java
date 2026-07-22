package com.bi.ai.tools;

import com.bi.ai.AiTool;
import com.bi.ai.ToolRegistry;
import com.bi.service.TableManagementService;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class PreviewTableTool implements AiTool {

    private final TableManagementService tableService;

    public PreviewTableTool(ToolRegistry registry, TableManagementService tableService) {
        this.tableService = tableService;
        registry.register(this);
    }

    @Override public String name() { return "preview_table"; }

    @Override public String description() { return "预览表中前几行数据，用于了解数据内容"; }

    @Override
    public Map<String, Object> parameters() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "tableName", Map.of("type", "string", "description", "表名"),
                "rows", Map.of("type", "integer", "description", "预览行数，默认 5")
            ),
            "required", List.of("tableName")
        );
    }

    @Override
    public String execute(Map<String, Object> args) {
        String tableName = (String) args.get("tableName");
        int rows = args.containsKey("rows") ? ((Number) args.get("rows")).intValue() : 5;
        rows = Math.min(rows, 10);

        String sql = "SELECT * FROM main.\"" + tableName + "\" LIMIT " + rows;
        List<List<Object>> data = tableService.query(sql);
        if (data.isEmpty()) return "表 " + tableName + " 无数据。";

        StringBuilder sb = new StringBuilder();
        for (List<Object> row : data) {
            sb.append("  ").append(row).append("\n");
        }
        return sb.toString();
    }
}
