package com.bi.controller;

import com.bi.model.dto.ApiResponse;
import com.bi.model.dto.UploadResult;
import com.bi.model.entity.DataSource;
import com.bi.service.DataSourceService;
import com.bi.service.FileImportService;
import com.bi.service.TableManagementService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import com.bi.model.dto.UploadResult;

@RestController
@RequestMapping("/api/datasources")
public class DataSourceController {

    private final DataSourceService dataSourceService;
    private final FileImportService fileImportService;
    private final TableManagementService tableManagementService;

    public DataSourceController(DataSourceService dataSourceService,
                                FileImportService fileImportService,
                                TableManagementService tableManagementService) {
        this.dataSourceService = dataSourceService;
        this.fileImportService = fileImportService;
        this.tableManagementService = tableManagementService;
    }

    @PostMapping("/upload")
    public ResponseEntity<ApiResponse<UploadResult>> upload(@RequestParam("file") MultipartFile file) {
        UploadResult result = fileImportService.importFile(file);
        return ResponseEntity.ok(ApiResponse.ok("导入成功", result));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<DataSource>>> list(Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(dataSourceService.list(pageable)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> detail(@PathVariable Long id) {
        DataSource ds = dataSourceService.getById(id);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", ds.getId());
        result.put("fileName", ds.getFileName());
        result.put("fileType", ds.getFileType());
        result.put("fileSize", ds.getFileSize());
        result.put("createdAt", ds.getCreatedAt());

        // Enrich with table/column info
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
        result.put("tables", tables);

        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        dataSourceService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok("删除成功", null));
    }

    @GetMapping("/{id}/preview")
    public ResponseEntity<ApiResponse<Map<String, Object>>> preview(
            @PathVariable Long id,
            @RequestParam("table") String tableName,
            @RequestParam(value = "rows", defaultValue = "20") int rows) {

        // Verify table belongs to this datasource
        DataSource ds = dataSourceService.getById(id);
        boolean found = Arrays.stream(ds.getTableNames().split(","))
                .map(String::trim)
                .anyMatch(t -> t.equalsIgnoreCase(tableName));
        if (!found) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, "表不属于该数据源"));
        }

        String sql = "SELECT * FROM main.\"" + tableName + "\" LIMIT " + Math.min(rows, 200);
        List<List<Object>> rowsData = tableManagementService.query(sql);
        List<UploadResult.ColumnInfo> columns = tableManagementService.getTableColumns(tableName);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("columns", columns);
        result.put("rows", rowsData);
        result.put("totalRows", tableManagementService.countRows(tableName));

        return ResponseEntity.ok(ApiResponse.ok(result));
    }
}
