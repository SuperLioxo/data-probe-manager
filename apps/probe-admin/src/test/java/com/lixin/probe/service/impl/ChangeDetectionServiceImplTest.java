package com.lixin.probe.service.impl;

import com.lixin.probe.entity.DataSnapshot;
import com.lixin.probe.entity.ChangeLog;
import com.lixin.probe.mapper.DataSnapshotMapper;
import com.lixin.probe.mapper.ChangeLogMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = Strictness.LENIENT)
class ChangeDetectionServiceImplTest {

    @Mock
    private DataSnapshotMapper snapshotMapper;

    @Mock
    private ChangeLogMapper changeLogMapper;

    @Mock
    private com.lixin.probe.service.ChangeAlertService changeAlertService;

    @InjectMocks
    private ChangeDetectionServiceImpl changeDetectionService;

    private final AtomicLong idSeq = new AtomicLong(100);

    @BeforeEach
    void setUp() {
        idSeq.set(100);
        when(snapshotMapper.insert(any(DataSnapshot.class))).thenAnswer(inv -> {
            DataSnapshot s = inv.getArgument(0);
            s.setId(idSeq.getAndIncrement());
            return 1;
        });
        when(changeLogMapper.insert(any(ChangeLog.class))).thenReturn(1);
    }

    @Test
    @DisplayName("行数增加应生成ROW_INSERT变化")
    void testSaveSnapshot_rowCountIncreased_shouldCreateInsertChange() {
        when(snapshotMapper.selectLatest(anyString(), anyString(), anyInt()))
                .thenAnswer(inv -> {
                    long newId = idSeq.get() - 1;
                    return List.of(
                            DataSnapshot.builder().id(newId).probeKey("probe-1").tableName("orders")
                                    .rowCount(100L).dataSize(1024L).indexSize(512L).build(),
                            DataSnapshot.builder().id(newId - 1).probeKey("probe-1").tableName("orders")
                                    .rowCount(80L).dataSize(1024L).indexSize(512L).build()
                    );
                });

        List<ChangeLog> changes = changeDetectionService.saveSnapshotAndDetect(
                "probe-1", "test_db", "orders", 100L, 1024L, 512L, "2026-04-17 10:00:00");

        assertTrue(changes.stream().anyMatch(c -> "ROW_INSERT".equals(c.getChangeType())),
                "Should create ROW_INSERT change log");
    }

    @Test
    @DisplayName("行数减少应生成ROW_DELETE变化")
    void testSaveSnapshot_rowCountDecreased_shouldCreateDeleteChange() {
        when(snapshotMapper.selectLatest(anyString(), anyString(), anyInt()))
                .thenAnswer(inv -> {
                    long newId = idSeq.get() - 1;
                    return List.of(
                            DataSnapshot.builder().id(newId).probeKey("probe-1").tableName("orders")
                                    .rowCount(80L).dataSize(1024L).indexSize(512L).build(),
                            DataSnapshot.builder().id(newId - 1).probeKey("probe-1").tableName("orders")
                                    .rowCount(100L).dataSize(1024L).indexSize(512L).build()
                    );
                });

        List<ChangeLog> changes = changeDetectionService.saveSnapshotAndDetect(
                "probe-1", "test_db", "orders", 80L, 1024L, 512L, "2026-04-17 12:00:00");

        assertTrue(changes.stream().anyMatch(c -> "ROW_DELETE".equals(c.getChangeType())),
                "Should create ROW_DELETE change log");
    }

    @Test
    @DisplayName("行数不变时不应生成行数变化")
    void testSaveSnapshot_sameRowCount_shouldNotCreateRowCountChange() {
        when(snapshotMapper.selectLatest(anyString(), anyString(), anyInt()))
                .thenAnswer(inv -> {
                    long newId = idSeq.get() - 1;
                    return List.of(
                            DataSnapshot.builder().id(newId).probeKey("probe-1").tableName("orders")
                                    .rowCount(100L).dataSize(1024L).indexSize(512L).build(),
                            DataSnapshot.builder().id(newId - 1).probeKey("probe-1").tableName("orders")
                                    .rowCount(100L).dataSize(1024L).indexSize(512L).build()
                    );
                });

        List<ChangeLog> changes = changeDetectionService.saveSnapshotAndDetect(
                "probe-1", "test_db", "orders", 100L, 1024L, 512L, "2026-04-17 12:00:00");

        assertFalse(changes.stream().anyMatch(c -> "ROW_INSERT".equals(c.getChangeType()) || "ROW_DELETE".equals(c.getChangeType())),
                "Should NOT create row count change when count is same");
    }

