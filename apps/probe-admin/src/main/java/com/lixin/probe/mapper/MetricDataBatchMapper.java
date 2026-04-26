package com.lixin.probe.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lixin.probe.entity.MetricData;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 监控数据Mapper（批量查询优化版）
 * 解决N+1查询问题，提供批量查询方法
 *
 * @author Claude Code
 * @date 2026-04-12
 */
@Mapper
public interface MetricDataBatchMapper extends BaseMapper<MetricData> {

    /**
     * 批量查询多个探针的指标数据
     * 解决N+1查询问题
     *
     * @param probeIds 探针ID列表
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 指标数据列表
     */
    @Select("<script>" +
            "SELECT * FROM metric_data " +
            "WHERE probe_id IN " +
            "<foreach collection='probeIds' item='id' open='(' separator=',' close=')'>" +
            "#{id}" +
            "</foreach>" +
            "<if test='startTime != null'>" +
            " AND timestamp &gt;= #{startTime}" +
            "</if>" +
            "<if test='endTime != null'>" +
            " AND timestamp &lt;= #{endTime}" +
            "</if>" +
            " ORDER BY timestamp DESC" +
            "</script>")
    List<MetricData> selectByProbeIds(
            @Param("probeIds") List<Long> probeIds,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );

    /**
     * 批量查询最新指标数据
     * 使用窗口函数优化性能
     *
     * @param probeIds 探针ID列表
     * @param limit 每个探针返回的记录数
     * @return 最新指标数据列表
     */
    @Select("<script>" +
            "SELECT * FROM (" +
            "  SELECT *, " +
            "         ROW_NUMBER() OVER (PARTITION BY probe_id ORDER BY timestamp DESC) as rn " +
            "  FROM metric_data " +
            "  WHERE probe_id IN " +
            "  <foreach collection='probeIds' item='id' open='(' separator=',' close=')'>" +
            "  #{id}" +
            "  </foreach>" +
            ") t " +
            "WHERE t.rn &lt;= #{limit}" +
            "</script>")
    List<MetricData> selectLatestByProbeIds(
            @Param("probeIds") List<Long> probeIds,
            @Param("limit") int limit
    );

    /**
     * 批量查询指定探针的指定指标数据
     *
     * @param probeKeys 探针Key列表
     * @param metricName 指标名称
     * @param limit 限制数量
     * @return 指标数据列表
     */
    @Select("<script>" +
            "SELECT * FROM metric_data " +
            "WHERE probe_key IN " +
            "<foreach collection='probeKeys' item='key' open='(' separator=',' close=')'>" +
            "#{key}" +
            "</foreach>" +
            "<if test='metricName != null and metricName != \"\"'>" +
            " AND metric_name = #{metricName}" +
            "</if>" +
            " ORDER BY timestamp DESC " +
            "<if test='limit > 0'>" +
            " LIMIT #{limit}" +
            "</if>" +
            "</script>")
    List<MetricData> selectByProbeKeysAndMetric(
            @Param("probeKeys") List<String> probeKeys,
            @Param("metricName") String metricName,
            @Param("limit") int limit
    );
}
