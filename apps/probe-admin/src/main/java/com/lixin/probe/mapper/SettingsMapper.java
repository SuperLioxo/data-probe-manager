package com.lixin.probe.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lixin.probe.entity.Settings;
import org.apache.ibatis.annotations.Mapper;

/**
 * 系统设置Mapper接口
 */
@Mapper
public interface SettingsMapper extends BaseMapper<Settings> {
}
