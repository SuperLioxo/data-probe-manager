package com.lixin.probe.service.impl;

import com.alibaba.fastjson2.JSON;
import com.lixin.probe.entity.DatabaseConnection;
import com.lixin.probe.service.DataImportService;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Slf4j
@Service
public class DataImportServiceImpl implements DataImportService {

    @Override
    public Map<String, Object> importFromFile(DatabaseConnection conn, MultipartFile file,
                                               String tableName, String schema) throws Exception {
        log.info("[数据导入] 开始导入: file={}, tableName={}, schema={}, db={}",
                file.getOriginalFilename(), tableName, schema, conn.getDatabaseName());

        String originalFilename = file.getOriginalFilename();
        String ext = originalFilename != null ? originalFilename.substring(originalFilename.lastIndexOf('.') + 1).toLowerCase() : "";

        List<String> headers;
        List<List<Object>> rows;

        if ("csv".equals(ext)) {
            try (InputStream is = file.getInputStream();
                 BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String headerLine = br.readLine();
                if (headerLine == null || headerLine.trim().isEmpty()) {
                    throw new IllegalArgumentException("CSV文件为空");
                }
                headers = parseCsvLine(headerLine);
                rows = new ArrayList<>();
                String line;
                while ((line = br.readLine()) != null) {
                    if (line.trim().isEmpty()) continue;
                    List<Object> row = new ArrayList<>(parseCsvLine(line));
                    rows.add(row);
                }
            }
        } else if ("xlsx".equals(ext) || "xls".equals(ext)) {
            try (InputStream is = file.getInputStream();
                 Workbook workbook = "xlsx".equals(ext) ? new XSSFWorkbook(is) : new HSSFWorkbook(is)) {
                Sheet sheet = workbook.getSheetAt(0);
                Row firstRow = sheet.getRow(0);
                headers = new ArrayList<>();
                if (firstRow != null) {
                    for (Cell cell : firstRow) {
                        headers.add(getCellStringValue(cell));
                    }
                }
                rows = new ArrayList<>();
                for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                    Row row = sheet.getRow(i);
                    if (row == null) continue;
                    List<Object> dataRow = new ArrayList<>();
                    for (int j = 0; j < headers.size(); j++) {
                        Cell cell = row.getCell(j);
                        dataRow.add(cell != null ? getCellStringValue(cell) : null);
                    }
                    rows.add(dataRow);
                }
            }
        } else {
            throw new IllegalArgumentException("不支持的文件格式: " + ext + "，请上传 .xlsx、.xls 或 .csv 文件");
        }

        if (headers.isEmpty()) {
            throw new IllegalArgumentException("文件为空或格式不正确");
        }

        log.info("[数据导入] 解析完成: 列数={}, 行数={}", headers.size(), rows.size());

        DataSource dataSource = createDataSource(conn);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        String qualifiedTable = schema != null && !schema.isEmpty() ? schema + "." + tableName : tableName;

