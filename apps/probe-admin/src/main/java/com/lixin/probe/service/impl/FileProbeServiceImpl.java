package com.lixin.probe.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lixin.probe.entity.FileMetadata;
import com.lixin.probe.entity.FileProbe;
import com.lixin.probe.entity.FileScanHistory;
import com.lixin.probe.entity.Probe;
import com.lixin.probe.enums.ProbeStatus;
import com.lixin.probe.mapper.FileProbeMapper;
import com.lixin.probe.mapper.FileScanHistoryMapper;
import com.lixin.probe.mapper.ProbeMapper;
import com.lixin.probe.service.FileMetadataService;
import com.lixin.probe.service.FileProbeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文件探针Service实现类
 */
@Service
public class FileProbeServiceImpl implements FileProbeService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(FileProbeServiceImpl.class);

    @Autowired
    private FileProbeMapper fileProbeMapper;

    @Autowired
    private ProbeMapper probeMapper;

    @Autowired
    private FileScanHistoryMapper fileScanHistoryMapper;

    @Autowired
    private FileMetadataService fileMetadataService;

    @Override
    public List<FileProbe> list() {
        return fileProbeMapper.selectList(null);
    }

    @Override
    public Page<FileProbe> getPage(int pageNum, int pageSize, String name, String status) {
        Page<FileProbe> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<FileProbe> queryWrapper = new LambdaQueryWrapper<FileProbe>()
            .like(name != null, FileProbe::getName, name)
            .eq(status != null, FileProbe::getStatus, status)
            .orderByDesc(FileProbe::getCreateTime);
        return fileProbeMapper.selectPage(page, queryWrapper);
    }

    @Override
    public FileProbe getById(Long id) {
        log.info("[FileProbeService] 查询文件探针 - id={}", id);
        FileProbe fileProbe = fileProbeMapper.selectById(id);
        log.info("[FileProbeService] 查询结果 - fileProbe={}", fileProbe != null ? fileProbe.getName() : "null");
        return fileProbe;
    }

    @Override
    public FileProbe getByProbeKey(String probeKey) {
        return fileProbeMapper.selectOne(
            new LambdaQueryWrapper<FileProbe>()
                .eq(FileProbe::getProbeKey, probeKey)
        );
    }

    @Override
    @Transactional
    public void create(FileProbe fileProbe) {
        log.info("[FileProbeService] 创建文件探针 - name={}, probeKey={}",
                fileProbe.getName(), fileProbe.getProbeKey());

        // 如果status为空，设置默认值为offline（小写，与系统探针保持一致）
        if (fileProbe.getStatus() == null || fileProbe.getStatus().isEmpty()) {
            fileProbe.setStatus("offline");
            log.info("[FileProbeService] 设置默认status为offline");
        }

        fileProbe.setCreateTime(LocalDateTime.now());
        fileProbe.setUpdateTime(LocalDateTime.now());

        log.info("[FileProbeService] 准备插入数据库 - status={}", fileProbe.getStatus());
        int result = fileProbeMapper.insert(fileProbe);
        log.info("[FileProbeService] 创建完成 - id={}, name={}, 影响行数: {}",
                fileProbe.getId(), fileProbe.getName(), result);

        if (result <= 0) {
            log.error("[FileProbeService] 创建失败：插入操作影响行数为0 - probeKey={}", fileProbe.getProbeKey());
            throw new RuntimeException("创建文件探针失败：数据库插入操作未成功");
        }
    }

    @Override
    @Transactional
    public void update(FileProbe fileProbe) {
        fileProbe.setUpdateTime(LocalDateTime.now());
        fileProbeMapper.updateById(fileProbe);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        fileProbeMapper.deleteById(id);
    }

    @Override
    @Transactional
    public void updateScanStatistics(String probeKey, Long fileCount, Long directoryCount, Long totalSize) {
        FileProbe probe = getByProbeKey(probeKey);
        if (probe != null) {
            probe.setTotalFileCount(fileCount);
            probe.setTotalDirectoryCount(directoryCount);
            probe.setTotalSize(totalSize);
            probe.setLastScanTime(LocalDateTime.now());
            probe.setUpdateTime(LocalDateTime.now());
            fileProbeMapper.updateById(probe);
        }
    }

    @Override
    @Transactional
    public void updateHeartbeat(String probeKey) {
        FileProbe probe = getByProbeKey(probeKey);
        if (probe != null) {
            probe.setLastHeartbeat(LocalDateTime.now());
            probe.setStatus(ProbeStatus.ONLINE.getCode()); // 设置状态为在线
            probe.setUpdateTime(LocalDateTime.now());
            fileProbeMapper.updateById(probe);
            log.debug("更新文件探针心跳: probeKey={}, status=ONLINE", probeKey);
        }
    }

    @Override
    public void recordScanHistory(Long probeId, String probeKey, Integer duration,
                                Long fileCount, Long directoryCount, Long totalSize,
                                String status) {
        FileScanHistory history = FileScanHistory.builder()
            .probeId(probeId)
            .probeKey(probeKey)
            .scanStartTime(LocalDateTime.now())
            .scanEndTime(LocalDateTime.now())
            .scanDuration(duration)
            .fileCount(fileCount)
            .directoryCount(directoryCount)
            .totalSize(totalSize)
            .scanStatus(status)
            .build();

        // 保存扫描历史到数据库
        try {
            fileScanHistoryMapper.insert(history);
            log.info("文件扫描历史已保存: probeKey={}, scanId={}, duration={}ms",
                    probeKey, history.getId(), duration);
        } catch (Exception e) {
            log.error("保存文件扫描历史失败: probeKey={}", probeKey, e);
            // 不抛出异常，避免影响主要功能
        }
    }

    @Override
    public Page<FileMetadata> getFileMetadata(Long probeId, int pageNum, int pageSize, String search) {
        String probeKey = resolveProbeKey(probeId);
        return fileMetadataService.listByProbeKey(probeKey, pageNum, pageSize);
    }

    @Override
    public Probe getProbeById(Long id) {
        return probeMapper.selectById(id);
    }

    /**
     * 解析 probeKey：优先从 file_probe 表查，回退到统一 probe 表
     */
    private String resolveProbeKey(Long probeId) {
        // 优先从 file_probe 表查
        FileProbe fileProbe = fileProbeMapper.selectById(probeId);
        if (fileProbe != null) {
            return fileProbe.getProbeKey();
        }
        // 回退到统一 probe 表
        Probe probe = probeMapper.selectById(probeId);
        if (probe != null && "FILE".equals(probe.getType())) {
            return probe.getProbeKey();
        }
        throw new IllegalArgumentException("文件探针不存在: id=" + probeId);
    }
}
