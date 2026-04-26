package com.lixin.probe.agent.plugin.impl.protocol;

import com.lixin.probe.agent.plugin.api.FilePlugin;
import com.lixin.probe.agent.pojo.response.ProbeResponse;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class FtpPlugin implements FilePlugin {

    private static final Logger log = LoggerFactory.getLogger(FtpPlugin.class);

    @Override
    public String getPluginId() {
        return "ftp-file-plugin";
    }

    @Override
    public String getName() {
        return "FTP Plugin";
    }

    @Override
    public String getType() {
        return "FILE";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getDescription() {
        return "FTP/SFTP file system plugin for file scanning and metadata";
    }

    @Override
    public CompletableFuture<ProbeResponse.DataFile> scanDirectory(String rootPath, Map<String, Object> config) {
        return CompletableFuture.supplyAsync(() -> {
            FTPClient ftp;
            try {
                ftp = createClient(config);
            } catch (Exception e) {
                ProbeResponse.DataFile dataFile = new ProbeResponse.DataFile();
                dataFile.setSuccess(false);
                dataFile.setMsg(List.of("FTP connection failed: " + e.getMessage()));
                return dataFile;
            }
            try {
                ProbeResponse.DataFile dataFile = new ProbeResponse.DataFile();
                List<String> messages = new ArrayList<>();
                Map<String, ProbeResponse.DataFile.Directory> directories = new LinkedHashMap<>();
                long totalSize = 0;
                long totalFiles = 0;
                long totalDirs = 0;
                int maxDepth = config.containsKey("maxDepth") ? ((Number) config.get("maxDepth")).intValue() : 3;
                String[] includeExtensions = config.containsKey("includeExtensions")
                        ? ((String) config.get("includeExtensions")).split(",") : null;
                String[] ignorePaths = config.containsKey("ignorePaths")
                        ? ((String) config.get("ignorePaths")).split(",") : null;

                String path = rootPath != null && !rootPath.isEmpty() ? rootPath : ".";
                ProbeResponse.DataFile.Directory rootDir = scanDirectoryRecursive(
                        ftp, path, path, 0, maxDepth, includeExtensions, ignorePaths, messages);
                if (rootDir != null) {
                    directories.put(path, rootDir);
                    totalFiles = countFiles(rootDir);
                    totalDirs = countDirs(rootDir);
                    totalSize = sumSize(rootDir);
                }

                dataFile.setDirectories(directories);
                dataFile.setSuccess(true);
                dataFile.setMsg(messages);
                dataFile.setTotalDirectoryCount(totalDirs);
                dataFile.setTotalFileCount(totalFiles);
                dataFile.setTotalSize(totalSize);
                return dataFile;

            } catch (Exception e) {
                ProbeResponse.DataFile dataFile = new ProbeResponse.DataFile();
                dataFile.setSuccess(false);
                dataFile.setMsg(List.of("FTP scan failed: " + e.getMessage()));
                return dataFile;
            } finally {
                try { ftp.disconnect(); } catch (Exception ignored) {}
            }
        });
    }

    @Override
    public String calculateFileMD5(String filePath) {
        return ""; // FTP doesn't support local file MD5
    }

    @Override
    public String getFileExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(dot + 1).toLowerCase() : "";
    }

    @Override
    public String formatFileSize(long size) {
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return String.format("%.1f KB", size / 1024.0);
        if (size < 1024 * 1024 * 1024) return String.format("%.1f MB", size / (1024.0 * 1024));
        return String.format("%.1f GB", size / (1024.0 * 1024 * 1024));
    }

    @Override
    public boolean matchesExtension(String fileName, String[] includeExtensions) {
        if (includeExtensions == null || includeExtensions.length == 0) return true;
        String ext = getFileExtension(fileName);
        for (String allowed : includeExtensions) {
            if (allowed.trim().equalsIgnoreCase(ext)) return true;
        }
        return false;
    }

    @Override
    public boolean shouldIgnorePath(String path, String[] ignorePaths) {
        if (ignorePaths == null || ignorePaths.length == 0) return false;
        for (String pattern : ignorePaths) {
            if (path.contains(pattern.trim())) return true;
        }
        return false;
    }

    private ProbeResponse.DataFile.Directory scanDirectoryRecursive(
            FTPClient ftp, String currentPath, String displayPath,
            int depth, int maxDepth, String[] includeExtensions,
            String[] ignorePaths, List<String> messages) {

        if (depth > maxDepth) return null;

        try {
            FTPFile[] files = ftp.listFiles(currentPath);
            if (files == null) return null;

            ProbeResponse.DataFile.Directory dir = new ProbeResponse.DataFile.Directory();
            dir.setName(displayPath.isEmpty() ? "/" : displayPath);
            dir.setPath(List.of(currentPath));

            Map<String, ProbeResponse.DataFile.Directory> subDirs = new LinkedHashMap<>();
            Map<String, ProbeResponse.DataFile.File> fileMap = new LinkedHashMap<>();
            long dirSize = 0;
            int fileCount = 0;
            int dirCount = 0;

            for (FTPFile file : files) {
                String name = file.getName();
                if (".".equals(name) || "..".equals(name)) continue;
                String fullPath = currentPath.endsWith("/") ? currentPath + name : currentPath + "/" + name;

                if (shouldIgnorePath(fullPath, ignorePaths)) continue;

                if (file.isDirectory()) {
                    ProbeResponse.DataFile.Directory subDir = scanDirectoryRecursive(
                            ftp, fullPath, name, depth + 1, maxDepth,
                            includeExtensions, ignorePaths, messages);
                    if (subDir != null) {
                        subDirs.put(name, subDir);
                        dirSize += subDir.getSize() != null ? subDir.getSize() : 0;
                        fileCount += subDir.getFileCount() != null ? subDir.getFileCount() : 0;
                        dirCount += subDir.getDirectoryCount() != null ? subDir.getDirectoryCount() : 0;
                        dirCount++;
                    }
                } else if (file.isFile()) {
                    if (matchesExtension(name, includeExtensions)) {
                        ProbeResponse.DataFile.File f = new ProbeResponse.DataFile.File();
                        f.setName(name);
                        f.setSize(file.getSize());
                        f.setType(file.getType() == FTPFile.FILE_TYPE ? "file" : "other");
                        f.setExtension(getFileExtension(name));
                        f.setLastModified(file.getTimestamp().getTimeInMillis());
                        f.setPath(List.of(fullPath));
                        fileMap.put(name, f);
                        dirSize += file.getSize();
                        fileCount++;
                    }
                }
            }

            dir.setFiles(fileMap);
            dir.setDirectories(subDirs);
            dir.setSize(dirSize);
            dir.setFileCount((long) fileCount);
            dir.setDirectoryCount((long) dirCount);
            return dir;

        } catch (Exception e) {
            messages.add("Scan failed for " + currentPath + ": " + e.getMessage());
            return null;
        }
    }

    private long countFiles(ProbeResponse.DataFile.Directory dir) {
        long count = dir.getFileCount() != null ? dir.getFileCount() : 0;
        if (dir.getDirectories() != null) {
            for (ProbeResponse.DataFile.Directory sub : dir.getDirectories().values()) {
                count += countFiles(sub);
            }
        }
        return count;
    }

    private long countDirs(ProbeResponse.DataFile.Directory dir) {
        long count = dir.getDirectoryCount() != null ? dir.getDirectoryCount() : 0;
        if (dir.getDirectories() != null) {
            for (ProbeResponse.DataFile.Directory sub : dir.getDirectories().values()) {
                count += countDirs(sub);
            }
        }
        return count;
    }

    private long sumSize(ProbeResponse.DataFile.Directory dir) {
        long size = dir.getSize() != null ? dir.getSize() : 0;
        if (dir.getDirectories() != null) {
            for (ProbeResponse.DataFile.Directory sub : dir.getDirectories().values()) {
                size += sumSize(sub);
            }
        }
        return size;
    }

    private FTPClient createClient(Map<String, Object> config) throws Exception {
        String host = (String) config.get("host");
        int port = (Integer) config.getOrDefault("port", 21);
        String username = (String) config.getOrDefault("username", "anonymous");
        String password = (String) config.getOrDefault("password", "");

        FTPClient ftp = new FTPClient();
        ftp.connect(host, port);
        ftp.login(username, password);
        ftp.setConnectTimeout(10000);
        ftp.setDataTimeout(30000);
        ftp.enterLocalPassiveMode();
        ftp.setFileType(FTP.BINARY_FILE_TYPE);
        return ftp;
    }
}
