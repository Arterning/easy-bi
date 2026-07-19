package com.bi.model.dto;

import java.util.List;

public class UploadResult {

    private Long dataSourceId;
    private String fileName;
    private String fileType;
    private long fileSize;
    private List<TableInfo> tables;

    public UploadResult() {}

    public UploadResult(Long dataSourceId, String fileName, String fileType, long fileSize, List<TableInfo> tables) {
        this.dataSourceId = dataSourceId;
        this.fileName = fileName;
        this.fileType = fileType;
        this.fileSize = fileSize;
        this.tables = tables;
    }

    // --- Getters / Setters ---

    public Long getDataSourceId() { return dataSourceId; }
    public void setDataSourceId(Long dataSourceId) { this.dataSourceId = dataSourceId; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getFileType() { return fileType; }
    public void setFileType(String fileType) { this.fileType = fileType; }

    public long getFileSize() { return fileSize; }
    public void setFileSize(long fileSize) { this.fileSize = fileSize; }

    public List<TableInfo> getTables() { return tables; }
    public void setTables(List<TableInfo> tables) { this.tables = tables; }

    // --- Inner ---

    public static class TableInfo {
        private String name;
        private int rowCount;
        private List<ColumnInfo> columns;

        public TableInfo() {}

        public TableInfo(String name, int rowCount, List<ColumnInfo> columns) {
            this.name = name;
            this.rowCount = rowCount;
            this.columns = columns;
        }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public int getRowCount() { return rowCount; }
        public void setRowCount(int rowCount) { this.rowCount = rowCount; }

        public List<ColumnInfo> getColumns() { return columns; }
        public void setColumns(List<ColumnInfo> columns) { this.columns = columns; }
    }

    public static class ColumnInfo {
        private String name;
        private String type;

        public ColumnInfo() {}

        public ColumnInfo(String name, String type) {
            this.name = name;
            this.type = type;
        }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
    }
}
