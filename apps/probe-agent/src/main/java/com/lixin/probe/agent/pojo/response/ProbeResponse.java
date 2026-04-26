package com.lixin.probe.agent.pojo.response;

import com.lixin.probe.agent.pojo.request.ProbeRequest;

import java.util.List;
import java.util.Map;

/**
 * 探针响应对象
 * 用于封装探针采集的各种数据
 */
public class ProbeResponse {

    /**
     * 数据库元数据
     */
    private Metadata metadata;

    /**
     * 数据量统计
     */
    private DataSize dataSize;

    /**
     * 示例数据
     */
    private DataContent dataContent;

    /**
     * 文件信息
     */
    private FileInfo fileInfo;

    /**
     * 数据文件
     */
    private DataFile dataFile;

    /**
     * 文件传输载荷
     */
    private FileTransferPayload fileTransferPayload;

    /**
     * MinIO 文件
     */
    private MinioFile minioFile;

    /**
     * 资源监控数据
     */
    private ResourceMonitor resourceMonitor;

    /**
     * 表数据查询结果
     */
    private TableData tableData;

    /**
     * 原始请求
     */
    private ProbeRequest request;

    /**
     * 数据哈希
     */
    private String hash;

    /**
     * 是否完整
     */
    private Boolean isFull;

    // Getters and Setters
    public Metadata getMetadata() { return metadata; }
    public void setMetadata(Metadata metadata) { this.metadata = metadata; }
    public DataSize getDataSize() { return dataSize; }
    public void setDataSize(DataSize dataSize) { this.dataSize = dataSize; }
    public DataContent getDataContent() { return dataContent; }
    public void setDataContent(DataContent dataContent) { this.dataContent = dataContent; }
    public FileInfo getFileInfo() { return fileInfo; }
    public void setFileInfo(FileInfo fileInfo) { this.fileInfo = fileInfo; }
    public DataFile getDataFile() { return dataFile; }
    public void setDataFile(DataFile dataFile) { this.dataFile = dataFile; }
    public FileTransferPayload getFileTransferPayload() { return fileTransferPayload; }
    public void setFileTransferPayload(FileTransferPayload fileTransferPayload) { this.fileTransferPayload = fileTransferPayload; }
    public MinioFile getMinioFile() { return minioFile; }
    public void setMinioFile(MinioFile minioFile) { this.minioFile = minioFile; }
    public ResourceMonitor getResourceMonitor() { return resourceMonitor; }
    public void setResourceMonitor(ResourceMonitor resourceMonitor) { this.resourceMonitor = resourceMonitor; }
    public TableData getTableData() { return tableData; }
    public void setTableData(TableData tableData) { this.tableData = tableData; }
    public ProbeRequest getRequest() { return request; }
    public void setRequest(ProbeRequest request) { this.request = request; }
    public String getHash() { return hash; }
    public void setHash(String hash) { this.hash = hash; }
    public Boolean getIsFull() { return isFull; }
    public void setIsFull(Boolean isFull) { this.isFull = isFull; }

