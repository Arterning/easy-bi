package com.bi.service;

import com.bi.model.dto.UploadResult.ColumnInfo;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class TableManagementService {

    private static final Logger log = LoggerFactory.getLogger(TableManagementService.class);

    private final JdbcTemplate duckDb;

    public TableManagementService(JdbcTemplate duckDbJdbcTemplate) {
        this.duckDb = duckDbJdbcTemplate;
    }

    @PostConstruct
    public void init() {
        // Load spatial extension for Excel support (st_read / st_layers)
        try {
            duckDb.execute("INSTALL spatial");
            duckDb.execute("LOAD spatial");
            log.info("DuckDB spatial extension loaded");
        } catch (Exception e) {
            log.warn("Failed to load spatial extension: {}", e.getMessage());
        }
        ensureSchema();
    }

    /**
     * Create the bi_data schema if it doesn't exist.
     */
    public void ensureSchema() {
        duckDb.execute("CREATE SCHEMA IF NOT EXISTS bi_data");
    }

    /**
     * Drop a table from bi_data schema.
     */
    public void dropTable(String tableName) {
        String sql = "DROP TABLE IF EXISTS bi_data.\"" + tableName + "\"";
        log.info("Dropping table: {}", tableName);
        duckDb.execute(sql);
    }

    /**
     * List all user-created tables in bi_data schema.
     */
    public List<String> listTables() {
        return duckDb.query(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'bi_data' ORDER BY table_name",
                (rs, rowNum) -> rs.getString("table_name")
        );
    }

    /**
     * Get column metadata for a table.
     */
    public List<ColumnInfo> getTableColumns(String tableName) {
        return duckDb.query(
                "SELECT column_name, data_type FROM information_schema.columns " +
                "WHERE table_schema = 'bi_data' AND table_name = ? ORDER BY ordinal_position",
                (rs, rowNum) -> new ColumnInfo(
                        rs.getString("column_name"),
                        rs.getString("data_type")
                ),
                tableName
        );
    }

    /**
     * Count rows in a table.
     */
    public int countRows(String tableName) {
        Integer count = duckDb.queryForObject(
                "SELECT COUNT(*) FROM bi_data.\"" + tableName + "\"",
                Integer.class
        );
        return count != null ? count : 0;
    }

    /**
     * Execute a read-only query and return rows as List&lt;List&lt;Object&gt;&gt;.
     */
    public List<List<Object>> query(String sql, Object... params) {
        return duckDb.query(sql, (rs, rowNum) -> {
            int colCount = rs.getMetaData().getColumnCount();
            List<Object> row = new ArrayList<>(colCount);
            for (int i = 1; i <= colCount; i++) {
                row.add(rs.getObject(i));
            }
            return row;
        }, params);
    }

    /**
     * Sanitize column name: replace special chars with _.
     */
    public static String sanitizeColumnName(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "COL";
        String name = raw.trim()
                .replaceAll("[^\\p{L}\\p{N}_]", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
        return name.isEmpty() ? "COL" : name;
    }
}
