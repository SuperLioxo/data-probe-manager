package com.lixin.probe.service.impl;

import com.lixin.probe.service.ProbeExportService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

/**
 * 探针导出服务实现
 * 临时禁用导出功能以修复编译问题
 */
@Service
@Slf4j
public class ProbeExportServiceImpl implements ProbeExportService {

    @Override
    public byte[] exportProbesToExcelStream(List<Long> probeIds) throws IOException {
        log.warn("Excel导出功能暂时禁用（POI依赖问题），请使用其他导出方式");
        return new byte[0];
    }
}
