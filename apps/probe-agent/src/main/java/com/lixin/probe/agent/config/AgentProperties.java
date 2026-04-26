package com.lixin.probe.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 探针端属性配置
 */
@Configuration
@ConfigurationProperties("probe.agent")
public class AgentProperties {

    /**
     * 探针编码
     */
    private String code = "AGENT";

    /**
     * 探针编码列表（支持多个code同时连接）
     */
    private List<String> codes = List.of("AGENT");

    /**
     * 认证密钥
     */
    private String key;

    /**
     * Agent自身配置
     */
    private Agent agent = new Agent();

    /**
     * 服务端配置
     */
    private Server server = new Server();

    /**
     * 模块配置
     */
    private Modules modules = new Modules();

    private Executor executor = new Executor();

    /**
     * 启动配置
     */
    private Startup startup = new Startup();

    // Getters and Setters
    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public List<String> getCodes() {
        return codes;
    }

    public void setCodes(List<String> codes) {
        this.codes = codes;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public Agent getAgent() {
        return agent;
    }

    public void setAgent(Agent agent) {
        this.agent = agent;
    }

    public Server getServer() {
        return server;
    }

    public void setServer(Server server) {
        this.server = server;
    }

    public Modules getModules() {
        return modules;
    }

    public void setModules(Modules modules) {
        this.modules = modules;
    }

    public Startup getStartup() {
        return startup;
    }

    public void setStartup(Startup startup) {
        this.startup = startup;
    }

    public Executor getExecutor() { return executor; }
    public void setExecutor(Executor executor) { this.executor = executor; }

    /**
     * 服务端配置
     */
    public static class Server {
        private String host = "localhost";
        private Integer port = 8080;
        private Integer udpPort = 9999;
        private Integer discoveryPort = 9090;
        private String wsMetaUrl = "ws://localhost:8080/ws/meta";
        private String wsFileUrl = "ws://localhost:8080/ws/file";

        // Getters and Setters
        public String getHost() { return host; }
        public void setHost(String host) { this.host = host; }
        public Integer getPort() { return port; }
        public void setPort(Integer port) { this.port = port; }
        public Integer getUdpPort() { return udpPort; }
        public void setUdpPort(Integer udpPort) { this.udpPort = udpPort; }
        public Integer getDiscoveryPort() { return discoveryPort; }
        public void setDiscoveryPort(Integer discoveryPort) { this.discoveryPort = discoveryPort; }
        public String getWsMetaUrl() { return wsMetaUrl; }
        public void setWsMetaUrl(String wsMetaUrl) { this.wsMetaUrl = wsMetaUrl; }
        public String getWsFileUrl() { return wsFileUrl; }
        public void setWsFileUrl(String wsFileUrl) { this.wsFileUrl = wsFileUrl; }
    }

    /**
     * Agent自身配置
     */
    public static class Agent {
        private Integer port = 58081;  // Agent HTTP服务端口，默认58081

        public Integer getPort() { return port; }
        public void setPort(Integer port) { this.port = port; }
    }

    /**
     * 模块配置
     */
    public static class Modules {
        private Database database = new Database();
        private SystemModule system = new SystemModule();
        private FileConfig file = new FileConfig();

        // Getters and Setters
        public Database getDatabase() { return database; }
        public void setDatabase(Database database) { this.database = database; }
        public SystemModule getSystem() { return system; }
        public void setSystem(SystemModule system) { this.system = system; }
        public FileConfig getFile() { return file; }
        public void setFile(FileConfig file) { this.file = file; }
    }

    /**
     * 系统模块配置
     */
    public static class SystemModule {
        private Boolean enabled = true;
        private Long collectInterval = 10000L;
        private Integer batchSize = 50;
        // 注意: probeKey已移除，由Admin动态生成并通过探针同步接口获取

        // Getters and Setters
        public Boolean getEnabled() { return enabled; }
        public void setEnabled(Boolean enabled) { this.enabled = enabled; }
        public Long getCollectInterval() { return collectInterval; }
        public void setCollectInterval(Long collectInterval) { this.collectInterval = collectInterval; }
        public Integer getBatchSize() { return batchSize; }
        public void setBatchSize(Integer batchSize) { this.batchSize = batchSize; }
    }

    /**
     * 数据库模块配置
     */
    public static class Database {
        private Boolean enabled = true;
        // 注意: probeKey已移除，由Admin动态生成并通过探针同步接口获取
        private List<DatabaseConfig> databases;

        /**
         * 数据库配置文件路径
         */
        private String configFile = "database-config.yml";

        /**
         * 数据库连接池大小
         */
        private Integer connectPoolSize = 10;

        /**
         * 连接超时时间（秒）
         */
        private Integer connectTimeout = 30;

        /**
         * 查询超时时间（秒）
         */
        private Integer queryTimeout = 60;

        // Getters and Setters
        public Boolean getEnabled() { return enabled; }
        public void setEnabled(Boolean enabled) { this.enabled = enabled; }
        public List<DatabaseConfig> getDatabases() { return databases; }
        public void setDatabases(List<DatabaseConfig> databases) { this.databases = databases; }
        public String getConfigFile() { return configFile; }
        public void setConfigFile(String configFile) { this.configFile = configFile; }
        public Integer getConnectPoolSize() { return connectPoolSize; }
        public void setConnectPoolSize(Integer connectPoolSize) { this.connectPoolSize = connectPoolSize; }
        public Integer getConnectTimeout() { return connectTimeout; }
        public void setConnectTimeout(Integer connectTimeout) { this.connectTimeout = connectTimeout; }
        public Integer getQueryTimeout() { return queryTimeout; }
        public void setQueryTimeout(Integer queryTimeout) { this.queryTimeout = queryTimeout; }
    }

    /**
     * 数据库配置
     */
    public static class DatabaseConfig {
        private String type;
        private String version;
        private String host;
        private Integer port;
        private String username;
        private String password;
        private String name;
        private String probeKey;  // 探针KEY，用于唯一标识配置
        private List<String> schemas;
        private Integer connectTimeout = 30;
        private Integer queryTimeout = 60;

        // Getters and Setters
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }
        public String getHost() { return host; }
        public void setHost(String host) { this.host = host; }
        public Integer getPort() { return port; }
        public void setPort(Integer port) { this.port = port; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getProbeKey() { return probeKey; }
        public void setProbeKey(String probeKey) { this.probeKey = probeKey; }
        public List<String> getSchemas() { return schemas; }
        public void setSchemas(List<String> schemas) { this.schemas = schemas; }
        public Integer getConnectTimeout() { return connectTimeout; }
        public void setConnectTimeout(Integer connectTimeout) { this.connectTimeout = connectTimeout; }
        public Integer getQueryTimeout() { return queryTimeout; }
        public void setQueryTimeout(Integer queryTimeout) { this.queryTimeout = queryTimeout; }
    }

    /**
     * 文件模块配置
     */
    public static class FileConfig {
        private Boolean enabled = true;
        // 注意: probeKey已移除，由Admin动态生成并通过探针同步接口获取
        private List<String> scanPaths;
        private List<String> fileExtensions = List.of(".txt", ".csv", ".json", ".xml", ".log");
        private Boolean recursive = true;
        private Integer maxDepth = 10;
        private Long minFileSize = 0L;
        private Long maxFileSize = 104857600L; // 100MB
        private Boolean calculateMD5 = true;
        private Boolean includeHidden = false;
        private List<String> excludePatterns;

        // Getters and Setters
        public Boolean getEnabled() { return enabled; }
        public void setEnabled(Boolean enabled) { this.enabled = enabled; }
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
     * 启动配置
     */
    public static class Startup {
        private Boolean executeImmediately = false;
        private Boolean exitAfterExecution = false;
        private Boolean autoRegister = true;
        private Integer registerRetryTimes = 3;
        private Long registerRetryInterval = 5000L;

        // Getters and Setters
        public Boolean getExecuteImmediately() { return executeImmediately; }
        public void setExecuteImmediately(Boolean executeImmediately) { this.executeImmediately = executeImmediately; }
        public Boolean getExitAfterExecution() { return exitAfterExecution; }
        public void setExitAfterExecution(Boolean exitAfterExecution) { this.exitAfterExecution = exitAfterExecution; }
        public Boolean getAutoRegister() { return autoRegister; }
        public void setAutoRegister(Boolean autoRegister) { this.autoRegister = autoRegister; }
        public Integer getRegisterRetryTimes() { return registerRetryTimes; }
        public void setRegisterRetryTimes(Integer registerRetryTimes) { this.registerRetryTimes = registerRetryTimes; }
        public Long getRegisterRetryInterval() { return registerRetryInterval; }
        public void setRegisterRetryInterval(Long registerRetryInterval) { this.registerRetryInterval = registerRetryInterval; }
    }

    /**
     * 任务执行器配置
     */
    public static class Executor {
        private Integer maxThreads = 4;
        private Integer queueCapacity = 100;
        private Integer timeoutSeconds = 300;

        public Integer getMaxThreads() { return maxThreads; }
        public void setMaxThreads(Integer maxThreads) { this.maxThreads = maxThreads; }
        public Integer getQueueCapacity() { return queueCapacity; }
        public void setQueueCapacity(Integer queueCapacity) { this.queueCapacity = queueCapacity; }
        public Integer getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(Integer timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
    }
}
