package com.lixin.probe.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lixin.probe.common.Result;
import com.lixin.probe.entity.FileMetadata;
import com.lixin.probe.service.FileUploadService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/files")
public class FileUploadController {

    @Autowired
    private FileUploadService fileUploadService;

    /**
     * 上传单个文件
     */
    @PostMapping("/upload")
    public Result<FileMetadata> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "probeKey", required = false) String probeKey,
            @RequestParam(value = "category", required = false) String category) {
        try {
            if (file.isEmpty()) {
                return Result.badRequest("上传文件不能为空");
            }
            FileMetadata metadata = fileUploadService.uploadFile(file, probeKey, category);
            return Result.success("上传成功", metadata);
        } catch (Exception e) {
            return Result.error("上传失败: " + e.getMessage());
        }
    }

    /**
     * 批量上传
     */
    @PostMapping("/upload/batch")
    public Result<java.util.List<FileMetadata>> uploadBatch(
            @RequestParam("files") MultipartFile[] files,
            @RequestParam(value = "probeKey", required = false) String probeKey,
            @RequestParam(value = "category", required = false) String category) {
        try {
            java.util.List<FileMetadata> results = new java.util.ArrayList<>();
            for (MultipartFile file : files) {
                if (!file.isEmpty()) {
                    results.add(fileUploadService.uploadFile(file, probeKey, category));
                }
            }
            return Result.success("批量上传成功", results);
        } catch (Exception e) {
            return Result.error("批量上传失败: " + e.getMessage());
        }
    }

    /**
     * 查询已上传的文件列表
     */
    @GetMapping
    public Result<Page<FileMetadata>> list(
            @RequestParam(required = false) String probeKey,
            @RequestParam(required = false) String fileName,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        try {
            return Result.success(fileUploadService.getFileList(probeKey, fileName, pageNum, pageSize));
        } catch (Exception e) {
            return Result.error("查询文件列表失败: " + e.getMessage());
        }
    }

    /**
     * 删除文件
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        try {
            fileUploadService.deleteFile(id);
            return Result.success();
        } catch (Exception e) {
            return Result.error("删除失败: " + e.getMessage());
        }
    }

    /**
     * 获取上传统计
     */
    @GetMapping("/statistics")
    public Result<java.util.Map<String, Object>> statistics(
            @RequestParam(required = false) String probeKey) {
        try {
            return Result.success(fileUploadService.getStatistics(probeKey));
        } catch (Exception e) {
            return Result.error("查询统计失败: " + e.getMessage());
        }
    }
}
