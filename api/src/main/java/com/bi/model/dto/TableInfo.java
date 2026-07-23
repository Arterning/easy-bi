package com.bi.model.dto;

import java.util.List;

public class TableInfo {

    private String physicalName;
    private String displayName;
    private String sourceSheet;
    private int rowCount;
    private List<ColumnInfo> columns;

    public TableInfo() {}

    public TableInfo(String physicalName, String displayName, String sourceSheet,
                     int rowCount, List<ColumnInfo> columns) {
        this.physicalName = physicalName;
        this.displayName = displayName;
        this.sourceSheet = sourceSheet;
        this.rowCount = rowCount;
        this.columns = columns;
    }

    public String getPhysicalName() { return physicalName; }
    public void setPhysicalName(String physicalName) { this.physicalName = physicalName; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getSourceSheet() { return sourceSheet; }
    public void setSourceSheet(String sourceSheet) { this.sourceSheet = sourceSheet; }

    public int getRowCount() { return rowCount; }
    public void setRowCount(int rowCount) { this.rowCount = rowCount; }

    public List<ColumnInfo> getColumns() { return columns; }
    public void setColumns(List<ColumnInfo> columns) { this.columns = columns; }
}
