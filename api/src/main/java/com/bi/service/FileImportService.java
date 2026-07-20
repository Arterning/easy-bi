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
    private final Path uploadDir;

    public FileImportService(DataSourceRepository dataSourceRepository,
                             TableManagementService tableManagementService,
                             @Value("${bi.import.type-inference-sample-rows:100}") int sampleRows,
                             @Value("${bi.import.batch-size:1000}") int batchSize,
                             @Value("${bi.upload-dir:./data/uploads}") String uploadDir) {
        this.dataSourceRepository = dataSourceRepository;
        this.tableManagementService = tableManagementService;
        this.sampleRows = sampleRows;
        this.batchSize = batchSize;
        this.uploadDir = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    /**
     * Main entry point: import a CSV or Excel file, create tables, persist metadata.
     */
    @Transactional
    public UploadResult importFile(MultipartFile file) {
        String originalName = file.getOriginalFilename();
        if (originalName == null || originalName.isBlank()) {
            throw new BusinessException("文件名为空");
        }

        String extension = getExtension(originalName).toLowerCase();
        String fileType;
        List<SheetData> sheets;

        if (CSV_EXTENSIONS.contains(extension)) {
            fileType = "csv";
            sheets = parseCsv(file);
        } else if (EXCEL_EXTENSIONS.contains(extension)) {
            fileType = "excel";
            sheets = parseExcel(file);
        } else {
            throw new BusinessException("不支持的文件类型: ." + extension + " (仅支持 csv/xls/xlsx)");
        }

        if (sheets.isEmpty()) {
            throw new BusinessException("文件中没有可导入的数据");
        }

        // Save DataSource metadata first (to get an ID for table naming)
        DataSource ds = new DataSource();
        ds.setFileName(originalName);
        ds.setFileType(fileType);
        ds.setFileSize(file.getSize());
        ds.setTableNames(""); // placeholder
        ds = dataSourceRepository.save(ds);

        Long dsId = ds.getId();
        List<TableInfo> tableInfos = new ArrayList<>();
        List<String> tableNameList = new ArrayList<>();

        try {
            for (int i = 0; i < sheets.size(); i++) {
                SheetData sheet = sheets.get(i);
                String tableName = fileType.equals("csv")
                        ? "T_" + dsId
                        : "T_" + dsId + "_" + i;

                importSheet(tableName, sheet);
                int rowCount = tableManagementService.countRows(tableName);

                List<ColumnInfo> columnInfos = new ArrayList<>();
                for (int j = 0; j < sheet.columnNames.size(); j++) {
                    columnInfos.add(new ColumnInfo(
                            TableManagementService.sanitizeColumnName(sheet.columnNames.get(j)),
                            sheet.columnTypes.get(j)));
                }

                tableInfos.add(new TableInfo(tableName, rowCount, columnInfos));
                tableNameList.add(tableName);
            }
        } catch (Exception e) {
            // Roll back: drop any tables we created
            for (String t : tableNameList) {
                try { tableManagementService.dropTable(t); } catch (Exception ignored) {}
            }
            dataSourceRepository.delete(ds);
            throw new BusinessException("导入失败: " + e.getMessage());
        }

        // Update table names
        ds.setTableNames(String.join(",", tableNameList));
        dataSourceRepository.save(ds);

        return new UploadResult(dsId, originalName, fileType, file.getSize(), tableInfos);
    }

    // --------------- CSV Parsing ---------------

    private List<SheetData> parseCsv(MultipartFile file) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {

            CSVParser parser = CSVFormat.DEFAULT
                    .withFirstRecordAsHeader()
                    .withIgnoreHeaderCase()
                    .withTrim()
                    .parse(reader);

            List<String> headers = parser.getHeaderNames();
            if (headers.isEmpty()) {
                throw new BusinessException("CSV 文件没有表头");
            }

            List<List<String>> sample = new ArrayList<>();
            List<List<String>> allRows = new ArrayList<>();
            int count = 0;

            for (CSVRecord record : parser) {
                List<String> row = new ArrayList<>();
                for (int i = 0; i < headers.size(); i++) {
                    row.add(i < record.size() ? record.get(i) : null);
                }
                if (count < sampleRows) {
                    sample.add(row);
                }
                allRows.add(row);
                count++;
            }

            // Infer types from sample
            List<String> types = inferTypes(headers.size(), sample);
            List<String> colNames = new ArrayList<>(headers);

            List<SheetData> result = new ArrayList<>();
            result.add(new SheetData("data", colNames, types, allRows));
            return result;

        } catch (IOException e) {
            throw new BusinessException("CSV 解析失败: " + e.getMessage());
        }
    }

    // --------------- Excel Parsing ---------------

    private List<SheetData> parseExcel(MultipartFile file) {
        // Allow larger byte arrays for big Excel files
        org.apache.poi.util.IOUtils.setByteArrayMaxOverride(500_000_000);

        List<SheetData> sheets = new ArrayList<>();

        try (InputStream is = file.getInputStream();
             Workbook workbook = file.getOriginalFilename() != null
                     && file.getOriginalFilename().toLowerCase().endsWith(".xlsx")
                     ? new XSSFWorkbook(is)
                     : new HSSFWorkbook(is)) {

            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                org.apache.poi.ss.usermodel.Sheet sheet = workbook.getSheetAt(i);
                if (sheet.getLastRowNum() < 0) continue; // empty sheet

                List<String> colNames = new ArrayList<>();
                List<List<String>> sample = new ArrayList<>();
                List<List<String>> allRows = new ArrayList<>();

                // First row = header
                Row headerRow = sheet.getRow(0);
                if (headerRow == null) continue;

                int colCount = headerRow.getLastCellNum();
                Set<Integer> emptyCols = new HashSet<>();

                for (int c = 0; c < colCount; c++) {
                    Cell cell = headerRow.getCell(c);
                    String name = cell == null ? "" : cell.toString().trim();
                    if (name.isEmpty()) {
                        name = "COL_" + (c + 1);
                        emptyCols.add(c);
                    }
                    colNames.add(name);
                }

                // Data rows
                for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                    Row row = sheet.getRow(r);
                    if (row == null) continue;
                    if (isEmptyRow(row, colCount)) continue;

                    List<String> values = new ArrayList<>(colCount);
                    for (int c = 0; c < colCount; c++) {
                        Cell cell = row.getCell(c);
                        String val = cell == null ? null : cell.toString().trim();
                        if (val != null && val.isEmpty()) val = null;
                        values.add(val);
                    }
                    if (allRows.size() < sampleRows) {
                        sample.add(values);
                    }
                    allRows.add(values);
                }

                if (allRows.isEmpty()) continue;

                List<String> types = inferTypes(colCount, sample);
                String sheetName = sheet.getSheetName();
                sheets.add(new SheetData(sheetName, colNames, types, allRows));
            }

        } catch (IOException e) {
            throw new BusinessException("Excel 解析失败: " + e.getMessage());
        }

        return sheets;
    }

    // --------------- Import ---------------

    private void importSheet(String tableName, SheetData sheet) {
        // Sanitize and deduplicate column names
        List<String> colNames = new ArrayList<>();
        for (String raw : sheet.columnNames) {
            String name = TableManagementService.sanitizeColumnName(raw);
            // Deduplicate: append _2, _3, ... for repeated names
            int suffix = 1;
            String candidate = name;
            while (colNames.contains(candidate)) {
                suffix++;
                candidate = name + "_" + suffix;
            }
            colNames.add(candidate);
        }

        // Build typed row values
        List<List<Object>> typedRows = new ArrayList<>();
        for (List<String> rawRow : sheet.allRows) {
            List<Object> typedRow = new ArrayList<>(colNames.size());
            for (int i = 0; i < colNames.size(); i++) {
                typedRow.add(parseValue(rawRow.get(i), sheet.columnTypes.get(i)));
            }
            typedRows.add(typedRow);
        }

        // Create table
        tableManagementService.createTable(tableName, colNames, sheet.columnTypes);

        // Batch insert
        for (int i = 0; i < typedRows.size(); i += batchSize) {
            int end = Math.min(i + batchSize, typedRows.size());
            List<List<Object>> batch = typedRows.subList(i, end);
            tableManagementService.batchInsert(tableName, colNames, batch);
        }

        log.info("Imported table {}: {} rows, {} columns", tableName, typedRows.size(), colNames.size());
    }

    // --------------- Type Inference ---------------

    /**
     * Infer column types by scanning sample rows.
     * Returns H2 type names: BIGINT, DOUBLE, DATE, TIMESTAMP, VARCHAR(1024)
     */
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
                allNull = false;

                if (allLong) allLong = isLong(val);
                if (allDouble) allDouble = isDouble(val);
                if (allDate) allDate = isDate(val);
                if (allTimestamp) allTimestamp = isTimestamp(val);
            }

            if (allNull) {
                types.add("VARCHAR(1024)");
            } else if (allLong) {
                types.add("BIGINT");
            } else if (allDouble) {
                types.add("DOUBLE");
            } else if (allTimestamp) {
                types.add("TIMESTAMP");
            } else if (allDate) {
                types.add("DATE");
            } else {
                types.add("VARCHAR(1024)");
            }
        }

        return types;
    }

    private static boolean isLong(String val) {
        try {
            Long.parseLong(val.replace(",", ""));
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean isDouble(String val) {
        try {
            Double.parseDouble(val.replace(",", ""));
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean isDate(String val) {
        for (DateTimeFormatter fmt : DATE_FORMATS) {
            try {
                LocalDate.parse(val, fmt);
                return true;
            } catch (DateTimeParseException ignored) {}
        }
        return false;
    }

    private static boolean isTimestamp(String val) {
        for (DateTimeFormatter fmt : TIMESTAMP_FORMATS) {
            try {
                LocalDateTime.parse(val, fmt);
                return true;
            } catch (DateTimeParseException ignored) {}
        }
        return false;
    }

    /**
     * Parse a string to the appropriate Java type for JDBC.
     */
    private Object parseValue(String val, String h2Type) {
        if (val == null) return null;
        return switch (h2Type) {
            case "BIGINT" -> {
                try { yield Long.parseLong(val.replace(",", "")); }
                catch (NumberFormatException e) { yield val; }
            }
            case "DOUBLE" -> {
                try { yield Double.parseDouble(val.replace(",", "")); }
                catch (NumberFormatException e) { yield val; }
            }
            case "DATE" -> {
                for (DateTimeFormatter fmt : DATE_FORMATS) {
                    try { yield LocalDate.parse(val, fmt); }
                    catch (DateTimeParseException ignored) {}
                }
                yield val;
            }
            case "TIMESTAMP" -> {
                for (DateTimeFormatter fmt : TIMESTAMP_FORMATS) {
                    try { yield LocalDateTime.parse(val, fmt); }
                    catch (DateTimeParseException ignored) {}
                }
                yield val;
            }
            default -> val;
        };
    }

    // --------------- Helpers ---------------

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

    // --------------- Internal ---------------

    private static class SheetData {
        final String sheetName;
        final List<String> columnNames;
        final List<String> columnTypes;
        final List<List<String>> allRows;

        SheetData(String sheetName, List<String> columnNames,
                  List<String> columnTypes, List<List<String>> allRows) {
            this.sheetName = sheetName;
            this.columnNames = columnNames;
            this.columnTypes = columnTypes;
            this.allRows = allRows;
        }
    }
}