    /**
     * 数据库元数据
     */
    public static class Metadata {
        private String type;
        private String version;
        private String host;
        private String port;
        private String username;
        private String password;
        private String name;
        private Map<String, Database> databases;

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }
        public String getHost() { return host; }
        public void setHost(String host) { this.host = host; }
        public String getPort() { return port; }
        public void setPort(String port) { this.port = port; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public Map<String, Database> getDatabases() { return databases; }
        public void setDatabases(Map<String, Database> databases) { this.databases = databases; }

        // 添加独立的 Table 和 Column 类供插件直接引用
        public static class Table {
            private String name;
            private String databaseName;
            private String comment;
            private Integer columnCount;
            private Map<String, Column> columns;
            private List<List<Object>> rows;

            public String getName() { return name; }
            public void setName(String name) { this.name = name; }
            public String getDatabaseName() { return databaseName; }
            public void setDatabaseName(String databaseName) { this.databaseName = databaseName; }
            public String getComment() { return comment; }
            public void setComment(String comment) { this.comment = comment; }
            public Integer getColumnCount() { return columnCount; }
            public void setColumnCount(Integer columnCount) { this.columnCount = columnCount; }
            public Map<String, Column> getColumns() { return columns; }
            public void setColumns(Map<String, Column> columns) { this.columns = columns; }
            public List<List<Object>> getRows() { return rows; }
            public void setRows(List<List<Object>> rows) { this.rows = rows; }

            public static Builder builder() { return new Builder(); }

            public static class Builder {
                private Table table = new Table();

                public Builder name(String name) { table.name = name; return this; }
                public Builder databaseName(String databaseName) { table.databaseName = databaseName; return this; }
                public Builder comment(String comment) { table.comment = comment; return this; }
                public Builder columnCount(Integer columnCount) { table.columnCount = columnCount; return this; }
                public Builder columns(Map<String, Column> columns) { table.columns = columns; return this; }
                public Builder rows(List<List<Object>> rows) { table.rows = rows; return this; }

                public Table build() { return table; }
            }
        }

        public static class Column {
            private String name;
            private String comment;
            private String type;

            public String getName() { return name; }
            public void setName(String name) { this.name = name; }
            public String getComment() { return comment; }
            public void setComment(String comment) { this.comment = comment; }
            public String getType() { return type; }
            public void setType(String type) { this.type = type; }

            public static Builder builder() { return new Builder(); }

            public static class Builder {
                private Column column = new Column();

                public Builder name(String name) { column.name = name; return this; }
                public Builder comment(String comment) { column.comment = comment; return this; }
                public Builder type(String type) { column.type = type; return this; }

                public Column build() { return column; }
            }
        }

        public static Builder builder() { return new Builder(); }

        public static class Builder {
            private Metadata metadata = new Metadata();

            public Builder type(String type) { metadata.type = type; return this; }
            public Builder version(String version) { metadata.version = version; return this; }
            public Builder host(String host) { metadata.host = host; return this; }
            public Builder port(String port) { metadata.port = port; return this; }
            public Builder username(String username) { metadata.username = username; return this; }
            public Builder password(String password) { metadata.password = password; return this; }
            public Builder name(String name) { metadata.name = name; return this; }
            public Builder databases(Map<String, Database> databases) { metadata.databases = databases; return this; }

            public Metadata build() { return metadata; }
        }

        public static class Database {
            private String type;
            private String version;
            private String host;
            private Integer port;
            private String username;
            private String password;
            private String name;
            private String charset;
            private String collation;
            private List<String> schemas;
            private Integer tableCount;
            private Integer columnCount;
            private Map<String, Table> tables;
            private String probeKey;  // 探针唯一标识符

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
            public String getCharset() { return charset; }
            public void setCharset(String charset) { this.charset = charset; }
            public String getCollation() { return collation; }
            public void setCollation(String collation) { this.collation = collation; }
            public List<String> getSchemas() { return schemas; }
            public void setSchemas(List<String> schemas) { this.schemas = schemas; }
            public Integer getTableCount() { return tableCount; }
            public void setTableCount(Integer tableCount) { this.tableCount = tableCount; }
            public Integer getColumnCount() { return columnCount; }
            public void setColumnCount(Integer columnCount) { this.columnCount = columnCount; }
            public Map<String, Table> getTables() { return tables; }
            public void setTables(Map<String, Table> tables) { this.tables = tables; }
            public String getProbeKey() { return probeKey; }
            public void setProbeKey(String probeKey) { this.probeKey = probeKey; }

            public static Builder builder() { return new Builder(); }

            public static class Builder {
                private Database database = new Database();

                public Builder type(String type) { database.type = type; return this; }
                public Builder version(String version) { database.version = version; return this; }
                public Builder host(String host) { database.host = host; return this; }
                public Builder port(Integer port) { database.port = port; return this; }
                public Builder username(String username) { database.username = username; return this; }
                public Builder password(String password) { database.password = password; return this; }
                public Builder name(String name) { database.name = name; return this; }
                public Builder charset(String charset) { database.charset = charset; return this; }
                public Builder collation(String collation) { database.collation = collation; return this; }
                public Builder schemas(List<String> schemas) { database.schemas = schemas; return this; }
                public Builder tableCount(Integer tableCount) { database.tableCount = tableCount; return this; }
                public Builder columnCount(Integer columnCount) { database.columnCount = columnCount; return this; }
                public Builder tables(Map<String, Table> tables) { database.tables = tables; return this; }
                public Builder probeKey(String probeKey) { database.probeKey = probeKey; return this; }

                public Database build() { return database; }
            }
        }
    }

    /**
     * 数据量统计
     */
    public static class DataSize {
        private Map<String, Database> databases;

        public Map<String, Database> getDatabases() { return databases; }
        public void setDatabases(Map<String, Database> databases) { this.databases = databases; }

        // 添加独立的 Table 类供插件直接引用
        public static class Table {
            private String name;
            private String databaseName;
            private Integer columnCount;
            private Long storage;
            private Long rowCount;

            public String getName() { return name; }
            public void setName(String name) { this.name = name; }
            public String getDatabaseName() { return databaseName; }
            public void setDatabaseName(String databaseName) { this.databaseName = databaseName; }
            public Integer getColumnCount() { return columnCount; }
            public void setColumnCount(Integer columnCount) { this.columnCount = columnCount; }
            public Long getStorage() { return storage; }
            public void setStorage(Long storage) { this.storage = storage; }
            public Long getRowCount() { return rowCount; }
            private Long indexes;

            public Long getIndexes() { return indexes; }
            public void setIndexes(Long indexes) { this.indexes = indexes; }
            public void setRowCount(Long rowCount) { this.rowCount = rowCount; }

