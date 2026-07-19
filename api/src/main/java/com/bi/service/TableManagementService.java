package com.bi.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class TableManagementService {

    private static final Logger log = LoggerFactory.getLogger(TableManagementService.class);

    private final DataSource dataSource;

    public TableManagementService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Create a table in BI_DATA schema with inferred column definitions.
     * Column names are sanitized and uppercased.
     */
    public void createTable(String tableName, List<String> columnNames, List<String> columnTypes) {
        if (columnNames.size() != columnTypes.size()) {
            throw new IllegalArgumentException("columnNames and columnTypes must have same size");
        }

        StringBuilder ddl = new StringBuilder();
        ddl.append("CREATE TABLE BI_DATA.").append(escapeName(tableName)).append(" (");

        for (int i = 0; i < columnNames.size(); i++) {
            if (i > 0) ddl.append(", ");
            String col = sanitizeColumnName(columnNames.get(i));
            ddl.append(escapeName(col)).append(" ").append(columnTypes.get(i));
        }
        ddl.append(")");

        log.info("Creating table: {}", ddl);
        execute(ddl.toString());
    }

    /**
     * Drop a table from BI_DATA schema if it exists.
     */
    public void dropTable(String tableName) {
        String sql = "DROP TABLE IF EXISTS BI_DATA." + escapeName(tableName);
        log.info("Dropping table: {}", sql);
        execute(sql);
    }

    /**
     * Batch insert rows into a table.
     */
    public void batchInsert(String tableName, List<String> columnNames, List<List<Object>> rows) {
        if (rows.isEmpty()) return;

        StringBuilder sql = new StringBuilder();
        sql.append("INSERT INTO BI_DATA.").append(escapeName(tableName)).append(" (");

        List<String> safeCols = new ArrayList<>();
        for (String col : columnNames) {
            safeCols.add(escapeName(sanitizeColumnName(col)));
        }
        sql.append(String.join(", ", safeCols));
        sql.append(") VALUES (");

        String placeholders = String.join(", ", Collections.nCopies(safeCols.size(), "?"));
        sql.append(placeholders).append(")");

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {

            for (List<Object> row : rows) {
                int idx = 1;
                for (Object val : row) {
                    ps.setObject(idx++, val);
                }
                ps.addBatch();
            }
            ps.executeBatch();

        } catch (SQLException e) {
            throw new RuntimeException("Batch insert failed for table " + tableName, e);
        }
    }

    /**
     * Execute a single DDL/DML statement.
     */
    public void execute(String sql) {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            throw new RuntimeException("SQL execution failed: " + sql, e);
        }
    }

    /**
     * Execute a query and return the result as list of rows.
     */
    public List<List<Object>> query(String sql, Object... params) {
        List<List<Object>> rows = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }

            try (ResultSet rs = ps.executeQuery()) {
                int colCount = rs.getMetaData().getColumnCount();
                while (rs.next()) {
                    List<Object> row = new ArrayList<>(colCount);
                    for (int i = 1; i <= colCount; i++) {
                        row.add(rs.getObject(i));
                    }
                    rows.add(row);
                }
            }

        } catch (SQLException e) {
            throw new RuntimeException("Query failed: " + sql, e);
        }
        return rows;
    }

    /**
     * List all tables in BI_DATA schema.
     */
    public List<String> listTables() {
        String sql = "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'BI_DATA'";
        List<String> tables = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                tables.add(rs.getString("TABLE_NAME"));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to list tables", e);
        }
        return tables;
    }

    /**
     * Get column metadata for a table.
     */
    public List<Map<String, String>> getTableColumns(String tableName) {
        String sql = "SELECT COLUMN_NAME, DATA_TYPE, DECLARED_DATA_TYPE FROM INFORMATION_SCHEMA.COLUMNS " +
                     "WHERE TABLE_SCHEMA = 'BI_DATA' AND TABLE_NAME = ? ORDER BY ORDINAL_POSITION";
        List<Map<String, String>> columns = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, tableName.toUpperCase());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, String> col = new LinkedHashMap<>();
                    col.put("name", rs.getString("COLUMN_NAME"));
                    String declaredType = rs.getString("DECLARED_DATA_TYPE");
                    String dataType = rs.getString("DATA_TYPE");
                    col.put("type", declaredType != null ? declaredType : dataType);
                    columns.add(col);
                }
            }

        } catch (SQLException e) {
            throw new RuntimeException("Failed to get columns for table " + tableName, e);
        }
        return columns;
    }

    /**
     * Count rows in a table.
     */
    public int countRows(String tableName) {
        String sql = "SELECT COUNT(*) FROM BI_DATA." + escapeName(tableName);
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to count rows for table " + tableName, e);
        }
        return 0;
    }

    /**
     * Sanitize a column name: replace spaces/special chars with underscore, uppercase.
     */
    public static String sanitizeColumnName(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return "COL";
        }
        // Keep Unicode letters, digits and underscores; replace everything else with _
        String name = raw.trim()
                .replaceAll("[^\\p{L}\\p{N}_]", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
        if (name.isEmpty()) name = "COL";
        return name;
    }

    private static String escapeName(String name) {
        return "\"" + name.replace("\"", "\"\"") + "\"";
    }
}
