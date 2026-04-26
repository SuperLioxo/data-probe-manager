package com.lixin.probe.agent.pojo.request;

import com.lixin.probe.agent.constant.ProbeContent;

import java.util.List;
import java.util.Map;

/**
 * 探针请求对象
 * 用于封装向探针发送的请求参数
 */
public class ProbeRequest {

    /**
     * 请求ID
     */
    private Long id;

    /**
     * 探针编码
     */
    private String code;

    /**
     * 探查内容
     * 可使用 ProbeContent 枚举值进行位运算组合
     */
    private Integer content;

    /**
     * 数据库配置
     */
    private DatabaseConfig database;

    /**
     * 文件扫描配置
     */
    private FileScanConfig fileScan;

    /**
     * 其他参数
     */
    private Map<String, Object> params;

    // Manual builder pattern
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Long id;
        private String code;
        private Integer content;
        private DatabaseConfig database;
        private FileScanConfig fileScan;
        private Map<String, Object> params;

        public Builder id(Long id) {
            this.id = id;
            return this;
        }

        public Builder code(String code) {
            this.code = code;
            return this;
        }

        public Builder content(Integer content) {
            this.content = content;
            return this;
        }

        public Builder database(DatabaseConfig database) {
            this.database = database;
            return this;
        }

        public Builder fileScan(FileScanConfig fileScan) {
            this.fileScan = fileScan;
            return this;
        }

        public Builder params(Map<String, Object> params) {
            this.params = params;
            return this;
        }

