package com.lixin.probe.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lixin.probe.entity.FileMetadata;

/**
 * 文件元数据Service接口
 */
public interface FileMetadataService {

    /**
     * 分页查询文件元数据
     */
    Page<FileMetadata> getPage(int pageNum, int pageSize, Long probeId, String parentPath,
                             String fileName, String extension);

    /**
     * 根据ID查询文件
     */
    FileMetadata getById(Long id);

    /**
     * 根据probeKey查询文件列表
     */
    Page<FileMetadata> listByProbeKey(String probeKey, int pageNum, int pageSize);

    /**
     * 按路径查询文件
     */
    Page<FileMetadata> listByPath(Long probeId, String parentPath, int pageNum, int pageSize);

    /**
     * 搜索文件
     */
    Page<FileMetadata> search(Long probeId, String fileName, String extension,
                            Long minSize, Long maxSize, int pageNum, int pageSize);

    /**
     * 保存文件元数据
     */
    void save(FileMetadata fileMetadata);

    /**
     * 批量保存文件元数据
     */
    void batchSave(java.util.List<FileMetadata> fileList);

    /**
     * 保存文件探针上报的元数据
     * @param probeKey 探针标识
     * @param fileInfo 文件信息Map
     */
    void saveFileMetadata(String probeKey, java.util.Map<String, Object> fileInfo);

    /**
     * 更新文件
     */
    void update(FileMetadata fileMetadata);

    /**
     * 删除文件
     */
    void delete(Long id);

    /**
     * 删除探针的所有文件元数据
     * @param probeId 探针ID
     */
    void deleteByProbeId(Long probeId);

    /**
     * 获取文件统计信息
     */
    java.util.Map<String, Object> getStatistics(Long probeId);
}
