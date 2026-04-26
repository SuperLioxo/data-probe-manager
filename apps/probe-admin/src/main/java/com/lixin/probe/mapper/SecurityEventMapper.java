package com.lixin.probe.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lixin.probe.entity.SecurityEvent;
import org.apache.ibatis.annotations.Mapper;

/**
 * 安全事件Mapper接口
 */
@Mapper
public interface SecurityEventMapper extends BaseMapper<SecurityEvent> {
}
