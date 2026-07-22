package com.bi.ai.tools;

import com.bi.ai.AiTool;
import com.bi.ai.ToolRegistry;
import com.bi.model.dto.QueryResult;
import com.bi.service.QueryService;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class ExecuteSqlTool implements AiTool {

    private final QueryService queryService;

    public ExecuteSqlTool(ToolRegistry registry, QueryService queryService) {
        this.queryService = queryService;
        registry.register(this);
    }

    @Override public String name() { return "execute_sql"; }

    @Override public String description() {
        return "执行只读 SQL 查询（SELECT/WITH）。返回列名 + 前 20 行数据 + 总行数。";
    }

    @Override
    public Map<String, Object> parameters() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "sql", Map.of("type", "string", "description", "要执行的 SQL 查询语句")
            ),
            "required", List.of("sql")
        );
    }

    @Override
    public String execute(Map<String, Object> args) {
        String sql = (String) args.get("sql");
        QueryResult result = queryService.execute(sql, 0, 20);

        StringBuilder sb = new StringBuilder();
        sb.append("总行数: ").append(result.getTotalRows()).append("\n");
        sb.append("列: ").append(String.join(", ", result.getColumns())).append("\n");
        sb.append("前 ").append(Math.min(result.getRows().size(), 20)).append(" 行:\n");
        for (List<Object> row : result.getRows()) {
            sb.append("  ").append(row).append("\n");
        }
        if (result.getTotalRows() > 20) {
            sb.append("  ... (共 ").append(result.getTotalRows()).append(" 行)");
        }
        return sb.toString();
    }
}
