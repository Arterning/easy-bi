package com.bi.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@Configuration
public class DuckDbConfig {

    @Value("${duckdb.url}")
    private String url;

    /**
     * Only expose JdbcTemplate — the DuckDB DataSource is NOT a Spring bean,
     * so Hibernate/JPA won't try to connect to it.
     */
    @Bean
    public JdbcTemplate duckDbJdbcTemplate() {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.duckdb.DuckDBDriver");
        ds.setUrl(url);
        return new JdbcTemplate(ds);
    }
}