            public static Builder builder() { return new Builder(); }

            public static class Builder {
                private Table table = new Table();

                public Builder name(String name) { table.name = name; return this; }
                public Builder indexes(Long indexes) { table.indexes = indexes; return this; }
                public Builder databaseName(String databaseName) { table.databaseName = databaseName; return this; }
                public Builder columnCount(Integer columnCount) { table.columnCount = columnCount; return this; }
                public Builder storage(Long storage) { table.storage = storage; return this; }
                public Builder rowCount(Long rowCount) { table.rowCount = rowCount; return this; }

                public Table build() { return table; }
            }
        }

        public static Builder builder() { return new Builder(); }

        public static class Builder {
            private DataSize dataSize = new DataSize();

            public Builder databases(Map<String, Database> databases) { dataSize.databases = databases; return this; }

            public DataSize build() { return dataSize; }
        }

        public static class Database {
            private String type;
            private String version;
            private String host;
            private Integer port;
            private String username;
            private String password;
            private String name;
            private List<String> schemas;
            private Integer tableCount;
            private Integer columnCount;
            private Long storage;
            private Long rowCount;
            private Map<String, Table> tables;
            private String probeKey;  // 探针唯一标识符

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
            public List<String> getSchemas() { return schemas; }
            public void setSchemas(List<String> schemas) { this.schemas = schemas; }
            public Integer getTableCount() { return tableCount; }
            public void setTableCount(Integer tableCount) { this.tableCount = tableCount; }
            public Integer getColumnCount() { return columnCount; }
            public void setColumnCount(Integer columnCount) { this.columnCount = columnCount; }
            public Long getStorage() { return storage; }
            public void setStorage(Long storage) { this.storage = storage; }
            public Long getRowCount() { return rowCount; }
            public void setRowCount(Long rowCount) { this.rowCount = rowCount; }
            public Map<String, Table> getTables() { return tables; }
            public void setTables(Map<String, Table> tables) { this.tables = tables; }
            public String getProbeKey() { return probeKey; }
            public void setProbeKey(String probeKey) { this.probeKey = probeKey; }

            public static Builder builder() { return new Builder(); }

            public static class Builder {
                private Database database = new Database();

                public Builder type(String type) { database.type = type; return this; }
                public Builder version(String version) { database.version = version; return this; }
                public Builder host(String host) { database.host = host; return this; }
                public Builder port(Integer port) { database.port = port; return this; }
                public Builder username(String username) { database.username = username; return this; }
                public Builder password(String password) { database.password = password; return this; }
                public Builder name(String name) { database.name = name; return this; }
                public Builder schemas(List<String> schemas) { database.schemas = schemas; return this; }
                public Builder tableCount(Integer tableCount) { database.tableCount = tableCount; return this; }
                public Builder columnCount(Integer columnCount) { database.columnCount = columnCount; return this; }
                public Builder storage(Long storage) { database.storage = storage; return this; }
                public Builder rowCount(Long rowCount) { database.rowCount = rowCount; return this; }
                public Builder tables(Map<String, Table> tables) { database.tables = tables; return this; }
                public Builder probeKey(String probeKey) { database.probeKey = probeKey; return this; }

                public Database build() { return database; }
            }
        }
    }

    /**
     * 示例数据
     */
    public static class DataContent {
        private String type;
        private String version;
        private String host;
        private Integer port;
        private String username;
        private String password;
        private String name;
        private String schema;
        private String table;
        private Integer limit;
        private List<Map<String, Object>> rows;
        private List<String> columns;
        private Boolean success;
        private String message;

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
        public String getSchema() { return schema; }
        public void setSchema(String schema) { this.schema = schema; }
        public String getTable() { return table; }
        public void setTable(String table) { this.table = table; }
        public Integer getLimit() { return limit; }
        public void setLimit(Integer limit) { this.limit = limit; }
        public List<Map<String, Object>> getRows() { return rows; }
        public void setRows(List<Map<String, Object>> rows) { this.rows = rows; }
        public List<String> getColumns() { return columns; }
        public void setColumns(List<String> columns) { this.columns = columns; }
        public Boolean getSuccess() { return success; }
        public void setSuccess(Boolean success) { this.success = success; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }

        public static Builder builder() { return new Builder(); }

        public static class Builder {
            private DataContent content = new DataContent();

            public Builder type(String type) { content.type = type; return this; }
            public Builder version(String version) { content.version = version; return this; }
            public Builder host(String host) { content.host = host; return this; }
            public Builder port(Integer port) { content.port = port; return this; }
            public Builder username(String username) { content.username = username; return this; }
            public Builder password(String password) { content.password = password; return this; }
            public Builder name(String name) { content.name = name; return this; }
            public Builder schema(String schema) { content.schema = schema; return this; }
            public Builder table(String table) { content.table = table; return this; }
            public Builder limit(Integer limit) { content.limit = limit; return this; }
            public Builder rows(List<Map<String, Object>> rows) { content.rows = rows; return this; }
            public Builder columns(List<String> columns) { content.columns = columns; return this; }
            public Builder success(Boolean success) { content.success = success; return this; }
            public Builder message(String message) { content.message = message; return this; }

