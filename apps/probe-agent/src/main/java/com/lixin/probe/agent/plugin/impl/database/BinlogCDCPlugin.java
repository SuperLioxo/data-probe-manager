package com.lixin.probe.agent.plugin.impl.database;

import com.github.shyiko.mysql.binlog.BinaryLogClient;
import com.github.shyiko.mysql.binlog.event.*;
import com.github.shyiko.mysql.binlog.event.deserialization.EventDeserializer;
import com.lixin.probe.agent.plugin.api.CDCPlugin;
import com.lixin.probe.agent.pojo.response.ProbeResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.math.BigDecimal;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class BinlogCDCPlugin implements CDCPlugin {

    private static final Logger log = LoggerFactory.getLogger(BinlogCDCPlugin.class);

    /** 自增serverId生成器，每个BinaryLogClient需要唯一ID以避免同一MySQL上的冲突 */
    private static final AtomicInteger SERVER_ID_SEQ = new AtomicInteger(10000);

    private final Map<String, BinaryLogClient> activeClients = new ConcurrentHashMap<>();
    private final Map<String, BlockingQueue<ProbeResponse.CDCEvent>> eventQueues = new ConcurrentHashMap<>();
    private final Map<String, TableMetadata> tableMetadataCache = new ConcurrentHashMap<>();
    private final Map<String, String[]> columnNamesCache = new ConcurrentHashMap<>();
    private volatile Map<String, Object> connectionConfig;

    @Override
    public String getPluginId() { return "mysql-binlog-cdc-plugin"; }

    @Override
    public String getName() { return "MySQL Binlog CDC Plugin"; }

    @Override
    public String getSubType() { return "mysql-binlog"; }

    @Override
    public String getVersion() { return "2.0.0"; }

    @Override
    public String getDescription() {
        return "MySQL binlog-based Change Data Capture for real-time row-level change detection";
    }

    @Override
    public CompletableFuture<ProbeResponse.CDCMetadata> getMetadata(Map<String, Object> config) {
        return CompletableFuture.supplyAsync(() -> {
            String host = (String) config.get("host");
            int port = config.get("port") != null ? ((Number) config.get("port")).intValue() : 3306;
            String database = (String) config.getOrDefault("database", "");

            ProbeResponse.CDCMetadata metadata = new ProbeResponse.CDCMetadata();
            metadata.setType("CDC");
            metadata.setSubType("mysql-binlog");
            metadata.setHost(host);
            metadata.setPort(port);
            metadata.setDatabaseName(database);
            metadata.setSupportedOperations(List.of("INSERT", "UPDATE", "DELETE"));

            try (Connection conn = buildJdbcConnection(config)) {
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery("SHOW VARIABLES LIKE 'log_bin'")) {
                    if (rs.next()) {
                        String logBin = rs.getString("Value");
                        metadata.setServerId("ON".equalsIgnoreCase(logBin) ? "binlog-enabled" : "binlog-disabled");
                    }
                }
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery("SHOW MASTER STATUS")) {
                    if (rs.next()) {
                        metadata.setServerId(metadata.getServerId() + ":" + rs.getString("File") + ":" + rs.getString("Position"));
                    }
                }
            } catch (Exception e) {
                log.warn("[CDC] Failed to query binlog status: {}", e.getMessage());
                metadata.setServerId("unknown");
            }

            return metadata;
        });
    }

    @Override
    public CompletableFuture<List<ProbeResponse.CDCEvent>> captureChanges(
            Map<String, Object> config, String database, String table,
            String fromPosition, int maxEvents) {
        return CompletableFuture.supplyAsync(() -> {
            String clientKey = buildClientKey(config, database);
            BlockingQueue<ProbeResponse.CDCEvent> queue = eventQueues.computeIfAbsent(
                    clientKey, k -> new LinkedBlockingQueue<>(10000));

            BinaryLogClient client = activeClients.get(clientKey);
            if (client == null) {
                startBinlogClient(config, database, table, fromPosition, clientKey, queue);
            }

            List<ProbeResponse.CDCEvent> events = new ArrayList<>();
            long deadline = System.currentTimeMillis() + 5000;
            while (events.size() < maxEvents && System.currentTimeMillis() < deadline) {
                ProbeResponse.CDCEvent event = null;
                try { event = queue.poll(500, TimeUnit.MILLISECONDS); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
                if (event != null) {
                    if ((table == null || table.isEmpty() || table.equals(event.getTable()))
                            && (database == null || database.isEmpty() || database.equals(event.getDatabase()))) {
                        events.add(event);
                    }
                } else if (!events.isEmpty()) {
                    break;
                }
            }
            return events;
        });
    }

    @Override
    public CompletableFuture<Boolean> testConnection(Map<String, Object> config) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = buildJdbcConnection(config)) {
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery("SHOW VARIABLES LIKE 'log_bin'")) {
                    if (rs.next()) {
                        boolean enabled = "ON".equalsIgnoreCase(rs.getString("Value"));
                        if (!enabled) {
                            log.warn("[CDC] MySQL binlog is not enabled on {}:{}", config.get("host"), config.get("port"));
                            return false;
                        }
                    }
                }
                // Check binlog format is ROW
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery("SHOW VARIABLES LIKE 'binlog_format'")) {
                    if (rs.next()) {
                        String format = rs.getString("Value");
                        if (!"ROW".equalsIgnoreCase(format)) {
                            log.warn("[CDC] MySQL binlog_format should be ROW, current: {}", format);
                            return false;
                        }
                    }
                }
                return true;
            } catch (Exception e) {
                log.error("[CDC] Connection test failed: {}", e.getMessage());
                return false;
            }
        });
    }

    /**
     * Start a BinaryLogClient that streams binlog events
     */
    private void startBinlogClient(Map<String, Object> config, String database, String table,
                                   String fromPosition, String clientKey,
                                   BlockingQueue<ProbeResponse.CDCEvent> queue) {
        String host = (String) config.get("host");
        int port = config.get("port") != null ? ((Number) config.get("port")).intValue() : 3306;
        String username = (String) config.get("username");
        String password = (String) config.get("password");

        BinaryLogClient client = new BinaryLogClient(host, port, username, password);
        // 每个client使用唯一的serverId，避免同一MySQL上多实例连接时冲突
        client.setServerId(SERVER_ID_SEQ.incrementAndGet());

        EventDeserializer eventDeserializer = new EventDeserializer();
        eventDeserializer.setCompatibilityMode(
                EventDeserializer.CompatibilityMode.DATE_AND_TIME_AS_LONG,
                EventDeserializer.CompatibilityMode.CHAR_AND_BINARY_AS_BYTE_ARRAY
        );
        client.setEventDeserializer(eventDeserializer);

        // Parse fromPosition: "binlog-file:position"
        if (fromPosition != null && !fromPosition.isEmpty() && fromPosition.contains(":")) {
            String[] parts = fromPosition.split(":", 2);
            client.setBinlogFilename(parts[0]);
            client.setBinlogPosition(Long.parseLong(parts[1]));
        }

        AtomicReference<String> currentDatabase = new AtomicReference<>();

        client.registerEventListener(event -> {
            EventHeader header = event.getHeader();
            EventType type = header.getEventType();

            if (type == EventType.TABLE_MAP) {
                TableMapEventData tableMap = event.getData();
                currentDatabase.set(tableMap.getDatabase());
                String tableKey = tableMap.getDatabase() + "." + tableMap.getTable();
                tableMetadataCache.put(tableKey, new TableMetadata(
                        tableMap.getDatabase(), tableMap.getTable(),
                        tableMap.getColumnTypes(), tableMap.getColumnMetadata()));
                // Fetch real column names via JDBC if not cached
                if (!columnNamesCache.containsKey(tableKey) && connectionConfig != null) {
                    fetchColumnNames(tableMap.getDatabase(), tableMap.getTable(), tableKey);
                }
                return;
            }

            String dbName = currentDatabase.get();
            if (dbName == null) return;

            String binlogPos = client.getBinlogFilename() + ":" + header.getHeaderLength();

            switch (type) {
                case EXT_WRITE_ROWS:
                case WRITE_ROWS:
                    handleWriteEvent(event, dbName, binlogPos, queue);
                    break;
                case EXT_UPDATE_ROWS:
                case UPDATE_ROWS:
                    handleUpdateEvent(event, dbName, binlogPos, queue);
                    break;
                case EXT_DELETE_ROWS:
                case DELETE_ROWS:
                    handleDeleteEvent(event, dbName, binlogPos, queue);
                    break;
                default:
                    break;
            }
        });

        client.registerLifecycleListener(new BinaryLogClient.LifecycleListener() {
            @Override
            public void onConnect(BinaryLogClient client) {
                log.info("[CDC] Connected to binlog stream: {}:{}", host, port);
            }
            @Override
            public void onCommunicationFailure(BinaryLogClient client, Exception ex) {
                log.error("[CDC] Communication failure: {}", ex.getMessage());
            }
            @Override
            public void onEventDeserializationFailure(BinaryLogClient client, Exception ex) {
                log.warn("[CDC] Event deserialization failure: {}", ex.getMessage());
            }
            @Override
            public void onDisconnect(BinaryLogClient client) {
                log.info("[CDC] Disconnected from binlog stream: {}:{}", host, port);
            }
        });

        try {
            client.connect(5000);
            this.connectionConfig = config;
            activeClients.put(clientKey, client);
            log.info("[CDC] Binlog client started for {}:{}, watching db={}, table={}", host, port, database, table);
        } catch (Exception e) {
            log.error("[CDC] Failed to start binlog client: {}", e.getMessage());
        }
    }

    private void handleWriteEvent(Event event, String database, String binlogPos,
                                  BlockingQueue<ProbeResponse.CDCEvent> queue) {
        WriteRowsEventData data = event.getData();
        String tableName = resolveTableName(database, data.getTableId());
        if (tableName == null) return;

        for (Serializable[] row : data.getRows()) {
            ProbeResponse.CDCEvent cdcEvent = new ProbeResponse.CDCEvent();
            cdcEvent.setEventType("INSERT");
            cdcEvent.setDatabase(database);
            cdcEvent.setTable(tableName);
            cdcEvent.setAfter(rowToMap(database, tableName, row));
            cdcEvent.setPosition(binlogPos);
            cdcEvent.setTimestamp(String.valueOf(System.currentTimeMillis()));
            queue.offer(cdcEvent);
        }
    }

    private void handleUpdateEvent(Event event, String database, String binlogPos,
                                   BlockingQueue<ProbeResponse.CDCEvent> queue) {
        UpdateRowsEventData data = event.getData();
        String tableName = resolveTableName(database, data.getTableId());
        if (tableName == null) return;

        for (Map.Entry<Serializable[], Serializable[]> entry : data.getRows()) {
            ProbeResponse.CDCEvent cdcEvent = new ProbeResponse.CDCEvent();
            cdcEvent.setEventType("UPDATE");
            cdcEvent.setDatabase(database);
            cdcEvent.setTable(tableName);
            cdcEvent.setBefore(rowToMap(database, tableName, entry.getKey()));
            cdcEvent.setAfter(rowToMap(database, tableName, entry.getValue()));
            cdcEvent.setPosition(binlogPos);
            cdcEvent.setTimestamp(String.valueOf(System.currentTimeMillis()));
            queue.offer(cdcEvent);
        }
    }

    private void handleDeleteEvent(Event event, String database, String binlogPos,
                                   BlockingQueue<ProbeResponse.CDCEvent> queue) {
        DeleteRowsEventData data = event.getData();
        String tableName = resolveTableName(database, data.getTableId());
        if (tableName == null) return;

        for (Serializable[] row : data.getRows()) {
            ProbeResponse.CDCEvent cdcEvent = new ProbeResponse.CDCEvent();
            cdcEvent.setEventType("DELETE");
            cdcEvent.setDatabase(database);
            cdcEvent.setTable(tableName);
            cdcEvent.setBefore(rowToMap(database, tableName, row));
            cdcEvent.setPosition(binlogPos);
            cdcEvent.setTimestamp(String.valueOf(System.currentTimeMillis()));
            queue.offer(cdcEvent);
        }
    }

    private String resolveTableName(String database, long tableId) {
        for (TableMetadata meta : tableMetadataCache.values()) {
            if (meta.database.equals(database)) {
                return meta.table;
            }
        }
        return "table_" + tableId;
    }

    private Map<String, Object> rowToMap(String database, String tableName, Serializable[] row) {
        Map<String, Object> map = new LinkedHashMap<>();
        String tableKey = database + "." + tableName;
        String[] colNames = columnNamesCache.get(tableKey);
        for (int i = 0; i < row.length; i++) {
            String colName = (colNames != null && i < colNames.length) ? colNames[i] : "col_" + i;
            Object value = row[i];
            if (value instanceof byte[]) {
                value = new String((byte[]) value);
            }
            map.put(colName, value);
        }
        return map;
    }

    private void fetchColumnNames(String database, String table, String tableKey) {
        try (Connection conn = buildJdbcConnection(connectionConfig)) {
            String sql = "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? ORDER BY ORDINAL_POSITION";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, database);
                ps.setString(2, table);
                try (ResultSet rs = ps.executeQuery()) {
                    List<String> names = new ArrayList<>();
                    while (rs.next()) {
                        names.add(rs.getString("COLUMN_NAME"));
                    }
                    if (!names.isEmpty()) {
                        columnNamesCache.put(tableKey, names.toArray(new String[0]));
                    }
                }
            }
        } catch (Exception e) {
            log.debug("[CDC] Could not fetch column names for {}.{}: {}", database, table, e.getMessage());
        }
    }

    private Connection buildJdbcConnection(Map<String, Object> config) throws SQLException {
        String host = (String) config.get("host");
        int port = config.get("port") != null ? ((Number) config.get("port")).intValue() : 3306;
        String database = (String) config.getOrDefault("database", "");
        String username = (String) config.get("username");
        String password = (String) config.get("password");

        String url = String.format("jdbc:mysql://%s:%d/%s?useSSL=false&serverTimezone=UTC", host, port, database);
        return DriverManager.getConnection(url, username, password);
    }

    private String buildClientKey(Map<String, Object> config, String database) {
        return config.get("host") + ":" + config.get("port") + "/" + database;
    }

    /**
     * Stop all active binlog clients
     */
    public void shutdown() {
        for (Map.Entry<String, BinaryLogClient> entry : activeClients.entrySet()) {
            try {
                entry.getValue().disconnect();
                log.info("[CDC] Stopped binlog client: {}", entry.getKey());
            } catch (Exception e) {
                log.warn("[CDC] Error stopping binlog client {}: {}", entry.getKey(), e.getMessage());
            }
        }
        activeClients.clear();
        eventQueues.clear();
    }

    private static class TableMetadata {
        final String database;
        final String table;
        final byte[] columnTypes;
        final int[] columnMetadata;

        TableMetadata(String database, String table, byte[] columnTypes, int[] columnMetadata) {
            this.database = database;
            this.table = table;
            this.columnTypes = columnTypes;
            this.columnMetadata = columnMetadata;
        }
    }
}
