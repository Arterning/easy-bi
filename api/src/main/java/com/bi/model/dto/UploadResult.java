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
}
