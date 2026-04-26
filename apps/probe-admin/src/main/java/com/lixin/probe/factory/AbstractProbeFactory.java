package com.lixin.probe.factory;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lixin.probe.dto.ProbeCreateRequest;
import com.lixin.probe.entity.Probe;
import com.lixin.probe.mapper.ProbeMapper;
import com.lixin.probe.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 抽象探针工厂
 * 实现通用的创建逻辑，子类只需实现特定类型的构建
 *
 * @author Claude Code
 * @since 1.0
 * @version 1.0
 */
public abstract class AbstractProbeFactory implements ProbeFactory {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    @Autowired
    protected ProbeMapper probeMapper;

    @Override
    public Probe createProbe(ProbeCreateRequest request) {
        log.info("创建探针: type={}, key={}", getSupportedType(), request.getProbeKey());

        // 1. 验证请求
        validateRequest(request);

        // 2. 检查探针Key是否已存在
        if (isProbeKeyExists(request.getProbeKey())) {
            throw new IllegalArgumentException("探针Key已存在: " + request.getProbeKey());
        }

        // 3. 构建探针实体
        Probe probe = buildProbeEntity(request);

        // 4. 应用默认配置
        applyDefaultConfig(probe);

        // 5. 保存到数据库
        probeMapper.insert(probe);

        log.info("探针创建成功: id={}, key={}", probe.getId(), probe.getProbeKey());
        return probe;
    }

    @Override
    public List<Probe> createProbes(List<ProbeCreateRequest> requests) {
        List<Probe> probes = new ArrayList<>();

        for (ProbeCreateRequest request : requests) {
            try {
                Probe probe = createProbe(request);
                probes.add(probe);
            } catch (Exception e) {
                log.error("创建探针失败: key={}", request.getProbeKey(), e);
            }
        }

        return probes;
    }

    @Override
    public void configureProbe(Probe probe, Map<String, Object> config) {
        if (config == null || config.isEmpty()) {
            log.warn("配置为空，跳过配置");
            return;
        }

        if (!validateConfig(config)) {
            throw new IllegalArgumentException("配置验证失败");
        }

        // 子类实现具体配置逻辑
        doConfigure(probe, config);

        // 更新数据库
        probe.setUpdateTime(LocalDateTime.now());
        probeMapper.updateById(probe);

        log.info("探针配置成功: id={}", probe.getId());
    }

    @Override
    public boolean validateConfig(Map<String, Object> config) {
        // 默认实现：配置不为空即可
        return config != null && !config.isEmpty();
    }

    /**
     * 构建探针实体（子类实现）
     *
     * @param request 创建请求
     * @return 探针实体
     */
    protected abstract Probe buildProbeEntity(ProbeCreateRequest request);

    /**
     * 执行具体配置（子类可选实现）
     *
     * @param probe 探针对象
     * @param config 配置信息
     */
    protected void doConfigure(Probe probe, Map<String, Object> config) {
        // 默认空实现，子类可覆盖
    }

    /**
     * 验证创建请求
     */
    protected void validateRequest(ProbeCreateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("创建请求不能为空");
        }

        if (StringUtils.isEmpty(request.getProbeKey())) {
            throw new IllegalArgumentException("探针Key不能为空");
        }

        if (request.getProbeKey().length() < 3 || request.getProbeKey().length() > 50) {
            throw new IllegalArgumentException("探针Key长度必须在3-50之间");
        }

        if (!request.getProbeKey().matches("^[a-zA-Z0-9_-]+$")) {
            throw new IllegalArgumentException("探针Key只能包含字母、数字、下划线和连字符");
        }

        if (StringUtils.isEmpty(request.getName())) {
            throw new IllegalArgumentException("探针名称不能为空");
        }

        if (request.getName().length() < 2 || request.getName().length() > 100) {
            throw new IllegalArgumentException("探针名称长度必须在2-100之间");
        }
    }

    /**
     * 应用默认配置
     */
    protected void applyDefaultConfig(Probe probe) {
        // 设置默认状态
        if (probe.getStatus() == null) {
            probe.setStatus("offline");
        }

        // 设置默认采集间隔
        if (probe.getCollectInterval() == null) {
            probe.setCollectInterval(getDefaultCollectInterval());
        }

        // 设置时间戳
        LocalDateTime now = LocalDateTime.now();
        probe.setCreateTime(now);
        probe.setUpdateTime(now);
    }

    /**
     * 检查探针Key是否已存在
     */
    protected boolean isProbeKeyExists(String probeKey) {
        LambdaQueryWrapper<Probe> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Probe::getProbeKey, probeKey);
        return probeMapper.selectCount(wrapper) > 0;
    }

    /**
     * 获取默认采集间隔（秒）
     * 子类可以覆盖
     */
    protected int getDefaultCollectInterval() {
        return 60; // 默认60秒
    }
}
