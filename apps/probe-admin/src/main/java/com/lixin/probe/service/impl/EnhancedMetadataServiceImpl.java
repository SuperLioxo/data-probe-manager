package com.lixin.probe.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lixin.probe.entity.ConstraintInfo;
import com.lixin.probe.entity.ForeignKeyInfo;
import com.lixin.probe.entity.IndexInfo;
import com.lixin.probe.mapper.ConstraintInfoMapper;
import com.lixin.probe.mapper.ForeignKeyInfoMapper;
import com.lixin.probe.mapper.IndexInfoMapper;
import com.lixin.probe.service.EnhancedMetadataService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
public class EnhancedMetadataServiceImpl implements EnhancedMetadataService {

    @Autowired
    private IndexInfoMapper indexInfoMapper;

    @Autowired
    private ForeignKeyInfoMapper foreignKeyInfoMapper;

    @Autowired
    private ConstraintInfoMapper constraintInfoMapper;

    @Override
    public List<IndexInfo> getTableIndexes(String probeKey, String databaseName, String tableName) {
        LambdaQueryWrapper<IndexInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(IndexInfo::getProbeKey, probeKey)
               .eq(IndexInfo::getTableName, tableName);
        if (databaseName != null) wrapper.eq(IndexInfo::getDatabaseName, databaseName);
        wrapper.orderByAsc(IndexInfo::getIndexName);
        return indexInfoMapper.selectList(wrapper);
    }

    @Override
    public List<ForeignKeyInfo> getTableForeignKeys(String probeKey, String databaseName, String tableName) {
        LambdaQueryWrapper<ForeignKeyInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ForeignKeyInfo::getProbeKey, probeKey)
               .eq(ForeignKeyInfo::getTableName, tableName);
        if (databaseName != null) wrapper.eq(ForeignKeyInfo::getDatabaseName, databaseName);
        return foreignKeyInfoMapper.selectList(wrapper);
    }

    @Override
    public List<ConstraintInfo> getTableConstraints(String probeKey, String databaseName, String tableName) {
        LambdaQueryWrapper<ConstraintInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ConstraintInfo::getProbeKey, probeKey)
               .eq(ConstraintInfo::getTableName, tableName);
        if (databaseName != null) wrapper.eq(ConstraintInfo::getDatabaseName, databaseName);
        return constraintInfoMapper.selectList(wrapper);
    }

    @Override
    public Map<String, Object> getEnhancedMetadata(String probeKey, String databaseName, String tableName) {
        Map<String, Object> result = new LinkedHashMap<>();

        List<IndexInfo> indexes = getTableIndexes(probeKey, databaseName, tableName);
        List<ForeignKeyInfo> fks = getTableForeignKeys(probeKey, databaseName, tableName);
        List<ConstraintInfo> constraints = getTableConstraints(probeKey, databaseName, tableName);

        result.put("indexes", indexes);
        result.put("foreignKeys", fks);
        result.put("constraints", constraints);
        result.put("indexCount", indexes.size());
        result.put("foreignKeyCount", fks.size());
        result.put("constraintCount", constraints.size());

        // 统计索引类型
        Map<String, Long> indexTypes = new LinkedHashMap<>();
        for (IndexInfo idx : indexes) {
            String type = idx.getIndexType() != null ? idx.getIndexType() : "UNKNOWN";
            indexTypes.merge(type, 1L, Long::sum);
        }
        result.put("indexTypes", indexTypes);

        return result;
    }

    @Override
    @Transactional
    public void saveIndexes(String probeKey, String databaseName, String tableName, List<IndexInfo> indexes) {
        deleteByTable(probeKey, databaseName, tableName);
        for (IndexInfo idx : indexes) {
            idx.setProbeKey(probeKey);
            idx.setDatabaseName(databaseName);
            idx.setTableName(tableName);
            idx.setCreateTime(LocalDateTime.now());
            indexInfoMapper.insert(idx);
        }
    }

    @Override
    @Transactional
    public void saveForeignKeys(String probeKey, String databaseName, String tableName, List<ForeignKeyInfo> fks) {
        for (ForeignKeyInfo fk : fks) {
            fk.setProbeKey(probeKey);
            fk.setDatabaseName(databaseName);
            fk.setTableName(tableName);
            fk.setCreateTime(LocalDateTime.now());
            foreignKeyInfoMapper.insert(fk);
        }
    }

    @Override
    @Transactional
    public void saveConstraints(String probeKey, String databaseName, String tableName, List<ConstraintInfo> constraints) {
        for (ConstraintInfo c : constraints) {
            c.setProbeKey(probeKey);
            c.setDatabaseName(databaseName);
            c.setTableName(tableName);
            c.setCreateTime(LocalDateTime.now());
            constraintInfoMapper.insert(c);
        }
    }

    @Override
    @Transactional
    public void deleteByTable(String probeKey, String databaseName, String tableName) {
        LambdaQueryWrapper<IndexInfo> idxWrapper = new LambdaQueryWrapper<>();
        idxWrapper.eq(IndexInfo::getProbeKey, probeKey).eq(IndexInfo::getTableName, tableName);
        if (databaseName != null) idxWrapper.eq(IndexInfo::getDatabaseName, databaseName);
        indexInfoMapper.delete(idxWrapper);

        LambdaQueryWrapper<ForeignKeyInfo> fkWrapper = new LambdaQueryWrapper<>();
        fkWrapper.eq(ForeignKeyInfo::getProbeKey, probeKey).eq(ForeignKeyInfo::getTableName, tableName);
        if (databaseName != null) fkWrapper.eq(ForeignKeyInfo::getDatabaseName, databaseName);
        foreignKeyInfoMapper.delete(fkWrapper);

        LambdaQueryWrapper<ConstraintInfo> cWrapper = new LambdaQueryWrapper<>();
        cWrapper.eq(ConstraintInfo::getProbeKey, probeKey).eq(ConstraintInfo::getTableName, tableName);
        if (databaseName != null) cWrapper.eq(ConstraintInfo::getDatabaseName, databaseName);
        constraintInfoMapper.delete(cWrapper);
    }
}
