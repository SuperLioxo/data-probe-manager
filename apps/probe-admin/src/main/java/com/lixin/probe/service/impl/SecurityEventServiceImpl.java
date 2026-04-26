package com.lixin.probe.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lixin.probe.entity.SecurityEvent;
import com.lixin.probe.mapper.SecurityEventMapper;
import com.lixin.probe.service.SecurityEventService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 安全事件Service实现类
 */
@Service
public class SecurityEventServiceImpl implements SecurityEventService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SecurityEventServiceImpl.class);

    @Autowired
    private SecurityEventMapper securityEventMapper;

    @Override
    @Transactional
    public void logSecurityEvent(String eventType, String probeKey, String ipAddress,
                                  String severity, String details) {
        try {
            SecurityEvent event = SecurityEvent.builder()
                    .eventType(eventType)
                    .probeKey(probeKey)
                    .ipAddress(ipAddress)
                    .eventDetails(details)
                    .severity(severity != null ? severity : "MEDIUM")
                    .status("PENDING")
                    .eventTime(LocalDateTime.now())
                    .createTime(LocalDateTime.now())
                    .build();

            securityEventMapper.insert(event);
            log.info("安全事件已记录: type={}, probeKey={}, ip={}, severity={}",
                    eventType, probeKey, ipAddress, severity);

        } catch (Exception e) {
            log.error("记录安全事件失败: type={}, probeKey={}, ip={}",
                    eventType, probeKey, ipAddress, e);
            // 不抛出异常，避免影响主要功能
        }
    }

    @Override
    public Page<SecurityEvent> getPage(int pageNum, int pageSize, String eventType,
                                       String severity, LocalDateTime startTime, LocalDateTime endTime) {
        Page<SecurityEvent> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<SecurityEvent> queryWrapper = new LambdaQueryWrapper<SecurityEvent>()
                .eq(eventType != null, SecurityEvent::getEventType, eventType)
                .eq(severity != null, SecurityEvent::getSeverity, severity)
                .ge(startTime != null, SecurityEvent::getEventTime, startTime)
                .le(endTime != null, SecurityEvent::getEventTime, endTime)
                .orderByDesc(SecurityEvent::getEventTime);

        return securityEventMapper.selectPage(page, queryWrapper);
    }

    @Override
    public SecurityEvent getById(Long id) {
        return securityEventMapper.selectById(id);
    }

    @Override
    @Transactional
    public void updateStatus(Long id, String status, String notes) {
        SecurityEvent event = securityEventMapper.selectById(id);
        if (event != null) {
            event.setStatus(status);
            event.setNotes(notes);
            securityEventMapper.updateById(event);
            log.info("安全事件状态已更新: id={}, status={}", id, status);
        }
    }

    @Override
    @Transactional
    public void deleteBefore(LocalDateTime time) {
        securityEventMapper.delete(
                new LambdaQueryWrapper<SecurityEvent>()
                        .lt(SecurityEvent::getEventTime, time)
        );
        log.info("删除历史安全事件: 时间之前 {}", time);
    }

    @Override
    public Map<String, Object> getStatistics(LocalDateTime startTime, LocalDateTime endTime) {
        Map<String, Object> statistics = new HashMap<>();

        // 统计总事件数
        Long totalEvents = securityEventMapper.selectCount(
                new LambdaQueryWrapper<SecurityEvent>()
                        .ge(startTime != null, SecurityEvent::getEventTime, startTime)
                        .le(endTime != null, SecurityEvent::getEventTime, endTime)
        );
        statistics.put("totalEvents", totalEvents);

        // 按严重程度统计
        Map<String, Long> severityStats = new HashMap<>();
        for (String severity : new String[]{"LOW", "MEDIUM", "HIGH", "CRITICAL"}) {
            Long count = securityEventMapper.selectCount(
                    new LambdaQueryWrapper<SecurityEvent>()
                            .eq(SecurityEvent::getSeverity, severity)
                            .ge(startTime != null, SecurityEvent::getEventTime, startTime)
                            .le(endTime != null, SecurityEvent::getEventTime, endTime)
            );
            severityStats.put(severity, count);
        }
        statistics.put("severityStats", severityStats);

        return statistics;
    }
}
