package com.lixin.probe.agent.plugin.impl.database;

import com.lixin.probe.agent.plugin.api.CDCPlugin;
import com.lixin.probe.agent.pojo.response.ProbeResponse;
import org.postgresql.PGConnection;
import org.postgresql.replication.LogSequenceNumber;
import org.postgresql.replication.PGReplicationStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.Properties;

/**
 * PostgreSQL WAL CDC 插件
 * 使用 pgjdbc 的逻辑复制 API 监听 PostgreSQL WAL 变更
 *
 * 前置条件：
 * 1. postgresql.conf: wal_level = logical
 * 2. postgresql.conf: max_replication_slots >= 2
 * 3. 需要创建 PUBLICATION: CREATE PUBLICATION probe_cdc FOR ALL TABLES;
 * 4. 需要创建复制槽: 可由本插件自动创建
 */
public class PostgreSQLCDCPlugin implements CDCPlugin {

    private static final Logger log = LoggerFactory.getLogger(PostgreSQLCDCPlugin.class);

    private static final String SLOT_NAME = "probe_cdc_slot";
    private static final String PUBLICATION_NAME = "probe_cdc";
    private static final long STATUS_INTERVAL_MS = 5000;

    private final Map<String, Connection> activeConnections = new ConcurrentHashMap<>();
    private final Map<String, BlockingQueue<ProbeResponse.CDCEvent>> eventQueues = new ConcurrentHashMap<>();

    @Override
    public String getPluginId() { return "postgresql-wal-cdc-plugin"; }

    @Override
    public String getName() { return "PostgreSQL WAL CDC Plugin"; }

    @Override
    public String getSubType() { return "postgresql-wal"; }

    @Override
    public String getVersion() { return "1.0.0"; }

    @Override
    public String getDescription() {
        return "PostgreSQL WAL-based Change Data Capture using logical replication (pgoutput)";
    }

