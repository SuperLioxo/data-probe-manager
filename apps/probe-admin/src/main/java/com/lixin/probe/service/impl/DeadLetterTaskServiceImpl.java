package com.lixin.probe.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lixin.probe.entity.DeadLetterTask;
import com.lixin.probe.entity.SyncTask;
import com.lixin.probe.mapper.DeadLetterTaskMapper;
import com.lixin.probe.mapper.SyncTaskMapper;
import com.lixin.probe.service.DeadLetterTaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
public class DeadLetterTaskServiceImpl implements DeadLetterTaskService {

    @Autowired
    private DeadLetterTaskMapper deadLetterTaskMapper;

    @Autowired
    private SyncTaskMapper syncTaskMapper;

    @Lazy
    @Autowired
    private com.lixin.probe.service.SyncTaskService syncTaskService;

    @Override
    public void capture(SyncTask task, Exception failure) {
        DeadLetterTask dlt = DeadLetterTask.builder()
                .originalTaskId(task.getId())
                .taskName(task.getTaskName())
                .sourceProbeKey(task.getSourceProbeKey())
                .sourceTableName(task.getSourceTableName())
                .targetType(task.getTargetType())
                .targetConfig(task.getTargetConfig())
                .syncMode(task.getSyncMode())
                .failureReason(truncate(failure.getMessage(), 500))
                .failureStack(truncate(getStackTrace(failure), 2000))
                .retryCount(0)
                .maxRetries(3)
                .status("PENDING")
                .nextRetryTime(LocalDateTime.now().plusMinutes(1))
                .createTime(LocalDateTime.now())
                .build();
        deadLetterTaskMapper.insert(dlt);
        log.info("[DeadLetter] Captured failed task: taskId={}, name={}", task.getId(), task.getTaskName());
    }

    @Override
    public Page<DeadLetterTask> query(String status, int pageNum, int pageSize) {
        LambdaQueryWrapper<DeadLetterTask> wrapper = new LambdaQueryWrapper<DeadLetterTask>()
                .eq(status != null && !status.isEmpty(), DeadLetterTask::getStatus, status)
                .orderByDesc(DeadLetterTask::getCreateTime);
        return deadLetterTaskMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    @Override
    public void retry(Long id) {
        DeadLetterTask dlt = deadLetterTaskMapper.selectById(id);
        if (dlt == null) throw new IllegalArgumentException("死信任务不存在: " + id);

        if (!"PENDING".equals(dlt.getStatus()) && !"RETRYING".equals(dlt.getStatus())) {
            throw new IllegalStateException("只能重试PENDING或RETRYING状态的任务");
        }

        SyncTask original = syncTaskMapper.selectById(dlt.getOriginalTaskId());
        if (original == null) {
            deadLetterTaskMapper.deleteById(id);
            log.warn("[DeadLetter] Original task {} not found, removing dead letter", dlt.getOriginalTaskId());
            return;
        }

        try {
            deadLetterTaskMapper.update(null, new LambdaUpdateWrapper<DeadLetterTask>()
                    .eq(DeadLetterTask::getId, id)
                    .set(DeadLetterTask::getStatus, "RETRYING")
                    .set(DeadLetterTask::getLastRetryTime, LocalDateTime.now()));

            syncTaskService.executeSync(original);

            deadLetterTaskMapper.update(null, new LambdaUpdateWrapper<DeadLetterTask>()
                    .eq(DeadLetterTask::getId, id)
                    .set(DeadLetterTask::getStatus, "RESOLVED")
                    .set(DeadLetterTask::getLastRetryTime, LocalDateTime.now()));
            log.info("[DeadLetter] Retry succeeded: dltId={}", id);

        } catch (Exception e) {
            int newCount = dlt.getRetryCount() + 1;
            if (newCount >= dlt.getMaxRetries()) {
                deadLetterTaskMapper.update(null, new LambdaUpdateWrapper<DeadLetterTask>()
                        .eq(DeadLetterTask::getId, id)
                        .set(DeadLetterTask::getRetryCount, newCount)
                        .set(DeadLetterTask::getStatus, "EXHAUSTED")
                        .set(DeadLetterTask::getLastRetryTime, LocalDateTime.now()));
                log.warn("[DeadLetter] Retry exhausted: dltId={}, retries={}", id, newCount);
            } else {
                long delayMinutes = (long) Math.min(Math.pow(2, newCount), 30);
                deadLetterTaskMapper.update(null, new LambdaUpdateWrapper<DeadLetterTask>()
                        .eq(DeadLetterTask::getId, id)
                        .set(DeadLetterTask::getRetryCount, newCount)
                        .set(DeadLetterTask::getStatus, "PENDING")
                        .set(DeadLetterTask::getNextRetryTime, LocalDateTime.now().plusMinutes(delayMinutes))
                        .set(DeadLetterTask::getLastRetryTime, LocalDateTime.now())
                        .set(DeadLetterTask::getFailureReason, truncate(e.getMessage(), 500)));
                log.info("[DeadLetter] Retry failed, will retry in {}min: dltId={}", delayMinutes, id);
            }
        }
    }

    @Override
    public void delete(Long id) {
        deadLetterTaskMapper.deleteById(id);
    }

    @Override
    public void purgeExhausted() {
        LambdaQueryWrapper<DeadLetterTask> wrapper = new LambdaQueryWrapper<DeadLetterTask>()
                .eq(DeadLetterTask::getStatus, "EXHAUSTED")
                .lt(DeadLetterTask::getCreateTime, LocalDateTime.now().minusDays(7));
        int count = deadLetterTaskMapper.delete(wrapper);
        if (count > 0) {
            log.info("[DeadLetter] Purged {} exhausted tasks older than 7 days", count);
        }
    }

    @Scheduled(fixedRate = 300000)
    public void autoRetryPending() {
        LambdaQueryWrapper<DeadLetterTask> wrapper = new LambdaQueryWrapper<DeadLetterTask>()
                .in(DeadLetterTask::getStatus, "PENDING", "RETRYING")
                .le(DeadLetterTask::getNextRetryTime, LocalDateTime.now());
        var tasks = deadLetterTaskMapper.selectList(wrapper);
        for (DeadLetterTask task : tasks) {
            try {
                retry(task.getId());
            } catch (Exception e) {
                log.warn("[DeadLetter] Auto-retry failed: dltId={}", task.getId());
            }
        }
    }

    @Override
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", deadLetterTaskMapper.selectCount(null));
        stats.put("pending", deadLetterTaskMapper.selectCount(
                new LambdaQueryWrapper<DeadLetterTask>().eq(DeadLetterTask::getStatus, "PENDING")));
        stats.put("retrying", deadLetterTaskMapper.selectCount(
                new LambdaQueryWrapper<DeadLetterTask>().eq(DeadLetterTask::getStatus, "RETRYING")));
        stats.put("exhausted", deadLetterTaskMapper.selectCount(
                new LambdaQueryWrapper<DeadLetterTask>().eq(DeadLetterTask::getStatus, "EXHAUSTED")));
        stats.put("resolved", deadLetterTaskMapper.selectCount(
                new LambdaQueryWrapper<DeadLetterTask>().eq(DeadLetterTask::getStatus, "RESOLVED")));
        return stats;
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) : s;
    }

    private String getStackTrace(Throwable t) {
        StringBuilder sb = new StringBuilder();
        sb.append(t.toString()).append("\n");
        for (StackTraceElement e : t.getStackTrace()) {
            sb.append("  at ").append(e.toString()).append("\n");
            if (sb.length() > 2000) break;
        }
        return sb.toString();
    }
}
