package com.lixin.probe.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lixin.probe.entity.DeadLetterTask;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DeadLetterTaskMapper extends BaseMapper<DeadLetterTask> {
}
