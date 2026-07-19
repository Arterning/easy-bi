package com.bi.service;

import com.bi.exception.BusinessException;
import com.bi.model.dto.QueryResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class QueryService {

    private static final Logger log = LoggerFactory.getLogger(QueryService.class);

    /** Only SELECT and WITH (CTE) are allowed. */
    private static final Pattern ALLOWED_PREFIX = Pattern.compile(
            "^\\s*(SELECT|WITH)\\b.*", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /** Forbidden keywords — stripped from the SQL before checking. */
    private static final Pattern FORBIDDEN = Pattern.compile(
            "\\b(INSERT|UPDATE|DELETE|DROP|TRUNCATE|ALTER|CREATE|MERGE|REPLACE|GRANT|REVOKE)\\b",
            Pattern.CASE_INSENSITIVE);

    private final DataSource dataSource;
    private final int timeoutSeconds;
    private final int maxRowsPerQuery;

    public QueryService(DataSource dataSource,
                        @Value("${bi.query.timeout-seconds:30}") int timeoutSeconds,
                        @Value("${bi.query.max-rows-per-query:50000}") int maxRowsPerQuery) {
        this.dataSource = dataSource;
        this.timeoutSeconds = timeoutSeconds;
        this.maxRowsPerQuery = maxRowsPerQuery;
    }

    /**
     * Validate that the SQL is read-only and contains no forbidden keywords.
     * Throws BusinessException if validation fails.
     */
    public void validate(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new BusinessException("SQL 不能为空");
        }

        String trimmed = sql.trim();

        if (!ALLOWED_PREFIX.matcher(trimmed).matches()) {
            throw new BusinessException("仅允许 SELECT 和 WITH (CTE) 开头的只读语句");
        }

        if (FORBIDDEN.matcher(trimmed).find()) {
            throw new BusinessException("SQL 包含禁止的关键词 (INSERT/UPDATE/DELETE/DROP/ALTER/CREATE 等)");
        }
    }

    /**
     * Validate syntax by attempting to prepare the statement (read-only, no execution).
     */
    public void validateSyntax(String sql) {
        try (Connection conn = dataSource.getConnection()) {
            conn.setReadOnly(true);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setQueryTimeout(1); // quick timeout — just parse, don't execute
                // Don't execute — just verify parsing succeeds
            }
        } catch (SQLException e) {
            throw new BusinessException("SQL 语法错误: " + e.getMessage());
        }
    }

    /**
     * Execute a user SQL with pagination.
     * Wraps user SQL in a subquery: SELECT * FROM ({userSql}) sub LIMIT ? OFFSET ?
     */
    public QueryResult execute(String userSql, int page, int size) {
        validate(userSql);

        // Clamp page size
        int s = Math.min(size, maxRowsPerQuery);
        s = Math.max(s, 1);
        int p = Math.max(page, 0);
        int offset = p * s;

        String countSql = "SELECT COUNT(*) FROM (" + userSql + ") subquery";
        String dataSql = "SELECT * FROM (" + userSql + ") subquery LIMIT " + s + " OFFSET " + offset;

        long totalRows;
        List<String> columns;
        List<List<Object>> rows;

        try (Connection conn = dataSource.getConnection()) {
            conn.setReadOnly(true);

            // Count
            try (Statement stmt = conn.createStatement()) {
                stmt.setQueryTimeout(timeoutSeconds);
                try (ResultSet rs = stmt.executeQuery(countSql)) {
                    totalRows = rs.next() ? rs.getLong(1) : 0;
                }
            }

            // Data
            try (Statement stmt = conn.createStatement()) {
                stmt.setQueryTimeout(timeoutSeconds);
                try (ResultSet rs = stmt.executeQuery(dataSql)) {
                    ResultSetMetaData meta = rs.getMetaData();
                    int colCount = meta.getColumnCount();

                    columns = new ArrayList<>(colCount);
                    for (int i = 1; i <= colCount; i++) {
                        columns.add(meta.getColumnLabel(i));
                    }

                    rows = new ArrayList<>(s);
                    while (rs.next()) {
                        List<Object> row = new ArrayList<>(colCount);
                        for (int i = 1; i <= colCount; i++) {
                            row.add(rs.getObject(i));
                        }
                        rows.add(row);
                    }
                }
            }

        } catch (SQLException e) {
            log.error("Query execution failed: {}", e.getMessage());
            throw new BusinessException("查询执行失败: " + e.getMessage());
        }

        return new QueryResult(columns, rows, totalRows, p, s);
    }

    /**
     * Execute a query without pagination (for export — caller should ensure limits).
     */
    public List<Map<String, Object>> executeFull(String sql) {
        validate(sql);

        try (Connection conn = dataSource.getConnection()) {
            conn.setReadOnly(true);
            try (Statement stmt = conn.createStatement()) {
                stmt.setQueryTimeout(timeoutSeconds);
                try (ResultSet rs = stmt.executeQuery(sql)) {
                    ResultSetMetaData meta = rs.getMetaData();
                    int colCount = meta.getColumnCount();
                    List<Map<String, Object>> result = new ArrayList<>();

                    while (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        for (int i = 1; i <= colCount; i++) {
                            row.put(meta.getColumnLabel(i), rs.getObject(i));
                        }
                        result.add(row);
                        if (result.size() >= maxRowsPerQuery) {
                            break;
                        }
                    }
                    return result;
                }
            }
        } catch (SQLException e) {
            log.error("Full query execution failed: {}", e.getMessage());
            throw new BusinessException("查询执行失败: " + e.getMessage());
        }
    }
}
