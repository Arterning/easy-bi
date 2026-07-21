package com.bi.controller;

import com.bi.model.dto.ApiResponse;
import com.bi.model.dto.QueryRequest;
import com.bi.model.dto.QueryResult;
import com.bi.model.entity.DataSource;
import com.bi.service.DataSourceService;
import com.bi.service.QueryService;
import com.bi.service.TableManagementService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/query")
public class QueryController {

    private final QueryService queryService;
    private final DataSourceService dataSourceService;
    private final TableManagementService tableManagementService;

    public QueryController(QueryService queryService,
                           DataSourceService dataSourceService,
                           TableManagementService tableManagementService) {
        this.queryService = queryService;
        this.dataSourceService = dataSourceService;
        this.tableManagementService = tableManagementService;
    }

    @PostMapping("/execute")
    public ResponseEntity<ApiResponse<QueryResult>> execute(@RequestBody QueryRequest req) {
        QueryResult result = queryService.execute(req.getSql(), req.getPage(), req.getSize());
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @GetMapping("/tables")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> tables() {
        List<DataSource> dataSources = dataSourceService.listAll();
        List<Map<String, Object>> result = new ArrayList<>();

        for (DataSource ds : dataSources) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", ds.getId());
            entry.put("fileName", ds.getFileName());
            entry.put("fileType", ds.getFileType());

            String[] tableNames = ds.getTableNames().split(",");
            List<Map<String, Object>> tables = new ArrayList<>();
            for (String tn : tableNames) {
                String t = tn.trim();
                if (t.isEmpty()) continue;
                Map<String, Object> tableInfo = new LinkedHashMap<>();
                tableInfo.put("name", t);
                tableInfo.put("rowCount", tableManagementService.countRows(t));
                tableInfo.put("columns", tableManagementService.getTableColumns(t));
                tables.add(tableInfo);
            }
            entry.put("tables", tables);
            result.add(entry);
        }

        return ResponseEntity.ok(ApiResponse.ok(result));
    }
}