    @Override
    public CompletableFuture<ProbeResponse.CDCMetadata> getMetadata(Map<String, Object> config) {
        return CompletableFuture.supplyAsync(() -> {
            String host = (String) config.get("host");
            int port = config.get("port") != null ? ((Number) config.get("port")).intValue() : 5432;
            String database = (String) config.getOrDefault("database", "");

            ProbeResponse.CDCMetadata metadata = new ProbeResponse.CDCMetadata();
            metadata.setType("CDC");
            metadata.setSubType("postgresql-wal");
            metadata.setHost(host);
            metadata.setPort(port);
            metadata.setDatabaseName(database);
            metadata.setSupportedOperations(List.of("INSERT", "UPDATE", "DELETE"));

            try (Connection conn = buildConnection(config)) {
                // Check wal_level
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery("SHOW wal_level")) {
                    if (rs.next()) {
                        metadata.setServerId(rs.getString("Value"));
                    }
                }
                // Check current LSN
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT pg_current_wal_lsn()")) {
                    if (rs.next()) {
                        metadata.setServerId(metadata.getServerId() + ":" + rs.getString(1));
                    }
                }
            } catch (Exception e) {
                log.warn("[PG CDC] Failed to query WAL status: {}", e.getMessage());
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
            String clientKey = buildClientKey(config);
            BlockingQueue<ProbeResponse.CDCEvent> queue = eventQueues.computeIfAbsent(
                    clientKey, k -> new LinkedBlockingQueue<>(10000));

            if (!activeConnections.containsKey(clientKey)) {
                startWALStream(config, fromPosition, clientKey, queue);
            }

            List<ProbeResponse.CDCEvent> events = new ArrayList<>();
            long deadline = System.currentTimeMillis() + 5000;
            while (events.size() < maxEvents && System.currentTimeMillis() < deadline) {
                ProbeResponse.CDCEvent event = null;
                try { event = queue.poll(500, TimeUnit.MILLISECONDS); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
                if (event != null) {
                    if (table == null || table.isEmpty() || table.equals(event.getTable())) {
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
            try (Connection conn = buildConnection(config)) {
                // Check wal_level is logical
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery("SHOW wal_level")) {
                    if (rs.next()) {
                        String level = rs.getString("Value");
                        if (!"logical".equalsIgnoreCase(level)) {
                            log.warn("[PG CDC] wal_level must be 'logical', current: {}", level);
                            return false;
                        }
                    }
                }
                // Check replication slots config
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery("SHOW max_replication_slots")) {
                    if (rs.next()) {
                        int maxSlots = rs.getInt("Value");
                        if (maxSlots < 1) {
                            log.warn("[PG CDC] max_replication_slots must be >= 1");
                            return false;
                        }
                    }
                }
                // Try to ensure publication exists
                ensurePublication(conn);
                return true;
            } catch (Exception e) {
                log.error("[PG CDC] Connection test failed: {}", e.getMessage());
                return false;
            }
        });
    }

    /**
     * Start WAL logical replication stream
     */
    private void startWALStream(Map<String, Object> config, String fromPosition,
                                String clientKey, BlockingQueue<ProbeResponse.CDCEvent> queue) {
        try {
            Connection conn = buildConnection(config);
            activeConnections.put(clientKey, conn);

            ensurePublication(conn);
            ensureReplicationSlot(conn);

            PGConnection pgConn = conn.unwrap(PGConnection.class);
            LogSequenceNumber startLsn = (fromPosition != null && !fromPosition.isEmpty())
                    ? LogSequenceNumber.valueOf(fromPosition)
                    : LogSequenceNumber.valueOf("0/0");

            Properties slotOptions = new Properties();
            slotOptions.setProperty("proto_version", "1");
            slotOptions.setProperty("publication_names", PUBLICATION_NAME);

            PGReplicationStream stream = pgConn.getReplicationAPI()
                    .replicationStream()
                    .logical()
                    .withSlotName(SLOT_NAME)
                    .withStartPosition(startLsn)
                    .withSlotOptions(slotOptions)
                    .start();

            log.info("[PG CDC] WAL stream started: slot={}, publication={}", SLOT_NAME, PUBLICATION_NAME);

            // Start reader thread
            Thread readerThread = new Thread(() -> {
                try {
                    while (!Thread.interrupted() && activeConnections.containsKey(clientKey)) {
                        ByteBuffer msg = stream.readPending();
                        if (msg != null) {
                            byte[] raw = decodeMessageBytes(msg);
                            if (raw != null) {
                                ProbeResponse.CDCEvent event = parseChangeEvent(raw, stream.getLastAppliedLSN());
                                if (event != null) {
                                    queue.offer(event);
                                }
                            }
                        }
                        stream.setAppliedLSN(stream.getLastReceiveLSN());
                        Thread.sleep(100);
                    }
                } catch (Exception e) {
                    log.error("[PG CDC] Stream reader error: {}", e.getMessage());
                }
            }, "pg-cdc-reader-" + clientKey);
            readerThread.setDaemon(true);
            readerThread.start();

        } catch (Exception e) {
            log.error("[PG CDC] Failed to start WAL stream: {}", e.getMessage());
        }
    }

    private void ensurePublication(Connection conn) throws SQLException {
        // Check if publication exists
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT 1 FROM pg_publication WHERE pubname = '" + PUBLICATION_NAME + "'")) {
            if (!rs.next()) {
                stmt.execute("CREATE PUBLICATION " + PUBLICATION_NAME + " FOR ALL TABLES");
                log.info("[PG CDC] Created publication: {}", PUBLICATION_NAME);
            }
        }
    }

