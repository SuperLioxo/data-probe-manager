package com.lixin.probe.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lixin.probe.entity.DatabaseMetadata;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 数据库元数据Mapper接口
 */
@Mapper
public interface DatabaseMetadataMapper extends BaseMapper<DatabaseMetadata> {

    /**
     * 根据probeKey查询数据库元数据
     *
     * @param probeKey 探针KEY
     * @return 数据库元数据
     */
    DatabaseMetadata selectByProbeKey(@Param("probeKey") String probeKey);

    /**
     * 根据probeKey和databaseName查询数据库元数据
     *
     * @param probeKey 探针KEY
     * @param databaseName 数据库名称
     * @return 数据库元数据
     */
    DatabaseMetadata selectByProbeKeyAndDatabase(@Param("probeKey") String probeKey, @Param("databaseName") String databaseName);
}
