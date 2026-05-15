package com.lixin.probe.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lixin.probe.dto.DatabaseTypeInfo;
import com.lixin.probe.dto.ProbeControlResponse;
import com.lixin.probe.entity.Probe;
import com.lixin.probe.enums.ProbeStatus;
import com.lixin.probe.exception.ProbeAlreadyExistsException;
import com.lixin.probe.mapper.ProbeMapper;
import com.lixin.probe.service.ProbeControlService;
import com.lixin.probe.service.ProbeMonitorService;
import com.lixin.probe.service.ProbeService;
import com.lixin.probe.util.ExcelExportUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 探针Service实现
 * 负责探针CRUD操作
 *
 * @author Claude Code
 * @date 2026-03-11
 * @version 2.0 (重构版)
 */
@Service
public class ProbeServiceImpl implements ProbeService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ProbeServiceImpl.class);

    @Autowired
    private ProbeMapper probeMapper;

    @Autowired
    private ProbeMonitorService probeMonitorService;

    @Lazy
    @Autowired
    private ProbeControlService probeControlService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    public Page<Probe> getPage(int pageNum, int pageSize) {
        Page<Probe> page = new Page<>(pageNum, pageSize);
        return probeMapper.selectPage(page, null);
    }

    @Override
    public Page<Probe> getPage(int pageNum, int pageSize, String name, String status, String type) {
        log.info("[ProbeService] 查询探针列表 - pageNum={}, pageSize={}, name={}, status={}, type={}",
                pageNum, pageSize, name, status, type);

        // 构建查询条件
        LambdaQueryWrapper<Probe> queryWrapper = new LambdaQueryWrapper<Probe>()
                .like(name != null && !name.isEmpty(), Probe::getName, name)
                .eq(status != null && !status.isEmpty(), Probe::getStatus, status)
                .eq(type != null && !type.isEmpty(), Probe::getType, type)
                .orderByDesc(Probe::getCreateTime);

        // 创建分页对象并使用MyBatis-Plus分页插件自动查询
        Page<Probe> page = new Page<>(pageNum, pageSize);
        probeMapper.selectPage(page, queryWrapper);

        log.info("[ProbeService] 分页查询完成: total={}, pageRecords.size()={}",
                page.getTotal(), page.getRecords().size());

        return page;
    }

    @Override
    public Probe getById(Long id) {
        return probeMapper.selectById(id);
    }

    @Override
    public Probe getByProbeKey(String probeKey) {
        LambdaQueryWrapper<Probe> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Probe::getProbeKey, probeKey);
        return probeMapper.selectOne(wrapper);
    }

    @Override
    @Transactional
    public void create(Probe probe) {
        log.info("[ProbeService] 开始创建探针 - name={}, type={}, probeKey={}, hostIp={}, port={}",
                probe.getName(), probe.getType(), probe.getProbeKey(), probe.getHostIp(), probe.getPort());

        // 处理 probeKey：如果为空，自动生成
        if (probe.getProbeKey() == null || probe.getProbeKey().trim().isEmpty()) {
            String generatedKey = generateProbeKey(probe.getType());
            probe.setProbeKey(generatedKey);
            log.info("[ProbeService] probeKey为空，自动生成: {}", generatedKey);
        }

        // 处理 hostIp：如果为空，使用默认值 127.0.0.1
        if (probe.getHostIp() == null || probe.getHostIp().trim().isEmpty()) {
            probe.setHostIp("127.0.0.1");
            log.info("[ProbeService] hostIp为空，使用默认值: 127.0.0.1");
        }

        // 检查probeKey是否已存在
        Probe existingProbe = getByProbeKey(probe.getProbeKey());
        if (existingProbe != null) {
            log.error("[ProbeService] 创建失败：probeKey已存在 - probeKey={}", probe.getProbeKey());
            throw new ProbeAlreadyExistsException(probe.getProbeKey());
        }

        // 检查SYSTEM类型探针：每个IP只能有一个系统探针
        if ("SYSTEM".equals(probe.getType())) {
            LambdaQueryWrapper<Probe> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Probe::getType, "SYSTEM")
                   .eq(Probe::getHostIp, probe.getHostIp());

            Probe existingSystemProbe = probeMapper.selectOne(wrapper);
            if (existingSystemProbe != null) {
                log.error("[ProbeService] 创建失败：该IP已存在系统探针 - hostIp={}, existingProbeKey={}, existingProbeName={}",
                         probe.getHostIp(), existingSystemProbe.getProbeKey(), existingSystemProbe.getName());
                throw new ProbeAlreadyExistsException(
                    probe.getHostIp(),
                    existingSystemProbe.getProbeKey(),
                    existingSystemProbe.getName()
                );
            }
            log.info("[ProbeService] 系统探针IP唯一性检查通过 - hostIp={}", probe.getHostIp());
        }

        // probeKey 格式验证已移除，允许任意格式的探针标识
        log.info("[ProbeService] probeKey 格式验证已跳过 - probeKey={}", probe.getProbeKey());

        probe.setCreateTime(LocalDateTime.now());
        probe.setStatus(ProbeStatus.OFFLINE.getCode());

        log.info("[ProbeService] 准备插入数据库 - probe对象: {}", probe);
        int result = probeMapper.insert(probe);
        log.info("[ProbeService] 创建探针完成 - id={}, name={}, type={}, probeKey={}, 影响行数: {}",
                probe.getId(), probe.getName(), probe.getType(), probe.getProbeKey(), result);

        if (result <= 0) {
            log.error("[ProbeService] 创建失败：插入操作影响行数为0 - probeKey={}", probe.getProbeKey());
            throw new RuntimeException("创建探针失败：数据库插入操作未成功");
        }
    }

    @Override
    @Transactional
    public void update(Probe probe) {
        log.info("[ProbeService] 开始更新探针 - id={}, name={}, type={}, probeKey={}, hostIp={}, port={}",
                probe.getId(), probe.getName(), probe.getType(), probe.getProbeKey(), probe.getHostIp(), probe.getPort());
        probe.setUpdateTime(LocalDateTime.now());
        int affected = probeMapper.updateById(probe);
        log.info("[ProbeService] 更新探针完成 - id={}, type={}, 影响行数: {}", probe.getId(), probe.getType(), affected);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        // 1. 先查询探针信息（获取版本号）
        Probe probe = probeMapper.selectById(id);
        if (probe == null) {
            throw new IllegalArgumentException("探针不存在");
        }

        String probeKey = probe.getProbeKey();
        String status = probe.getStatus();

        // 2. 如果探针在线或异常，先停止探针（在事务外执行，避免超时影响事务）
        if ("online".equals(status) || "error".equals(status)) {
            try {
                log.info("删除探针前先停止探针: probeKey={}, currentVersion={}", probeKey, probe.getVersion());
                ProbeControlResponse response = probeControlService.sendControlCommand(
                    probeKey,
                    "STOP",
                    Map.of()
                );
                if (!response.isSuccess()) {
                    log.warn("停止探针失败，但继续删除: probeKey={}, error={}", probeKey, response.getMessage());
                } else {
                    log.info("探针已停止: probeKey={}", probeKey);
                }
            } catch (Exception e) {
                log.warn("停止探针异常，但继续删除: probeKey={}, error={}", probeKey, e.getMessage());
            }
        }

        // 3. 删除关联数据
        String[] cleanupTables = {
            "change_log", "data_snapshot", "database_performance",
            "table_info", "column_info", "database_metadata",
            "sync_log", "file_metadata"
        };
        for (String table : cleanupTables) {
            int deleted = jdbcTemplate.update(
                "DELETE FROM " + table + " WHERE probe_key = ?", probeKey);
            if (deleted > 0) {
                log.info("清理关联数据: table={}, probeKey={}, deleted={}", table, probeKey, deleted);
            }
        }

        // 4. 删除同步任务
        jdbcTemplate.update("DELETE FROM sync_task WHERE source_probe_key = ? OR target_probe_key = ?", probeKey, probeKey);

        // 5. 删除探针记录（乐观锁会自动检查version）
        int affected = probeMapper.deleteById(id);

        if (affected == 0) {
            throw new RuntimeException("删除探针失败：探针可能已被其他用户删除（版本冲突）");
        }

        log.info("删除探针成功: id={}, probeKey={}, affected_rows={}", id, probeKey, affected);
    }

    @Override
    public void updateHeartbeat(String probeKey) {
        // 委托给ProbeMonitorService处理
        probeMonitorService.updateHeartbeat(probeKey);
    }

    @Override
    @Transactional
    public List<Probe> batchCreate(List<Probe> probes) {
        List<Probe> createdProbes = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        for (Probe probe : probes) {
            try {
                // 检查probeKey是否已存在
                Probe existingProbe = getByProbeKey(probe.getProbeKey());
                if (existingProbe != null) {
                    log.warn("探针已存在，跳过: probeKey={}", probe.getProbeKey());
                    createdProbes.add(existingProbe);
                    continue;
                }

                // 设置创建时间和状态
                probe.setCreateTime(now);
                probe.setStatus(ProbeStatus.OFFLINE.getCode());

                // 插入探针
                probeMapper.insert(probe);
                createdProbes.add(probe);
                log.info("批量创建探针成功: probeKey={}", probe.getProbeKey());

            } catch (Exception e) {
                log.error("创建探针失败: probeKey={}", probe.getProbeKey(), e);
                // 继续处理下一个
            }
        }

        return createdProbes;
    }

    @Override
    public byte[] exportProbesToExcel(String name, String status, String type) {
        log.info("导出探针列表到Excel: name={}, status={}, type={}", name, status, type);

        // 获取所有数据（使用大页面）
        Page<Probe> page = getPage(1, 10000, name, status, type);
        List<Probe> probes = page.getRecords();

        if (probes == null) {
            log.warn("获取探针列表返回null，使用空列表");
            probes = new ArrayList<>();
        }

        // 生成Excel
        byte[] excelBytes = ExcelExportUtil.exportProbes(probes);
        log.info("导出探针列表成功，共{}条记录", probes.size());

        return excelBytes;
    }

    @Override
    public String exportProbesToJson(String name, String status, String type) {
        log.info("导出探针列表到JSON: name={}, status={}, type={}", name, status, type);

        // 获取所有数据
        Page<Probe> page = getPage(1, 10000, name, status, type);
        List<Probe> probes = page.getRecords();

        if (probes == null) {
            log.warn("获取探针列表返回null，使用空列表");
            probes = new ArrayList<>();
        }

        // 构建JSON
        Map<String, Object> result = new HashMap<>();
        result.put("probes", probes);
        String jsonStr = JSON.toJSONString(result);

        log.info("导出探针JSON成功，共{}条记录", probes.size());
        return jsonStr;
    }

    @Override
    public List<Probe> list() {
        log.debug("[ProbeService] 查询所有探针列表");
        return probeMapper.selectList(null);
    }

    @Override
    public List<Probe> listByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            log.debug("[ProbeService] 批量查询探针列表 - IDs为空");
            return new ArrayList<>();
        }

        log.debug("[ProbeService] 批量查询探针列表 - ID数量: {}", ids.size());

        // 使用 MyBatis-Plus 的 selectBatchIds 方法进行批量查询
        List<Probe> probes = probeMapper.selectBatchIds(ids);

        log.debug("[ProbeService] 批量查询探针列表完成 - 查询到 {} 个探针", probes.size());

        return probes;
    }

    @Override
    public List<String> getAllProbeKeys() {
        log.debug("[ProbeService] 获取所有探针的probeKey列表");
        List<Probe> probes = probeMapper.selectList(null);
        return probes.stream()
                .map(Probe::getProbeKey)
                .filter(key -> key != null && !key.isEmpty())
                .collect(Collectors.toList());
    }

    /**
     * 从探针key中提取Agent代码
     * 例如：AGENT-database → AGENT
     *       AGENT-database-test → AGENT
     *       AGENT-file → AGENT
     *       AGENT-file-test → AGENT
     *
     * @param probeKey 探针key
     * @return Agent代码，如果无法提取则返回null
     */
    private String extractAgentCodeFromProbeKey(String probeKey) {
        if (probeKey == null || probeKey.isEmpty()) {
            return null;
        }

        // 检查是否为DATABASE类型的探针key（支持 AGENT-XXX-database 或 AGENT-XXX-database-xxx 格式）
        if (probeKey.contains("-database")) {
            // 提取 -database 之前的agent code
            int index = probeKey.indexOf("-database");
            return probeKey.substring(0, index);
        }
        // 检查是否为FILE类型的探针key（支持 AGENT-XXX-file 或 AGENT-XXX-file-xxx 格式）
        else if (probeKey.contains("-file")) {
            // 提取 -file 之前的agent code
            int index = probeKey.indexOf("-file");
            return probeKey.substring(0, index);
        }
        // 检查是否为SYSTEM类型的探针key（支持 AGENT-XXX-system 或 AGENT-XXX-system-xxx 格式）
        else if (probeKey.contains("-system")) {
            // 提取 -system 之前的agent code
            int index = probeKey.indexOf("-system");
            return probeKey.substring(0, index);
        }

        return null;
    }

    /**
     * 自动生成探针Key
     *
     * @param probeType 探针类型
     * @return 生成的探针Key
     */
    private String generateProbeKey(String probeType) {
        // 获取当前时间戳（36进制）
        String timestamp = Long.toString(System.currentTimeMillis(), 36);
        // 取后6位
        timestamp = timestamp.substring(Math.max(0, timestamp.length() - 6));

        // 生成随机字符串（3位）
        String random = Long.toString(System.nanoTime(), 36).substring(0, 3);

        // 根据类型确定后缀
        String suffix = probeType != null ? probeType.toLowerCase() : "probe";

        // 生成格式：AGENT-{类型}-{时间戳}-{随机}
        return "AGENT-" + suffix + "-" + timestamp + "-" + random;
    }

    @Override
    public List<DatabaseTypeInfo> getAvailableDatabaseTypes() {
        log.debug("[ProbeService] 获取可用的数据库类型列表");

        // TODO: 未来可以通过扫描插件目录动态加载插件信息
        // 当前实现：返回已知的数据库插件类型列表
        List<DatabaseTypeInfo> databaseTypes = new ArrayList<>();

        // MySQL
        databaseTypes.add(new DatabaseTypeInfo(
                "mysql",
                "MySQL",
                3306,
                "5.7,8.0",
                "MySQL开源关系型数据库"
        ));

        // PostgreSQL
        databaseTypes.add(new DatabaseTypeInfo(
                "postgresql",
                "PostgreSQL",
                5432,
                "12-15",
                "PostgreSQL开源对象关系型数据库"
        ));

        // Oracle
        databaseTypes.add(new DatabaseTypeInfo(
                "oracle",
                "Oracle",
                1521,
                "23.7.0",
                "Oracle企业级关系型数据库"
        ));

        // SQL Server
        databaseTypes.add(new DatabaseTypeInfo(
                "sqlserver",
                "SQL Server",
                1433,
                "12.8.1",
                "Microsoft SQL Server数据库"
        ));

        // DM (达梦数据库)
        databaseTypes.add(new DatabaseTypeInfo(
                "dm",
                "DM数据库",
                5236,
                "8.1.3",
                "达梦数据库"
        ));

        // SQLite
        databaseTypes.add(new DatabaseTypeInfo(
                "sqlite",
                "SQLite",
                null,
                "3.47.2",
                "SQLite轻量级嵌入式数据库"
        ));

        log.debug("[ProbeService] 返回数据库类型列表，共{}种", databaseTypes.size());
        return databaseTypes;
    }

    @Override
    public Probe getSystemProbeByIp(String hostIp) {
        if (hostIp == null || hostIp.isEmpty()) {
            return null;
        }

        LambdaQueryWrapper<Probe> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Probe::getHostIp, hostIp)
               .eq(Probe::getType, "SYSTEM");

        return probeMapper.selectOne(wrapper);
    }

    @Override
    public Probe getSystemProbeByIpExclude(String hostIp, String excludeProbeKey) {
        if (hostIp == null || hostIp.isEmpty()) {
            return null;
        }

        LambdaQueryWrapper<Probe> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Probe::getHostIp, hostIp)
               .eq(Probe::getType, "SYSTEM")
               .ne(Probe::getProbeKey, excludeProbeKey);

        return probeMapper.selectOne(wrapper);
    }

    @Override
    public List<Probe> getOnlineProbes() {
        LambdaQueryWrapper<Probe> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Probe::getStatus, "online");
        return probeMapper.selectList(wrapper);
    }
}
