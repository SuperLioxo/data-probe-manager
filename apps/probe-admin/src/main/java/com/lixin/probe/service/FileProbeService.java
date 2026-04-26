package com.lixin.probe.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lixin.probe.entity.FileProbe;

import java.util.List;

/**
 * 文件探针Service接口
 */
public interface FileProbeService {

    /**
     * 查询所有文件探针
     */
    List<FileProbe> list();

    /**
     * 分页查询文件探针
     */
    Page<FileProbe> getPage(int pageNum, int pageSize, String name, String status);

    /**
     * 根据ID查询文件探针
     */
    FileProbe getById(Long id);

    /**
     * 根据probeKey查询文件探针
     */
    FileProbe getByProbeKey(String probeKey);

    /**
     * 创建文件探针
     */
    void create(FileProbe fileProbe);

    /**
     * 更新文件探针
     */
    void update(FileProbe fileProbe);

    /**
     * 删除文件探针
     */
    void delete(Long id);

    /**
     * 更新扫描统计信息
     */
    void updateScanStatistics(String probeKey, Long fileCount, Long directoryCount, Long totalSize);

    /**
     * 更新心跳时间
     */
    void updateHeartbeat(String probeKey);

    /**
     * 记录扫描历史
     */
    void recordScanHistory(Long probeId, String probeKey, Integer duration,
                            Long fileCount, Long directoryCount, Long totalSize,
                            String status);

    /**
     * 获取文件元数据列表
     */
    Page<com.lixin.probe.entity.FileMetadata> getFileMetadata(Long probeId, int pageNum, int pageSize, String search);

    /**
     * 从统一probe表查询探针
     */
    com.lixin.probe.entity.Probe getProbeById(Long id);
}
