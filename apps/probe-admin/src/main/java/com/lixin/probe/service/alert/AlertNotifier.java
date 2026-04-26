package com.lixin.probe.service.alert;

import com.lixin.probe.entity.ChangeAlertRecord;
import com.lixin.probe.entity.AlertChannel;

public interface AlertNotifier {
    boolean supports(String channelType);
    void send(AlertChannel channel, ChangeAlertRecord alert);
}
