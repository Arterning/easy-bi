package com.bi.model.dto;

import java.util.List;

public class AppendResult {

    private Long dataSourceId;
    private String fileName;
    private List<TableAppend> tables;

    public AppendResult() {}

    public AppendResult(Long dataSourceId, String fileName, List<TableAppend> tables) {
        this.dataSourceId = dataSourceId;
        this.fileName = fileName;
        this.tables = tables;
    }

    public Long getDataSourceId() { return dataSourceId; }
    public void setDataSourceId(Long dataSourceId) { this.dataSourceId = dataSourceId; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public List<TableAppend> getTables() { return tables; }
    public void setTables(List<TableAppend> tables) { this.tables = tables; }

    // ---- inner ----

    public static class TableAppend {
        private String tableName;
        private int rowsBefore;
        private int rowsAppended;
        private int rowsAfter;
        private List<String> newColumns;
        private List<String> matchedColumns;
        private List<String> missingColumns;
        private boolean skipped;
        private String skipReason;

        public TableAppend() {}

        public String getTableName() { return tableName; }
        public void setTableName(String tableName) { this.tableName = tableName; }

        public int getRowsBefore() { return rowsBefore; }
        public void setRowsBefore(int rowsBefore) { this.rowsBefore = rowsBefore; }

        public int getRowsAppended() { return rowsAppended; }
        public void setRowsAppended(int rowsAppended) { this.rowsAppended = rowsAppended; }

        public int getRowsAfter() { return rowsAfter; }
        public void setRowsAfter(int rowsAfter) { this.rowsAfter = rowsAfter; }

        public List<String> getNewColumns() { return newColumns; }
        public void setNewColumns(List<String> newColumns) { this.newColumns = newColumns; }

        public List<String> getMatchedColumns() { return matchedColumns; }
        public void setMatchedColumns(List<String> matchedColumns) { this.matchedColumns = matchedColumns; }

        public List<String> getMissingColumns() { return missingColumns; }
        public void setMissingColumns(List<String> missingColumns) { this.missingColumns = missingColumns; }

        public boolean isSkipped() { return skipped; }
        public void setSkipped(boolean skipped) { this.skipped = skipped; }

        public String getSkipReason() { return skipReason; }
        public void setSkipReason(String skipReason) { this.skipReason = skipReason; }
    }
}
