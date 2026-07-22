package com.bi.ai.tools;

import com.bi.ai.AiTool;
import com.bi.ai.ToolRegistry;
import com.bi.service.TableManagementService;
import com.bi.model.dto.UploadResult.ColumnInfo;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class ListTablesTool implements AiTool {

    private final TableManagementService tableService;

    public ListTablesTool(ToolRegistry registry, TableManagementService tableService) {
        this.tableService = tableService;
        registry.register(this);
    }

    @Override public String name() { return "list_tables"; }

    @Override public String description() { return "列出 DuckDB 中所有可用的数据表，返回表名和行数"; }

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
        List<String> tables = tableService.listTables();
        if (tables.isEmpty()) return "暂无数据表。请先上传 CSV/Excel 文件。";

        StringBuilder sb = new StringBuilder();
        for (String t : tables) {
            int rows = tableService.countRows(t);
            sb.append(t).append(" (").append(rows).append(" 行)\n");
        }
        return sb.toString();
    }
}
