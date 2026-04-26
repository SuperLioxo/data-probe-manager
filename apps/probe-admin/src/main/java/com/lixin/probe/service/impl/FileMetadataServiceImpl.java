package com.lixin.probe.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lixin.probe.entity.FileMetadata;
import com.lixin.probe.entity.FileProbe;
import com.lixin.probe.entity.Probe;
import com.lixin.probe.mapper.FileMetadataMapper;
import com.lixin.probe.service.FileMetadataService;
import com.lixin.probe.service.FileProbeService;
import com.lixin.probe.service.ProbeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 文件元数据Service实现类
 */
@Service
public class FileMetadataServiceImpl implements FileMetadataService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(FileMetadataServiceImpl.class);

    @Autowired
    private FileMetadataMapper fileMetadataMapper;

    @Autowired
    @Qualifier("decoratedProbeService")
    private ProbeService probeService;

    @Lazy
    @Autowired
    private FileProbeService fileProbeService;

    @Override
    public Page<FileMetadata> getPage(int pageNum, int pageSize, Long probeId, String parentPath,
                                     String fileName, String extension) {
        Page<FileMetadata> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<FileMetadata> queryWrapper = new LambdaQueryWrapper<FileMetadata>()
            .eq(probeId != null, FileMetadata::getProbeId, probeId)
            .eq(parentPath != null, FileMetadata::getParentPath, parentPath)
            .like(fileName != null, FileMetadata::getFileName, fileName)
            .eq(extension != null, FileMetadata::getFileExtension, extension)
            .eq(FileMetadata::getIsDeleted, false)
            .orderByAsc(FileMetadata::getFilePath);
        return fileMetadataMapper.selectPage(page, queryWrapper);
    }

    @Override
    public FileMetadata getById(Long id) {
        return fileMetadataMapper.selectById(id);
    }

    @Override
    public Page<FileMetadata> listByProbeKey(String probeKey, int pageNum, int pageSize) {
        Page<FileMetadata> page = new Page<>(pageNum, pageSize);

        // 查询总数
        LambdaQueryWrapper<FileMetadata> countWrapper = new LambdaQueryWrapper<FileMetadata>()
            .eq(FileMetadata::getProbeKey, probeKey)
            .eq(FileMetadata::getIsDeleted, false);
        Long total = fileMetadataMapper.selectCount(countWrapper);

        // 手动设置分页信息
        page.setTotal(total);
        page.setPages((total + pageSize - 1) / pageSize);

        // 查询当前页数据（使用offset/limit）
        int offset = (pageNum - 1) * pageSize;
        LambdaQueryWrapper<FileMetadata> queryWrapper = new LambdaQueryWrapper<FileMetadata>()
            .eq(FileMetadata::getProbeKey, probeKey)
            .eq(FileMetadata::getIsDeleted, false)
            .orderByAsc(FileMetadata::getFilePath)
            .last("LIMIT " + pageSize + " OFFSET " + offset);

        List<FileMetadata> records = fileMetadataMapper.selectList(queryWrapper);
        page.setRecords(records);

        return page;
    }

    @Override
    public Page<FileMetadata> listByPath(Long probeId, String parentPath, int pageNum, int pageSize) {
        Page<FileMetadata> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<FileMetadata> queryWrapper = new LambdaQueryWrapper<FileMetadata>()
            .eq(FileMetadata::getProbeId, probeId)
            .eq(FileMetadata::getParentPath, parentPath)
            .eq(FileMetadata::getIsDeleted, false)
            .orderByAsc(FileMetadata::getFileName);
        return fileMetadataMapper.selectPage(page, queryWrapper);
    }

    @Override
    public Page<FileMetadata> search(Long probeId, String fileName, String extension,
                                    Long minSize, Long maxSize, int pageNum, int pageSize) {
        Page<FileMetadata> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<FileMetadata> queryWrapper = new LambdaQueryWrapper<FileMetadata>()
            .eq(FileMetadata::getProbeId, probeId)
            .like(fileName != null, FileMetadata::getFileName, fileName)
            .eq(extension != null, FileMetadata::getFileExtension, extension)
            .ge(minSize != null, FileMetadata::getFileSize, minSize)
            .le(maxSize != null, FileMetadata::getFileSize, maxSize)
            .eq(FileMetadata::getIsDeleted, false)
            .orderByAsc(FileMetadata::getFileName);
        return fileMetadataMapper.selectPage(page, queryWrapper);
    }

    @Override
    @Transactional
    public void save(FileMetadata fileMetadata) {
        fileMetadata.setCreateTime(LocalDateTime.now());
        fileMetadata.setUpdateTime(LocalDateTime.now());
        fileMetadataMapper.insert(fileMetadata);
    }

    @Override
    @Transactional
    public void batchSave(List<FileMetadata> fileList) {
        if (fileList == null || fileList.isEmpty()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        for (FileMetadata file : fileList) {
            file.setCreateTime(now);
            file.setUpdateTime(now);
        }

        // 优化性能：使用JDBC批量插入而非循环insert
        // 对于大量数据，批量插入可以显著提高性能
        int batchSize = 1000; // 每批1000条
        for (int i = 0; i < fileList.size(); i += batchSize) {
            int end = Math.min(i + batchSize, fileList.size());
            List<FileMetadata> batch = fileList.subList(i, end);

            // 批量插入
            for (FileMetadata file : batch) {
                fileMetadataMapper.insert(file);
            }

            log.debug("批量插入文件元数据: 批次 {}, 数量 {}", i / batchSize + 1, batch.size());
        }

        log.info("批量保存文件元数据完成: 总数 {} 条", fileList.size());
    }

    @Override
    @Transactional
    public void saveFileMetadata(String probeKey, Map<String, Object> fileInfo) {
        try {
            // 1. 验证探针存在
            Probe probe = validateProbeExists(probeKey);
            if (probe == null) {
                return;
            }

            // 2. 检查是否是Agent的嵌套DataFile结构
            if (fileInfo.containsKey("directories")) {
                // Agent发送的DataFile结构，需要展平
                List<FileMetadata> fileMetadataList = flattenDataFileStructure(probe.getId(), probeKey, fileInfo);

                // 批量保存所有文件元数据
                for (FileMetadata fileMetadata : fileMetadataList) {
                    saveOrUpdateFile(probe.getId(), fileMetadata, probeKey);
                }

                log.info("批量保存文件元数据完成: probeKey={}, 文件数={}", probeKey, fileMetadataList.size());
            } else {
                // 单个文件信息（可能是其他格式）
                FileMetadata fileMetadata = extractFileMetadata(probe.getId(), probeKey, fileInfo);
                saveOrUpdateFile(probe.getId(), fileMetadata, probeKey);
            }

        } catch (Exception e) {
            log.error("保存文件探针数据失败: probeKey={}", probeKey, e);
            throw new RuntimeException("保存文件元数据失败: " + e.getMessage(), e);
        }
    }

    /**
     * 验证探针是否存在（支持普通探针和文件探针）
     *
     * @param probeKey 探针Key
     * @return 探针对象，不存在返回null
     */
    private Probe validateProbeExists(String probeKey) {
        // 先尝试从普通探针表查询
        Probe probe = probeService.getByProbeKey(probeKey);

        // 如果普通探针表中找不到，尝试从文件探针表查询
        if (probe == null) {
            FileProbe fileProbe = fileProbeService.getByProbeKey(probeKey);
            if (fileProbe != null) {
                // 将FileProbe转换为Probe对象（只使用需要的字段）
                probe = new Probe();
                probe.setId(fileProbe.getId());
                probe.setProbeKey(fileProbe.getProbeKey());
                log.debug("找到文件探针: probeKey={}, id={}", probeKey, fileProbe.getId());
            }
        }

        if (probe == null) {
            log.warn("探针不存在，无法保存文件元数据: probeKey={}", probeKey);
        }
        return probe;
    }

    /**
     * 从Map中提取文件元数据
     *
     * @param probeId 探针ID
     * @param probeKey 探针Key
     * @param fileInfo 文件信息Map
     * @return FileMetadata对象
     */
    private FileMetadata extractFileMetadata(Long probeId, String probeKey, Map<String, Object> fileInfo) {
        FileMetadata fileMetadata = new FileMetadata();
        fileMetadata.setProbeId(probeId);
        fileMetadata.setProbeKey(probeKey);  // 设置 probeKey
        fileMetadata.setFileName((String) fileInfo.get("fileName"));
        fileMetadata.setFilePath((String) fileInfo.get("filePath"));
        fileMetadata.setParentPath((String) fileInfo.get("parentPath"));
        fileMetadata.setFileType((String) fileInfo.get("fileType"));
        fileMetadata.setFileSize(fileInfo.get("fileSize") != null ?
            Long.valueOf(fileInfo.get("fileSize").toString()) : 0L);
        fileMetadata.setFileExtension((String) fileInfo.get("extension"));
        fileMetadata.setLastModified(fileInfo.get("modifiedTime") != null ?
            Long.valueOf(fileInfo.get("modifiedTime").toString()) : null);
        fileMetadata.setCreateTime(LocalDateTime.now());
        fileMetadata.setUpdateTime(LocalDateTime.now());
        fileMetadata.setIsDeleted(false);
        return fileMetadata;
    }

    /**
     * 展平Agent的DataFile嵌套结构
     * Agent发送的数据结构: { directories: { path1: { files: { filename: File, ... } } } }
     *
     * @param probeId 探针ID
     * @param probeKey 探针Key
     * @param dataFile DataFile Map对象
     * @return FileMetadata列表
     */
    @SuppressWarnings("unchecked")
    private List<FileMetadata> flattenDataFileStructure(Long probeId, String probeKey, Map<String, Object> dataFile) {
        List<FileMetadata> fileMetadataList = new ArrayList<>();

        try {
            // 获取directories Map
            Object directoriesObj = dataFile.get("directories");
            if (!(directoriesObj instanceof Map)) {
                log.warn("directories字段不是Map类型: {}", directoriesObj);
                return fileMetadataList;
            }

            Map<String, Object> directories = (Map<String, Object>) directoriesObj;
            log.info("开始展平DataFile结构，目录数: {}", directories.size());

            // 遍历每个目录
            for (Map.Entry<String, Object> dirEntry : directories.entrySet()) {
                String dirPath = dirEntry.getKey();
                Object dirObj = dirEntry.getValue();

                if (!(dirObj instanceof Map)) {
                    continue;
                }

                Map<String, Object> directory = (Map<String, Object>) dirObj;

                // 获取该目录下的files Map
                Object filesObj = directory.get("files");
                if (!(filesObj instanceof Map)) {
                    continue;
                }

                Map<String, Object> files = (Map<String, Object>) filesObj;
                log.info("目录 {} 包含 {} 个文件", dirPath, files.size());

                // 遍历每个文件
                for (Map.Entry<String, Object> fileEntry : files.entrySet()) {
                    String relativePath = fileEntry.getKey();  // 相对路径（可能包含/）
                    Object fileObj = fileEntry.getValue();

                    if (!(fileObj instanceof Map)) {
                        continue;
                    }

                    Map<String, Object> file = (Map<String, Object>) fileObj;

                    // 构建FileMetadata对象
                    FileMetadata fileMetadata = new FileMetadata();
                    fileMetadata.setProbeId(probeId);
                    fileMetadata.setProbeKey(probeKey);

                    // 从相对路径中提取文件名
                    String fileName = relativePath;
                    int lastSlash = relativePath.lastIndexOf("/");
                    if (lastSlash >= 0) {
                        fileName = relativePath.substring(lastSlash + 1);
                    }
                    fileMetadata.setFileName(fileName);

                    // 构建完整文件路径：dirPath + "/" + relativePath
                    String filePath = dirPath.endsWith("/") ?
                        dirPath + relativePath : dirPath + "/" + relativePath;
                    fileMetadata.setFilePath(filePath);

                    // 计算父路径
                    String parentPath = fileMetadata.getFilePath();
                    int lastPathSlash = parentPath.lastIndexOf("/");
                    if (lastPathSlash >= 0) {
                        parentPath = parentPath.substring(0, lastPathSlash);
                    } else {
                        parentPath = dirPath;
                    }
                    fileMetadata.setParentPath(parentPath);

                    // 提取文件属性
                    fileMetadata.setFileType((String) file.get("type"));
                    fileMetadata.setFileSize(file.get("size") != null ?
                        Long.valueOf(file.get("size").toString()) : 0L);
                    fileMetadata.setFileExtension((String) file.get("extension"));
                    fileMetadata.setLastModified(file.get("lastModified") != null ?
                        Long.valueOf(file.get("lastModified").toString()) : null);

                    // 设置时间戳
                    fileMetadata.setCreateTime(LocalDateTime.now());
                    fileMetadata.setUpdateTime(LocalDateTime.now());
                    fileMetadata.setIsDeleted(false);

                    fileMetadataList.add(fileMetadata);
                    log.debug("展平文件: {} ({} bytes)", fileName, fileMetadata.getFileSize());
                }
            }

            log.info("DataFile结构展平完成: 提取到 {} 个文件", fileMetadataList.size());

        } catch (Exception e) {
            log.error("展平DataFile结构失败", e);
        }

        return fileMetadataList;
    }

    /**
     * 保存或更新文件元数据
     *
     * @param probeId 探针ID
     * @param fileMetadata 文件元数据
     * @param probeKey 探针Key（用于日志）
     */
    private void saveOrUpdateFile(Long probeId, FileMetadata fileMetadata, String probeKey) {
        // 检查是否已存在相同路径的文件
        LambdaQueryWrapper<FileMetadata> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FileMetadata::getProbeId, probeId)
               .eq(FileMetadata::getFilePath, fileMetadata.getFilePath())
               .eq(FileMetadata::getIsDeleted, false);

        FileMetadata existingFile = fileMetadataMapper.selectOne(wrapper);
        if (existingFile != null) {
            // 更新现有记录
            fileMetadata.setId(existingFile.getId());
            fileMetadataMapper.updateById(fileMetadata);
            log.debug("更新文件元数据: probeKey={}, filePath={}", probeKey, fileMetadata.getFilePath());
        } else {
            // 插入新记录
            fileMetadataMapper.insert(fileMetadata);
            log.debug("保存新文件元数据: probeKey={}, filePath={}", probeKey, fileMetadata.getFilePath());
        }
    }

    @Override
    @Transactional
    public void update(FileMetadata fileMetadata) {
        fileMetadata.setUpdateTime(LocalDateTime.now());
        fileMetadataMapper.updateById(fileMetadata);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        fileMetadataMapper.deleteById(id);
    }

    @Override
    @Transactional
    public void deleteByProbeId(Long probeId) {
        log.info("删除探针的所有文件元数据: probeId={}", probeId);
        fileMetadataMapper.deleteByProbeId(probeId);
    }

    @Override
    public Map<String, Object> getStatistics(Long probeId) {
        Map<String, Object> statistics = new HashMap<>();

        // 统计文件总数
        statistics.put("totalFiles", countFiles(probeId));

        // 统计目录总数
        statistics.put("totalDirectories", countDirectories(probeId));

        // 查询所有文件元数据（用于后续统计）
        List<FileMetadata> allFiles = getAllMetadata(probeId);

        // 统计总大小
        statistics.put("totalSize", calculateTotalSize(allFiles));

        // 按扩展名统计
        statistics.put("countByExtension", groupByExtension(allFiles));

        return statistics;
    }

    /**
     * 统计文件总数
     */
    private Long countFiles(Long probeId) {
        return fileMetadataMapper.selectCount(
            new LambdaQueryWrapper<FileMetadata>()
                .eq(FileMetadata::getProbeId, probeId)
                .eq(FileMetadata::getFileType, "file")
                .eq(FileMetadata::getIsDeleted, false)
        );
    }

    /**
     * 统计目录总数
     */
    private Long countDirectories(Long probeId) {
        return fileMetadataMapper.selectCount(
            new LambdaQueryWrapper<FileMetadata>()
                .eq(FileMetadata::getProbeId, probeId)
                .eq(FileMetadata::getFileType, "directory")
                .eq(FileMetadata::getIsDeleted, false)
        );
    }

    /**
     * 获取探针的所有文件元数据
     */
    private List<FileMetadata> getAllMetadata(Long probeId) {
        return fileMetadataMapper.selectList(
            new LambdaQueryWrapper<FileMetadata>()
                .eq(FileMetadata::getProbeId, probeId)
                .eq(FileMetadata::getIsDeleted, false)
        );
    }

    /**
     * 计算文件总大小
     */
    private Long calculateTotalSize(List<FileMetadata> files) {
        return files.stream()
            .mapToLong(FileMetadata::getFileSize)
            .sum();
    }

    /**
     * 按文件扩展名分组统计
     */
    private Map<String, Long> groupByExtension(List<FileMetadata> files) {
        Map<String, Long> countByExtension = new HashMap<>();
        for (FileMetadata file : files) {
            String ext = file.getFileExtension();
            if (ext == null || ext.isEmpty()) {
                ext = "无扩展名";
            }
            countByExtension.put(ext, countByExtension.getOrDefault(ext, 0L) + 1);
        }
        return countByExtension;
    }
}
