package com.lixin.probe.controller;

import com.lixin.probe.common.Result;
import com.lixin.probe.entity.ConstraintInfo;
import com.lixin.probe.entity.ForeignKeyInfo;
import com.lixin.probe.entity.IndexInfo;
import com.lixin.probe.service.EnhancedMetadataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/metadata")
public class EnhancedMetadataController {

    @Autowired
    private EnhancedMetadataService enhancedMetadataService;

    @GetMapping("/enhanced")
    public Result<Map<String, Object>> getEnhancedMetadata(
            @RequestParam String probeKey,
            @RequestParam String tableName,
            @RequestParam(required = false) String databaseName) {
        try {
            return Result.success(enhancedMetadataService.getEnhancedMetadata(probeKey, databaseName, tableName));
        } catch (Exception e) {
            return Result.error("查询增强元数据失败: " + e.getMessage());
        }
    }

    @GetMapping("/{probeKey}/indexes")
    public Result<List<IndexInfo>> getIndexes(
            @PathVariable String probeKey,
            @RequestParam String tableName,
            @RequestParam(required = false) String databaseName) {
        try {
            return Result.success(enhancedMetadataService.getTableIndexes(probeKey, databaseName, tableName));
        } catch (Exception e) {
            return Result.error("查询索引失败: " + e.getMessage());
        }
    }

    @GetMapping("/{probeKey}/foreign-keys")
    public Result<List<ForeignKeyInfo>> getForeignKeys(
            @PathVariable String probeKey,
            @RequestParam String tableName,
            @RequestParam(required = false) String databaseName) {
        try {
            return Result.success(enhancedMetadataService.getTableForeignKeys(probeKey, databaseName, tableName));
        } catch (Exception e) {
            return Result.error("查询外键失败: " + e.getMessage());
        }
    }

    @GetMapping("/{probeKey}/constraints")
    public Result<List<ConstraintInfo>> getConstraints(
            @PathVariable String probeKey,
            @RequestParam String tableName,
            @RequestParam(required = false) String databaseName) {
        try {
            return Result.success(enhancedMetadataService.getTableConstraints(probeKey, databaseName, tableName));
        } catch (Exception e) {
            return Result.error("查询约束失败: " + e.getMessage());
        }
    }

    @PostMapping("/{probeKey}/indexes")
    public Result<Void> saveIndexes(
            @PathVariable String probeKey,
            @RequestParam String tableName,
            @RequestParam(required = false) String databaseName,
            @RequestBody List<IndexInfo> indexes) {
        try {
            enhancedMetadataService.saveIndexes(probeKey, databaseName, tableName, indexes);
            return Result.success();
        } catch (Exception e) {
            return Result.error("保存索引失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/{probeKey}/metadata")
    public Result<Void> deleteMetadata(
            @PathVariable String probeKey,
            @RequestParam String tableName,
            @RequestParam(required = false) String databaseName) {
        try {
            enhancedMetadataService.deleteByTable(probeKey, databaseName, tableName);
            return Result.success();
        } catch (Exception e) {
            return Result.error("删除元数据失败: " + e.getMessage());
        }
    }
}
