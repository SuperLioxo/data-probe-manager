package com.lixin.probe.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lixin.probe.entity.ProbeGroup;
import com.lixin.probe.entity.ProbeGroupRelation;
import com.lixin.probe.mapper.ProbeGroupMapper;
import com.lixin.probe.mapper.ProbeGroupRelationMapper;
import com.lixin.probe.service.ProbeGroupService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 探针分组服务实现类
 */
@Service
public class ProbeGroupServiceImpl extends ServiceImpl<ProbeGroupMapper, ProbeGroup> implements ProbeGroupService {

    @Autowired
    private ProbeGroupRelationMapper groupRelationMapper;

    @Override
    public List<ProbeGroup> getGroupTree() {
        // 查询所有分组
        LambdaQueryWrapper<ProbeGroup> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(ProbeGroup::getSortOrder);
        List<ProbeGroup> allGroups = this.list(wrapper);

        // 构建树形结构
        return buildTree(allGroups, 0L);
    }

    /**
     * 递归构建树形结构
     */
    private List<ProbeGroup> buildTree(List<ProbeGroup> allGroups, Long parentId) {
        List<ProbeGroup> result = new ArrayList<>();

        for (ProbeGroup group : allGroups) {
            if (group.getParentId().equals(parentId)) {
                // 递归查找子节点
                List<ProbeGroup> children = buildTree(allGroups, group.getId());
                group.setChildren(children);
                result.add(group);
            }
        }

        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean createGroup(ProbeGroup group) {
        // 设置创建时间
        group.setCreateTime(LocalDateTime.now());
        group.setUpdateTime(LocalDateTime.now());

        // 如果有父分组，构建路径
        if (group.getParentId() != null && group.getParentId() > 0) {
            ProbeGroup parent = this.getById(group.getParentId());
            if (parent != null) {
                group.setPath(parent.getPath() + parent.getId() + "/");
            } else {
                group.setPath("/0/");
            }
        } else {
            group.setParentId(0L);
            group.setPath("/0/");
        }

        return this.save(group);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateGroup(ProbeGroup group) {
        group.setUpdateTime(LocalDateTime.now());

        // 如果修改了父分组，需要更新路径
        if (group.getParentId() != null && group.getParentId() > 0) {
            ProbeGroup parent = this.getById(group.getParentId());
            if (parent != null) {
                group.setPath(parent.getPath() + parent.getId() + "/");
            }
        } else {
            group.setParentId(0L);
            group.setPath("/0/");
        }

        return this.updateById(group);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteGroup(Long groupId) {
        // 检查是否有子分组
        LambdaQueryWrapper<ProbeGroup> childWrapper = new LambdaQueryWrapper<>();
        childWrapper.eq(ProbeGroup::getParentId, groupId);
        long childCount = this.count(childWrapper);
        if (childCount > 0) {
            throw new RuntimeException("该分组下存在子分组，无法删除");
        }

        // 删除分组关联的探针
        LambdaQueryWrapper<ProbeGroupRelation> relationWrapper = new LambdaQueryWrapper<>();
        relationWrapper.eq(ProbeGroupRelation::getGroupId, groupId);
        groupRelationMapper.delete(relationWrapper);

        // 删除分组
        return this.removeById(groupId);
    }

    @Override
    public boolean addProbeToGroup(Long probeId, Long groupId) {
        // 检查是否已存在
        LambdaQueryWrapper<ProbeGroupRelation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProbeGroupRelation::getProbeId, probeId);
        wrapper.eq(ProbeGroupRelation::getGroupId, groupId);
        if (groupRelationMapper.selectCount(wrapper) > 0) {
            return true; // 已存在，直接返回成功
        }

        // 创建关联关系
        ProbeGroupRelation relation = ProbeGroupRelation.builder()
                .probeId(probeId)
                .groupId(groupId)
                .createTime(LocalDateTime.now())
                .build();

        return groupRelationMapper.insert(relation) > 0;
    }

    @Override
    public boolean removeProbeFromGroup(Long probeId, Long groupId) {
        LambdaQueryWrapper<ProbeGroupRelation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProbeGroupRelation::getProbeId, probeId);
        wrapper.eq(ProbeGroupRelation::getGroupId, groupId);

        return groupRelationMapper.delete(wrapper) > 0;
    }

    @Override
    public List<Long> getProbeIdsByGroupId(Long groupId) {
        // 获取当前分组的探针
        List<Long> probeIds = getProbeIdsBySingleGroupId(groupId);

        // 递归获取所有子分组的探针ID
        List<Long> childGroupIds = getGroupAndChildrenIds(groupId);
        for (Long childGroupId : childGroupIds) {
            if (!childGroupId.equals(groupId)) {
                probeIds.addAll(getProbeIdsBySingleGroupId(childGroupId));
            }
        }

        return probeIds.stream().distinct().collect(Collectors.toList());
    }

    private List<Long> getProbeIdsBySingleGroupId(Long groupId) {
        LambdaQueryWrapper<ProbeGroupRelation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProbeGroupRelation::getGroupId, groupId);
        List<ProbeGroupRelation> relations = groupRelationMapper.selectList(wrapper);

        return relations.stream()
                .map(ProbeGroupRelation::getProbeId)
                .collect(Collectors.toList());
    }

    @Override
    public List<Long> getGroupAndChildrenIds(Long groupId) {
        List<Long> result = new ArrayList<>();
        result.add(groupId);

        // 递归查找所有子分组
        LambdaQueryWrapper<ProbeGroup> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProbeGroup::getParentId, groupId);
        List<ProbeGroup> children = this.list(wrapper);

        for (ProbeGroup child : children) {
            result.addAll(getGroupAndChildrenIds(child.getId()));
        }

        return result.stream().distinct().collect(Collectors.toList());
    }
}
