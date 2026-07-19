package com.bi.model.dto;

public class DatasetCreateRequest {

    private String name;
    private String sql;
    private String description;

    public DatasetCreateRequest() {}

    public DatasetCreateRequest(String name, String sql, String description) {
        this.name = name;
        this.sql = sql;
        this.description = description;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getSql() { return sql; }
    public void setSql(String sql) { this.sql = sql; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