        createTableIfNotExists(jdbc, qualifiedTable, headers, conn.getDatabaseType());
        insertData(jdbc, qualifiedTable, headers, rows, conn.getDatabaseType());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tableName", tableName);
        result.put("columns", headers.size());
        result.put("rows", rows.size());
        result.put("filename", originalFilename);
        log.info("[数据导入] 导入成功: {} 行数据写入表 {}", rows.size(), qualifiedTable);
        return result;
    }

    private DataSource createDataSource(DatabaseConnection conn) {
        String dbType = conn.getDatabaseType().toLowerCase();
        String url;
        if ("postgresql".equals(dbType)) {
            url = "jdbc:postgresql://" + conn.getDatabaseHost() + ":" + conn.getDatabasePort() + "/" + conn.getDatabaseName();
        } else if ("mysql".equals(dbType)) {
            url = "jdbc:mysql://" + conn.getDatabaseHost() + ":" + conn.getDatabasePort() + "/" + conn.getDatabaseName() + "?useSSL=false&characterEncoding=utf8&allowPublicKeyRetrieval=true";
        } else {
            throw new IllegalArgumentException("不支持的数据库类型: " + dbType);
        }
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setUrl(url);
        ds.setUsername(conn.getUsername());
        ds.setPassword(conn.getPassword());
        ds.setDriverClassName(getDriverClassName(dbType));
        return ds;
    }

    private String getDriverClassName(String dbType) {
        return switch (dbType) {
            case "postgresql" -> "org.postgresql.Driver";
            case "mysql" -> "com.mysql.cj.jdbc.Driver";
            default -> throw new IllegalArgumentException("不支持的数据库类型: " + dbType);
        };
    }

    private void createTableIfNotExists(JdbcTemplate jdbc, String table, List<String> headers, String dbType) {
        String fullTableName = table;
        String checkSql;
        String columnDefs = buildColumnDefinitions(headers, dbType);

        if ("mysql".equals(dbType)) {
            checkSql = "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ?";
            // MySQL uses backticks
            columnDefs = buildColumnDefinitions(headers, dbType);
            fullTableName = "`" + table + "`";
        } else {
            checkSql = "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = ?";
        }

        Integer count = jdbc.queryForObject(checkSql, Integer.class, table);
        if (count == null || count == 0) {
            String createSql;
            if ("mysql".equals(dbType)) {
                createSql = "CREATE TABLE " + fullTableName + " (" + columnDefs + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
            } else {
                createSql = "CREATE TABLE " + fullTableName + " (" + columnDefs + ")";
            }
            log.info("[数据导入] 创建表: {}", createSql);
            jdbc.execute(createSql);
        } else {
            log.info("[数据导入] 表 {} 已存在，直接插入数据", table);
        }
    }

    private String buildColumnDefinitions(List<String> headers, String dbType) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < headers.size(); i++) {
            if (i > 0) sb.append(", ");
            String col = headers.get(i).trim();
            if (col.isEmpty()) col = "column_" + (i + 1);
            // Sanitize column name
            col = col.replaceAll("[^a-zA-Z0-9_]", "_");
            if (col.matches("^\\d.*")) col = "col_" + col;
            headers.set(i, col);
            sb.append(col).append(" TEXT");
        }
        return sb.toString();
    }

    private void insertData(JdbcTemplate jdbc, String table, List<String> headers, List<List<Object>> rows, String dbType) {
        if (rows.isEmpty()) return;

        StringBuilder sql = new StringBuilder("INSERT INTO ");
        if ("mysql".equals(dbType)) {
            sql.append("`").append(table).append("`");
        } else {
            sql.append(table);
        }
        sql.append(" (");
        for (int i = 0; i < headers.size(); i++) {
            if (i > 0) sql.append(", ");
            sql.append("\"").append(headers.get(i)).append("\"");
        }
        sql.append(") VALUES (");
        for (int i = 0; i < headers.size(); i++) {
            if (i > 0) sql.append(", ");
            sql.append("?");
        }
        sql.append(")");

        String insertSql = sql.toString();
        List<Object[]> batchArgs = rows.stream().map(row -> {
            Object[] params = new Object[headers.size()];
            for (int i = 0; i < headers.size(); i++) {
                params[i] = i < row.size() ? row.get(i) : null;
            }
            return params;
        }).toList();

        jdbc.batchUpdate(insertSql, batchArgs);
    }

    private String getCellStringValue(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                double val = cell.getNumericCellValue();
                if (val == Math.floor(val)) yield String.valueOf((long) val);
                yield String.valueOf(val);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try {
                    yield cell.getStringCellValue();
                } catch (Exception e) {
                    yield String.valueOf(cell.getNumericCellValue());
                }
            }
            case BLANK -> "";
            default -> cell.toString().trim();
        };
    }

    private List<String> parseCsvLine(String line) {
        List<String> result = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    sb.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                result.add(sb.toString().trim());
                sb = new StringBuilder();
            } else {
                sb.append(c);
            }
        }
        result.add(sb.toString().trim());
        return result;
    }
}
