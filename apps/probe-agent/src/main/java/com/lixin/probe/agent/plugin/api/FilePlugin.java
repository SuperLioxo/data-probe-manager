package com.lixin.probe.agent.plugin.api;

import com.lixin.probe.agent.pojo.response.ProbeResponse;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 文件插件 SPI 接口
 * 所有文件插件必须实现此接口
 *
 * @author probe-agent
 * @since 1.0.0
 */
public interface FilePlugin {

    // ====== 插件元数据 ======

    /**
     * 获取插件ID（唯一标识）
     *
     * @return 插件ID，如 "local-file-plugin"
     */
    String getPluginId();

    /**
     * 获取插件名称
     *
     * @return 插件名称，如 "Local File Plugin"
     */
    String getName();

    /**
     * 获取插件类型
     *
     * @return 插件类型，如 "FILE"
     */
    String getType();

    /**
     * 获取插件版本
     *
     * @return 插件版本，如 "1.0.0"
     */
    String getVersion();

    /**
     * 获取插件描述
     *
     * @return 插件描述
     */
    String getDescription();

    // ====== 文件探针功能 ======

    /**
     * 扫描目录获取文件信息
     *
     * @param rootPath   根目录路径
     * @param config     扫描配置（扩展名过滤、深度限制等）
     * @return 文件数据响应
     */
    CompletableFuture<ProbeResponse.DataFile> scanDirectory(
        String rootPath,
        Map<String, Object> config
    );

    /**
     * 计算文件的MD5哈希值
     *
     * @param filePath 文件路径
     * @return MD5哈希值（32位小写十六进制字符串）
     */
    String calculateFileMD5(String filePath);

    /**
     * 获取文件扩展名
     *
     * @param fileName 文件名
     * @return 文件扩展名（不含点号，如 "pdf"）
     */
    String getFileExtension(String fileName);

    /**
     * 格式化文件大小
     *
     * @param size 文件大小（字节）
     * @return 格式化后的大小（如 "1.5 MB"）
     */
    String formatFileSize(long size);

    /**
     * 检查文件是否匹配扩展名过滤
     *
     * @param fileName       文件名
     * @param includeExtensions 包含的扩展名列表
     * @return 是否匹配
     */
    boolean matchesExtension(String fileName, String[] includeExtensions);

    /**
     * 检查路径是否应该被忽略
     *
     * @param path         文件路径
     * @param ignorePaths 忽略的路径模式列表
     * @return 是否应该忽略
     */
    boolean shouldIgnorePath(String path, String[] ignorePaths);
}
