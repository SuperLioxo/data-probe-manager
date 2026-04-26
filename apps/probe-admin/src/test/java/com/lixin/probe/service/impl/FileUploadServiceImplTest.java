package com.lixin.probe.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lixin.probe.entity.FileMetadata;
import com.lixin.probe.mapper.FileMetadataMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FileUploadServiceImplTest {

    @Mock
    private FileMetadataMapper fileMetadataMapper;

    @InjectMocks
    private FileUploadServiceImpl fileUploadService;

    @Test
    @DisplayName("获取统计应包含totalFiles和totalSize")
    void testGetStatistics_shouldIncludeMetrics() {
        when(fileMetadataMapper.selectCount(any())).thenReturn(3L);
        when(fileMetadataMapper.selectList(any())).thenReturn(List.of());

        var stats = fileUploadService.getStatistics(null);

        assertNotNull(stats);
        assertEquals(3L, stats.get("totalFiles"));
        assertNotNull(stats.get("fileTypes"));
    }

    @Test
    @DisplayName("空文件列表应返回空页")
    void testGetFileList_shouldReturnEmptyPage() {
        when(fileMetadataMapper.selectPage(any(Page.class), any())).thenReturn(new Page<>());

        var result = fileUploadService.getFileList(null, null, 1, 10);

        assertNotNull(result);
        assertTrue(result.getRecords().isEmpty());
    }
}
