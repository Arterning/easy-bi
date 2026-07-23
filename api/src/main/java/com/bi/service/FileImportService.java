package com.bi.service;

import com.bi.exception.BusinessException;
import com.bi.model.dto.AppendResult;
import com.bi.model.dto.ColumnInfo;
import com.bi.model.dto.TableInfo;
import com.bi.model.dto.UploadResult;
import com.bi.model.entity.BiTable;
import com.bi.model.entity.DataSource;
import com.bi.repository.BiTableRepository;
import com.bi.repository.DataSourceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;

@Service
public class FileImportService {

    private static final Logger log = LoggerFactory.getLogger(FileImportService.class);

    private static final Set<String> CSV_EXTENSIONS = Set.of("csv", "txt");
    private static final Set<String> EXCEL_EXTENSIONS = Set.of("xls", "xlsx");

    private final DataSourceRepository dataSourceRepository;
    private final BiTableRepository biTableRepository;
    private final TableManagementService tableManagementService;
    private final JdbcTemplate duckDb;
    private final Path uploadDir;

    public FileImportService(DataSourceRepository dataSourceRepository,
                             BiTableRepository biTableRepository,
                             TableManagementService tableManagementService,
                             JdbcTemplate duckDbJdbcTemplate,
                             @Value("${bi.upload-dir:./data/uploads}") String uploadDir) {
        this.dataSourceRepository = dataSourceRepository;
        this.biTableRepository = biTableRepository;
        this.tableManagementService = tableManagementService;
        this.duckDb = duckDbJdbcTemplate;
        this.uploadDir = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

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

        // Save uploaded file to disk
        Path savedPath;
        try {
            Files.createDirectories(uploadDir);
            savedPath = uploadDir.resolve(originalName);
            Files.copy(file.getInputStream(), savedPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new BusinessException("文件保存失败: " + e.getMessage());
        }
        String filePath = savedPath.toAbsolutePath().toString().replace("\\", "/");

        // Save metadata
        DataSource ds = new DataSource();
        ds.setFileName(originalName);
        ds.setFileType(fileType);
        ds.setFileSize(file.getSize());
        ds = dataSourceRepository.save(ds);

        List<TableInfo> tableInfos = new ArrayList<>();
        List<BiTable> biTables = new ArrayList<>();
        List<String> duckDbTables = new ArrayList<>();

        try {
            if (fileType.equals("csv")) {
                String uuid = UUID.randomUUID().toString().substring(0, 8);
                String physicalName = "t_" + uuid;
                importCsv(filePath, physicalName);
                int rowCount = tableManagementService.countRows(physicalName);
                List<ColumnInfo> columns = tableManagementService.getTableColumns(physicalName);
                String displayName = stripExtension(originalName);
                tableInfos.add(new TableInfo(physicalName, displayName, null, rowCount, columns));

                BiTable bt = new BiTable();
                bt.setDataSource(ds);
                bt.setPhysicalName(physicalName);
                bt.setDisplayName(displayName);
                bt.setSourceSheet(null);
                biTables.add(bt);
                duckDbTables.add(physicalName);
            } else {
                List<String> sheetNames = getSheetNames(filePath);
                if (sheetNames.isEmpty()) {
                    throw new BusinessException("Excel 文件中没有可读的 Sheet");
                }
                for (int i = 0; i < sheetNames.size(); i++) {
                    String uuid = UUID.randomUUID().toString().substring(0, 8);
                    String physicalName = "t_" + uuid;
                    importExcelSheet(filePath, sheetNames.get(i), physicalName);
                    int rowCount = tableManagementService.countRows(physicalName);
                    List<ColumnInfo> columns = tableManagementService.getTableColumns(physicalName);
                    tableInfos.add(new TableInfo(physicalName, sheetNames.get(i), sheetNames.get(i), rowCount, columns));

                    BiTable bt = new BiTable();
                    bt.setDataSource(ds);
                    bt.setPhysicalName(physicalName);
                    bt.setDisplayName(sheetNames.get(i));
                    bt.setSourceSheet(sheetNames.get(i));
                    biTables.add(bt);
                    duckDbTables.add(physicalName);
                }
            }
        } catch (Exception e) {
            log.error("数据源导入失败: {}", e.getMessage(), e);
            for (String t : duckDbTables) {
                try { tableManagementService.dropTable(t); } catch (Exception ignored) {}
            }
            dataSourceRepository.delete(ds);
            if (e instanceof BusinessException) throw (BusinessException) e;
            throw new BusinessException("导入失败: " + e.getMessage());
        }

        biTableRepository.saveAll(biTables);
        return new UploadResult(ds.getId(), originalName, fileType, file.getSize(), tableInfos);
    }

    // ==================== DuckDB native import ====================

    private void importCsv(String filePath, String tableName) {
        String sql = "CREATE TABLE main.\"" + tableName + "\" AS " +
                     "SELECT * FROM read_csv('" + filePath + "', header=true, auto_detect=true)";
        log.info("Importing CSV: {} → {}", filePath, tableName);
        duckDb.execute(sql);
        log.info("CSV imported: table={}", tableName);
    }

    private void importExcelSheet(String filePath, String sheetName, String tableName) {
        String sql = "CREATE TABLE main.\"" + tableName + "\" AS " +
                     "SELECT * FROM read_xlsx('" + filePath + "', sheet='" + sheetName.replace("'", "''") + "',ignore_errors=True)";
        log.info("Importing Excel sheet: {}[{}] → {}", filePath, sheetName, tableName);
        duckDb.execute(sql);
        log.info("Excel sheet imported: table={}", tableName);
    }

    /**
     * Extract sheet names from Excel files.
     * .xlsx: parsed from ZIP/xl/workbook.xml (no extra deps needed)
     * .xls:  falls back to single "Sheet1" (old binary format requires POI)
     */
    private List<String> getSheetNames(String filePath) {
        if (filePath.toLowerCase().endsWith(".xlsx")) {
            return getXlsxSheetNames(filePath);
        }
        return List.of("Sheet1");
    }

    private List<String> getXlsxSheetNames(String filePath) {
        List<String> names = new ArrayList<>();
        try (ZipFile zip = new ZipFile(filePath)) {
            var entry = zip.getEntry("xl/workbook.xml");
            if (entry == null) return List.of("Sheet1");

            var doc = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .parse(zip.getInputStream(entry));
            var sheets = doc.getElementsByTagName("sheet");
            for (int i = 0; i < sheets.getLength(); i++) {
                Element sheet = (Element) sheets.item(i);
                String name = sheet.getAttribute("name");
                if (name != null && !name.isEmpty()) {
                    names.add(name);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse sheet names from xlsx: {}", e.getMessage());
            return List.of("Sheet1");
        }
        return names.isEmpty() ? List.of("Sheet1") : names;
    }

    // ==================== Append ====================

    @Transactional
    public AppendResult appendToDataSource(Long dsId, MultipartFile file) {
        DataSource ds = dataSourceRepository.findById(dsId)
                .orElseThrow(() -> new BusinessException("数据源不存在: id=" + dsId));

        String originalName = file.getOriginalFilename();
        List<BiTable> existingTables = biTableRepository.findByDataSourceId(dsId);

        if (existingTables.isEmpty()) {
            throw new BusinessException("该数据源下没有可追加的表");
        }

        // Save file temporarily
        Path tmpPath;
        try {
            Files.createDirectories(uploadDir);
            tmpPath = uploadDir.resolve("_append_" + System.currentTimeMillis() + "_" + originalName);
            Files.copy(file.getInputStream(), tmpPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new BusinessException("文件保存失败: " + e.getMessage());
        }
        String filePath = tmpPath.toAbsolutePath().toString().replace("\\", "/");

        List<AppendResult.TableAppend> results = new ArrayList<>();

        try {
            String ext = getExtension(originalName).toLowerCase();
            List<String> newSheetNames;

            if (EXCEL_EXTENSIONS.contains(ext)) {
                newSheetNames = getSheetNames(filePath);
            } else {
                newSheetNames = List.of("data");
            }

            int matchCount = Math.min(existingTables.size(), newSheetNames.size());

            for (int i = 0; i < existingTables.size(); i++) {
                BiTable bt = existingTables.get(i);
                String tableName = bt.getPhysicalName();
                AppendResult.TableAppend ta = new AppendResult.TableAppend();
                ta.setTableName(tableName);

                if (i >= matchCount) {
                    ta.setSkipped(true);
                    ta.setSkipReason("新文件 Sheet 数量(" + newSheetNames.size() + ")少于目标表数量(" + existingTables.size() + ")，跳过");
                    ta.setRowsBefore(tableManagementService.countRows(tableName));
                    ta.setRowsAppended(0);
                    ta.setRowsAfter(ta.getRowsBefore());
                    results.add(ta);
                    continue;
                }

                int rowsBefore = tableManagementService.countRows(tableName);
                ta.setRowsBefore(rowsBefore);

                String sheetName = newSheetNames.get(i);
                appendSheetToTable(filePath, sheetName, tableName, ext, ta);
                results.add(ta);
            }

        } catch (Exception e) {
            if (e instanceof BusinessException) throw (BusinessException) e;
            throw new BusinessException("追加失败: " + e.getMessage());
        } finally {
            try { Files.deleteIfExists(tmpPath); } catch (IOException ignored) {}
        }

        return new AppendResult(dsId, originalName, results);
    }

    private void appendSheetToTable(String filePath, String sheetName, String tableName,
                                     String fileExt, AppendResult.TableAppend result) {
        String tmpTable = "_append_tmp_" + System.currentTimeMillis();

        try {
            if (EXCEL_EXTENSIONS.contains(fileExt)) {
                duckDb.execute("CREATE TEMP TABLE " + tmpTable + " AS " +
                        "SELECT * FROM read_xlsx('" + filePath + "', sheet='" + sheetName.replace("'", "''") + "')");
            } else {
                duckDb.execute("CREATE TEMP TABLE " + tmpTable + " AS " +
                        "SELECT * FROM read_csv('" + filePath + "', header=true, auto_detect=true)");
            }
        } catch (Exception e) {
            throw new BusinessException("读取文件失败 (sheet=" + sheetName + "): " + e.getMessage());
        }

        try {
            List<String> targetCols = duckDb.query(
                    "SELECT column_name FROM information_schema.columns " +
                    "WHERE table_schema='main' AND table_name=? ORDER BY ordinal_position",
                    (rs, rn) -> rs.getString("column_name"), tableName);

            List<String> sourceCols = duckDb.query(
                    "SELECT column_name FROM information_schema.columns " +
                    "WHERE table_schema='main' AND table_name=? ORDER BY ordinal_position",
                    (rs, rn) -> rs.getString("column_name"), tmpTable);

            Set<String> targetLower = new HashSet<>();
            for (String c : targetCols) targetLower.add(c.toLowerCase());

            List<String> newColumns = new ArrayList<>();
            List<String> matchedColumns = new ArrayList<>();
            List<String> missingColumns = new ArrayList<>();

            for (String sc : sourceCols) {
                if (targetLower.contains(sc.toLowerCase())) {
                    matchedColumns.add(sc);
                } else {
                    newColumns.add(sc);
                }
            }
            Set<String> sourceLower = new HashSet<>();
            for (String sc : sourceCols) sourceLower.add(sc.toLowerCase());
            for (String tc : targetCols) {
                if (!sourceLower.contains(tc.toLowerCase())) {
                    missingColumns.add(tc);
                }
            }

            for (String nc : newColumns) {
                String ddl = "ALTER TABLE main.\"" + tableName + "\" ADD COLUMN \"" + nc + "\" VARCHAR";
                log.info("Adding column: {}", ddl);
                duckDb.execute(ddl);
            }

            StringBuilder insertSql = new StringBuilder();
            insertSql.append("INSERT INTO main.\"").append(tableName).append("\" (");
            List<String> allTargetCols = new ArrayList<>(targetCols);
            allTargetCols.addAll(newColumns);
            for (int i = 0; i < allTargetCols.size(); i++) {
                if (i > 0) insertSql.append(", ");
                insertSql.append("\"").append(allTargetCols.get(i)).append("\"");
            }
            insertSql.append(") SELECT ");
            for (int i = 0; i < allTargetCols.size(); i++) {
                if (i > 0) insertSql.append(", ");
                String col = allTargetCols.get(i);
                if (sourceLower.contains(col.toLowerCase())) {
                    insertSql.append("\"").append(col).append("\"");
                } else {
                    insertSql.append("NULL");
                }
            }
            insertSql.append(" FROM ").append(tmpTable);

            log.info("Appending: {}", insertSql);
            duckDb.execute(insertSql.toString());

            int rowsAfter = tableManagementService.countRows(tableName);
            result.setRowsAppended(rowsAfter - result.getRowsBefore());
            result.setRowsAfter(rowsAfter);
            result.setNewColumns(newColumns);
            result.setMatchedColumns(matchedColumns);
            result.setMissingColumns(missingColumns);
            result.setSkipped(false);

        } finally {
            try { duckDb.execute("DROP TABLE IF EXISTS " + tmpTable); } catch (Exception ignored) {}
        }
    }

    // ==================== Helpers ====================

    private String getExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1);
    }

    private String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? fileName : fileName.substring(0, dot);
    }
}
