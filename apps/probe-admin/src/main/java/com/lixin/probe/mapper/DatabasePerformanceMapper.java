package com.lixin.probe.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lixin.probe.entity.DatabasePerformance;
import org.apache.ibatis.annotations.Mapper;

/**
 * 数据库性能Mapper
 */
@Mapper
public interface DatabasePerformanceMapper extends BaseMapper<DatabasePerformance> {
}
