package com.lixin.probe.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 不合格数据记录
 */
@Data
public class BadRecord {
    private Map<String, Object> rowData;
    private List<String> violatedRules;
    private String rejectionReason;
}
