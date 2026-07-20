package com.bi.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.sql.Connection;

@Configuration
public class DuckDbConfig {

    private static final Logger log = LoggerFactory.getLogger(DuckDbConfig.class);

    @Value("${duckdb.url}")
    private String url;

    /**
     * Single shared connection for DuckDB — required because DuckDB extensions
     * (spatial) are per-connection, not per-database. All operations share one
     * connection so that LOAD spatial applies everywhere.
     */
    @Bean
    public JdbcTemplate duckDbJdbcTemplate() {
        SingleConnectionDataSource ds = new SingleConnectionDataSource();
        ds.setDriverClassName("org.duckdb.DuckDBDriver");
        ds.setUrl(url);
        ds.setSuppressClose(true); // don't close on each operation
        ds.setAutoCommit(true);
        // Initialize connection and load extensions
        try {
            ds.initConnection();
            Connection conn = ds.getConnection();
            try (var stmt = conn.createStatement()) {
                stmt.execute("INSTALL spatial");
                stmt.execute("LOAD spatial");
                log.info("DuckDB spatial extension loaded on shared connection");
            }
        } catch (Exception e) {
            log.error("Failed to init DuckDB connection: {}", e.getMessage());
        }
        return new JdbcTemplate(ds);
    }
}
