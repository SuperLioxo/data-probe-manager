# 数据探针管理系统 — 核心代码导读

> 本文档精选系统中最核心的模块和代码，用于快速理解整体架构与关键实现。

---

## 一、架构总览

```
┌─────────────────────────────────────────────────┐
│              Web Frontend (Vue 3)                │
│         Element Plus + ECharts + Axios           │
└──────────────────────┬──────────────────────────┘
                       │ REST + WebSocket
┌──────────────────────▼──────────────────────────┐
│              Admin Service (Spring Boot)         │
│    JWT · RBAC · WebSocket · 质量过滤 · 同步调度    │
└──────┬─────────────────────────────┬────────────┘
       │                             │
  PostgreSQL                    Redis + MinIO

┌──────────────────────────────────────────────────┐
│              Agent Service (Spring Boot)          │
│   Binlog CDC · WAL CDC · 文件扫描 · 插件系统       │
└──────────────────────────────────────────────────┘
```

**代码规模**：~570 文件，~100,500 行

---

## 二、Admin 核心模块

### 2.1 安全过滤链 — WebConfig

三层拦截器形成请求安全链：

> [WebConfig.java](apps/probe-admin/src/main/java/com/lixin/probe/config/WebConfig.java)

```
请求 → RateLimitInterceptor（登录限流）→ JwtInterceptor（Token 验证）→ PermissionInterceptor（RBAC 权限）→ Controller
```

### 2.2 分布式锁切面 — DistributedLockAspect

基于 Redisson，支持 SpEL 表达式锁键：

> [DistributedLockAspect.java](apps/probe-admin/src/main/java/com/lixin/probe/aspect/DistributedLockAspect.java)

```java
@Around("@annotation(distributedLock)")
public Object around(ProceedingJoinPoint joinPoint, DistributedLock distributedLock) throws Throwable {
    String lockKey = resolveKey(joinPoint, distributedLock.key());  // 解析 SpEL
    RLock lock = redissonClient.getLock(LOCK_PREFIX + lockKey);

    boolean acquired = lock.tryLock(
        distributedLock.waitTime(), distributedLock.leaseTime(), distributedLock.timeUnit());

    if (!acquired) throw new RuntimeException("操作正在执行中，请稍后再试");

    try { return joinPoint.proceed(); }
    finally { if (lock.isHeldByCurrentThread()) lock.unlock(); }
}
```

### 2.3 WebSocket Meta 通道 — MetaProbeWebSocketHandler (~620 行)

双键会话索引 + AES 加密通信：

> [MetaProbeWebSocketHandler.java](apps/probe-admin/src/main/java/com/lixin/probe/websocket/MetaProbeWebSocketHandler.java)

```java
// 双键索引
Map<String, WebSocketSession> sessions;         // probeKey → Session
Map<String, WebSocketSession> sessionsByCode;    // agentCode → Session

void handleTextMessage(WebSocketSession session, TextMessage message) {
    // 1. 解析 JSON → 2. AES 解密 → 3. 提取 type/cmd → 4. 委托 MessageDispatcher
}

// 指令下发
void sendControlCommand(probeKey, type, command);  // 采集/启停/升级
void sendEncryptedMessage(probeKey, code, message); // AES 加密后发送
```

### 2.4 变更检测 — ChangeDetectionServiceImpl

五维快照对比 + CDC 事件处理：

> [ChangeDetectionServiceImpl.java](apps/probe-admin/src/main/java/com/lixin/probe/service/impl/ChangeDetectionServiceImpl.java)

```java
@DistributedLock(key = "'detect:' + #probeKey + ':' + #tableName")
public void saveSnapshotAndDetect(...) {
    // 1. 保存新快照
    // 2. 五维对比：行数增减、大小变化、校验和、索引、更新时间
    // 3. 持久化 change_log
    // 4. 发布 CDCChangeEvent（Spring Event）→ 触发实时同步
}
```

### 2.5 同步任务 — SyncTaskServiceImpl

分布式锁 + 虚拟线程并发执行：

> [SyncTaskServiceImpl.java](apps/probe-admin/src/main/java/com/lixin/probe/service/impl/SyncTaskServiceImpl.java)

```java
@DistributedLock(key = "'sync:' + #task.id", waitTime = 5, leaseTime = 60)
public void executeSync(SyncTask task) {
    // 拉取源数据 → 质量过滤 → 写入目标 → 记录日志
}

private void executeSyncAsync(SyncTask task) {
    Thread.ofVirtual().name("sync-" + task.getId()).start(() -> {
        semaphore.acquire();
        executeSync(task);
        semaphore.release();
    });
}
```

### 2.6 质量过滤引擎 — QualityFilterEngineImpl

六维数据质量校验：

