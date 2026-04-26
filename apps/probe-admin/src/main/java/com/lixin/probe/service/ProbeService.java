package com.lixin.probe.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lixin.probe.dto.DatabaseTypeInfo;
import com.lixin.probe.entity.Probe;

import java.util.List;

/**
 * 探针Service接口
 */
public interface ProbeService {

    /**
     * 分页查询探针
     */
    Page<Probe> getPage(int pageNum, int pageSize);

    /**
     * 分页查询探针（带筛选条件）
     */
    Page<Probe> getPage(int pageNum, int pageSize, String name, String status, String type);

    /**
     * 根据ID查询探针
     */
    Probe getById(Long id);

    /**
     * 根据probeKey查询探针
     */
    Probe getByProbeKey(String probeKey);

    /**
     * 创建探针
     */
    void create(Probe probe);

    /**
     * 更新探针
     */
    void update(Probe probe);

    /**
     * 删除探针
     */
    void delete(Long id);

    /**
     * 更新探针心跳时间
     */
    void updateHeartbeat(String probeKey);

    /**
     * 批量创建探针
     */
    List<Probe> batchCreate(List<Probe> probes);

    /**
     * 导出探针列表为Excel字节数组
     *
     * @param name 探针名称筛选
     * @param status 状态筛选
     * @param type 类型筛选
     * @return Excel文件字节数组
     */
    byte[] exportProbesToExcel(String name, String status, String type);

    /**
     * 导出探针列表为JSON字符串
     *
     * @param name 探针名称筛选
     * @param status 状态筛选
     * @param type 类型筛选
     * @return JSON字符串
     */
    String exportProbesToJson(String name, String status, String type);

    /**
     * 查询所有探针列表
     *
     * @return 所有探针列表
     */
    List<Probe> list();

    /**
     * 根据ID列表批量查询探针
     *
     * @param ids 探针ID列表
     * @return 探针列表
     */
    List<Probe> listByIds(List<Long> ids);

    /**
     * 获取所有探针的probeKey列表
     *
     * @return probeKey列表
     */
    List<String> getAllProbeKeys();

    /**
     * 获取可用的数据库类型列表
     * 返回系统支持的所有数据库类型及其配置信息
     *
     * @return 数据库类型信息列表
     */
    List<DatabaseTypeInfo> getAvailableDatabaseTypes();

    /**
     * 检查指定IP是否已存在系统探针
     *
     * @param hostIp 主机IP地址
     * @return 如果存在返回该探针，否则返回null
     */
    Probe getSystemProbeByIp(String hostIp);

    /**
     * 检查指定IP是否已存在系统探针（排除指定的probeKey）
     * 用于更新探针时检查IP冲突
     *
     * @param hostIp 主机IP地址
     * @param excludeProbeKey 要排除的probeKey（当前探针的probeKey）
     * @return 如果存在其他系统探针返回该探针，否则返回null
     */
    Probe getSystemProbeByIpExclude(String hostIp, String excludeProbeKey);

    /**
     * 获取所有在线的探针
     *
     * @return 在线探针列表
     */
    List<Probe> getOnlineProbes();
}
