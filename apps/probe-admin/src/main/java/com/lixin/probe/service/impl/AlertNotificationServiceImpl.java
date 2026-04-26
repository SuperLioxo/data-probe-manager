package com.lixin.probe.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lixin.probe.entity.AlertChannel;
import com.lixin.probe.entity.ChangeAlertRecord;
import com.lixin.probe.mapper.AlertChannelMapper;
import com.lixin.probe.service.AlertNotificationService;
import com.lixin.probe.service.alert.AlertNotifier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class AlertNotificationServiceImpl implements AlertNotificationService {

    @Autowired
    private AlertChannelMapper alertChannelMapper;

    @Autowired
    private List<AlertNotifier> notifiers;

    @Override
    @Async
    public void notify(ChangeAlertRecord alert) {
        List<AlertChannel> channels = alertChannelMapper.selectList(
                new LambdaQueryWrapper<AlertChannel>().eq(AlertChannel::getEnabled, true));
        for (AlertChannel channel : channels) {
            for (AlertNotifier notifier : notifiers) {
                if (notifier.supports(channel.getChannelType())) {
                    try {
                        notifier.send(channel, alert);
                    } catch (Exception e) {
                        log.warn("[AlertNotify] Failed via {}: {}", channel.getChannelType(), e.getMessage());
                    }
                }
            }
        }
    }

    @Override
    public AlertChannel createChannel(AlertChannel channel) {
        channel.setCreateTime(LocalDateTime.now());
        channel.setUpdateTime(LocalDateTime.now());
        if (channel.getEnabled() == null) channel.setEnabled(true);
        alertChannelMapper.insert(channel);
        return channel;
    }

    @Override
    public AlertChannel updateChannel(AlertChannel channel) {
        channel.setUpdateTime(LocalDateTime.now());
        alertChannelMapper.updateById(channel);
        return channel;
    }

    @Override
    public void deleteChannel(Long id) {
        alertChannelMapper.deleteById(id);
    }

    @Override
    public Page<AlertChannel> listChannels(String channelType, int pageNum, int pageSize) {
        LambdaQueryWrapper<AlertChannel> wrapper = new LambdaQueryWrapper<AlertChannel>()
                .eq(channelType != null && !channelType.isEmpty(), AlertChannel::getChannelType, channelType)
                .orderByDesc(AlertChannel::getCreateTime);
        return alertChannelMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    @Override
    public boolean testChannel(Long id) {
        AlertChannel channel = alertChannelMapper.selectById(id);
        if (channel == null) return false;
        ChangeAlertRecord testAlert = ChangeAlertRecord.builder()
                .probeKey("test-probe").tableName("test_table").changeType("TEST")
                .alertLevel("INFO").status("PENDING").changeDetail("Test notification").build();
        for (AlertNotifier notifier : notifiers) {
            if (notifier.supports(channel.getChannelType())) {
                try {
                    notifier.send(channel, testAlert);
                    return true;
                } catch (Exception e) {
                    log.warn("[AlertNotify] Test failed: {}", e.getMessage());
                    return false;
                }
            }
        }
        return false;
    }
}
