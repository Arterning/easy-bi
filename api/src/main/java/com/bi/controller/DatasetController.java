package com.bi.controller;

import com.bi.model.dto.ApiResponse;
import com.bi.model.dto.DatasetCreateRequest;
import com.bi.model.dto.QueryRequest;
import com.bi.model.dto.QueryResult;
import com.bi.model.entity.Dataset;
import com.bi.service.DatasetService;
import com.bi.service.QueryService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/datasets")
public class DatasetController {

    private final DatasetService datasetService;
    private final QueryService queryService;

    public DatasetController(DatasetService datasetService, QueryService queryService) {
        this.datasetService = datasetService;
        this.queryService = queryService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Dataset>> create(@RequestBody DatasetCreateRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("创建成功", datasetService.create(req)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<Dataset>>> list(Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(datasetService.list(pageable)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Dataset>> detail(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(datasetService.getById(id)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Dataset>> update(@PathVariable Long id,
                                                        @RequestBody DatasetCreateRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("更新成功", datasetService.update(id, req)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        datasetService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok("删除成功", null));
    }

    @PostMapping("/{id}/execute")
    public ResponseEntity<ApiResponse<QueryResult>> execute(@PathVariable Long id,
                                                             @RequestBody QueryRequest req) {
        Dataset dataset = datasetService.getById(id);
        QueryResult result = queryService.execute(dataset.getSql(), req.getPage(), req.getSize());
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @GetMapping("/{id}/export")
    public void export(@PathVariable Long id,
                       @RequestParam(value = "format", defaultValue = "csv") String format,
                       HttpServletResponse response) throws IOException {

        Dataset dataset = datasetService.getById(id);
        List<Map<String, Object>> data = queryService.executeFull(dataset.getSql());

        response.setCharacterEncoding("UTF-8");
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"" + dataset.getName() + ".csv\"");

        // BOM for Excel compatibility
        response.getOutputStream().write(0xEF);
        response.getOutputStream().write(0xBB);
        response.getOutputStream().write(0xBF);

        try (PrintWriter writer = new PrintWriter(
                new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8))) {

            // Header
            if (!data.isEmpty()) {
                writer.println(String.join(",", data.get(0).keySet()));
            }

            // Data rows
            for (Map<String, Object> row : data) {
                StringBuilder sb = new StringBuilder();
                boolean first = true;
                for (Object val : row.values()) {
                    if (!first) sb.append(",");
                    first = false;
                    if (val != null) {
                        String s = val.toString();
                        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
                            s = "\"" + s.replace("\"", "\"\"") + "\"";
                        }
                        sb.append(s);
                    }
                }
                writer.println(sb);
            }
        }
    }
}
