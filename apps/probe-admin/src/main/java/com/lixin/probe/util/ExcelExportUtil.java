package com.lixin.probe.util;

import com.lixin.probe.entity.Alert;
import com.lixin.probe.entity.Probe;
import com.lixin.probe.entity.AuditLog;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Excel导出工具类
 */
public class ExcelExportUtil {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 导出探针列表
     */
    public static byte[] exportProbes(List<Probe> probes) {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("探针列表");

            // 创建表头样式
            CellStyle headerStyle = createHeaderStyle(workbook);

            // 创建表头
            Row headerRow = sheet.createRow(0);
            String[] headers = {"探针Key", "名称", "类型", "状态", "IP地址", "端口", "版本", "创建时间"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // 自动调整列宽
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            // 填充数据
            CellStyle dataStyle = createDataStyle(workbook);
            for (int i = 0; i < probes.size(); i++) {
                Probe probe = probes.get(i);
                Row row = sheet.createRow(i + 1);

                createCell(row, 0, probe.getProbeKey(), dataStyle);
                createCell(row, 1, probe.getName(), dataStyle);
                createCell(row, 2, probe.getType(), dataStyle);
                createCell(row, 3, probe.getStatus(), dataStyle);
                createCell(row, 4, probe.getHostIp(), dataStyle);
                createCell(row, 5, probe.getPort() != null ? probe.getPort().toString() : "", dataStyle);
                createCell(row, 6, probe.getVersion() != null ? probe.getVersion().toString() : "", dataStyle);
                createCell(row, 7, formatDate(probe.getCreateTime()), dataStyle);
            }

            // 写入字节数组
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);
            return outputStream.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("导出Excel失败", e);
        }
    }

    /**
     * 导出告警列表
     */
    public static byte[] exportAlerts(List<Alert> alerts) {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("告警列表");

            // 创建表头样式
            CellStyle headerStyle = createHeaderStyle(workbook);

            // 创建表头
            Row headerRow = sheet.createRow(0);
            String[] headers = {"告警ID", "探针名称", "规则ID", "告警级别", "状态", "告警内容", "触发时间", "确认时间"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // 自动调整列宽
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            // 填充数据
            CellStyle dataStyle = createDataStyle(workbook);
            for (int i = 0; i < alerts.size(); i++) {
                Alert alert = alerts.get(i);
                Row row = sheet.createRow(i + 1);

                createCell(row, 0, alert.getId() != null ? alert.getId().toString() : "", dataStyle);
                createCell(row, 1, alert.getProbeName() != null ? alert.getProbeName() : "", dataStyle);
                createCell(row, 2, alert.getRuleId() != null ? alert.getRuleId().toString() : "", dataStyle);
                createCell(row, 3, alert.getSeverity() != null ? alert.getSeverity() : "", dataStyle);
                createCell(row, 4, alert.getStatus() != null ? alert.getStatus() : "", dataStyle);
                createCell(row, 5, alert.getMessage() != null ? alert.getMessage() : "", dataStyle);
                createCell(row, 6, formatDate(alert.getTriggeredAt()), dataStyle);
                createCell(row, 7, formatDate(alert.getAcknowledgedAt()), dataStyle);
            }

            // 写入字节数组
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);
            return outputStream.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("导出Excel失败", e);
        }
    }

    /**
     * 导出审计日志
     */
    public static byte[] exportAuditLogs(List<AuditLog> logs) {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("审计日志");

            // 创建表头样式
            CellStyle headerStyle = createHeaderStyle(workbook);

            // 创建表头
            Row headerRow = sheet.createRow(0);
            String[] headers = {"ID", "用户名", "操作类型", "模块", "描述", "请求URL", "响应码", "执行时间(ms)", "IP地址", "操作时间"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // 自动调整列宽
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            // 填充数据
            CellStyle dataStyle = createDataStyle(workbook);
            for (int i = 0; i < logs.size(); i++) {
                AuditLog log = logs.get(i);
                Row row = sheet.createRow(i + 1);

                createCell(row, 0, log.getId() != null ? log.getId().toString() : "", dataStyle);
                createCell(row, 1, log.getUsername() != null ? log.getUsername() : "", dataStyle);
                createCell(row, 2, log.getOperation() != null ? log.getOperation() : "", dataStyle);
                createCell(row, 3, log.getModule() != null ? log.getModule() : "", dataStyle);
                createCell(row, 4, log.getDescription() != null ? log.getDescription() : "", dataStyle);
                createCell(row, 5, log.getRequestUrl() != null ? log.getRequestUrl() : "", dataStyle);
                createCell(row, 6, log.getResponseCode() != null ? log.getResponseCode().toString() : "", dataStyle);
                createCell(row, 7, log.getExecutionTime() != null ? log.getExecutionTime().toString() : "", dataStyle);
                createCell(row, 8, log.getIpAddress() != null ? log.getIpAddress() : "", dataStyle);
                createCell(row, 9, formatDate(log.getCreateTime()), dataStyle);
            }

            // 写入字节数组
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);
            return outputStream.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("导出Excel失败", e);
        }
    }

    /**
     * 创建表头样式
     */
    private static CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 11);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    /**
     * 创建数据样式
     */
    private static CellStyle createDataStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setWrapText(true);
        return style;
    }

    /**
     * 创建单元格并设置值
     */
    private static void createCell(Row row, int column, String value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value != null ? value : "");
        cell.setCellStyle(style);
    }

    /**
     * 格式化日期
     */
    private static String formatDate(LocalDateTime dateTime) {
        if (dateTime == null) {
            return "";
        }
        return dateTime.format(DATE_FORMATTER);
    }
}
