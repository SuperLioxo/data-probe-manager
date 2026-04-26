package com.lixin.probe.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("quality_rule")
public class QualityRule implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String ruleName;

    private String probeKey;

    private String databaseName;

    private String tableName;

    private String columnName;

    /** NOT_NULL, REGEX, RANGE, ENUM, LENGTH, TYPE_CHECK, CUSTOM_SQL */
    private String ruleType;

    /** JSON: 规则参数 */
    private String ruleParams;

    /** ERROR, WARNING, INFO */
    private String severity;

    private Boolean enabled;

    private Boolean autoFix;

    /** SET_DEFAULT, TRIM, UPPERCASE, LOWERCASE, REPLACE, NULLIFY */
    private String fixAction;

    /** JSON: 修复参数 */
    private String fixParams;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
