package com.lixin.probe.service;

import com.lixin.probe.entity.DatabaseConnection;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 数据导入服务
 */
public interface DataImportService {

    /**
     * 从 Excel/CSV 文件导入数据到数据库表
     */
    Map<String, Object> importFromFile(DatabaseConnection connection, MultipartFile file,
                                          String tableName, String schema) throws Exception;
}
