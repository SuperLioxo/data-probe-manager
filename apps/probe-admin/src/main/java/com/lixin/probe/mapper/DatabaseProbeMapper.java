package com.lixin.probe.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lixin.probe.entity.DatabaseProbe;
import org.apache.ibatis.annotations.Mapper;

/**
 * 数据库探针Mapper
 *
 * @author Claude Code
 * @date 2026-03-26
 */
@Mapper
public interface DatabaseProbeMapper extends BaseMapper<DatabaseProbe> {
}
