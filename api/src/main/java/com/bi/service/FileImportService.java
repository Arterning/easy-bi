package com.bi.service;

import com.bi.exception.BusinessException;
import com.bi.model.dto.UploadResult;
import com.bi.model.dto.UploadResult.ColumnInfo;
import com.bi.model.dto.UploadResult.TableInfo;
import com.bi.model.entity.DataSource;
import com.bi.repository.DataSourceRepository;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

@Service
public class FileImportService {

    private static final Logger log = LoggerFactory.getLogger(FileImportService.class);

    private static final Set<String> CSV_EXTENSIONS = Set.of("csv", "txt");
    private static final Set<String> EXCEL_EXTENSIONS = Set.of("xls", "xlsx");

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("yyyyMMdd")
    );

    private static final List<DateTimeFormatter> TIMESTAMP_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE_TIME,
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
    );

    private final DataSourceRepository dataSourceRepository;
    private final TableManagementService tableManagementService;
    private final int sampleRows;
    private final int batchSize;

    public FileImportService(DataSourceRepository dataSourceRepository,
                             TableManagementService tableManagementService,
                             @Value("${bi.import.type-inference-sample-rows:100}") int sampleRows,
                             @Value("${bi.import.batch-size:1000}") int batchSize) {
        this.dataSourceRepository = dataSourceRepository;
        this.tableManagementService = tableManagementService;
        this.sampleRows = sampleRows;
        this.batchSize = batchSize;
    }

    /**
     * Main entry point: import a CSV or Excel file, create tables, persist metadata.
     * Uses streaming to avoid holding all rows in memory.
     */
    @Transactional
    public UploadResult importFile(MultipartFile file) {
        String originalName = file.getOriginalFilename();
        if (originalName == null || originalName.isBlank()) {
            throw new BusinessException("文件名为空");
        }

        String extension = getExtension(originalName).toLowerCase();
        String fileType;

        if (CSV_EXTENSIONS.contains(extension)) {
            fileType = "csv";
        } else if (EXCEL_EXTENSIONS.contains(extension)) {
            fileType = "excel";
        } else {
            throw new BusinessException("不支持的文件类型: ." + extension + " (仅支持 csv/xls/xlsx)");
        }

        // Save DataSource metadata first (to get an ID for table naming)
        DataSource ds = new DataSource();
        ds.setFileName(originalName);
        ds.setFileType(fileType);
        ds.setFileSize(file.getSize());
        ds.setTableNames("");
        ds = dataSourceRepository.save(ds);

        Long dsId = ds.getId();
        List<TableInfo> tableInfos = new ArrayList<>();
        List<String> tableNameList = new ArrayList<>();

        try {
            if (fileType.equals("csv")) {
                TableInfo info = importCsvStreaming(file, dsId);
                tableInfos.add(info);
                tableNameList.add(info.getName());
            } else {
                List<TableInfo> infos = importExcelStreaming(file, dsId);
                tableInfos.addAll(infos);
                for (TableInfo ti : infos) {
                    tableNameList.add(ti.getName());
                }
            }
        } catch (Exception e) {
            // Roll back: drop any tables we created
            for (String t : tableNameList) {
                try { tableManagementService.dropTable(t); } catch (Exception ignored) {}
            }
            dataSourceRepository.delete(ds);
            if (e instanceof BusinessException) throw (BusinessException) e;
            throw new BusinessException("导入失败: " + e.getMessage());
        }

        ds.setTableNames(String.join(",", tableNameList));
        dataSourceRepository.save(ds);

        return new UploadResult(dsId, originalName, fileType, file.getSize(), tableInfos);
    }

    // ==================== CSV Streaming Import ====================

    private TableInfo importCsvStreaming(MultipartFile file, Long dsId) {
        String tableName = "T_" + dsId;

        // Pass 1: read headers + sample rows to infer types
        List<String> headers;
        List<List<String>> sample = new ArrayList<>();
        int totalRows;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {

            CSVParser parser = CSVFormat.DEFAULT
                    .withFirstRecordAsHeader()
                    .withIgnoreHeaderCase()
                    .withTrim()
                    .parse(reader);

            headers = new ArrayList<>(parser.getHeaderNames());
            if (headers.isEmpty()) {
                throw new BusinessException("CSV 文件没有表头");
            }

            totalRows = 0;
            for (CSVRecord record : parser) {
                if (totalRows < sampleRows) {
                    List<String> row = new ArrayList<>();
                    for (int i = 0; i < headers.size(); i++) {
                        row.add(i < record.size() ? record.get(i) : null);
                    }
                    sample.add(row);
                }
                totalRows++;
            }

        } catch (IOException e) {
            throw new BusinessException("CSV 解析失败: " + e.getMessage());
        }

        List<String> types = inferTypes(headers.size(), sample);
        List<String> colNames = deduplicateColumnNames(headers);

        // Create table
        tableManagementService.createTable(tableName, colNames, types);

        // Pass 2: stream rows in batches and insert
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {

            CSVParser parser = CSVFormat.DEFAULT
                    .withFirstRecordAsHeader()
                    .withIgnoreHeaderCase()
                    .withTrim()
                    .parse(reader);

            int effectiveBatchSize = Math.max(50, batchSize * 10 / Math.max(colNames.size(), 1));
            List<List<Object>> batch = new ArrayList<>(effectiveBatchSize);
            int inserted = 0;

            for (CSVRecord record : parser) {
                List<Object> row = new ArrayList<>(colNames.size());
                for (int i = 0; i < colNames.size(); i++) {
                    String raw = i < record.size() ? record.get(i) : null;
                    row.add(parseValue(raw, types.get(i)));
                }
                batch.add(row);

                if (batch.size() >= effectiveBatchSize) {
                    tableManagementService.batchInsert(tableName, colNames, batch);
                    inserted += batch.size();
                    batch.clear();
                    log.debug("CSV batch inserted: {} rows so far", inserted);
                }
            }
            // Flush remaining
            if (!batch.isEmpty()) {
                tableManagementService.batchInsert(tableName, colNames, batch);
                inserted += batch.size();
            }

            log.info("CSV import complete: table={}, rows={}", tableName, inserted);

        } catch (IOException e) {
            throw new BusinessException("CSV 流式导入失败: " + e.getMessage());
        }

        List<ColumnInfo> columnInfos = new ArrayList<>();
        for (int j = 0; j < colNames.size(); j++) {
            columnInfos.add(new ColumnInfo(colNames.get(j), types.get(j)));
        }
        return new TableInfo(tableName, totalRows, columnInfos);
    }

    // ==================== Excel Streaming Import ====================

    private List<TableInfo> importExcelStreaming(MultipartFile file, Long dsId) {
        // Allow larger byte arrays for big Excel files
        org.apache.poi.util.IOUtils.setByteArrayMaxOverride(500_000_000);

        List<TableInfo> result = new ArrayList<>();

        try (InputStream is = file.getInputStream();
             Workbook workbook = file.getOriginalFilename() != null
                     && file.getOriginalFilename().toLowerCase().endsWith(".xlsx")
                     ? new XSSFWorkbook(is)
                     : new HSSFWorkbook(is)) {

            for (int sheetIdx = 0; sheetIdx < workbook.getNumberOfSheets(); sheetIdx++) {
                org.apache.poi.ss.usermodel.Sheet sheet = workbook.getSheetAt(sheetIdx);
                if (sheet.getLastRowNum() < 0) continue;

                String tableName = "T_" + dsId + "_" + sheetIdx;
                TableInfo info = importSheetStreaming(sheet, tableName);
                if (info != null) {
                    result.add(info);
                }
            }

        } catch (IOException e) {
            throw new BusinessException("Excel 解析失败: " + e.getMessage());
        }

        return result;
    }

    private TableInfo importSheetStreaming(org.apache.poi.ss.usermodel.Sheet sheet, String tableName) {
        // Header row
        Row headerRow = sheet.getRow(0);
        if (headerRow == null) return null;

        int colCount = headerRow.getLastCellNum();
        List<String> colNames = new ArrayList<>();
        for (int c = 0; c < colCount; c++) {
            Cell cell = headerRow.getCell(c);
            String name = (cell == null || cell.toString().trim().isEmpty())
                    ? "COL_" + (c + 1) : cell.toString().trim();
            colNames.add(name);
        }

        // Gather sample rows for type inference (first N non-empty data rows)
        List<List<String>> sample = new ArrayList<>();
        int totalRows = 0;
        for (int r = 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null || isEmptyRow(row, colCount)) continue;
            totalRows++;
            if (sample.size() < sampleRows) {
                List<String> values = new ArrayList<>(colCount);
                for (int c = 0; c < colCount; c++) {
                    Cell cell = row.getCell(c);
                    String val = (cell == null || cell.toString().trim().isEmpty()) ? null : cell.toString().trim();
                    values.add(val);
                }
                sample.add(values);
            }
        }

        if (totalRows == 0) return null;

        List<String> types = inferTypes(colCount, sample);
        List<String> safeColNames = deduplicateColumnNames(colNames);

        // Create table
        tableManagementService.createTable(tableName, safeColNames, types);

        // Adjust batch size for wide tables to avoid H2 memory issues
        int effectiveBatchSize = Math.max(50, batchSize * 10 / Math.max(colCount, 1));

        // Stream rows in batches and insert
        List<List<Object>> batch = new ArrayList<>(effectiveBatchSize);
        int inserted = 0;

        for (int r = 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null || isEmptyRow(row, colCount)) continue;

            List<Object> values = new ArrayList<>(colCount);
            for (int c = 0; c < colCount; c++) {
                Cell cell = row.getCell(c);
                String raw = (cell == null || cell.toString().trim().isEmpty()) ? null : cell.toString().trim();
                values.add(parseValue(raw, types.get(c)));
            }
            batch.add(values);

            if (batch.size() >= effectiveBatchSize) {
                tableManagementService.batchInsert(tableName, safeColNames, batch);
                inserted += batch.size();
                batch.clear();
            }
        }
        // Flush remaining
        if (!batch.isEmpty()) {
            tableManagementService.batchInsert(tableName, safeColNames, batch);
            inserted += batch.size();
        }

        log.info("Excel sheet imported: table={}, sheet={}, rows={}", tableName, sheet.getSheetName(), inserted);

        List<ColumnInfo> columnInfos = new ArrayList<>();
        for (int j = 0; j < safeColNames.size(); j++) {
            columnInfos.add(new ColumnInfo(safeColNames.get(j), types.get(j)));
        }
        return new TableInfo(tableName, totalRows, columnInfos);
    }

    // ==================== Type Inference ====================

    List<String> inferTypes(int colCount, List<List<String>> sample) {
        List<String> types = new ArrayList<>();
        for (int c = 0; c < colCount; c++) {
            boolean allNull = true;
            boolean allLong = true;
            boolean allDouble = true;
            boolean allDate = true;
            boolean allTimestamp = true;

            for (List<String> row : sample) {
                String val = c < row.size() ? row.get(c) : null;
                if (val == null) continue;
                if (isPlaceholder(val)) continue; // skip NA, N/A, etc.
                allNull = false;
                if (allLong) allLong = isLong(val);
                if (allDouble) allDouble = isDouble(val);
                if (allDate) allDate = isDate(val);
                if (allTimestamp) allTimestamp = isTimestamp(val);
            }

            if (allNull) { types.add("VARCHAR(8192)"); }
            else if (allLong) { types.add("BIGINT"); }
            else if (allDouble) { types.add("DOUBLE"); }
            else if (allTimestamp) { types.add("TIMESTAMP"); }
            else if (allDate) { types.add("DATE"); }
            else { types.add("VARCHAR(8192)"); }
        }
        return types;
    }

    // ==================== Value Parsing ====================

    private Object parseValue(String val, String h2Type) {
        if (val == null) return null;
        // Treat common non-value placeholders as null
        String upper = val.trim().toUpperCase();
        if (upper.isEmpty() || upper.equals("NA") || upper.equals("N/A") || upper.equals("NULL")
                || upper.equals("NONE") || upper.equals("-") || upper.equals("--")) {
            return null;
        }
        return switch (h2Type) {
            case "BIGINT" -> {
                try { yield Long.parseLong(val.replace(",", "")); }
                catch (NumberFormatException e) { yield null; }
            }
            case "DOUBLE" -> {
                try { yield Double.parseDouble(val.replace(",", "")); }
                catch (NumberFormatException e) { yield null; }
            }
            case "DATE" -> {
                for (DateTimeFormatter fmt : DATE_FORMATS) {
                    try { yield LocalDate.parse(val, fmt); }
                    catch (DateTimeParseException ignored) {}
                }
                yield null;
            }
            case "TIMESTAMP" -> {
                for (DateTimeFormatter fmt : TIMESTAMP_FORMATS) {
                    try { yield LocalDateTime.parse(val, fmt); }
                    catch (DateTimeParseException ignored) {}
                }
                yield null;
            }
            default -> val;
        };
    }

    // ==================== Helpers ====================

    /**
     * Deduplicate column names: if two columns have the same sanitized name,
     * append _2, _3, etc.
     */
    private List<String> deduplicateColumnNames(List<String> rawNames) {
        List<String> result = new ArrayList<>();
        for (String raw : rawNames) {
            String name = TableManagementService.sanitizeColumnName(raw);
            int suffix = 1;
            String candidate = name;
            while (result.contains(candidate)) {
                suffix++;
                candidate = name + "_" + suffix;
            }
            result.add(candidate);
        }
        return result;
    }

    private static boolean isPlaceholder(String val) {
        if (val == null) return true;
        String u = val.trim().toUpperCase();
        return u.isEmpty() || u.equals("NA") || u.equals("N/A") || u.equals("NULL")
                || u.equals("NONE") || u.equals("-") || u.equals("--");
    }

    private static boolean isLong(String val) {
        try { Long.parseLong(val.replace(",", "")); return true; }
        catch (NumberFormatException e) { return false; }
    }

    private static boolean isDouble(String val) {
        try { Double.parseDouble(val.replace(",", "")); return true; }
        catch (NumberFormatException e) { return false; }
    }

    private static boolean isDate(String val) {
        for (DateTimeFormatter fmt : DATE_FORMATS) {
            try { LocalDate.parse(val, fmt); return true; }
            catch (DateTimeParseException ignored) {}
        }
        return false;
    }

    private static boolean isTimestamp(String val) {
        for (DateTimeFormatter fmt : TIMESTAMP_FORMATS) {
            try { LocalDateTime.parse(val, fmt); return true; }
            catch (DateTimeParseException ignored) {}
        }
        return false;
    }

    private String getExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1);
    }

    private boolean isEmptyRow(Row row, int colCount) {
        for (int c = 0; c < colCount; c++) {
            Cell cell = row.getCell(c);
            if (cell != null && cell.getCellType() != CellType.BLANK
                    && !cell.toString().trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }
}
