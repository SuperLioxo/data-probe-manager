package com.lixin.probe.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lixin.probe.entity.FileMetadata;

import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

public interface FileUploadService {

    FileMetadata uploadFile(MultipartFile file, String probeKey, String category);

    Page<FileMetadata> getFileList(String probeKey, String fileName, int pageNum, int pageSize);

    void deleteFile(Long id);

    Map<String, Object> getStatistics(String probeKey);
}