    @Test
    @DisplayName("数据大小变化应生成SIZE_CHANGE")
    void testSaveSnapshot_sizeChanged_shouldCreateSizeChange() {
        when(snapshotMapper.selectLatest(anyString(), anyString(), anyInt()))
                .thenAnswer(inv -> {
                    long newId = idSeq.get() - 1;
                    return List.of(
                            DataSnapshot.builder().id(newId).probeKey("probe-1").tableName("orders")
                                    .rowCount(100L).dataSize(2048L).indexSize(512L).build(),
                            DataSnapshot.builder().id(newId - 1).probeKey("probe-1").tableName("orders")
                                    .rowCount(100L).dataSize(1024L).indexSize(512L).build()
                    );
                });

        List<ChangeLog> changes = changeDetectionService.saveSnapshotAndDetect(
                "probe-1", "test_db", "orders", 100L, 2048L, 512L, "2026-04-17 12:00:00");

        assertTrue(changes.stream().anyMatch(c -> "SIZE_CHANGE".equals(c.getChangeType())),
                "Should create SIZE_CHANGE change log");
    }

    @Test
    @DisplayName("无checksum快照不应生成CHECKSUM_CHANGE")
    void testSaveSnapshot_noChecksum_shouldNotCreateChecksumChange() {
        when(snapshotMapper.selectLatest(anyString(), anyString(), anyInt()))
                .thenAnswer(inv -> {
                    long newId = idSeq.get() - 1;
                    return List.of(
                            DataSnapshot.builder().id(newId).probeKey("probe-1").tableName("orders")
                                    .rowCount(100L).dataSize(1024L).indexSize(512L).build(),
                            DataSnapshot.builder().id(newId - 1).probeKey("probe-1").tableName("orders")
                                    .rowCount(100L).dataSize(1024L).indexSize(512L).build()
                    );
                });

        List<ChangeLog> changes = changeDetectionService.saveSnapshotAndDetect(
                "probe-1", "test_db", "orders", 100L, 1024L, 512L, "2026-04-17 12:00:00");

        assertFalse(changes.stream().anyMatch(c -> "CHECKSUM_CHANGE".equals(c.getChangeType())),
                "Should NOT create CHECKSUM_CHANGE when checksums are not set");
    }

