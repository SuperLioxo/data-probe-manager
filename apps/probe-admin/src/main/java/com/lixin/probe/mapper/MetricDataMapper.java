package com.lixin.probe.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lixin.probe.dto.ProbeMetricsSummary;
import com.lixin.probe.entity.MetricData;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 指标数据Mapper
 */
@Mapper
public interface MetricDataMapper extends BaseMapper<MetricData> {

    /**
     * 获取探针指标摘要
     *
     * @param probeId 探针ID
     * @return 指标摘要
     */
    ProbeMetricsSummary getMetricsSummary(@Param("probeId") Long probeId);
}
