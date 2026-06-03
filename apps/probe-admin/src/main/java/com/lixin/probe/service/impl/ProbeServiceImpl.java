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
 * 探针管理核心服务实现类，处理探针的增删改查、探针控制指令下发、级联清理关联数据。
 * <p>
 * 主要职责包括：
 * <ul>
 *     <li>探针的分页查询、条件筛选查询</li>
 *     <li>探针的创建（含 probeKey 自动生成、IP 唯一性校验）</li>
 *     <li>探针的更新和删除（含级联清理关联业务表数据）</li>
 *     <li>探针的批量创建、导出（Excel / JSON）</li>
 *     <li>探针心跳委托、按 IP 查询系统探针</li>
 *     <li>可用数据库类型的元信息查询</li>
 * </ul>
 * <p>
 * 事务说明：create、update、delete、batchCreate 方法标注了 {@link Transactional}，
 * 其中 delete 方法会先通过 {@link ProbeControlService} 停止在线探针，
 * 再级联删除 change_log、data_snapshot 等多张关联表数据，最后删除探针主表记录。
 *
 * @author Claude Code
 * @date 2026-03-11
 * @version 2.0 (重构版)
 */
@Service
public class ProbeServiceImpl implements ProbeService {

    /** 类级别日志记录器 */
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ProbeServiceImpl.class);

    /** 探针数据访问层，基于 MyBatis-Plus Mapper */
    @Autowired
    private ProbeMapper probeMapper;

    /** 探针监控服务，负责心跳更新与探针状态检测 */
    @Autowired
    private ProbeMonitorService probeMonitorService;

    /**
     * 探针控制服务，负责向探针下发启停等控制指令。
     * <p>使用 @Lazy 延迟加载以避免与 ProbeControlService 之间的循环依赖问题。</p>
     */
    @Lazy
    @Autowired
    private ProbeControlService probeControlService;

    /** Spring JDBC 模板，用于执行级联删除等原生 SQL 操作 */
    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 无条件分页查询探针列表。
     * <p>返回按默认排序规则排列的全量探针分页结果。</p>
     *
     * @param pageNum  当前页码（从 1 开始）
     * @param pageSize 每页记录数
     * @return MyBatis-Plus 分页对象，包含当前页的探针记录列表及总条数
     */
    @Override
    public Page<Probe> getPage(int pageNum, int pageSize) {
        Page<Probe> page = new Page<>(pageNum, pageSize);
        return probeMapper.selectPage(page, null);
    }

    /**
     * 带条件的分页查询探针列表。
     * <p>支持按名称模糊查询、按状态和类型精确筛选，结果按创建时间倒序排列。</p>
     *
     * @param pageNum  当前页码（从 1 开始）
     * @param pageSize 每页记录数
     * @param name     探针名称（模糊匹配），可为 null 或空字符串表示不筛选
     * @param status   探针状态（精确匹配），如 "online"、"offline"，可为 null 表示不筛选
     * @param type     探针类型（精确匹配），如 "SYSTEM"、"DATABASE"，可为 null 表示不筛选
     * @return MyBatis-Plus 分页对象，包含符合条件的探针记录
     */
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

    /**
     * 根据主键 ID 查询单个探针。
     *
     * @param id 探针主键 ID
     * @return 探针对象，若不存在则返回 null
     */
    @Override
    public Probe getById(Long id) {
        return probeMapper.selectById(id);
    }

    /**
     * 根据探针唯一标识（probeKey）查询单个探针。
     * <p>probeKey 是探针在系统中的唯一业务标识，用于心跳上报、数据关联等场景。</p>
     *
     * @param probeKey 探针唯一标识
     * @return 探针对象，若不存在则返回 null
     */
    @Override
    public Probe getByProbeKey(String probeKey) {
        LambdaQueryWrapper<Probe> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Probe::getProbeKey, probeKey);
        return probeMapper.selectOne(wrapper);
    }

    /**
     * 创建单个探针。
     * <p>
     * 处理流程：
     * <ol>
     *     <li>若 probeKey 为空则自动生成（格式：AGENT-{类型}-{时间戳}-{随机串}）</li>
     *     <li>若 hostIp 为空则默认设置为 127.0.0.1</li>
     *     <li>校验 probeKey 全局唯一性，重复则抛出 {@link ProbeAlreadyExistsException}</li>
     *     <li>若类型为 SYSTEM，额外校验同一 IP 只能存在一个系统探针</li>
     *     <li>设置创建时间与初始状态（OFFLINE），执行数据库插入</li>
     * </ol>
     * </p>
     *
     * @param probe 待创建的探针对象（ID 由数据库自增生成）
     * @throws ProbeAlreadyExistsException 当 probeKey 已存在或 SYSTEM 类型探针的 IP 已被占用时抛出
     * @throws RuntimeException            当数据库插入操作失败时抛出
     */
    @Override
    @Transactional
    public void create(Probe probe) {
        log.info("[ProbeService] 开始创建探针 - name={}, type={}, probeKey={}, hostIp={}, port={}",
                probe.getName(), probe.getType(), probe.getProbeKey(), probe.getHostIp(), probe.getPort());

        // ---- probeKey 处理：如果为空，自动生成 ----
        if (probe.getProbeKey() == null || probe.getProbeKey().trim().isEmpty()) {
            // 生成格式为 AGENT-{类型后缀}-{时间戳后6位}-{3位随机串}
            String generatedKey = generateProbeKey(probe.getType());
            probe.setProbeKey(generatedKey);
            log.info("[ProbeService] probeKey为空，自动生成: {}", generatedKey);
        }

        // ---- hostIp 处理：如果为空，使用默认值 127.0.0.1 ----
        if (probe.getHostIp() == null || probe.getHostIp().trim().isEmpty()) {
            probe.setHostIp("127.0.0.1");
            log.info("[ProbeService] hostIp为空，使用默认值: 127.0.0.1");
        }

        // ---- 唯一性校验：检查 probeKey 是否已被其他探针占用 ----
        Probe existingProbe = getByProbeKey(probe.getProbeKey());
        if (existingProbe != null) {
            log.error("[ProbeService] 创建失败：probeKey已存在 - probeKey={}", probe.getProbeKey());
            throw new ProbeAlreadyExistsException(probe.getProbeKey());
        }

        // ---- SYSTEM 类型探针的 IP 唯一性校验 ----
        // 每个 hostIp 只允许创建一个 SYSTEM 类型的探针，避免同一主机重复注册系统探针
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

        // ---- 设置初始字段值 ----
        probe.setCreateTime(LocalDateTime.now());
        // 新创建的探针默认状态为 OFFLINE（离线），待探针启动并上报心跳后变为 ONLINE
        probe.setStatus(ProbeStatus.OFFLINE.getCode());

        // ---- 执行数据库插入 ----
        log.info("[ProbeService] 准备插入数据库 - probe对象: {}", probe);
        int result = probeMapper.insert(probe);
        log.info("[ProbeService] 创建探针完成 - id={}, name={}, type={}, probeKey={}, 影响行数: {}",
                probe.getId(), probe.getName(), probe.getType(), probe.getProbeKey(), result);

        if (result <= 0) {
            log.error("[ProbeService] 创建失败：插入操作影响行数为0 - probeKey={}", probe.getProbeKey());
            throw new RuntimeException("创建探针失败：数据库插入操作未成功");
        }
    }

    /**
     * 更新探针信息。
     * <p>根据探针 ID 更新指定字段，自动设置更新时间为当前时间。</p>
     *
     * @param probe 包含待更新字段的探针对象（ID 不能为空）
     */
    @Override
    @Transactional
    public void update(Probe probe) {
        log.info("[ProbeService] 开始更新探针 - id={}, name={}, type={}, probeKey={}, hostIp={}, port={}",
                probe.getId(), probe.getName(), probe.getType(), probe.getProbeKey(), probe.getHostIp(), probe.getPort());
        probe.setUpdateTime(LocalDateTime.now());
        int affected = probeMapper.updateById(probe);
        log.info("[ProbeService] 更新探针完成 - id={}, type={}, 影响行数: {}", probe.getId(), probe.getType(), affected);
    }

    /**
     * 删除探针及其全部关联数据。
     * <p>
     * 完整处理流程：
     * <ol>
     *     <li>查询探针信息，获取 probeKey 和当前状态</li>
     *     <li>若探针处于 online 或 error 状态，先通过控制服务下发 STOP 指令停止探针</li>
     *     <li>级联清理关联业务表数据（change_log、data_snapshot、database_performance 等 8 张表）</li>
     *     <li>清理同步任务表（sync_task）中源探针或目标探针为当前探针的记录</li>
     *     <li>删除探针主表记录（基于乐观锁，若版本冲突则抛出异常）</li>
     * </ol>
     * </p>
     *
     * @param id 待删除探针的主键 ID
     * @throws IllegalArgumentException 当探针不存在时抛出
     * @throws RuntimeException         当数据库删除失败（乐观锁版本冲突）时抛出
     */
    @Override
    @Transactional
    public void delete(Long id) {
        // ---- 步骤1：查询探针信息，获取 probeKey（用于级联删除关联数据）和当前状态 ----
        Probe probe = probeMapper.selectById(id);
        if (probe == null) {
            throw new IllegalArgumentException("探针不存在");
        }

        String probeKey = probe.getProbeKey();
        String status = probe.getStatus();

        // ---- 步骤2：若探针在线或异常，先通过控制服务下发 STOP 指令 ----
        // 注意：停止探针操作在事务内执行，但网络通信可能导致事务超时，
        // 若停止失败仅记录警告日志，不阻止后续删除流程
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

        // ---- 步骤3：级联清理关联业务表数据 ----
        // 以下 8 张表通过 probe_key 字段与探针关联，需逐一清理以避免孤立数据
        String[] cleanupTables = {
            "change_log",           // 变更日志表
            "data_snapshot",        // 数据快照表
            "database_performance", // 数据库性能指标表
            "table_info",           // 表元信息表
            "column_info",          // 列元信息表
            "database_metadata",    // 数据库元数据表
            "sync_log",             // 同步日志表
            "file_metadata"         // 文件元数据表
        };
        for (String table : cleanupTables) {
            int deleted = jdbcTemplate.update(
                "DELETE FROM " + table + " WHERE probe_key = ?", probeKey);
            if (deleted > 0) {
                log.info("清理关联数据: table={}, probeKey={}, deleted={}", table, probeKey, deleted);
            }
        }

        // ---- 步骤4：删除同步任务（sync_task）中与当前探针相关的记录 ----
        // 同步任务通过 source_probe_key 或 target_probe_key 关联探针
        jdbcTemplate.update("DELETE FROM sync_task WHERE source_probe_key = ? OR target_probe_key = ?", probeKey, probeKey);

        // ---- 步骤5：删除探针主表记录 ----
        // MyBatis-Plus 的乐观锁机制会自动检查 version 字段，
        // 若版本号不匹配（说明在删除过程中被其他线程修改过），affected 为 0
        int affected = probeMapper.deleteById(id);

        if (affected == 0) {
            throw new RuntimeException("删除探针失败：探针可能已被其他用户删除（版本冲突）");
        }

        log.info("删除探针成功: id={}, probeKey={}, affected_rows={}", id, probeKey, affected);
    }

    /**
     * 更新探针心跳时间。
     * <p>委托给 {@link ProbeMonitorService} 处理，用于维持探针的在线状态。</p>
     *
     * @param probeKey 探针唯一标识
     */
    @Override
    public void updateHeartbeat(String probeKey) {
        // 委托给ProbeMonitorService处理
        probeMonitorService.updateHeartbeat(probeKey);
    }

    /**
     * 批量创建探针。
     * <p>
     * 逐个处理列表中的探针对象，对每条记录检查 probeKey 唯一性：
     * <ul>
     *     <li>若 probeKey 已存在，跳过该条记录并将已有探针对象加入结果列表</li>
     *     <li>若 probeKey 不存在，设置创建时间和初始状态后插入数据库</li>
     *     <li>单条记录创建失败不影响其他记录的处理（容错机制）</li>
     * </ul>
     * </p>
     *
     * @param probes 待创建的探针对象列表
     * @return 成功创建或已存在的探针对象列表
     */
    @Override
    @Transactional
    public List<Probe> batchCreate(List<Probe> probes) {
        List<Probe> createdProbes = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        for (Probe probe : probes) {
            try {
                // 检查probeKey是否已存在，已存在则跳过创建但将已有探针加入结果列表
                Probe existingProbe = getByProbeKey(probe.getProbeKey());
                if (existingProbe != null) {
                    log.warn("探针已存在，跳过: probeKey={}", probe.getProbeKey());
                    createdProbes.add(existingProbe);
                    continue;
                }

                // 设置创建时间和初始状态（离线）
                probe.setCreateTime(now);
                probe.setStatus(ProbeStatus.OFFLINE.getCode());

                // 插入探针到数据库
                probeMapper.insert(probe);
                createdProbes.add(probe);
                log.info("批量创建探针成功: probeKey={}", probe.getProbeKey());

            } catch (Exception e) {
                log.error("创建探针失败: probeKey={}", probe.getProbeKey(), e);
                // 继续处理下一个，保证批量操作的容错性
            }
        }

        return createdProbes;
    }

    /**
     * 将探针列表导出为 Excel 文件（字节数组）。
     * <p>按条件查询探针数据，最多导出 10000 条记录，通过 {@link ExcelExportUtil} 生成 Excel 字节流。</p>
     *
     * @param name   探针名称筛选条件（模糊匹配），可为 null
     * @param status 探针状态筛选条件，可为 null
     * @param type   探针类型筛选条件，可为 null
     * @return Excel 文件的字节数组
     */
    @Override
    public byte[] exportProbesToExcel(String name, String status, String type) {
        log.info("导出探针列表到Excel: name={}, status={}, type={}", name, status, type);

        // 获取所有符合条件的数据（使用最大页面容量10000）
        Page<Probe> page = getPage(1, 10000, name, status, type);
        List<Probe> probes = page.getRecords();

        if (probes == null) {
            log.warn("获取探针列表返回null，使用空列表");
            probes = new ArrayList<>();
        }

        // 调用工具类生成Excel字节数组
        byte[] excelBytes = ExcelExportUtil.exportProbes(probes);
        log.info("导出探针列表成功，共{}条记录", probes.size());

        return excelBytes;
    }

    /**
     * 将探针列表导出为 JSON 字符串。
     * <p>按条件查询探针数据，最多导出 10000 条记录，返回格式为 {"probes": [...]} 的 JSON 字符串。</p>
     *
     * @param name   探针名称筛选条件（模糊匹配），可为 null
     * @param status 探针状态筛选条件，可为 null
     * @param type   探针类型筛选条件，可为 null
     * @return JSON 格式的探针列表字符串
     */
    @Override
    public String exportProbesToJson(String name, String status, String type) {
        log.info("导出探针列表到JSON: name={}, status={}, type={}", name, status, type);

        // 获取所有符合条件的数据
        Page<Probe> page = getPage(1, 10000, name, status, type);
        List<Probe> probes = page.getRecords();

        if (probes == null) {
            log.warn("获取探针列表返回null，使用空列表");
            probes = new ArrayList<>();
        }

        // 构建 {"probes": [...]} 格式的 JSON 字符串
        Map<String, Object> result = new HashMap<>();
        result.put("probes", probes);
        String jsonStr = JSON.toJSONString(result);

        log.info("导出探针JSON成功，共{}条记录", probes.size());
        return jsonStr;
    }

    /**
     * 查询所有探针列表（不分页）。
     *
     * @return 全部探针对象列表
     */
    @Override
    public List<Probe> list() {
        log.debug("[ProbeService] 查询所有探针列表");
        return probeMapper.selectList(null);
    }

    /**
     * 根据主键 ID 列表批量查询探针。
     * <p>使用 MyBatis-Plus 的 selectBatchIds 方法，底层通过 WHERE id IN (...) 实现。</p>
     *
     * @param ids 探针主键 ID 列表，若为 null 或空则返回空列表
     * @return 查询到的探针对象列表
     */
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

    /**
     * 获取系统中所有探针的 probeKey 列表。
     * <p>过滤掉 probeKey 为 null 或空字符串的记录。</p>
     *
     * @return 探针唯一标识列表
     */
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
     * 从探针 Key 中提取 Agent 代码（即类型后缀之前的部分）。
     * <p>
     * 支持的 probeKey 格式及提取规则：
     * <ul>
     *     <li>AGENT-database → AGENT（包含 "-database" 后缀）</li>
     *     <li>AGENT-database-test → AGENT</li>
     *     <li>AGENT-file → AGENT（包含 "-file" 后缀）</li>
     *     <li>AGENT-file-test → AGENT</li>
     *     <li>AGENT-system → AGENT（包含 "-system" 后缀）</li>
     *     <li>AGENT-system-test → AGENT</li>
     * </ul>
     * </p>
     *
     * @param probeKey 探针唯一标识
     * @return Agent 代码（类型后缀之前的部分），无法提取时返回 null
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
     * 自动生成探针 Key。
     * <p>
     * 生成算法：
     * <ol>
     *     <li>获取当前毫秒时间戳，转换为 36 进制字符串，取后 6 位作为时间戳标识</li>
     *     <li>获取当前纳秒时间，转换为 36 进制字符串，取前 3 位作为随机标识</li>
     *     <li>根据探针类型确定后缀（如 database、file、system）</li>
     *     <li>拼接格式：AGENT-{类型后缀}-{时间戳后6位}-{3位随机串}</li>
     * </ol>
     * 示例：AGENT-database-m5x7ab-9k3
     * </p>
     *
     * @param probeType 探针类型，如 "DATABASE"、"FILE"、"SYSTEM"
     * @return 自动生成的探针唯一标识
     */
    private String generateProbeKey(String probeType) {
        // 获取当前时间戳（36进制），利用36进制压缩时间戳长度
        String timestamp = Long.toString(System.currentTimeMillis(), 36);
        // 取后6位，兼顾唯一性和简洁性
        timestamp = timestamp.substring(Math.max(0, timestamp.length() - 6));

        // 生成3位随机字符串，基于纳秒时间的36进制转换
        String random = Long.toString(System.nanoTime(), 36).substring(0, 3);

        // 根据探针类型确定后缀，若类型为空则使用 "probe" 作为默认后缀
        String suffix = probeType != null ? probeType.toLowerCase() : "probe";

        // 最终格式：AGENT-{类型后缀}-{时间戳后6位}-{3位随机串}
        return "AGENT-" + suffix + "-" + timestamp + "-" + random;
    }

    /**
     * 获取系统支持的可用数据库类型列表。
     * <p>
     * 当前返回硬编码的数据库类型元信息，未来计划通过扫描插件目录动态加载。
     * 支持的数据库类型：MySQL、PostgreSQL、Oracle、SQL Server、达梦（DM）、SQLite。
     * </p>
     *
     * @return 数据库类型信息列表，包含类型编码、显示名称、默认端口、支持版本和描述
     */
    @Override
    public List<DatabaseTypeInfo> getAvailableDatabaseTypes() {
        log.debug("[ProbeService] 获取可用的数据库类型列表");

        // TODO: 未来可以通过扫描插件目录动态加载插件信息
        // 当前实现：返回已知的数据库插件类型列表
        List<DatabaseTypeInfo> databaseTypes = new ArrayList<>();

        // MySQL 开源关系型数据库
        databaseTypes.add(new DatabaseTypeInfo(
                "mysql",
                "MySQL",
                3306,
                "5.7,8.0",
                "MySQL开源关系型数据库"
        ));

        // PostgreSQL 开源对象关系型数据库
        databaseTypes.add(new DatabaseTypeInfo(
                "postgresql",
                "PostgreSQL",
                5432,
                "12-15",
                "PostgreSQL开源对象关系型数据库"
        ));

        // Oracle 企业级关系型数据库
        databaseTypes.add(new DatabaseTypeInfo(
                "oracle",
                "Oracle",
                1521,
                "23.7.0",
                "Oracle企业级关系型数据库"
        ));

        // Microsoft SQL Server 数据库
        databaseTypes.add(new DatabaseTypeInfo(
                "sqlserver",
                "SQL Server",
                1433,
                "12.8.1",
                "Microsoft SQL Server数据库"
        ));

        // 达梦数据库（国产数据库）
        databaseTypes.add(new DatabaseTypeInfo(
                "dm",
                "DM数据库",
                5236,
                "8.1.3",
                "达梦数据库"
        ));

        // SQLite 轻量级嵌入式数据库（无默认端口）
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

    /**
     * 根据主机 IP 查询该 IP 上的系统探针（SYSTEM 类型）。
     * <p>用于校验同一 IP 是否已存在系统探针，或根据 IP 获取系统探针信息。</p>
     *
     * @param hostIp 主机 IP 地址
     * @return 系统探针对象，若不存在或 hostIp 为空则返回 null
     */
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

    /**
     * 根据主机 IP 查询系统探针，排除指定的 probeKey。
     * <p>
     * 主要用于更新场景：当更新 SYSTEM 类型探针的 IP 时，
     * 需要排除自身记录，检查目标 IP 是否已被其他系统探针占用。
     * </p>
     *
     * @param hostIp          主机 IP 地址
     * @param excludeProbeKey 需要排除的探针 Key（通常为当前正在更新的探针自身）
     * @return 符合条件的系统探针对象，若不存在则返回 null
     */
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

    /**
     * 获取所有在线状态的探针列表。
     * <p>查询 status = "online" 的探针，用于监控面板展示或批量操作。</p>
     *
     * @return 在线探针对象列表
     */
    @Override
    public List<Probe> getOnlineProbes() {
        LambdaQueryWrapper<Probe> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Probe::getStatus, "online");
        return probeMapper.selectList(wrapper);
    }
}
