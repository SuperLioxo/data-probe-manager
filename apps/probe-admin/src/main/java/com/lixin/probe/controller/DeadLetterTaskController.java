package com.lixin.probe.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lixin.probe.common.Result;
import com.lixin.probe.entity.DeadLetterTask;
import com.lixin.probe.service.DeadLetterTaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/dead-letter-tasks")
public class DeadLetterTaskController {

    @Autowired
    private DeadLetterTaskService deadLetterTaskService;

    @GetMapping
    public Result<Page<DeadLetterTask>> list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.success(deadLetterTaskService.query(status, pageNum, pageSize));
    }

    @PostMapping("/{id}/retry")
    public Result<Void> retry(@PathVariable Long id) {
        deadLetterTaskService.retry(id);
        return Result.success(null);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        deadLetterTaskService.delete(id);
        return Result.success(null);
    }

    @DeleteMapping("/purge")
    public Result<Void> purge() {
        deadLetterTaskService.purgeExhausted();
        return Result.success(null);
    }

    @GetMapping("/statistics")
    public Result<Map<String, Object>> statistics() {
        return Result.success(deadLetterTaskService.getStatistics());
    }
}
