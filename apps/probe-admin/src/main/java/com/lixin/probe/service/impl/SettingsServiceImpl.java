package com.lixin.probe.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lixin.probe.entity.Settings;
import com.lixin.probe.mapper.SettingsMapper;
import com.lixin.probe.service.SettingsService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 系统设置服务实现类
 */
@Service
public class SettingsServiceImpl extends ServiceImpl<SettingsMapper, Settings> implements SettingsService {

    @Override
    public Map<String, String> getAllSettings() {
        List<Settings> settingsList = this.list();
        Map<String, String> settingsMap = new HashMap<>();
        for (Settings setting : settingsList) {
            settingsMap.put(setting.getSettingKey(), setting.getSettingValue());
        }
        return settingsMap;
    }

    @Override
    public String getSettingValue(String key) {
        LambdaQueryWrapper<Settings> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Settings::getSettingKey, key);
        Settings setting = this.getOne(wrapper);
        return setting != null ? setting.getSettingValue() : null;
    }

    @Override
    public boolean updateSettings(Map<String, String> settings) {
        for (Map.Entry<String, String> entry : settings.entrySet()) {
            updateSetting(entry.getKey(), entry.getValue());
        }
        return true;
    }

    @Override
    public boolean updateSetting(String key, String value) {
        LambdaQueryWrapper<Settings> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Settings::getSettingKey, key);
        Settings setting = this.getOne(wrapper);

        if (setting != null) {
            setting.setSettingValue(value);
            setting.setUpdateTime(LocalDateTime.now());
            return this.updateById(setting);
        } else {
            Settings newSetting = new Settings();
            newSetting.setSettingKey(key);
            newSetting.setSettingValue(value);
            newSetting.setCreateTime(LocalDateTime.now());
            newSetting.setUpdateTime(LocalDateTime.now());
            return this.save(newSetting);
        }
    }

    private static final Map<String, String> DEFAULT_SETTINGS = Map.of(
            "language", "zh-CN",
            "timezone", "Asia/Shanghai",
            "dateFormat", "YYYY-MM-DD",
            "timeFormat", "HH:mm:ss",
            "pageSize", "20",
            "refreshInterval", "30",
            "sessionTimeout", "30",
            "monitorInterval", "60",
            "dataRetention", "90",
            "alertThreshold", "80"
    );

    @Override
    public void resetToDefaults() {
        updateSettings(DEFAULT_SETTINGS);
    }
}
