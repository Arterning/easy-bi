package com.bi.service;

import com.bi.exception.BusinessException;
import com.bi.model.dto.QueryResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class QueryService {

    private static final Logger log = LoggerFactory.getLogger(QueryService.class);

    private static final Pattern ALLOWED_PREFIX = Pattern.compile(
            "^\\s*(SELECT|WITH|EXPLAIN|DESCRIBE|SHOW|PRAGMA)\\b.*",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static final Pattern FORBIDDEN = Pattern.compile(
            "\\b(INSERT|UPDATE|DELETE|DROP|TRUNCATE|ALTER|CREATE|MERGE|REPLACE|GRANT|REVOKE)\\b",
            Pattern.CASE_INSENSITIVE);

    private final JdbcTemplate duckDb;
    private final int timeoutSeconds;
    private final int maxRowsPerQuery;

    public QueryService(JdbcTemplate duckDbJdbcTemplate,
                        @Value("${bi.query.timeout-seconds:60}") int timeoutSeconds,
                        @Value("${bi.query.max-rows-per-query:100000}") int maxRowsPerQuery) {
        this.duckDb = duckDbJdbcTemplate;
        this.timeoutSeconds = timeoutSeconds;
        this.maxRowsPerQuery = maxRowsPerQuery;
    }

    public void validate(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new BusinessException("SQL 不能为空");
        }
        String trimmed = sql.trim();
        if (!ALLOWED_PREFIX.matcher(trimmed).matches()) {
            throw new BusinessException("仅允许 SELECT / WITH / EXPLAIN / DESCRIBE / SHOW / PRAGMA 开头的只读语句");
        }
        if (FORBIDDEN.matcher(trimmed).find()) {
            throw new BusinessException("SQL 包含禁止的关键词 (INSERT/UPDATE/DELETE/DROP/ALTER/CREATE 等)");
        }
    }

    public void validateSyntax(String sql) {
        try {
            // DuckDB: try EXPLAIN to validate without executing
            duckDb.execute("EXPLAIN " + sql);
        } catch (Exception e) {
            throw new BusinessException("SQL 语法错误: " + e.getMessage());
        }
    }

    public QueryResult execute(String userSql, int page, int size) {
        validate(userSql);

        int s = Math.min(Math.max(size, 1), maxRowsPerQuery);
        int p = Math.max(page, 0);
        int offset = p * s;

        String countSql = "SELECT COUNT(*) FROM (" + userSql + ") subquery";
        String dataSql = "SELECT * FROM (" + userSql + ") subquery LIMIT " + s + " OFFSET " + offset;

        long totalRows = duckDb.queryForObject(countSql, Long.class);
        if (totalRows > maxRowsPerQuery) {
            totalRows = Math.min(totalRows, maxRowsPerQuery);
        }

        final List<String> columns = new ArrayList<>();
        List<List<Object>> rows = duckDb.query(dataSql, (org.springframework.jdbc.core.ResultSetExtractor<List<List<Object>>>) rs -> {
            List<List<Object>> result = new ArrayList<>();
            ResultSetMetaData meta = rs.getMetaData();
            for (int i = 1; i <= meta.getColumnCount(); i++) {
                columns.add(meta.getColumnLabel(i));
            }
            while (rs.next()) {
                List<Object> row = new ArrayList<>(columns.size());
                for (int i = 1; i <= columns.size(); i++) {
                    row.add(rs.getObject(i));
                }
                result.add(row);
            }
            return result;
        });

        if (rows == null) rows = List.of();
        return new QueryResult(columns, rows, totalRows, p, s);
    }

    public List<Map<String, Object>> executeFull(String sql) {
        validate(sql);
        return duckDb.query(sql, (org.springframework.jdbc.core.RowMapper<Map<String, Object>>) (rs, rowNum) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            ResultSetMetaData meta = rs.getMetaData();
            for (int i = 1; i <= meta.getColumnCount(); i++) {
                row.put(meta.getColumnLabel(i), rs.getObject(i));
            }
            return row;
        });
    }
}
