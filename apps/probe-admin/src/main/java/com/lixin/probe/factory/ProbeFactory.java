package com.lixin.probe.factory;

import com.lixin.probe.dto.ProbeCreateRequest;
import com.lixin.probe.entity.Probe;

import java.util.List;
import java.util.Map;

/**
 * 探针工厂接口
 * 定义探针创建和配置的统一接口
 *
 * @author Claude Code
 * @since 1.0
 * @version 1.0
 */
public interface ProbeFactory {

    /**
     * 创建探针
     *
     * @param request 创建请求
     * @return 探针对象
     */
    Probe createProbe(ProbeCreateRequest request);

    /**
     * 批量创建探针
     *
     * @param requests 创建请求列表
     * @return 探针列表
     */
    List<Probe> createProbes(List<ProbeCreateRequest> requests);

    /**
     * 配置探针
     *
     * @param probe 探针对象
     * @param config 配置信息
     */
    void configureProbe(Probe probe, Map<String, Object> config);

    /**
     * 验证配置
     *
     * @param config 配置信息
     * @return true如果配置有效
     */
    boolean validateConfig(Map<String, Object> config);

    /**
     * 获取支持的探针类型
     *
     * @return 探针类型
     */
    String getSupportedType();
}
