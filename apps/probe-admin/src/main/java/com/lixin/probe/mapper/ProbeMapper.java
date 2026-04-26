package com.lixin.probe.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lixin.probe.entity.Probe;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 探针Mapper
 */
@Mapper
public interface ProbeMapper extends BaseMapper<Probe> {

    /**
     * 根据probeKey查询探针
     */
    @Select("SELECT * FROM probe WHERE probe_key = #{probeKey}")
    Probe selectByKey(@Param("probeKey") String probeKey);
}