> [QualityFilterEngineImpl.java](apps/probe-admin/src/main/java/com/lixin/probe/service/impl/QualityFilterEngineImpl.java)

```java
boolean checkRule(Map<String, Object> row, QualityRule rule) {
    switch (rule.getRuleType()) {
        case "NOT_NULL":   return value != null && !value.toString().isEmpty();
        case "REGEX":      return Pattern.matches(params.getString("pattern"), value.toString());
        case "RANGE":      return checkRange(value, params);    // BigDecimal 精确比较
        case "ENUM":       return allowedSet.contains(value);
        case "LENGTH":     return len >= minLength && len <= maxLength;
        case "TYPE_CHECK": return checkType(value, params);     // INTEGER/DECIMAL/BOOLEAN/DATE
    }
}
```

---

## 三、Agent 核心模块

### 3.1 插件接口 — DatabasePlugin

SPI 接口，10+ 数据库类型实现：

> [DatabasePlugin.java](apps/probe-agent/src/main/java/com/lixin/probe/agent/plugin/api/DatabasePlugin.java)

```java
public interface DatabasePlugin {
    String getDbType();                            // "mysql"
    Connection getConnection(Map<String, Object> params);
    CompletableFuture<ProbeResponse.Metadata> getMetadata(Connection conn, ProbeRequest req);
    String escapeTableName(String fullTableName);  // MySQL反引号，PG双引号
    default String exportDataAsCsv(...) { ... }    // 默认实现
}

public interface CDCPlugin {
    CompletableFuture<List<CDCEvent>> captureChanges(...);
    default void shutdown() {}                     // 释放连接和资源
}
```

**插件层次**：

```
DatabasePlugin ← MySqlPlugin / PostgreSQLPlugin / OraclePlugin / SQLServerPlugin / SQLitePlugin / DmPlugin
CDCPlugin      ← BinlogCDCPlugin (MySQL) / PostgreSQLCDCPlugin (WAL)
NoSQLPlugin    ← MongoPlugin / RedisPlugin / ElasticsearchPlugin
```

### 3.2 Binlog CDC — BinlogCDCPlugin

MySQL Binlog 流式捕获，生产者-消费者模式。每个客户端使用唯一 `serverId` 避免多实例冲突：

> [BinlogCDCPlugin.java](apps/probe-agent/src/main/java/com/lixin/probe/agent/plugin/impl/database/BinlogCDCPlugin.java)

```java
private static final AtomicInteger SERVER_ID_SEQ = new AtomicInteger(10000);

CompletableFuture<List<CDCEvent>> captureChanges(...) {
    BinaryLogClient client = startBinlogClient(config, fromPosition);
    client.setServerId(SERVER_ID_SEQ.incrementAndGet());  // 唯一 ID，避免冲突

    List<CDCEvent> events = new ArrayList<>();
    while (events.size() < maxEvents && !timeout) {
        CDCEvent event = queue.poll(1, TimeUnit.SECONDS);
        if (event != null) events.add(event);
    }
    return events;  // 每个事件携带 binlogPos 支持断点续传
}
```

### 3.3 PostgreSQL WAL CDC — PostgreSQLCDCPlugin

直接解析 pgoutput 二进制协议：

> [PostgreSQLCDCPlugin.java](apps/probe-agent/src/main/java/com/lixin/probe/agent/plugin/impl/database/PostgreSQLCDCPlugin.java)

```java
PGReplicationStream startWALStream(Map config, String fromPosition) {
    ensurePublication(pgConn, pubName);    // CREATE PUBLICATION ... FOR ALL TABLES
    ensureReplicationSlot(pgConn, slotName);

    PGReplicationStream stream = pgConn.getReplicationAPI()
        .replicationStream().logical().withSlotName(slotName).start();

    // 后台守护线程读取 WAL
    new Thread(() -> {
        while (running) {
            ByteBuffer msg = stream.readPending();
            if (msg != null) parseChangeEvent(msg);  // 解析 I/U/D 二进制
            stream.setAppliedLSN(stream.getLastReceiveLSN());  // 背压信号
        }
    }).start();
}
```

### 3.4 CDC 事件管理 — CDCManager

50,000 事件有界缓冲区 + 5 秒批量质量过滤上报：

> [CDCManager.java](apps/probe-agent/src/main/java/com/lixin/probe/agent/service/CDCManager.java)

```java
BlockingQueue<CDCEvent> eventBuffer = new LinkedBlockingQueue<>(50000);

@Scheduled(fixedDelay = 5000)
public void flushEvents() {
    List<CDCEvent> batch = new ArrayList<>(500);
    eventBuffer.drainTo(batch, 500);

    // Agent 端质量过滤
    for (CDCEvent event : batch) {
        if (qualityValidator.validate(row, rules).isEmpty()) goodEvents.add(event);
        else badRecords.add(buildBadRecord(event));
    }

    restTemplate.postForObject(adminUrl + "/api/agents/" + agentCode + "/cdc-events", goodEvents, ...);
}
```

