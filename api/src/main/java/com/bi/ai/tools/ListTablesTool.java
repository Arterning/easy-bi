package com.bi.ai.tools;

import com.bi.ai.AiTool;
import com.bi.ai.ToolRegistry;
import com.bi.model.entity.BiTable;
import com.bi.repository.BiTableRepository;
import com.bi.service.TableManagementService;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class ListTablesTool implements AiTool {

    private final BiTableRepository biTableRepo;
    private final TableManagementService tableService;

    public ListTablesTool(ToolRegistry registry, BiTableRepository biTableRepo,
                          TableManagementService tableService) {
        this.biTableRepo = biTableRepo;
        this.tableService = tableService;
        registry.register(this);
    }

    @Override public String name() { return "list_tables"; }

    @Override public String description() {
        return "列出所有可用的数据表，返回展示名、物理表名和行数";
    }

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
        List<BiTable> all = biTableRepo.findAll();
        if (all.isEmpty()) return "暂无数据表。请先上传 CSV/Excel 文件。";

        StringBuilder sb = new StringBuilder();
        for (BiTable bt : all) {
            int rows = tableService.countRows(bt.getPhysicalName());
            sb.append(bt.getDisplayName())
              .append(" (").append(bt.getPhysicalName()).append(")")
              .append(" — ").append(rows).append(" 行\n");
        }
        return sb.toString();
    }
}
