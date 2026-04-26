package com.lixin.probe.service;

import java.io.IOException;
import java.util.List;

/**
 * 探针导出服务接口
 * 提供批量导出功能
 *
 * @author Claude Code
 * @date 2026-04-12
 */
public interface ProbeExportService {

    /**
     * 流式导出探针列表为Excel
     * 支持大数据量导出
     *
     * @param probeIds 探针ID列表
     * @return Excel文件字节数组
     * @throws IOException IO异常
     */
    byte[] exportProbesToExcelStream(List<Long> probeIds) throws IOException;
}