            public DataContent build() { return content; }
        }
    }

    /**
     * 文件信息
     */
    public static class FileInfo {
        private String rootPath;
        private Long totalFileCount;
        private Long totalDirectoryCount;
        private Long totalSize;
        private String storageUnit;
        private Boolean success;
        private List<String> msg;
        private Map<String, Directory> directories;
        private Map<String, File> files;

        public String getRootPath() { return rootPath; }
        public void setRootPath(String rootPath) { this.rootPath = rootPath; }
        public Long getTotalFileCount() { return totalFileCount; }
        public void setTotalFileCount(Long totalFileCount) { this.totalFileCount = totalFileCount; }
        public Long getTotalDirectoryCount() { return totalDirectoryCount; }
        public void setTotalDirectoryCount(Long totalDirectoryCount) { this.totalDirectoryCount = totalDirectoryCount; }
        public Long getTotalSize() { return totalSize; }
        public void setTotalSize(Long totalSize) { this.totalSize = totalSize; }
        public String getStorageUnit() { return storageUnit; }
        public void setStorageUnit(String storageUnit) { this.storageUnit = storageUnit; }
        public Boolean getSuccess() { return success; }
        public void setSuccess(Boolean success) { this.success = success; }
        public List<String> getMsg() { return msg; }
        public void setMsg(List<String> msg) { this.msg = msg; }
        public Map<String, Directory> getDirectories() { return directories; }
        public void setDirectories(Map<String, Directory> directories) { this.directories = directories; }
        public Map<String, File> getFiles() { return files; }
        public void setFiles(Map<String, File> files) { this.files = files; }

        public static class Directory {
            private String path;
            private String name;
            private Long fileCount;
            private Long subdirectoryCount;
            private Long size;

            public String getPath() { return path; }
            public void setPath(String path) { this.path = path; }
            public String getName() { return name; }
            public void setName(String name) { this.name = name; }
            public Long getFileCount() { return fileCount; }
            public void setFileCount(Long fileCount) { this.fileCount = fileCount; }
            public Long getSubdirectoryCount() { return subdirectoryCount; }
            public void setSubdirectoryCount(Long subdirectoryCount) { this.subdirectoryCount = subdirectoryCount; }
            public Long getSize() { return size; }
            public void setSize(Long size) { this.size = size; }
        }

        public static class File {
            private String path;
            private String name;
            private String extension;
            private Long size;
            private String md5;
            private Long lastModified;

            public String getPath() { return path; }
            public void setPath(String path) { this.path = path; }
            public String getName() { return name; }
            public void setName(String name) { this.name = name; }
            public String getExtension() { return extension; }
            public void setExtension(String extension) { this.extension = extension; }
            public Long getSize() { return size; }
            public void setSize(Long size) { this.size = size; }
            public String getMd5() { return md5; }
            public void setMd5(String md5) { this.md5 = md5; }
            public Long getLastModified() { return lastModified; }
            public void setLastModified(Long lastModified) { this.lastModified = lastModified; }
        }
    }

    /**
     * 数据文件
     */
    public static class DataFile {
        private Map<String, Directory> directories;
        private Boolean success;
        private List<String> msg;
        private Long totalDirectoryCount;
        private Long totalFileCount;
        private Long totalSize;

        public Map<String, Directory> getDirectories() { return directories; }
        public void setDirectories(Map<String, Directory> directories) { this.directories = directories; }
        public Boolean getSuccess() { return success; }
        public void setSuccess(Boolean success) { this.success = success; }
        public List<String> getMsg() { return msg; }
        public void setMsg(List<String> msg) { this.msg = msg; }
        public Long getTotalDirectoryCount() { return totalDirectoryCount; }
        public void setTotalDirectoryCount(Long totalDirectoryCount) { this.totalDirectoryCount = totalDirectoryCount; }
        public Long getTotalFileCount() { return totalFileCount; }
        public void setTotalFileCount(Long totalFileCount) { this.totalFileCount = totalFileCount; }
        public Long getTotalSize() { return totalSize; }
        public void setTotalSize(Long totalSize) { this.totalSize = totalSize; }

        public static Builder builder() { return new Builder(); }

        public static class Builder {
            private DataFile dataFile = new DataFile();

            public Builder directories(Map<String, Directory> directories) { dataFile.directories = directories; return this; }
            public Builder success(Boolean success) { dataFile.success = success; return this; }
            public Builder msg(List<String> msg) { dataFile.msg = msg; return this; }
            public Builder totalDirectoryCount(Long totalDirectoryCount) { dataFile.totalDirectoryCount = totalDirectoryCount; return this; }
            public Builder totalFileCount(Long totalFileCount) { dataFile.totalFileCount = totalFileCount; return this; }
            public Builder totalSize(Long totalSize) { dataFile.totalSize = totalSize; return this; }

