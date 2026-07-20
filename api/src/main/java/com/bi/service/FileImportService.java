package com.bi.service;

import com.bi.exception.BusinessException;
import com.bi.model.dto.UploadResult;
import com.bi.model.dto.UploadResult.ColumnInfo;
import com.bi.model.dto.UploadResult.TableInfo;
import com.bi.model.entity.DataSource;
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

@Service
public class FileImportService {

    private static final Logger log = LoggerFactory.getLogger(FileImportService.class);

    private static final Set<String> CSV_EXTENSIONS = Set.of("csv", "txt");
    private static final Set<String> EXCEL_EXTENSIONS = Set.of("xls", "xlsx");

    private final DataSourceRepository dataSourceRepository;
    private final TableManagementService tableManagementService;
    private final JdbcTemplate duckDb;
    private final Path uploadDir;

    public FileImportService(DataSourceRepository dataSourceRepository,
                             TableManagementService tableManagementService,
                             JdbcTemplate duckDbJdbcTemplate,
                             @Value("${bi.upload-dir:./data/uploads}") String uploadDir) {
        this.dataSourceRepository = dataSourceRepository;
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

        // Save uploaded file to disk (DuckDB needs a file path, not a stream)
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
        ds.setTableNames("");
        ds = dataSourceRepository.save(ds);

        Long dsId = ds.getId();
        List<TableInfo> tableInfos = new ArrayList<>();
        List<String> tableNameList = new ArrayList<>();

        try {
            if (fileType.equals("csv")) {
                String tableName = "T_" + dsId;
                importCsv(filePath, tableName);
                int rowCount = tableManagementService.countRows(tableName);
                List<ColumnInfo> columns = tableManagementService.getTableColumns(tableName);
                tableInfos.add(new TableInfo(tableName, rowCount, columns));
                tableNameList.add(tableName);
            } else {
                // Excel: detect sheet names, import each as a separate table
                List<String> sheetNames = getSheetNames(filePath);
                if (sheetNames.isEmpty()) {
                    throw new BusinessException("Excel 文件中没有可读的 Sheet");
                }
                for (int i = 0; i < sheetNames.size(); i++) {
                    String tableName = "T_" + dsId + "_" + i;
                    importExcelSheet(filePath, sheetNames.get(i), tableName);
                    int rowCount = tableManagementService.countRows(tableName);
                    List<ColumnInfo> columns = tableManagementService.getTableColumns(tableName);
                    tableInfos.add(new TableInfo(tableName, rowCount, columns));
                    tableNameList.add(tableName);
                }
            }
        } catch (Exception e) {
            // Rollback: drop created tables
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

    // ==================== DuckDB native import ====================

    private void importCsv(String filePath, String tableName) {
        String sql = "CREATE TABLE bi_data.\"" + tableName + "\" AS " +
                     "SELECT * FROM read_csv('" + filePath + "', header=true, auto_detect=true)";
        log.info("Importing CSV: {} → {}", filePath, tableName);
        duckDb.execute(sql);
        log.info("CSV imported: table={}", tableName);
    }

    private void importExcelSheet(String filePath, String sheetName, String tableName) {
        String sql = "CREATE TABLE bi_data.\"" + tableName + "\" AS " +
                     "SELECT * FROM st_read('" + filePath + "', layer='" + sheetName.replace("'", "''") + "')";
        log.info("Importing Excel sheet: {}[{}] → {}", filePath, sheetName, tableName);
        duckDb.execute(sql);
        log.info("Excel sheet imported: table={}", tableName);
    }

    private List<String> getSheetNames(String filePath) {
        return duckDb.query(
                "SELECT name FROM st_layers('" + filePath + "')",
                (rs, rowNum) -> rs.getString("name")
        );
    }

    // ==================== Helpers ====================

    private String getExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1);
    }
}