        public ProbeRequest build() {
            ProbeRequest request = new ProbeRequest();
            request.id = this.id;
            request.code = this.code;
            request.content = this.content;
            request.database = this.database;
            request.fileScan = this.fileScan;
            request.params = this.params;
            return request;
        }
    }

    // Constructors
    public ProbeRequest() {}

    public ProbeRequest(Long id, String code, Integer content, DatabaseConfig database, FileScanConfig fileScan, Map<String, Object> params) {
        this.id = id;
        this.code = code;
        this.content = content;
        this.database = database;
        this.fileScan = fileScan;
        this.params = params;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public Integer getContent() { return content; }
    public void setContent(Integer content) { this.content = content; }

    public DatabaseConfig getDatabase() { return database; }
    public void setDatabase(DatabaseConfig database) { this.database = database; }

    public FileScanConfig getFileScan() { return fileScan; }
    public void setFileScan(FileScanConfig fileScan) { this.fileScan = fileScan; }

    public Map<String, Object> getParams() { return params; }
    public void setParams(Map<String, Object> params) { this.params = params; }

    /**
     * 数据库配置
     */
    public static class DatabaseConfig {
        /**
         * 探针唯一标识符
         */
        private String probeKey;

        /**
         * 数据库类型: MySQL, PostgreSQL, Oracle, SQLServer, DM, SQLite
         */
        private String type;

        /**
         * 数据库版本
         */
        private String version;

        /**
         * 主机地址
         */
        private String host;

        /**
         * 端口
         */
        private Integer port;

        /**
         * 数据库名称
         */
        private String name;

        /**
         * 用户名
         */
        private String username;

        /**
         * 密码
         */
        private String password;

        /**
         * Schema 列表（部分数据库需要）
         */
        private List<String> schemas;

        /**
         * 表名过滤（可选）
         */
        private List<String> tables;

        /**
         * 是否获取示例数据
         */
        private Boolean fetchExampleData;

        /**
         * 示例数据行数
         */
        private Integer exampleRowCount;

        // Manual builder pattern
        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String probeKey;
            private String type;
            private String version;
            private String host;
            private Integer port;
            private String name;
            private String username;
            private String password;
            private List<String> schemas;
            private List<String> tables;
            private Boolean fetchExampleData;
            private Integer exampleRowCount;

            public Builder probeKey(String probeKey) {
                this.probeKey = probeKey;
                return this;
            }

            public Builder type(String type) {
                this.type = type;
                return this;
            }

            public Builder version(String version) {
                this.version = version;
                return this;
            }

            public Builder host(String host) {
                this.host = host;
                return this;
            }

            public Builder port(Integer port) {
                this.port = port;
                return this;
            }

            public Builder name(String name) {
                this.name = name;
                return this;
            }

            public Builder username(String username) {
                this.username = username;
                return this;
            }

            public Builder password(String password) {
                this.password = password;
                return this;
            }

            public Builder schemas(List<String> schemas) {
                this.schemas = schemas;
                return this;
            }

            public Builder tables(List<String> tables) {
                this.tables = tables;
                return this;
            }

            public Builder fetchExampleData(Boolean fetchExampleData) {
                this.fetchExampleData = fetchExampleData;
                return this;
            }

            public Builder exampleRowCount(Integer exampleRowCount) {
                this.exampleRowCount = exampleRowCount;
                return this;
            }

            public DatabaseConfig build() {
                DatabaseConfig config = new DatabaseConfig();
                config.probeKey = this.probeKey;
                config.type = this.type;
                config.version = this.version;
                config.host = this.host;
                config.port = this.port;
                config.name = this.name;
                config.username = this.username;
                config.password = this.password;
                config.schemas = this.schemas;
                config.tables = this.tables;
                config.fetchExampleData = this.fetchExampleData;
                config.exampleRowCount = this.exampleRowCount;
                return config;
            }
        }

        // Getters and Setters
        public String getProbeKey() { return probeKey; }
        public void setProbeKey(String probeKey) { this.probeKey = probeKey; }

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }

        public String getHost() { return host; }
        public void setHost(String host) { this.host = host; }

        public Integer getPort() { return port; }
        public void setPort(Integer port) { this.port = port; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }

        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }

        public List<String> getSchemas() { return schemas; }
        public void setSchemas(List<String> schemas) { this.schemas = schemas; }

        public List<String> getTables() { return tables; }
        public void setTables(List<String> tables) { this.tables = tables; }

        public Boolean getFetchExampleData() { return fetchExampleData; }
        public void setFetchExampleData(Boolean fetchExampleData) { this.fetchExampleData = fetchExampleData; }

        public Integer getExampleRowCount() { return exampleRowCount; }
        public void setExampleRowCount(Integer exampleRowCount) { this.exampleRowCount = exampleRowCount; }
    }

    /**
     * 文件扫描配置
     */
    public static class FileScanConfig {
        /**
         * 扫描路径列表
         */
        private List<String> scanPaths;

        /**
         * 文件扩展名过滤（可选）
         * 例如: [".log", ".txt"]
         */
        private List<String> fileExtensions;

        /**
         * 是否递归扫描
         */
        private Boolean recursive;

        /**
         * 最大扫描深度
         */
        private Integer maxDepth;

        /**
         * 最小文件大小（字节）
         */
        private Long minFileSize;

        /**
         * 最大文件大小（字节）
         */
        private Long maxFileSize;

        /**
         * 是否计算 MD5
         */
        private Boolean calculateMD5;

        /**
         * 是否包含隐藏文件
         */
        private Boolean includeHidden;

        /**
         * 排除目录模式
         */
        private List<String> excludePatterns;

        // Manual builder pattern
        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private List<String> scanPaths;
            private List<String> fileExtensions;
            private Boolean recursive;
            private Integer maxDepth;
            private Long minFileSize;
            private Long maxFileSize;
            private Boolean calculateMD5;
            private Boolean includeHidden;
            private List<String> excludePatterns;

            public Builder scanPaths(List<String> scanPaths) {
                this.scanPaths = scanPaths;
                return this;
            }

            public Builder fileExtensions(List<String> fileExtensions) {
                this.fileExtensions = fileExtensions;
                return this;
            }

            public Builder recursive(Boolean recursive) {
                this.recursive = recursive;
                return this;
            }

            public Builder maxDepth(Integer maxDepth) {
                this.maxDepth = maxDepth;
                return this;
            }

            public Builder minFileSize(Long minFileSize) {
                this.minFileSize = minFileSize;
                return this;
            }

            public Builder maxFileSize(Long maxFileSize) {
                this.maxFileSize = maxFileSize;
                return this;
            }

            public Builder calculateMD5(Boolean calculateMD5) {
                this.calculateMD5 = calculateMD5;
                return this;
            }

            public Builder includeHidden(Boolean includeHidden) {
                this.includeHidden = includeHidden;
                return this;
            }

            public Builder excludePatterns(List<String> excludePatterns) {
                this.excludePatterns = excludePatterns;
                return this;
            }

            public FileScanConfig build() {
                FileScanConfig config = new FileScanConfig();
                config.scanPaths = this.scanPaths;
                config.fileExtensions = this.fileExtensions;
                config.recursive = this.recursive;
                config.maxDepth = this.maxDepth;
                config.minFileSize = this.minFileSize;
                config.maxFileSize = this.maxFileSize;
                config.calculateMD5 = this.calculateMD5;
                config.includeHidden = this.includeHidden;
                config.excludePatterns = this.excludePatterns;
                return config;
            }
        }

        // Getters and Setters
        public List<String> getScanPaths() { return scanPaths; }
        public void setScanPaths(List<String> scanPaths) { this.scanPaths = scanPaths; }

        public List<String> getFileExtensions() { return fileExtensions; }
        public void setFileExtensions(List<String> fileExtensions) { this.fileExtensions = fileExtensions; }

        public Boolean getRecursive() { return recursive; }
        public void setRecursive(Boolean recursive) { this.recursive = recursive; }

        public Integer getMaxDepth() { return maxDepth; }
        public void setMaxDepth(Integer maxDepth) { this.maxDepth = maxDepth; }

        public Long getMinFileSize() { return minFileSize; }
        public void setMinFileSize(Long minFileSize) { this.minFileSize = minFileSize; }

        public Long getMaxFileSize() { return maxFileSize; }
        public void setMaxFileSize(Long maxFileSize) { this.maxFileSize = maxFileSize; }

        public Boolean getCalculateMD5() { return calculateMD5; }
        public void setCalculateMD5(Boolean calculateMD5) { this.calculateMD5 = calculateMD5; }

        public Boolean getIncludeHidden() { return includeHidden; }
        public void setIncludeHidden(Boolean includeHidden) { this.includeHidden = includeHidden; }

        public List<String> getExcludePatterns() { return excludePatterns; }
        public void setExcludePatterns(List<String> excludePatterns) { this.excludePatterns = excludePatterns; }
    }

    /**
     * 判断是否包含指定探查内容
     */
    public boolean containsContent(ProbeContent probeContent) {
        if (content == null || probeContent == null) {
            return false;
        }
        return ProbeContent.contains(content, probeContent.getValue());
    }

    /**
     * 判断是否需要探查元数据
     */
    public boolean needMetadata() {
        return containsContent(ProbeContent.METADATA);
    }

    /**
     * 判断是否需要探查数据量
     */
    public boolean needDataSize() {
        return containsContent(ProbeContent.DATA_SIZE);
    }

    /**
     * 判断是否需要探查示例数据
     */
    public boolean needDataContent() {
        return containsContent(ProbeContent.DATA_CONTENT);
    }

    /**
     * 判断是否需要探查数据文件
     */
    public boolean needDataFile() {
        return containsContent(ProbeContent.DATA_FILE);
    }
}