            public DataFile build() { return dataFile; }
        }

        public static class Directory {
            private String name;
            private Long size;
            private Long fileCount;
            private Long directoryCount;
            private List<String> path;
            private Map<String, File> files;
            private Map<String, Directory> directories;

            public String getName() { return name; }
            public void setName(String name) { this.name = name; }
            public Long getSize() { return size; }
            public void setSize(Long size) { this.size = size; }
            public Long getFileCount() { return fileCount; }
            public void setFileCount(Long fileCount) { this.fileCount = fileCount; }
            public Long getDirectoryCount() { return directoryCount; }
            public void setDirectoryCount(Long directoryCount) { this.directoryCount = directoryCount; }
            public List<String> getPath() { return path; }
            public void setPath(List<String> path) { this.path = path; }
            public Map<String, File> getFiles() { return files; }
            public void setFiles(Map<String, File> files) { this.files = files; }
            public Map<String, Directory> getDirectories() { return directories; }
            public void setDirectories(Map<String, Directory> directories) { this.directories = directories; }

            public static Builder builder() { return new Builder(); }

            public static class Builder {
                private Directory directory = new Directory();

                public Builder name(String name) { directory.name = name; return this; }
                public Builder size(Long size) { directory.size = size; return this; }
                public Builder fileCount(Long fileCount) { directory.fileCount = fileCount; return this; }
                public Builder directoryCount(Long directoryCount) { directory.directoryCount = directoryCount; return this; }
                public Builder path(List<String> path) { directory.path = path; return this; }
                public Builder files(Map<String, File> files) { directory.files = files; return this; }
                public Builder directories(Map<String, Directory> directories) { directory.directories = directories; return this; }

                public Directory build() { return directory; }
            }
        }

        public static class File {
            private String name;
            private Long size;
            private String type;
            private String extension;
            private String md5;
            private Long lastModified;
            private List<String> path;

            public String getName() { return name; }
            public void setName(String name) { this.name = name; }
            public Long getSize() { return size; }
            public void setSize(Long size) { this.size = size; }
            public String getType() { return type; }
            public void setType(String type) { this.type = type; }
            public String getExtension() { return extension; }
            public void setExtension(String extension) { this.extension = extension; }
            public String getMd5() { return md5; }
            public void setMd5(String md5) { this.md5 = md5; }
            public Long getLastModified() { return lastModified; }
            public void setLastModified(Long lastModified) { this.lastModified = lastModified; }
            public List<String> getPath() { return path; }
            public void setPath(List<String> path) { this.path = path; }

            public static Builder builder() { return new Builder(); }

            public static class Builder {
                private File file = new File();

                public Builder name(String name) { file.name = name; return this; }
                public Builder size(Long size) { file.size = size; return this; }
                public Builder type(String type) { file.type = type; return this; }
                public Builder extension(String extension) { file.extension = extension; return this; }
                public Builder md5(String md5) { file.md5 = md5; return this; }
                public Builder lastModified(Long lastModified) { file.lastModified = lastModified; return this; }
                public Builder path(List<String> path) { file.path = path; return this; }

                public File build() { return file; }
            }
        }
    }

    /**
     * 文件传输命令
     */
    public enum FileTransferCmd {
        METADATA,
        CHUNK,
        COMPLETE
    }

