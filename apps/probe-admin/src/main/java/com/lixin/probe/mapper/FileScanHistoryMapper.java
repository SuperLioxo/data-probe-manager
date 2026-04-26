package com.lixin.probe.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lixin.probe.entity.FileScanHistory;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文件扫描历史Mapper
 */
@Mapper
public interface FileScanHistoryMapper extends BaseMapper<FileScanHistory> {
}
