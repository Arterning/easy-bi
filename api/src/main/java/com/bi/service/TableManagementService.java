package com.bi.service;

import com.bi.model.dto.UploadResult.ColumnInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class TableManagementService {

    private static final Logger log = LoggerFactory.getLogger(TableManagementService.class);

    /** DuckDB default schema — avoids conflict with catalog name. */
    private static final String DATA_SCHEMA = "main";

    private final JdbcTemplate duckDb;

    public TableManagementService(JdbcTemplate duckDbJdbcTemplate) {
        this.duckDb = duckDbJdbcTemplate;
    }

    public void dropTable(String tableName) {
        String sql = "DROP TABLE IF EXISTS " + DATA_SCHEMA + ".\"" + tableName + "\"";
        log.info("Dropping table: {}", tableName);
        duckDb.execute(sql);
    }

    public List<String> listTables() {
        return duckDb.query(
                "SELECT table_name FROM information_schema.tables " +
                "WHERE table_schema = '" + DATA_SCHEMA + "' ORDER BY table_name",
                (rs, rowNum) -> rs.getString("table_name")
        );
    }

    public List<ColumnInfo> getTableColumns(String tableName) {
        return duckDb.query(
                "SELECT column_name, data_type FROM information_schema.columns " +
                "WHERE table_schema = '" + DATA_SCHEMA + "' AND table_name = ? ORDER BY ordinal_position",
                (rs, rowNum) -> new ColumnInfo(
                        rs.getString("column_name"),
                        rs.getString("data_type")
                ),
                tableName
        );
    }

    public int countRows(String tableName) {
        Integer count = duckDb.queryForObject(
                "SELECT COUNT(*) FROM " + DATA_SCHEMA + ".\"" + tableName + "\"",
                Integer.class
        );
        return count != null ? count : 0;
    }

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

    public static String sanitizeColumnName(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "COL";
        String name = raw.trim()
                .replaceAll("[^\\p{L}\\p{N}_]", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
        return name.isEmpty() ? "COL" : name;
    }
}
