package com.lixin.probe.service.impl;

import com.lixin.probe.entity.QualityReport;
import com.lixin.probe.entity.QualityRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = Strictness.LENIENT)
class QualityRuleServiceImplTest {

    @Mock
    private com.lixin.probe.mapper.QualityRuleMapper ruleMapper;

    @Mock
    private com.lixin.probe.mapper.QualityReportMapper reportMapper;

    @InjectMocks
    private QualityRuleServiceImpl qualityRuleService;

    @Test
    @DisplayName("NOT_NULL规则：空值应触发违规")
    void testNotNullRule_nullValue_shouldViolate() {
        QualityRule notNullRule = QualityRule.builder().id(1L).ruleType("NOT_NULL")
                .tableName("users").columnName("email").build();

        Map<String, Object> row = new HashMap<>();
        row.put("id", 1);
        row.put("email", null);

        List<QualityReport> results = qualityRuleService.checkRuleWithSampleData(notNullRule, List.of(row));
        assertFalse(results.isEmpty(), "Null value should violate NOT_NULL rule");
    }

    @Test
    @DisplayName("REGEX规则：无效邮箱应触发违规")
    void testRegexRule_invalidEmail_shouldViolate() {
        QualityRule emailRule = QualityRule.builder().id(2L).ruleType("REGEX")
                .ruleParams("{\"pattern\":\"^[a-z]+@[a-z]+\\.[a-z]{2,}$\"}")
                .tableName("users").columnName("email").build();

        List<QualityReport> results = qualityRuleService.checkRuleWithSampleData(
                emailRule, List.of(
                        new HashMap<>(Map.of("id", 1, "email", "invalid-email")),
                        new HashMap<>(Map.of("id", 2, "email", "test@domain.com"))
                ));
        assertEquals(1, results.size(), "One email should violate");
    }

    @Test
    @DisplayName("RANGE规则：超出范围应触发违规")
    void testRangeRule_outOfRange_shouldViolate() {
        QualityRule rangeRule = QualityRule.builder().id(3L).ruleType("RANGE")
                .ruleParams("{\"min\":\"0\",\"max\":\"100\"}").tableName("orders").columnName("amount").build();

        List<QualityReport> results = qualityRuleService.checkRuleWithSampleData(
                rangeRule, List.of(
                        Map.of("id", 1, "amount", 50),
                        Map.of("id", 2, "amount", -5),
                        Map.of("id", 3, "amount", 150)
                ));
        assertEquals(2, results.size(), "Two values out of range");
    }

    @Test
    @DisplayName("LENGTH规则：长度超限应触发违规")
    void testLengthRule_tooLong_shouldViolate() {
        QualityRule lengthRule = QualityRule.builder().id(4L).ruleType("LENGTH")
                .ruleParams("{\"min\":\"1\",\"max\":\"10\"}").tableName("users").columnName("username").build();

        List<QualityReport> results = qualityRuleService.checkRuleWithSampleData(
                lengthRule, List.of(
                        Map.of("id", 1, "username", "bob"),
                        Map.of("id", 2, "username", "this-is-way-too-long")
                ));
        assertEquals(1, results.size(), "One value exceeds max length");
    }

    @Test
    @DisplayName("ENUM规则：不在枚举内应触发违规")
    void testEnumRule_invalidValue_shouldViolate() {
        QualityRule enumRule = QualityRule.builder().id(5L).ruleType("ENUM")
                .ruleParams("{\"values\":\"ACTIVE,INACTIVE,PENDING\"}").tableName("users").columnName("status").build();

        List<QualityReport> results = qualityRuleService.checkRuleWithSampleData(
                enumRule, List.of(
                        Map.of("id", 1, "status", "ACTIVE"),
                        Map.of("id", 2, "status", "DELETED")
                ));
        assertEquals(1, results.size(), "DELETED is not in enum");
    }

    @Test
    @DisplayName("TYPE_CHECK规则：非数字应触发违规")
    void testTypeCheckRule_nonNumeric_shouldViolate() {
        QualityRule typeRule = QualityRule.builder().id(6L).ruleType("TYPE_CHECK")
                .ruleParams("{\"expected\":\"numeric\"}").tableName("products").columnName("price").build();

        List<QualityReport> results = qualityRuleService.checkRuleWithSampleData(
                typeRule, List.of(
                        Map.of("id", 1, "price", "29.99"),
                        Map.of("id", 2, "price", "free")
                ));
        assertEquals(1, results.size(), "'free' is not numeric");
    }

    @Test
    @DisplayName("空数据应返回空列表")
    void testCheckRuleWithSampleData_emptyData_shouldReturnEmpty() {
        QualityRule rule = QualityRule.builder().id(1L).ruleType("NOT_NULL")
                .tableName("users").columnName("email").build();

        List<QualityReport> results = qualityRuleService.checkRuleWithSampleData(rule, Collections.emptyList());
        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("generateFixSql: NULLIFY")
    void testGenerateFixSql_nullify() {
        QualityRule rule = QualityRule.builder()
                .tableName("users").columnName("phone").autoFix(true).fixAction("NULLIFY").build();
        assertEquals("UPDATE `users` SET `phone` = NULL WHERE `phone` IS NOT NULL",
                qualityRuleService.generateFixSql(rule));
    }

    @Test
    @DisplayName("generateFixSql: TRIM")
    void testGenerateFixSql_trim() {
        QualityRule rule = QualityRule.builder()
                .tableName("users").columnName("name").autoFix(true).fixAction("TRIM").build();
        assertEquals("UPDATE `users` SET `name` = TRIM(`name`) WHERE `name` LIKE '% %'",
                qualityRuleService.generateFixSql(rule));
    }

    @Test
    @DisplayName("generateFixSql: SET_DEFAULT")
    void testGenerateFixSql_setDefault() {
        QualityRule rule = QualityRule.builder()
                .tableName("users").columnName("status").autoFix(true).fixAction("SET_DEFAULT")
                .fixParams("{\"defaultValue\":\"PENDING\"}").build();
        assertEquals("UPDATE `users` SET `status` = 'PENDING' WHERE `status` IS NULL",
                qualityRuleService.generateFixSql(rule));
    }

    @Test
    @DisplayName("generateFixSql: 无autoFix时返回null")
    void testGenerateFixSql_noAutoFix_shouldReturnNull() {
        QualityRule rule = QualityRule.builder()
                .tableName("users").columnName("name").autoFix(false).build();
        assertNull(qualityRuleService.generateFixSql(rule));
    }

    @Test
    @DisplayName("统计数据应包含所有维度")
    void testGetQualityStatistics_shouldIncludeAllDimensions() {
        when(ruleMapper.selectCount(any())).thenReturn(10L);

        Map<String, Object> stats = qualityRuleService.getQualityStatistics("probe-1");
        assertNotNull(stats);
        assertTrue(stats.containsKey("totalRules"));
        assertTrue(stats.containsKey("enabledRules"));
        assertTrue(stats.containsKey("totalViolations"));
        assertTrue(stats.containsKey("rulesBySeverity"));
        assertTrue(stats.containsKey("autoFixRules"));
    }
}