    /**
     * 文件传输载荷
     */
    public static class FileTransferPayload {
        private String fileName;
        private long totalSize;
        private String fileMd5;
        private boolean success;
        private String errorMsg;
        private String sourceFilePath;
        private String fileId;
        private int totalChunks;
        private String formattedSize;
        private List<byte[]> chunkDataList;
        private int chunkIndex;
        private byte[] singleChunkData;
        private FileTransferCmd transferCmd;

        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }
        public long getTotalSize() { return totalSize; }
        public void setTotalSize(long totalSize) { this.totalSize = totalSize; }
        public String getFileMd5() { return fileMd5; }
        public void setFileMd5(String fileMd5) { this.fileMd5 = fileMd5; }
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getErrorMsg() { return errorMsg; }
        public void setErrorMsg(String errorMsg) { this.errorMsg = errorMsg; }
        public String getSourceFilePath() { return sourceFilePath; }
        public void setSourceFilePath(String sourceFilePath) { this.sourceFilePath = sourceFilePath; }
        public String getFileId() { return fileId; }
        public void setFileId(String fileId) { this.fileId = fileId; }
        public int getTotalChunks() { return totalChunks; }
        public void setTotalChunks(int totalChunks) { this.totalChunks = totalChunks; }
        public String getFormattedSize() { return formattedSize; }
        public void setFormattedSize(String formattedSize) { this.formattedSize = formattedSize; }
        public List<byte[]> getChunkDataList() { return chunkDataList; }
        public void setChunkDataList(List<byte[]> chunkDataList) { this.chunkDataList = chunkDataList; }
        public int getChunkIndex() { return chunkIndex; }
        public void setChunkIndex(int chunkIndex) { this.chunkIndex = chunkIndex; }
        public byte[] getSingleChunkData() { return singleChunkData; }
        public void setSingleChunkData(byte[] singleChunkData) { this.singleChunkData = singleChunkData; }
        public FileTransferCmd getTransferCmd() { return transferCmd; }
        public void setTransferCmd(FileTransferCmd transferCmd) { this.transferCmd = transferCmd; }
    }

    /**
     * MinIO 文件
     */
    public static class MinioFile {
        private String url;
        private String fileName;
        private Long size;

        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }
        public Long getSize() { return size; }
        public void setSize(Long size) { this.size = size; }
    }

    /**
     * 资源监控数据
     */
    public static class ResourceMonitor {
        private String resourceType;
        private String resourceName;
        private Double currentValue;
        private String unit;
        private Long timestamp;
        private String status;
        private Map<String, Object> attributes;

        public String getResourceType() { return resourceType; }
        public void setResourceType(String resourceType) { this.resourceType = resourceType; }
        public String getResourceName() { return resourceName; }
        public void setResourceName(String resourceName) { this.resourceName = resourceName; }
        public Double getCurrentValue() { return currentValue; }
        public void setCurrentValue(Double currentValue) { this.currentValue = currentValue; }
        public String getUnit() { return unit; }
        public void setUnit(String unit) { this.unit = unit; }
        public Long getTimestamp() { return timestamp; }
        public void setTimestamp(Long timestamp) { this.timestamp = timestamp; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public Map<String, Object> getAttributes() { return attributes; }
        public void setAttributes(Map<String, Object> attributes) { this.attributes = attributes; }
    }

    /**
     * 表数据查询结果
     */
    // ====== NoSQL 响应类 ======

    public static class NoSQLMetadata {
        private String type;
        private String subType;
        private String version;
        private String host;
        private Integer port;
        private String databaseName;
        private List<String> collectionNames;
        private Long totalCollections;
        private Map<String, CollectionInfo> collections;

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getSubType() { return subType; }
        public void setSubType(String subType) { this.subType = subType; }
        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }
        public String getHost() { return host; }
        public void setHost(String host) { this.host = host; }
        public Integer getPort() { return port; }
        public void setPort(Integer port) { this.port = port; }
        public String getDatabaseName() { return databaseName; }
        public void setDatabaseName(String databaseName) { this.databaseName = databaseName; }
        public List<String> getCollectionNames() { return collectionNames; }
        public void setCollectionNames(List<String> collectionNames) { this.collectionNames = collectionNames; }
        public Long getTotalCollections() { return totalCollections; }
        public void setTotalCollections(Long totalCollections) { this.totalCollections = totalCollections; }
        public Map<String, CollectionInfo> getCollections() { return collections; }
        public void setCollections(Map<String, CollectionInfo> collections) { this.collections = collections; }

        public static class CollectionInfo {
            private String name;
            private Long documentCount;
            private Long avgDocumentSize;
            private Long totalSize;
            private Long indexCount;
            private List<IndexInfo> indexes;

            public String getName() { return name; }
            public void setName(String name) { this.name = name; }
            public Long getDocumentCount() { return documentCount; }
            public void setDocumentCount(Long documentCount) { this.documentCount = documentCount; }
            public Long getAvgDocumentSize() { return avgDocumentSize; }
            public void setAvgDocumentSize(Long avgDocumentSize) { this.avgDocumentSize = avgDocumentSize; }
            public Long getTotalSize() { return totalSize; }
            public void setTotalSize(Long totalSize) { this.totalSize = totalSize; }
            public Long getIndexCount() { return indexCount; }
            public void setIndexCount(Long indexCount) { this.indexCount = indexCount; }
            public List<IndexInfo> getIndexes() { return indexes; }
            public void setIndexes(List<IndexInfo> indexes) { this.indexes = indexes; }
        }

        public static class IndexInfo {
            private String name;
            private String keys;
            private Boolean unique;
            private String type;

            public String getName() { return name; }
            public void setName(String name) { this.name = name; }
            public String getKeys() { return keys; }
            public void setKeys(String keys) { this.keys = keys; }
            public Boolean getUnique() { return unique; }
            public void setUnique(Boolean unique) { this.unique = unique; }
            public String getType() { return type; }
            public void setType(String type) { this.type = type; }
        }
    }

    public static class NoSQLStats {
        private String databaseName;
        private Long totalCollections;
        private Long totalDocuments;
        private Long totalSize;
        private Long totalIndexes;
        private Double avgDocumentSize;
        private Map<String, CollectionStats> collectionStats;

        public String getDatabaseName() { return databaseName; }
        public void setDatabaseName(String databaseName) { this.databaseName = databaseName; }
        public Long getTotalCollections() { return totalCollections; }
        public void setTotalCollections(Long totalCollections) { this.totalCollections = totalCollections; }
        public Long getTotalDocuments() { return totalDocuments; }
        public void setTotalDocuments(Long totalDocuments) { this.totalDocuments = totalDocuments; }
        public Long getTotalSize() { return totalSize; }
        public void setTotalSize(Long totalSize) { this.totalSize = totalSize; }
        public Long getTotalIndexes() { return totalIndexes; }
        public void setTotalIndexes(Long totalIndexes) { this.totalIndexes = totalIndexes; }
        public Double getAvgDocumentSize() { return avgDocumentSize; }
        public void setAvgDocumentSize(Double avgDocumentSize) { this.avgDocumentSize = avgDocumentSize; }
        public Map<String, CollectionStats> getCollectionStats() { return collectionStats; }
        public void setCollectionStats(Map<String, CollectionStats> collectionStats) { this.collectionStats = collectionStats; }

        public static class CollectionStats {
            private String name;
            private Long documentCount;
            private Long size;
            private Long indexSize;
            private Long indexCount;
            private Double avgObjSize;

            public String getName() { return name; }
            public void setName(String name) { this.name = name; }
            public Long getDocumentCount() { return documentCount; }
            public void setDocumentCount(Long documentCount) { this.documentCount = documentCount; }
            public Long getSize() { return size; }
            public void setSize(Long size) { this.size = size; }
            public Long getIndexSize() { return indexSize; }
            public void setIndexSize(Long indexSize) { this.indexSize = indexSize; }
            public Long getIndexCount() { return indexCount; }
            public void setIndexCount(Long indexCount) { this.indexCount = indexCount; }
            public Double getAvgObjSize() { return avgObjSize; }
            public void setAvgObjSize(Double avgObjSize) { this.avgObjSize = avgObjSize; }
        }
    }

    // ====== Message Queue 响应类 ======

    public static class MessageQueueMetadata {
        private String type;
        private String subType;
        private String host;
        private Integer port;
        private List<String> topicNames;
        private Long totalTopics;
        private Map<String, TopicInfo> topics;

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getSubType() { return subType; }
        public void setSubType(String subType) { this.subType = subType; }
        public String getHost() { return host; }
        public void setHost(String host) { this.host = host; }
        public Integer getPort() { return port; }
        public void setPort(Integer port) { this.port = port; }
        public List<String> getTopicNames() { return topicNames; }
        public void setTopicNames(List<String> topicNames) { this.topicNames = topicNames; }
        public Long getTotalTopics() { return totalTopics; }
        public void setTotalTopics(Long totalTopics) { this.totalTopics = totalTopics; }
        public Map<String, TopicInfo> getTopics() { return topics; }
        public void setTopics(Map<String, TopicInfo> topics) { this.topics = topics; }

        public static class TopicInfo {
            private String name;
            private Integer partitionCount;
            private Integer replicationFactor;

            public String getName() { return name; }
            public void setName(String name) { this.name = name; }
            public Integer getPartitionCount() { return partitionCount; }
            public void setPartitionCount(Integer partitionCount) { this.partitionCount = partitionCount; }
            public Integer getReplicationFactor() { return replicationFactor; }
            public void setReplicationFactor(Integer replicationFactor) { this.replicationFactor = replicationFactor; }
        }
    }

    public static class MessageQueueStats {
        private Long totalTopics;
        private Long totalMessages;
        private Long totalPartitions;
        private Map<String, Long> topicOffsets;

        public Long getTotalTopics() { return totalTopics; }
        public void setTotalTopics(Long totalTopics) { this.totalTopics = totalTopics; }
        public Long getTotalMessages() { return totalMessages; }
        public void setTotalMessages(Long totalMessages) { this.totalMessages = totalMessages; }
        public Long getTotalPartitions() { return totalPartitions; }
        public void setTotalPartitions(Long totalPartitions) { this.totalPartitions = totalPartitions; }
        public Map<String, Long> getTopicOffsets() { return topicOffsets; }
        public void setTopicOffsets(Map<String, Long> topicOffsets) { this.topicOffsets = topicOffsets; }
    }

    // ====== CDC 响应类 ======

    public static class CDCMetadata {
        private String type;
        private String subType;
        private String host;
        private Integer port;
        private String databaseName;
        private List<String> supportedOperations;
        private String serverId;

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getSubType() { return subType; }
        public void setSubType(String subType) { this.subType = subType; }
        public String getHost() { return host; }
        public void setHost(String host) { this.host = host; }
        public Integer getPort() { return port; }
        public void setPort(Integer port) { this.port = port; }
        public String getDatabaseName() { return databaseName; }
        public void setDatabaseName(String databaseName) { this.databaseName = databaseName; }
        public List<String> getSupportedOperations() { return supportedOperations; }
        public void setSupportedOperations(List<String> supportedOperations) { this.supportedOperations = supportedOperations; }
        public String getServerId() { return serverId; }
        public void setServerId(String serverId) { this.serverId = serverId; }
    }

    public static class CDCEvent {
        private String eventType;
        private String database;
        private String table;
        private Map<String, Object> before;
        private Map<String, Object> after;
        private String position;
        private String timestamp;

        public String getEventType() { return eventType; }
        public void setEventType(String eventType) { this.eventType = eventType; }
        public String getDatabase() { return database; }
        public void setDatabase(String database) { this.database = database; }
        public String getTable() { return table; }
        public void setTable(String table) { this.table = table; }
        public Map<String, Object> getBefore() { return before; }
        public void setBefore(Map<String, Object> before) { this.before = before; }
        public Map<String, Object> getAfter() { return after; }
        public void setAfter(Map<String, Object> after) { this.after = after; }
        public String getPosition() { return position; }
        public void setPosition(String position) { this.position = position; }
        public String getTimestamp() { return timestamp; }
        public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
    }

    public static class MessageQueueDataContent {
        private String topicName;
        private Long total;
        private List<Map<String, Object>> rows;
        private List<String> columns;
        private Boolean success;
        private String message;

        public String getTopicName() { return topicName; }
        public void setTopicName(String topicName) { this.topicName = topicName; }
        public Long getTotal() { return total; }
        public void setTotal(Long total) { this.total = total; }
        public List<Map<String, Object>> getRows() { return rows; }
        public void setRows(List<Map<String, Object>> rows) { this.rows = rows; }
        public List<String> getColumns() { return columns; }
        public void setColumns(List<String> columns) { this.columns = columns; }
        public Boolean getSuccess() { return success; }
        public void setSuccess(Boolean success) { this.success = success; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }

    public static class NoSQLDataContent {
        private String collectionName;
        private Long total;
        private Integer pageNum;
        private Integer pageSize;
        private List<Map<String, Object>> rows;
        private List<String> columns;
        private Boolean success;
        private String message;

        public String getCollectionName() { return collectionName; }
        public void setCollectionName(String collectionName) { this.collectionName = collectionName; }
        public Long getTotal() { return total; }
        public void setTotal(Long total) { this.total = total; }
        public Integer getPageNum() { return pageNum; }
        public void setPageNum(Integer pageNum) { this.pageNum = pageNum; }
        public Integer getPageSize() { return pageSize; }
        public void setPageSize(Integer pageSize) { this.pageSize = pageSize; }
        public List<Map<String, Object>> getRows() { return rows; }
        public void setRows(List<Map<String, Object>> rows) { this.rows = rows; }
        public List<String> getColumns() { return columns; }
        public void setColumns(List<String> columns) { this.columns = columns; }
        public Boolean getSuccess() { return success; }
        public void setSuccess(Boolean success) { this.success = success; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }

    public static class TableData {
        private List<Map<String, Object>> columns;
        private List<Map<String, Object>> rows;
        private Long total;
        private Integer pageNum;
        private Integer pageSize;
        private String error;
        private String message;

        public List<Map<String, Object>> getColumns() { return columns; }
        public void setColumns(List<Map<String, Object>> columns) { this.columns = columns; }
        public List<Map<String, Object>> getRows() { return rows; }
        public void setRows(List<Map<String, Object>> rows) { this.rows = rows; }
        public Long getTotal() { return total; }
        public void setTotal(Long total) { this.total = total; }
        public Integer getPageNum() { return pageNum; }
        public void setPageNum(Integer pageNum) { this.pageNum = pageNum; }
        public Integer getPageSize() { return pageSize; }
        public void setPageSize(Integer pageSize) { this.pageSize = pageSize; }
        public String getError() { return error; }
        public void setError(String error) { this.error = error; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }

        public static Builder builder() { return new Builder(); }

        public static class Builder {
            private TableData tableData = new TableData();

            public Builder columns(List<Map<String, Object>> columns) {
                tableData.columns = columns;
                return this;
            }

            public Builder rows(List<Map<String, Object>> rows) {
                tableData.rows = rows;
                return this;
            }

            public Builder total(Long total) {
                tableData.total = total;
                return this;
            }

            public Builder pageNum(Integer pageNum) {
                tableData.pageNum = pageNum;
                return this;
            }

            public Builder pageSize(Integer pageSize) {
                tableData.pageSize = pageSize;
                return this;
            }

            public Builder error(String error) {
                tableData.error = error;
                return this;
            }

            public Builder message(String message) {
                tableData.message = message;
                return this;
            }

            public TableData build() {
                return tableData;
            }
        }
    }
}
