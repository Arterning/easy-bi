package com.bi.model.dto;

public class QueryRequest {

    private String sql;
    private int page = 0;
    private int size = 50;

    public QueryRequest() {}

    public QueryRequest(String sql, int page, int size) {
        this.sql = sql;
        this.page = page;
        this.size = size;
    }

    public String getSql() { return sql; }
    public void setSql(String sql) { this.sql = sql; }

    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }

    public int getSize() { return size; }
    public void setSize(int size) { this.size = size; }
}
