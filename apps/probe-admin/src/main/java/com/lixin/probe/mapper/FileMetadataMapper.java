package com.lixin.probe.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lixin.probe.entity.FileMetadata;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 文件元数据Mapper
 */
@Mapper
public interface FileMetadataMapper extends BaseMapper<FileMetadata> {

    /**
     * 删除指定探针的所有文件元数据
     * @param probeId 探针ID
     */
    @Delete("DELETE FROM file_metadata WHERE probe_id = #{probeId}")
    int deleteByProbeId(@Param("probeId") Long probeId);
}
