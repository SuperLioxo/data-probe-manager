package com.lixin.probe.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lixin.probe.entity.Settings;

import java.util.Map;

/**
 * 系统设置服务接口
 */
public interface SettingsService extends IService<Settings> {

    /**
     * 获取所有设置（以Map形式返回）
     * @return 设置键值对
     */
    Map<String, String> getAllSettings();

    /**
     * 根据键获取设置值
     * @param key 设置键
     * @return 设置值
     */
    String getSettingValue(String key);

    /**
     * 更新设置
     * @param settings 设置键值对
     * @return 是否成功
     */
    boolean updateSettings(Map<String, String> settings);

    /**
     * 根据键更新设置值
     * @param key 设置键
     * @param value 设置值
     * @return 是否成功
     */
    boolean updateSetting(String key, String value);

    /**
     * 重置所有设置为默认值
     */
    void resetToDefaults();
}
