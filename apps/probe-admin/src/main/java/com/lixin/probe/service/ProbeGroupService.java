package com.lixin.probe.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lixin.probe.entity.ProbeGroup;

import java.util.List;

/**
 * 探针分组服务接口
 */
public interface ProbeGroupService extends IService<ProbeGroup> {

    /**
     * 获取分组树形结构
     * @return 分组树
     */
    List<ProbeGroup> getGroupTree();

    /**
     * 创建分组
     * @param group 分组信息
     * @return 是否成功
     */
    boolean createGroup(ProbeGroup group);

    /**
     * 更新分组
     * @param group 分组信息
     * @return 是否成功
     */
    boolean updateGroup(ProbeGroup group);

    /**
     * 删除分组
     * @param groupId 分组ID
     * @return 是否成功
     */
    boolean deleteGroup(Long groupId);

    /**
     * 将探针添加到分组
     * @param probeId 探针ID
     * @param groupId 分组ID
     * @return 是否成功
     */
    boolean addProbeToGroup(Long probeId, Long groupId);

    /**
     * 将探针从分组中移除
     * @param probeId 探针ID
     * @param groupId 分组ID
     * @return 是否成功
     */
    boolean removeProbeFromGroup(Long probeId, Long groupId);

    /**
     * 获取分组下的所有探针ID
     * @param groupId 分组ID
     * @return 探针ID列表
     */
    List<Long> getProbeIdsByGroupId(Long groupId);

    /**
     * 获取分组及其所有子分组的ID列表
     * @param groupId 分组ID
     * @return 分组ID列表（包含所有子分组）
     */
    List<Long> getGroupAndChildrenIds(Long groupId);
}
