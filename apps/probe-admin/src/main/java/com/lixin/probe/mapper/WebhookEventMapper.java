package com.lixin.probe.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lixin.probe.entity.WebhookEvent;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WebhookEventMapper extends BaseMapper<WebhookEvent> {
}