    private void ensureReplicationSlot(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT 1 FROM pg_replication_slots WHERE slot_name = '" + SLOT_NAME + "'")) {
            if (!rs.next()) {
                stmt.execute("SELECT pg_create_logical_replication_slot('" + SLOT_NAME + "', 'pgoutput')");
                log.info("[PG CDC] Created replication slot: {}", SLOT_NAME);
            }
        }
    }

    /**
     * Decode pgoutput protocol message from ByteBuffer
     * pgoutput binary format per PostgreSQL protocol docs
     */
    private byte[] decodeMessageBytes(ByteBuffer msg) {
        if (msg == null) return null;
        byte[] bytes = new byte[msg.remaining()];
        msg.get(bytes);
        return bytes;
    }

    /**
     * Parse pgoutput change event into CDCEvent
     * pgoutput message format (after the initial relation/relation-column messages):
     *   Byte1: message type ('I', 'U', 'D')
     *   Then type-specific payload
     */
    private ProbeResponse.CDCEvent parseChangeEvent(byte[] raw, LogSequenceNumber lsn) {
        if (raw == null || raw.length == 0) return null;

        // pgoutput message types:
        // 'R' = Relation, 'B' = Begin, 'I' = Insert, 'U' = Update, 'D' = Delete, 'T' = Commit, 'C' = Commit (proto v2)
        char msgType = (char) (raw[0] & 0xFF);
        if (msgType != 'I' && msgType != 'U' && msgType != 'D') return null;

        ProbeResponse.CDCEvent event = new ProbeResponse.CDCEvent();
        event.setPosition(lsn != null ? lsn.asString() : "");
        event.setTimestamp(String.valueOf(System.currentTimeMillis()));

        try {
            ByteBuffer buf = ByteBuffer.wrap(raw).order(ByteOrder.BIG_ENDIAN);
            buf.get(); // skip message type byte

            switch (msgType) {
                case 'I': {
                    // Insert: Int32 relationId, Byte1 'N' (new tuple), then tuple data
                    buf.getInt(); // relation Oid
                    char tupleType = (char) (buf.get() & 0xFF);
                    if (tupleType == 'N') {
                        event.setEventType("INSERT");
                        event.setAfter(parseTuple(buf));
                    }
                    break;
                }
                case 'U': {
                    // Update: Int32 relationId, optional 'O' old key, optional 'N' new tuple
                    buf.getInt(); // relation Oid
                    char tupleType = (char) (buf.get() & 0xFF);
                    if (tupleType == 'K' || tupleType == 'O') {
                        // Old key/old tuple
                        event.setBefore(parseTuple(buf));
                        tupleType = (char) (buf.get() & 0xFF);
                    }
                    if (tupleType == 'N') {
                        event.setEventType("UPDATE");
                        event.setAfter(parseTuple(buf));
                    }
                    break;
                }
                case 'D': {
                    // Delete: Int32 relationId, Byte1 'K' (key) or 'O' (old), then tuple data
                    buf.getInt(); // relation Oid
                    char delTupleType = (char) (buf.get() & 0xFF);
                    if (delTupleType == 'K' || delTupleType == 'O') {
                        event.setEventType("DELETE");
                        event.setBefore(parseTuple(buf));
                    }
                    break;
                }
            }
        } catch (Exception e) {
            log.warn("[PG CDC] Failed to parse pgoutput message type={}: {}", msgType, e.getMessage());
            return null;
        }

        if (event.getEventType() == null) return null;
        return event;
    }

    /**
     * Parse a tuple from pgoutput message
     * Format: Int16 ncols, then for each column: Byte1 flag + colData
     *   flag: 'n' = null, 't' = text formatted value (Int32 length + bytes)
     */
    private Map<String, Object> parseTuple(ByteBuffer buf) {
        Map<String, Object> data = new LinkedHashMap<>();
        short ncols = buf.getShort();
        for (int i = 0; i < ncols; i++) {
            char flag = (char) (buf.get() & 0xFF);
            String colName = "col_" + i;
            if (flag == 'n') {
                data.put(colName, null);
            } else if (flag == 't') {
                int len = buf.getInt();
                if (len > 0 && len <= buf.remaining()) {
                    byte[] valBytes = new byte[len];
                    buf.get(valBytes);
                    data.put(colName, new String(valBytes, java.nio.charset.StandardCharsets.UTF_8));
                } else if (len == 0) {
                    data.put(colName, "");
                } else {
                    data.put(colName, null);
                }
            } else if (flag == 'u') {
                // unchanged TOAST value — treat as null for now
                data.put(colName, null);
            }
        }
        return data;
    }

    private Connection buildConnection(Map<String, Object> config) throws SQLException {
        String host = (String) config.get("host");
        int port = config.get("port") != null ? ((Number) config.get("port")).intValue() : 5432;
        String database = (String) config.getOrDefault("database", "");
        String username = (String) config.get("username");
        String password = (String) config.get("password");

        String url = String.format("jdbc:postgresql://%s:%d/%s?replication=database&preferQueryMode=simple",
                host, port, database);
        Properties props = new Properties();
        props.setProperty("user", username);
        props.setProperty("password", password);
        props.setProperty("replication", "database");
        props.setProperty("preferQueryMode", "simple");
        return DriverManager.getConnection(url, props);
    }

    private String buildClientKey(Map<String, Object> config) {
        return config.get("host") + ":" + config.get("port") + "/" + config.get("database");
    }

    /**
     * Stop all active connections
     */
    public void shutdown() {
        for (Map.Entry<String, Connection> entry : activeConnections.entrySet()) {
            try {
                entry.getValue().close();
                log.info("[PG CDC] Stopped WAL connection: {}", entry.getKey());
            } catch (Exception e) {
                log.warn("[PG CDC] Error stopping connection {}: {}", entry.getKey(), e.getMessage());
            }
        }
        activeConnections.clear();
        eventQueues.clear();
    }
}
