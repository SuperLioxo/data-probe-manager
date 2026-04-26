package com.lixin.probe.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lixin.probe.entity.FileProbe;
import com.lixin.probe.entity.Probe;
import com.lixin.probe.enums.ProbeStatus;
import com.lixin.probe.mapper.FileProbeMapper;
import com.lixin.probe.mapper.ProbeMapper;
import com.lixin.probe.service.ProbeMonitorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 探针监控服务实现
 * 负责探针状态监控、心跳管理（支持 probe 表和 file_probe 表）
 */
@Service
public class ProbeMonitorServiceImpl implements ProbeMonitorService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ProbeMonitorServiceImpl.class);

    @Autowired
    private ProbeMapper probeMapper;

    @Autowired
    private FileProbeMapper fileProbeMapper;

    @Override
    @Transactional
    public void updateHeartbeat(String probeKey) {
        // 先查 probe 表
        Probe probe = probeMapper.selectOne(
            new LambdaQueryWrapper<Probe>().eq(Probe::getProbeKey, probeKey)
        );

        if (probe != null) {
            probe.setLastHeartbeat(LocalDateTime.now());
            probe.setUpdateTime(LocalDateTime.now());

            if (!ProbeStatus.ONLINE.getCode().equals(probe.getStatus())) {
                log.info("收到探针心跳，自动更新状态为在线: probeKey={}, oldStatus={}",
                        probeKey, probe.getStatus());
                probe.setStatus(ProbeStatus.ONLINE.getCode());
            }

            probeMapper.updateById(probe);
            log.debug("更新探针心跳: probeKey={}, status=online", probeKey);
            return;
        }

        // probe 表没找到，查 file_probe 表
        FileProbe fileProbe = fileProbeMapper.selectOne(
            new LambdaQueryWrapper<FileProbe>().eq(FileProbe::getProbeKey, probeKey)
        );

        if (fileProbe != null) {
            fileProbe.setLastHeartbeat(LocalDateTime.now());
            fileProbe.setUpdateTime(LocalDateTime.now());

            if (!"online".equals(fileProbe.getStatus())) {
                log.info("收到文件探针心跳，自动更新状态为在线: probeKey={}, oldStatus={}",
                        probeKey, fileProbe.getStatus());
                fileProbe.setStatus("online");
            }

            fileProbeMapper.updateById(fileProbe);
            log.debug("更新文件探针心跳: probeKey={}, status=online", probeKey);
            return;
        }

        log.warn("探针不存在，无法更新心跳: probeKey={}", probeKey);
    }
}
