package com.lixin.probe.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lixin.probe.entity.DatabaseConnection;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 数据库连接Mapper
 *
 * @author Claude Code
 * @date 2026-03-26
 */
@Mapper
public interface DatabaseConnectionMapper extends BaseMapper<DatabaseConnection> {

    /**
     * 根据数据库类型查询启用的连接
     */
    @Select("SELECT * FROM database_connection WHERE database_type = #{databaseType} AND is_active = true ORDER BY name")
    List<DatabaseConnection> findByDatabaseType(@Param("databaseType") String databaseType);

    /**
     * 根据ID查询连接
     */
    @Select("SELECT * FROM database_connection WHERE id = #{id}")
    DatabaseConnection findById(@Param("id") Long id);
}
