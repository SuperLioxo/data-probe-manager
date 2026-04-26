package com.lixin.probe.controller;

import com.lixin.probe.common.Result;
import com.lixin.probe.entity.DatabaseConnection;
import com.lixin.probe.mapper.DatabaseConnectionMapper;
import com.lixin.probe.service.DataImportService;
import com.lixin.probe.util.ControllerHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/database-metadata")
public class DataImportController {

    @Autowired(required = false)
    private DataImportService dataImportService;

    @Autowired(required = false)
    private DatabaseConnectionMapper databaseConnectionMapper;

    /**
     * 上传文件并导入数据到数据库表
     */
    @PostMapping("/{probeKey}/import")
    public Result<Map<String, Object>> importData(
            @PathVariable String probeKey,
            @RequestParam("file") MultipartFile file,
            @RequestParam("tableName") String tableName,
            @RequestParam(value = "schema", required = false) String schema,
            @RequestParam(value = "connectionId", required = false) Long connectionId) {

        return ControllerHelper.safeGet(() -> {
            if (dataImportService == null) {
                throw new RuntimeException("数据导入服务不可用");
            }

            if (file.isEmpty()) {
                throw new IllegalArgumentException("请选择要导入的文件");
            }

            if (tableName == null || tableName.trim().isEmpty()) {
                throw new IllegalArgumentException("请指定目标表名");
            }

            // 获取数据库连接
            DatabaseConnection connection;
            if (connectionId != null) {
                connection = databaseConnectionMapper.findById(connectionId);
            } else {
                // 从活跃连接中取第一个
                List<DatabaseConnection> connections = databaseConnectionMapper.selectList(null);
                connection = connections.stream().findFirst().orElse(null);
            }

            if (connection == null) {
                throw new IllegalArgumentException("未找到可用的数据库连接");
            }

            Map<String, Object> result;
            try {
                result = dataImportService.importFromFile(connection, file, tableName.trim(), schema);
            } catch (Exception e) {
                throw new RuntimeException(e.getMessage(), e);
            }
            result.put("probeKey", probeKey);
            return result;
        }, "数据导入失败");
    }
}
