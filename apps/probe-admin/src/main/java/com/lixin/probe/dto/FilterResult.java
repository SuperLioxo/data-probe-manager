package com.lixin.probe.dto;

import lombok.Data;

import java.util.*;

/**
 * 数据过滤结果
 */
@Data
public class FilterResult {
    private List<Map<String, Object>> passedRows = new ArrayList<>();
    private List<BadRecord> failedRows = new ArrayList<>();
    private int totalRows;
    private int passedCount;
    private int failedCount;

    public void addPassed(Map<String, Object> row) {
        passedRows.add(row);
        passedCount++;
        totalRows++;
    }

    public void addFailed(Map<String, Object> row, String ruleName, String reason) {
        BadRecord bad = new BadRecord();
        bad.setRowData(row);
        bad.setViolatedRules(List.of(ruleName));
        bad.setRejectionReason(reason);
        failedRows.add(bad);
        failedCount++;
        totalRows++;
    }
}
