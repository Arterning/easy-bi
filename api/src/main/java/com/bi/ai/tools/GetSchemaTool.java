package com.bi.ai.tools;

import com.bi.ai.AiTool;
import com.bi.ai.ToolRegistry;
import com.bi.service.TableManagementService;
import com.bi.model.dto.ColumnInfo;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class GetSchemaTool implements AiTool {

    private final TableManagementService tableService;

    public GetSchemaTool(ToolRegistry registry, TableManagementService tableService) {
        this.tableService = tableService;
        registry.register(this);
    }

    @Override public String name() { return "get_table_schema"; }

    @Override public String description() { return "获取指定表的所有列名和数据类型，以及总行数"; }

    @Override
    public Map<String, Object> parameters() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "tableName", Map.of("type", "string", "description", "表名，如 T_1_0")
            ),
            "required", List.of("tableName")
        );
    }

    @Override
    public String execute(Map<String, Object> args) {
        String tableName = (String) args.get("tableName");
        int rows = tableService.countRows(tableName);
        List<ColumnInfo> cols = tableService.getTableColumns(tableName);

        StringBuilder sb = new StringBuilder();
        sb.append("表 ").append(tableName).append(" (").append(rows).append(" 行)\n");
        sb.append("列:\n");
        for (ColumnInfo c : cols) {
            sb.append("  ").append(c.getName()).append(": ").append(c.getType()).append("\n");
        }
        return sb.toString();
    }
}