### 3.5 模块管理 — ModuleManager

热插拔模块生命周期，30 秒同步 Admin 配置。DatabaseModule 启动时自动拉起 CDC 调度器：

> [ModuleManager.java](apps/probe-agent/src/main/java/com/lixin/probe/agent/module/ModuleManager.java) · [DatabaseModule.java](apps/probe-agent/src/main/java/com/lixin/probe/agent/module/DatabaseModule.java)

> [ModuleManager.java](apps/probe-agent/src/main/java/com/lixin/probe/agent/module/ModuleManager.java)

```java
@Scheduled(fixedDelay = 30000)
public void syncModulesWithProbes() {
    Set<ProbeType> currentTypes = probeSyncService.getAllProbeTypes();

    // 启动新增模块
    for (ProbeType type : currentTypes)
        if (!lastSyncedTypes.contains(type)) moduleMap.get(type).start();

    // 停止移除模块（SYSTEM 模块永不停止）
    for (ProbeType type : lastSyncedTypes)
        if (!currentTypes.contains(type) && type != ProbeType.SYSTEM) moduleMap.get(type).stop();

    lastSyncedTypes = currentTypes;
}
```

**DatabaseModule 与 CDC 生命周期**：

```java
// DatabaseModule.java — 启动时拉起 CDC 调度器
public void start() {
    // ... 元数据采集 ...
    cdcScheduler.start();  // 根据 database-config.yml 中 cdcEnabled=true 的实例自动启动 CDC
}

public void stop() {
    cdcScheduler.stop();   // 停止所有 CDC 流，调用 CDCPlugin.shutdown() 释放连接
}
```

**CDC 启用配置** — `database-config.yml` 中每个实例可独立开关：

```yaml
databases:
  - name: "开发库"
    type: "postgresql"
    enabled: true
    cdcEnabled: true       # 启用后自动对该实例进行 CDC 监听
```

### 3.6 消息处理器 — MessageHandler (824 行)

AES 解密 + 命令路由，独立线程池执行：

> [MessageHandler.java](apps/probe-agent/src/main/java/com/lixin/probe/agent/websocket/MessageHandler.java)

```java
ExecutorService commandExecutor = Executors.newFixedThreadPool(4);

void handleMessage(WebSocketSession session, WebSocketMessage<?> msg) {
    commandExecutor.submit(() -> {
        switch (Command.valueOf(cmd)) {
            case PROBE: case FILE_PROBE: case START: case STOP:
            case SHUTDOWN: case UPGRADE:
        }
    });
}
```

---

## 四、Web 前端核心模块

### 4.1 HTTP 请求层 — request.js

JWT 双 Token + 并发刷新排队：

> [request.js](apps/probe-web/src/api/request.js)

```javascript
// 响应拦截器：401 自动刷新 Token
if (error.response?.status === 401 && !error.config._retry) {
  if (isRefreshing) {
    return new Promise((resolve, reject) => {
      failedQueue.push({ resolve, reject })  // 排队等待刷新完成
    }).then(token => { return request(error.config) })
  }
  isRefreshing = true
  const { accessToken } = (await axios.post('/api/auth/refresh', { refreshToken })).data.data
  failedQueue.forEach(p => p.resolve(accessToken))  // 重放排队请求
}
```

### 4.2 全局状态 — store/index.js

Vue 3 reactive 手写 Store（非 Pinia）：

> [index.js (store)](apps/probe-web/src/store/index.js)

```javascript
const state = reactive({
  user: {
    roles: JSON.parse(localStorage.getItem('roles') || '[]'),
    permissions: JSON.parse(localStorage.getItem('permissions') || '[]')
  },
  settings: { general: {}, appearance: {}, notification: {}, security: {}, system: {} }
})

const getters = {
  isLoggedIn: computed(() => !!state.user.id),
  isAdmin: computed(() => state.user.roles.includes('ROLE_ADMIN')),
  isDarkTheme: computed(() => state.settings.appearance?.theme === 'dark'),
}
```

### 4.3 实时监控 — MonitorDashboard.vue (2237 行)

三级性能优化策略：

> [MonitorDashboard.vue](apps/probe-web/src/views/monitoring/MonitorDashboard.vue)

