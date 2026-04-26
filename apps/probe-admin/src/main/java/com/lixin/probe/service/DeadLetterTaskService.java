package com.lixin.probe.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lixin.probe.entity.DeadLetterTask;

import java.util.Map;

public interface DeadLetterTaskService {

    void capture(com.lixin.probe.entity.SyncTask task, Exception failure);

    Page<DeadLetterTask> query(String status, int pageNum, int pageSize);

    void retry(Long id);

    void delete(Long id);

    void purgeExhausted();

    Map<String, Object> getStatistics();
}
