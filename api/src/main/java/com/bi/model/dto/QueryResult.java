package com.bi.model.dto;

import java.util.List;

public class QueryResult {

    private List<String> columns;
    private List<List<Object>> rows;
    private long totalRows;
    private int page;
    private int size;

    public QueryResult() {}

    public QueryResult(List<String> columns, List<List<Object>> rows, long totalRows, int page, int size) {
        this.columns = columns;
        this.rows = rows;
        this.totalRows = totalRows;
        this.page = page;
        this.size = size;
    }

    // --- Getters / Setters ---

    public List<String> getColumns() { return columns; }
    public void setColumns(List<String> columns) { this.columns = columns; }

    public List<List<Object>> getRows() { return rows; }
    public void setRows(List<List<Object>> rows) { this.rows = rows; }

    public long getTotalRows() { return totalRows; }
    public void setTotalRows(long totalRows) { this.totalRows = totalRows; }

    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }

    public int getSize() { return size; }
    public void setSize(int size) { this.size = size; }
}
