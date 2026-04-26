package com.lixin.probe.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lixin.probe.common.Result;
import com.lixin.probe.entity.HttpApiSource;
import com.lixin.probe.mapper.HttpApiSourceMapper;
import com.lixin.probe.util.ControllerHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/http-api-sources")
public class HttpApiSourceController {

    @Autowired
    private HttpApiSourceMapper httpApiSourceMapper;

    @GetMapping
    public Result<Page<HttpApiSource>> list(
            @RequestParam(required = false) String name,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return ControllerHelper.safeGet(() -> {
            LambdaQueryWrapper<HttpApiSource> wrapper = new LambdaQueryWrapper<HttpApiSource>()
                    .like(name != null && !name.isEmpty(), HttpApiSource::getName, name)
                    .orderByDesc(HttpApiSource::getCreateTime);
            return httpApiSourceMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        }, "查询HTTP API数据源失败");
    }

    @GetMapping("/{id}")
    public Result<HttpApiSource> getById(@PathVariable Long id) {
        return ControllerHelper.safeGet(() -> {
            HttpApiSource source = httpApiSourceMapper.selectById(id);
            if (source == null) throw new IllegalArgumentException("HTTP API数据源不存在");
            return source;
        }, "查询HTTP API数据源失败");
    }

    @PostMapping
    public Result<String> create(@RequestBody HttpApiSource source) {
        return ControllerHelper.safeExecute(() -> {
            source.setCreateTime(LocalDateTime.now());
            source.setUpdateTime(LocalDateTime.now());
            source.setEnabled(true);
            httpApiSourceMapper.insert(source);
        }, "创建成功", "创建HTTP API数据源失败");
    }

    @PutMapping("/{id}")
    public Result<String> update(@PathVariable Long id, @RequestBody HttpApiSource source) {
        return ControllerHelper.safeExecute(() -> {
            source.setId(id);
            source.setUpdateTime(LocalDateTime.now());
            httpApiSourceMapper.updateById(source);
        }, "更新成功", "更新HTTP API数据源失败");
    }

    @DeleteMapping("/{id}")
    public Result<String> delete(@PathVariable Long id) {
        return ControllerHelper.safeExecute(() -> {
            httpApiSourceMapper.deleteById(id);
        }, "删除成功", "删除HTTP API数据源失败");
    }
}
