package com.lixin.probe.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lixin.probe.entity.DataSnapshot;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DataSnapshotMapper extends BaseMapper<DataSnapshot> {

    @Select("SELECT * FROM data_snapshot " +
            "WHERE probe_key = #{probeKey} AND table_name = #{tableName} " +
            "ORDER BY snapshot_time DESC LIMIT #{limit}")
    List<DataSnapshot> selectLatest(@Param("probeKey") String probeKey,
                                     @Param("tableName") String tableName,
                                     @Param("limit") int limit);
}