    @Test
    @DisplayName("空快照列表应返回空变化")
    void testGetLatestSnapshots_shouldReturnEmpty() {
        when(snapshotMapper.selectLatest(anyString(), anyString(), anyInt()))
                .thenReturn(List.of());

        List<DataSnapshot> result = changeDetectionService.getLatestSnapshots("probe-1", "orders", 5);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("索引大小变化应生成INDEX_SIZE_CHANGE")
    void testSaveSnapshot_indexSizeChanged_shouldCreateIndexSizeChange() {
        when(snapshotMapper.selectLatest(anyString(), anyString(), anyInt()))
                .thenAnswer(inv -> {
                    long newId = idSeq.get() - 1;
                    return List.of(
                            DataSnapshot.builder().id(newId).probeKey("probe-1").tableName("orders")
                                    .rowCount(100L).dataSize(1024L).indexSize(1024L).build(),
                            DataSnapshot.builder().id(newId - 1).probeKey("probe-1").tableName("orders")
                                    .rowCount(100L).dataSize(1024L).indexSize(512L).build()
                    );
                });

        List<ChangeLog> changes = changeDetectionService.saveSnapshotAndDetect(
                "probe-1", "test_db", "orders", 100L, 1024L, 1024L, "2026-04-17 12:00:00");

        assertTrue(changes.stream().anyMatch(c -> "INDEX_SIZE_CHANGE".equals(c.getChangeType())));
    }

    @Test
    @DisplayName("checksum变化且行数不变应生成CHECKSUM_CHANGE (via redetect)")
    void testRedetect_checksumChangedSameRows_shouldCreateChecksumChange() {
        // Set up snapshots for redetectFromLatestSnapshots
        DataSnapshot latest = DataSnapshot.builder().id(1L).probeKey("probe-1")
                .databaseName("test_db").tableName("orders")
                .rowCount(100L).dataSize(1024L).indexSize(512L)
                .dataChecksum("abc123").snapshotTime(java.time.LocalDateTime.now()).build();
        DataSnapshot previous = DataSnapshot.builder().id(2L).probeKey("probe-1")
                .databaseName("test_db").tableName("orders")
                .rowCount(100L).dataSize(1024L).indexSize(512L)
                .dataChecksum("def456").snapshotTime(java.time.LocalDateTime.now().minusHours(1)).build();

        when(snapshotMapper.selectList(any())).thenReturn(List.of(latest, previous));
        when(changeLogMapper.insert(any(ChangeLog.class))).thenReturn(1);

        List<ChangeLog> changes = changeDetectionService.redetectFromLatestSnapshots("probe-1");

        assertTrue(changes.stream().anyMatch(c -> "CHECKSUM_CHANGE".equals(c.getChangeType())),
                "Should create CHECKSUM_CHANGE when checksum differs but row count is same");
    }

    @Test
    @DisplayName("更新时间变化应生成DATA_UPDATE")
    void testSaveSnapshot_updateTimeChanged_shouldCreateDataUpdate() {
        when(snapshotMapper.selectLatest(anyString(), anyString(), anyInt()))
                .thenAnswer(inv -> {
                    long newId = idSeq.get() - 1;
                    return List.of(
                            DataSnapshot.builder().id(newId).probeKey("probe-1").tableName("orders")
                                    .rowCount(100L).dataSize(1024L).indexSize(512L)
                                    .maxUpdateTime("2026-04-17 12:00:00").build(),
                            DataSnapshot.builder().id(newId - 1).probeKey("probe-1").tableName("orders")
                                    .rowCount(100L).dataSize(1024L).indexSize(512L)
                                    .maxUpdateTime("2026-04-17 10:00:00").build()
                    );
                });

        List<ChangeLog> changes = changeDetectionService.saveSnapshotAndDetect(
                "probe-1", "test_db", "orders", 100L, 1024L, 512L, "2026-04-17 12:00:00");

        assertTrue(changes.stream().anyMatch(c -> "DATA_UPDATE".equals(c.getChangeType())));
    }

    @Test
    @DisplayName("首次快照无对比应返回空变化")
    void testSaveSnapshot_firstSnapshot_shouldReturnNoChanges() {
        // Only the just-inserted snapshot is returned (size == 1)
        when(snapshotMapper.selectLatest(anyString(), anyString(), anyInt()))
                .thenAnswer(inv -> {
                    long newId = idSeq.get() - 1;
                    return List.of(
                            DataSnapshot.builder().id(newId).probeKey("probe-1").tableName("orders")
                                    .rowCount(100L).dataSize(1024L).indexSize(512L).build()
                    );
                });

        List<ChangeLog> changes = changeDetectionService.saveSnapshotAndDetect(
                "probe-1", "test_db", "orders", 100L, 1024L, 512L, "2026-04-17 12:00:00");

        assertTrue(changes.isEmpty(), "First snapshot should have no changes");
    }

    @Test
    @DisplayName("变化统计应返回正确结构")
    void testGetChangeStatistics_shouldReturnCorrectStructure() {
        when(changeLogMapper.selectCount(any())).thenReturn(10L);
        ChangeLog log1 = ChangeLog.builder().changeType("ROW_INSERT").tableName("orders").detectedTime(java.time.LocalDateTime.now()).build();
        ChangeLog log2 = ChangeLog.builder().changeType("SIZE_CHANGE").tableName("users").detectedTime(java.time.LocalDateTime.now()).build();
        when(changeLogMapper.selectList(any())).thenReturn(List.of(log1, log2));

        java.util.Map<String, Object> stats = changeDetectionService.getChangeStatistics("probe-1");

        assertNotNull(stats);
        assertEquals(10L, stats.get("totalChanges"));
        assertNotNull(stats.get("byType"));
        assertEquals(2, stats.get("affectedTables"));
    }
}