```javascript
// 1. 虚拟滚动：探针 > 50 时仅渲染可见卡片
usingVirtualScroll = probeList.length > 50

// 2. 客户端缓存：Map + 10 秒 TTL
const getCachedMetrics = (id) => {
  const cached = metricsCache.get(id)
  if (cached && Date.now() - cached.timestamp < 10000) return cached.data
  return null
}

// 3. 批量加载：每 3 个探针一批，间隔 100ms
for (let i = 0; i < onlineProbes.length; i += 3) {
  const batch = onlineProbes.slice(i, i + 3)
  await Promise.all(batch.map(p => loadMetrics(p.id)))
  await new Promise(r => setTimeout(r, 100))
}
```

---

## 五、核心数据库表

```sql
-- 探针主表
CREATE TABLE probe (
    probe_key VARCHAR(255) UNIQUE,     -- 探针唯一标识
    type VARCHAR(50),                   -- SYSTEM / FILE / DATABASE
    status VARCHAR(20) DEFAULT 'offline',
    running_status VARCHAR(20),         -- running / stopped
    agent_code VARCHAR(100)             -- 所属 Agent
);

-- 变更日志
CREATE TABLE change_log (
    change_type VARCHAR(30),            -- ROW_INSERT / ROW_DELETE / CHECKSUM_CHANGE / DATA_UPDATE
    probe_key VARCHAR(255),
    table_name VARCHAR(255),
    detected_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 同步任务
CREATE TABLE sync_task (
    sync_mode VARCHAR(20),              -- FULL / INCREMENTAL / CHANGE_BASED
    quality_check_enabled BOOLEAN,      -- 同步前质量过滤
    realtime_sync_enabled BOOLEAN,      -- CDC 实时触发
    cron_expression VARCHAR(100)
);

-- 质量规则
CREATE TABLE quality_rule (
    rule_type VARCHAR(50),              -- NOT_NULL / REGEX / RANGE / ENUM / LENGTH / TYPE_CHECK
    rule_params TEXT,
    auto_fix BOOLEAN,
    fix_action VARCHAR(50)
);

-- 质量不合格记录（汇聚库）
CREATE TABLE aggregation.quality_bad_records (
    row_data JSONB,
    violated_rules JSONB
);
```

---

## 六、设计模式速查

| 模式 | 位置 | 解决的问题 |
|------|------|-----------|
| **策略 + 工厂** | `ProbeHeartbeatStrategy` + `ProbeStrategyFactory` | 数据库探针 60s 超时，文件探针 300s |
| **插件 (SPI)** | `DatabasePlugin` / `CDCPlugin` 10+ 实现 | 新增数据库类型只需实现接口 |
| **抽象工厂** | `ProbeFactory` → `DatabaseProbeFactory` / `FileProbeFactory` | 探针创建逻辑隔离 |
| **装饰器** | `CachingProbeServiceDecorator` → `Logging` → `Monitoring` | 三层横切关注点可开关叠加 |
| **观察者** | `CDCChangeEvent` Spring Events | 检测 → 同步 → 告警解耦 |
| **责任链** | `MessageDispatcher` + 9 个 `MessageHandler` | WebSocket 消息精准路由 |
| **拦截过滤器** | `RateLimit` → `JWT` → `Permission` | HTTP 请求安全链 |
| **生产者-消费者** | CDCManager `BlockingQueue(50000)` | CDC 事件缓冲与批量上报 |
| **模板方法** | `AbstractProbeFactory.createProbe()` | 创建通用流程，子类定制细节 |

---

## 七、数据流

### 7.1 元数据采集

```
前端「采集」→ POST /api/probes/{key}/collect
→ WebSocket COMMAND:PROBE → Agent MessageHandler
→ DatabasePlugin.getMetadata() → JDBC DatabaseMetaData
→ WebSocket 上报 → Admin MessageDispatcher → 存储 → 前端刷新
```

### 7.2 CDC 变更捕获

```
MySQL Binlog → BinlogCDCPlugin → BlockingQueue
→ CDCManager (5s 批量) → QualityValidator (Agent 端过滤)
  ├ 合格 → HTTP POST /api/agents/{code}/cdc-events → Admin change_log
  │        → CDCChangeEvent → ChangeTriggeredSyncListener → 实时同步
  └ 不合格 → quality_bad_records
```

### 7.3 登录认证与 RBAC

```
Login.vue → POST /api/auth/login
→ AuthController: BCrypt 验证 → JWT 双 Token → 返回 roles + permissions
→ localStorage: token / roles / permissions
→ Sidebar.vue: 按 item.adminOnly 逐项过滤菜单（非整组隐藏）
→ TopNav.vue: 系统链接按角色指向不同页面（admin→升级页，普通→设置页）
→ router beforeEach: meta.requireAdmin=true 的路由拦截非管理员
→ Settings.vue: 安全/系统标签仅管理员可见
```

---

> 文档生成时间：2026-05-18
