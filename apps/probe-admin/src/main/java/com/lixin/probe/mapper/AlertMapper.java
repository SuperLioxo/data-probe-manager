package com.lixin.probe.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lixin.probe.entity.Alert;
import org.apache.ibatis.annotations.Mapper;

/**
 * 告警Mapper
 */
@Mapper
public interface AlertMapper extends BaseMapper<Alert> {
}
