package com.lixin.probe.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lixin.probe.entity.AlertChannel;
import com.lixin.probe.entity.ChangeAlertRecord;

public interface AlertNotificationService {

    void notify(ChangeAlertRecord alert);

    AlertChannel createChannel(AlertChannel channel);

    AlertChannel updateChannel(AlertChannel channel);

    void deleteChannel(Long id);

    Page<AlertChannel> listChannels(String channelType, int pageNum, int pageSize);

    boolean testChannel(Long id);
}
