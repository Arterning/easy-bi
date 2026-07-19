package com.bi.controller;

import com.bi.model.dto.ApiResponse;
import com.bi.model.dto.QueryRequest;
import com.bi.model.dto.QueryResult;
import com.bi.service.QueryService;
import com.bi.service.TableManagementService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/query")
public class QueryController {

    private final QueryService queryService;
    private final TableManagementService tableManagementService;

    public QueryController(QueryService queryService, TableManagementService tableManagementService) {
        this.queryService = queryService;
        this.tableManagementService = tableManagementService;
    }

    @PostMapping("/execute")
    public ResponseEntity<ApiResponse<QueryResult>> execute(@RequestBody QueryRequest req) {
        QueryResult result = queryService.execute(req.getSql(), req.getPage(), req.getSize());
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @GetMapping("/tables")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> tables() {
        List<String> tableNames = tableManagementService.listTables();
        List<Map<String, Object>> tables = new ArrayList<>();

        for (String name : tableNames) {
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("name", name);
            info.put("rowCount", tableManagementService.countRows(name));
            info.put("columns", tableManagementService.getTableColumns(name));
            tables.add(info);
        }

        return ResponseEntity.ok(ApiResponse.ok(tables));
    }
}
