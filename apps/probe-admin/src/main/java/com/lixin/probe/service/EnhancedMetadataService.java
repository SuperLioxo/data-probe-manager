package com.lixin.probe.service;

import com.lixin.probe.entity.ConstraintInfo;
import com.lixin.probe.entity.ForeignKeyInfo;
import com.lixin.probe.entity.IndexInfo;

import java.util.List;
import java.util.Map;

public interface EnhancedMetadataService {

    List<IndexInfo> getTableIndexes(String probeKey, String databaseName, String tableName);

    List<ForeignKeyInfo> getTableForeignKeys(String probeKey, String databaseName, String tableName);

    List<ConstraintInfo> getTableConstraints(String probeKey, String databaseName, String tableName);

    Map<String, Object> getEnhancedMetadata(String probeKey, String databaseName, String tableName);

    void saveIndexes(String probeKey, String databaseName, String tableName, List<IndexInfo> indexes);

    void saveForeignKeys(String probeKey, String databaseName, String tableName, List<ForeignKeyInfo> fks);

    void saveConstraints(String probeKey, String databaseName, String tableName, List<ConstraintInfo> constraints);

    void deleteByTable(String probeKey, String databaseName, String tableName);
}
