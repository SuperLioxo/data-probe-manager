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

    private static final Map<String, String> DEFAULT_SETTINGS = Map.ofEntries(
            Map.entry("general.language", "zh-CN"),
            Map.entry("general.timezone", "GMT+8"),
            Map.entry("general.dateFormat", "YYYY-MM-DD"),
            Map.entry("general.timeFormat", "24h"),
            Map.entry("general.pageSize", "20"),
            Map.entry("general.refreshInterval", "30"),
            Map.entry("appearance.theme", "light"),
            Map.entry("appearance.primaryColor", "#409eff"),
            Map.entry("appearance.sidebarWidth", "medium"),
            Map.entry("appearance.animation", "true"),
            Map.entry("appearance.compact", "false"),
            Map.entry("appearance.shadow", "none"),
            Map.entry("notification.desktop", "true"),
            Map.entry("notification.alert", "true"),
            Map.entry("notification.system", "true"),
            Map.entry("notification.sound", "true"),
            Map.entry("notification.alertSound", "default"),
            Map.entry("notification.volume", "70"),
            Map.entry("security.sessionTimeout", "120"),
            Map.entry("security.singleSignOn", "false"),
            Map.entry("security.logOperations", "true"),
            Map.entry("security.logRetention", "30"),
            Map.entry("security.ipWhitelist", "false"),
            Map.entry("security.whitelistIPs", "[]"),
            Map.entry("system.defaultInterval", "60"),
            Map.entry("system.dataRetention", "30"),
            Map.entry("system.cpuThreshold", "80"),
            Map.entry("system.memoryThreshold", "85"),
            Map.entry("system.alertSilence", "0"),
            Map.entry("system.enableCache", "true"),
            Map.entry("system.cacheTime", "5"),
            Map.entry("system.maxConnections", "100")
    );

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

    @Override
    public void resetToDefaults() {
        this.remove(new LambdaQueryWrapper<>());
        for (Map.Entry<String, String> entry : DEFAULT_SETTINGS.entrySet()) {
            Settings setting = new Settings();
            setting.setSettingKey(entry.getKey());
            setting.setSettingValue(entry.getValue());
            setting.setCreateTime(LocalDateTime.now());
            setting.setUpdateTime(LocalDateTime.now());
            this.save(setting);
        }
    }
}
