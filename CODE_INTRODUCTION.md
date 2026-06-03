# 数据探针管理系统 — 代码详细介绍

> 本文档详细介绍了系统的全部源代码结构、核心类设计、关键算法和实现细节，适用于毕业答辩及代码审查。

---

## 目录

- [一、项目总览](#一项目总览)
- [二、技术栈与工程结构](#二技术栈与工程结构)
- [三、数据库设计](#三数据库设计)
- [四、Admin 管理端代码详解](#四admin-管理端代码详解)
  - [4.1 配置层 (config)](#41-配置层-config)
  - [4.2 自定义注解 (annotation)](#42-自定义注解-annotation)
  - [4.3 AOP 切面 (aspect)](#43-aop-切面-aspect)
  - [4.4 实体层 (entity)](#44-实体层-entity)
  - [4.5 工厂模式 (factory)](#45-工厂模式-factory)
  - [4.6 策略模式 (strategy)](#46-策略模式-strategy)
  - [4.7 WebSocket 通信层 (websocket)](#47-websocket-通信层-websocket)
  - [4.8 业务服务层 (service)](#48-业务服务层-service)
  - [4.9 控制器层 (controller)](#49-控制器层-controller)
  - [4.10 调度器 (scheduler)](#410-调度器-scheduler)
  - [4.11 事件引擎 (engine)](#411-事件引擎-engine)
- [五、Agent 探针端代码详解](#五agent-探针端代码详解)
  - [5.1 插件系统 (plugin)](#51-插件系统-plugin)
  - [5.2 模块管理 (module)](#52-模块管理-module)
  - [5.3 核心服务 (service)](#53-核心服务-service)
  - [5.4 WebSocket 客户端 (websocket)](#54-websocket-客户端-websocket)
  - [5.5 服务发现 (discovery)](#55-服务发现-discovery)
  - [5.6 系统指标采集 (collector)](#56-系统指标采集-collector)
- [六、Web 前端代码详解](#六web-前端代码详解)
  - [6.1 项目架构](#61-项目架构)
  - [6.2 路由系统](#62-路由系统)
  - [6.3 HTTP 请求层](#63-http-请求层)
  - [6.4 登录与权限（RBAC）](#64-登录与权限rbac)
  - [6.5 状态管理](#65-状态管理)
  - [6.6 页面组件详解](#66-页面组件详解)
  - [6.7 组合式函数 (composables)](#67-组合式函数-composables)
  - [6.8 工具函数 (utils)](#68-工具函数-utils)
- [七、设计模式总结](#七设计模式总结)
- [八、数据流全景](#八数据流全景)
- [九、安全设计](#九安全设计)
- [十、部署架构](#十部署架构)

---

## 一、项目总览

### 1.1 项目定位

**数据探针管理系统**是一个分布式数据采集与汇聚管理平台。将分散在各业务端、服务器、文件系统中的数据，通过探针（Agent）自动采集、质量过滤、实时同步，统一汇聚到管理平台后端，实现集中查看、查询与管理。

### 1.2 代码规模

| 模块 | 文件数 | 代码行数 | 语言 |
|------|--------|----------|------|
| probe-admin（管理端） | 353 个 Java 文件 | ~43,593 行 | Java 21 |
| probe-agent（探针端） | 113 个 Java 文件 | ~26,886 行 | Java 21 |
| probe-web（前端） | 80 个 Vue/JS 文件 | ~29,833 行 | JavaScript |
| SQL Schema | 24 个脚本 | ~1,237 行 | SQL |
| **合计** | **~570 个源文件** | **~100,549 行** | — |

### 1.3 核心能力

| 能力 | 说明 |
|------|------|
| 多数据源适配 | 插件化架构，支持 MySQL、PostgreSQL、Oracle、SQL Server、SQLite、达梦、MongoDB、Redis、ES、Kafka |
| 变更数据捕获 (CDC) | MySQL Binlog / PostgreSQL WAL 实时行级变更监听 |
| 数据质量过滤 | 六维校验：完整性、格式、范围、枚举、长度、类型 |
| 数据自动同步 | 实时同步（CDC 触发）+ 定时同步（Cron 调度） |
| 文件管理 | 文件探针递归扫描、结构化文件解析、MinIO/本地存储 |
| 告警引擎 | 数据变化告警 + 数据源连通性告警，多渠道通知 |
| 安全认证 | JWT 双 Token + RBAC 权限 + AES 加密通信 |

---

## 二、技术栈与工程结构

### 2.1 技术选型

```
┌─────────────────────────────────────────────────────────────────┐
│                         Web Frontend                            │
│              Vue 3.4 + Vite 5.4 + Element Plus 2.5             │
│              ECharts 5.5 + Axios + Vue Router + Sass           │
└──────────────────────────────┬──────────────────────────────────┘
                               │ HTTP REST / WebSocket
┌──────────────────────────────▼──────────────────────────────────┐
│                        Admin Service                            │
│              Java 21 + Spring Boot 3.4.2 + PostgreSQL 16       │
│              MyBatis-Plus + Druid + JWT + Redisson              │
│              Spring WebSocket + Spring Events                   │
└──────┬──────────────────────────────────┬───────────────────────┘
       │                                  │
PostgreSQL 16                       Redis 7 + MinIO

┌─────────────────────────────────────────────────────────────────┐
│                         Agent Service                           │
│              Spring Boot 3.4.2 + mysql-binlog-connector         │
│              OSHI + Netty 4.1 + pgjdbc replication API          │
│              多数据库 JDBC 驱动 + MinIO SDK                      │
└─────────────────────────────────────────────────────────────────┘
```

### 2.2 工程目录

```
data-probe-manager/
├── apps/
│   ├── probe-admin/                  # Admin 管理端 (Spring Boot)
│   │   ├── pom.xml                   # Maven 依赖配置
│   │   └── src/main/java/com/lixin/probe/
│   │       ├── config/               # 配置类（24个）
│   │       ├── annotation/           # 自定义注解（4个）
│   │       ├── aspect/               # AOP 切面（3个）
│   │       ├── entity/               # 数据库实体（40个）
│   │       ├── mapper/               # MyBatis-Plus Mapper
│   │       ├── service/              # 业务接口 + impl（38个实现）
│   │       ├── controller/           # REST 控制器（37个）
│   │       ├── websocket/            # WebSocket 服务端（7个 + handler/）
│   │       ├── strategy/             # 策略模式（1接口 + 工厂 + 2实现）
│   │       ├── factory/              # 工厂模式（1接口 + 抽象类 + 管理器 + 2实现）
│   │       ├── scheduler/            # 定时任务调度器
│   │       ├── engine/               # 告警规则引擎
│   │       ├── interceptor/          # 拦截器
│   │       └── exception/            # 全局异常处理
│   │
│   ├── probe-agent/                  # Agent 探针端 (数据采集)
│   │   ├── pom.xml
│   │   └── src/main/java/com/lixin/probe/agent/
│   │       ├── config/               # 配置管理（9个）
│   │       ├── controller/           # REST 控制器（8个）
│   │       ├── service/              # 业务服务（8个）
│   │       ├── plugin/               # 插件系统
│   │       │   ├── api/              #   DatabasePlugin, CDCPlugin, FilePlugin 等 6 个接口
│   │       │   └── impl/             #   10+ 数据库/NoSQL/MQ/协议插件实现
│   │       ├── module/               # 模块管理（9个）
│   │       ├── websocket/            # WebSocket 客户端（9个）
│   │       ├── discovery/            # UDP 服务发现（3个）
│   │       ├── collector/            # 系统指标采集（1个）
│   │       ├── connection/           # 数据库连接池（1个）
│   │       ├── probe/                # 探针管理（4个）
│   │       ├── reporter/             # 插件状态上报（2个）
│   │       └── sync/                 # 数据同步（1个）
│   │
│   └── probe-web/                    # Web 前端 (Vue.js)
│       ├── package.json
│       ├── vite.config.js
│       └── src/
│           ├── api/                  # API 客户端（23个模块）
│           ├── views/                # 页面组件（20+个）
│           │   ├── dashboard/        #   仪表板
│           │   ├── collection/       #   数据采集
│           │   ├── sync/             #   同步任务
│           │   ├── quality/          #   质量管理
│           │   ├── monitoring/       #   实时监控
│           │   └── system/           #   系统管理
│           ├── layouts/              # 页面布局（3个）
│           ├── components/           # 公共组件（9个）
│           ├── composables/          # 组合式函数（3个）
│           ├── router/               # 路由配置
│           ├── store/                # 状态管理
│           └── utils/                # 工具函数（7个）
│
├── config/
│   ├── app/                          # 应用配置
│   ├── infra/database/               # 数据库配置
│   └── security/                     # 安全配置（JWT、加密密钥）
│
├── init/mysql/                       # MySQL 测试库初始化 SQL
├── monitoring/grafana/               # Grafana 监控配置
├── helm/                             # Kubernetes Helm Charts
├── tests/                            # 测试（集成测试 + 单元测试）
├── docker-compose.yml                # Docker Compose 编排（6 个服务）
├── .env                              # 环境变量
└── pom.xml                           # Maven 父 POM
```

---

## 三、数据库设计

系统共涉及 **24 张数据表**，分为以下几大类。

### 3.1 探针管理表

```sql
-- 探针主表：存储所有探针的基本信息
CREATE TABLE probe (
    id BIGSERIAL PRIMARY KEY,
    probe_key VARCHAR(255) UNIQUE NOT NULL,   -- 探针唯一标识（如 AGENT-database-xxx-xxx）
    name VARCHAR(255) NOT NULL,                -- 探针名称
    type VARCHAR(50) NOT NULL,                 -- 类型：SYSTEM / FILE / DATABASE
    status VARCHAR(20) DEFAULT 'offline',      -- 连接状态：online / offline / error / disabled
    running_status VARCHAR(20),                -- 运行状态：running / stopped
    agent_code VARCHAR(100),                   -- 所属 Agent 编码
    host_ip VARCHAR(50),                       -- 主机 IP
    port INTEGER,                              -- 端口
    version VARCHAR(50),                       -- 软件版本号
    config TEXT,                               -- JSON 格式配置
    collect_interval INTEGER DEFAULT 60,       -- 采集间隔（秒）
    last_heartbeat TIMESTAMP,                  -- 最后心跳时间
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

```sql
-- 文件探针表：文件类型探针的专属配置
CREATE TABLE file_probe (
    id BIGSERIAL PRIMARY KEY,
    probe_key VARCHAR(255) UNIQUE NOT NULL,
    scan_paths TEXT,                  -- 扫描路径（多个路径逗号分隔）
    file_extensions TEXT,             -- 文件扩展名过滤
    recursive BOOLEAN DEFAULT TRUE,  -- 是否递归扫描
    max_depth INTEGER DEFAULT 10,    -- 最大扫描深度
    min_file_size BIGINT DEFAULT 0,  -- 最小文件大小（字节）
    max_file_size BIGINT DEFAULT 104857600,  -- 最大文件大小（100MB）
    calculate_md5 BOOLEAN DEFAULT FALSE,     -- 是否计算 MD5
    include_hidden BOOLEAN DEFAULT FALSE,    -- 是否包含隐藏文件
    status VARCHAR(20) DEFAULT 'offline',
    file_count INTEGER DEFAULT 0,            -- 文件数量
    total_size BIGINT DEFAULT 0              -- 总大小
);
```

### 3.2 元数据表

```sql
-- 数据库元信息
CREATE TABLE database_metadata (
    id BIGSERIAL PRIMARY KEY,
    probe_key VARCHAR(255) NOT NULL,
    database_type VARCHAR(50),    -- mysql / postgresql / oracle 等
    database_name VARCHAR(255),
    version VARCHAR(50),
    charset VARCHAR(50),
    collation VARCHAR(50),
    url VARCHAR(512)
);

-- 表信息
CREATE TABLE table_info (
    id BIGSERIAL PRIMARY KEY,
    probe_key VARCHAR(255) NOT NULL,
    table_name VARCHAR(255) NOT NULL,
    engine VARCHAR(50),           -- 存储引擎
    row_count BIGINT,             -- 行数
    data_size BIGINT,             -- 数据大小
    index_size BIGINT,            -- 索引大小
    total_size BIGINT             -- 总大小
);

-- 列信息
CREATE TABLE column_info (
    id BIGSERIAL PRIMARY KEY,
    probe_key VARCHAR(255) NOT NULL,
    table_name VARCHAR(255) NOT NULL,
    column_name VARCHAR(255) NOT NULL,
    data_type VARCHAR(100),
    column_type VARCHAR(255),
    is_nullable BOOLEAN,
    key_type VARCHAR(20),         -- PRI / UNI / MUL 等
    default_value VARCHAR(255),
    extra VARCHAR(255),
    comment TEXT
);
```

### 3.3 数据同步表

```sql
-- 同步任务配置表
CREATE TABLE sync_task (
    id BIGSERIAL PRIMARY KEY,
    task_name VARCHAR(200) NOT NULL,
    source_probe_key VARCHAR(255) NOT NULL,    -- 源探针
    source_table_name VARCHAR(255),             -- 源表名
    target_type VARCHAR(50) NOT NULL,           -- 目标类型：DATABASE / MINIO / API
    target_config TEXT NOT NULL,                -- 目标连接配置（JSON）
    sync_mode VARCHAR(20) DEFAULT 'INCREMENTAL', -- FULL / INCREMENTAL / CHANGE_BASED
    cron_expression VARCHAR(100),               -- Cron 表达式
    conflict_strategy VARCHAR(20) DEFAULT 'UPSERT', -- INSERT / UPSERT / SKIP
    quality_check_enabled BOOLEAN,              -- 是否启用质量检查
    realtime_sync_enabled BOOLEAN,              -- 是否启用实时 CDC 同步
    realtime_sync_config TEXT,                  -- 实时同步配置（JSON）
    enabled BOOLEAN DEFAULT TRUE
);

-- 同步执行日志表
CREATE TABLE sync_log (
    id BIGSERIAL PRIMARY KEY,
    task_id BIGINT NOT NULL,
    sync_mode VARCHAR(20),
    start_time TIMESTAMP,
    end_time TIMESTAMP,
    status VARCHAR(20),           -- RUNNING / SUCCESS / FAILED
    rows_processed BIGINT DEFAULT 0,
    rows_failed BIGINT DEFAULT 0,
    error_message TEXT
);
```

### 3.4 数据质量表

```sql
-- 质量规则表
CREATE TABLE quality_rule (
    id BIGSERIAL PRIMARY KEY,
    rule_name VARCHAR(200) NOT NULL,
    probe_key VARCHAR(255),
    database_name VARCHAR(255),
    table_name VARCHAR(255),
    column_name VARCHAR(255),
    rule_type VARCHAR(50) NOT NULL,    -- NOT_NULL / REGEX / RANGE / ENUM / LENGTH / TYPE_CHECK
    rule_params TEXT NOT NULL,          -- JSON 规则参数
    severity VARCHAR(20) DEFAULT 'WARNING',  -- ERROR / WARNING / INFO
    enabled BOOLEAN DEFAULT TRUE,
    auto_fix BOOLEAN DEFAULT FALSE,
    fix_action VARCHAR(50),            -- SET_DEFAULT / TRIM / UPPERCASE / LOWERCASE / REPLACE / NULLIFY
    fix_params TEXT
);

-- 质量检查报告表
CREATE TABLE quality_report (
    id BIGSERIAL PRIMARY KEY,
    rule_id BIGINT,
    probe_key VARCHAR(255),
    table_name VARCHAR(255),
    column_name VARCHAR(255),
    row_identifier VARCHAR(512),
    violation_detail TEXT,
    check_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### 3.5 变更检测与告警表

```sql
-- 数据快照表（用于快照对比检测变更）
CREATE TABLE data_snapshot (
    id BIGSERIAL PRIMARY KEY,
    probe_key VARCHAR(255) NOT NULL,
    database_name VARCHAR(255),
    table_name VARCHAR(255) NOT NULL,
    row_count BIGINT,
    data_size BIGINT,
    index_size BIGINT,
    data_checksum VARCHAR(64),
    max_update_time VARCHAR(100),
    snapshot_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 变更日志表
CREATE TABLE change_log (
    id BIGSERIAL PRIMARY KEY,
    probe_key VARCHAR(255) NOT NULL,
    table_name VARCHAR(255) NOT NULL,
    change_type VARCHAR(30) NOT NULL,    -- ROW_INSERT / ROW_DELETE / SIZE_CHANGE / CHECKSUM_CHANGE / DATA_UPDATE
    change_detail TEXT,
    affected_rows BIGINT DEFAULT 0,
    snapshot_before_id BIGINT,
    snapshot_after_id BIGINT,
    detected_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 变化告警配置表
CREATE TABLE change_alert_config (
    id BIGSERIAL PRIMARY KEY,
    alert_name VARCHAR(200) NOT NULL,
    probe_key VARCHAR(255),
    table_name VARCHAR(255),
    change_types VARCHAR(255),          -- 监控的变更类型
    threshold_rows BIGINT DEFAULT 100,  -- 触发告警的行数阈值
    alert_level VARCHAR(20) DEFAULT 'WARNING',
    notify_channels VARCHAR(100) DEFAULT 'LOG',  -- LOG / WEBHOOK
    enabled BOOLEAN DEFAULT TRUE
);

-- 变化告警记录表
CREATE TABLE change_alert_record (
    id BIGSERIAL PRIMARY KEY,
    alert_config_id BIGINT,
    probe_key VARCHAR(255) NOT NULL,
    table_name VARCHAR(255),
    change_type VARCHAR(30),
    change_detail TEXT,
    affected_rows BIGINT,
    alert_level VARCHAR(20),
    notify_channel VARCHAR(100),
    status VARCHAR(20) DEFAULT 'PENDING',  -- PENDING / RESOLVED
    created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    resolved_time TIMESTAMP
);
```

### 3.6 汇聚库（aggregation schema）

汇聚库使用独立的 PostgreSQL Schema，用于存储从所有数据源汇聚而来的统一数据。

```sql
CREATE SCHEMA IF NOT EXISTS aggregation;

-- 数据源注册表
CREATE TABLE aggregation.data_source_registry (
    id BIGSERIAL PRIMARY KEY,
    source_id BIGINT NOT NULL,
    source_name VARCHAR(200) NOT NULL,
    source_type VARCHAR(50) NOT NULL,
    database_type VARCHAR(50),
    host VARCHAR(200), port INTEGER,
    database_name VARCHAR(200),
    agent_code VARCHAR(100),
    status VARCHAR(20) DEFAULT 'active'
);

-- 聚合表元数据
CREATE TABLE aggregation.table_metadata (
    id BIGSERIAL PRIMARY KEY,
    source_id BIGINT NOT NULL,
    database_name VARCHAR(200),
    table_name VARCHAR(200) NOT NULL,
    row_count BIGINT,
    data_size BIGINT,
    column_count INTEGER,
    table_comment TEXT,
    synced_at TIMESTAMP
);

-- 聚合列元数据
CREATE TABLE aggregation.column_metadata (
    id BIGSERIAL PRIMARY KEY,
    table_metadata_id BIGINT NOT NULL REFERENCES aggregation.table_metadata(id),
    column_name VARCHAR(200) NOT NULL,
    column_type VARCHAR(100),
    column_length INTEGER,
    is_nullable BOOLEAN DEFAULT TRUE,
    is_primary_key BOOLEAN DEFAULT FALSE,
    column_comment TEXT,
    ordinal_position INTEGER
);

-- 文件注册表
CREATE TABLE aggregation.file_registry (
    id BIGSERIAL PRIMARY KEY,
    source_id BIGINT,
    file_name VARCHAR(500) NOT NULL,
    file_path TEXT,
    file_size BIGINT,
    file_extension VARCHAR(20),
    file_md5 VARCHAR(64),
    storage_path TEXT,
    aggregation_table VARCHAR(200),
    status VARCHAR(20) DEFAULT 'active'
);

-- 质量不合格记录（JSONB 存储原始数据）
CREATE TABLE aggregation.quality_bad_records (
    id BIGSERIAL PRIMARY KEY,
    sync_task_id BIGINT,
    source_id BIGINT,
    table_name VARCHAR(200),
    row_data JSONB,           -- 原始行数据
    violated_rules JSONB,     -- 违反的规则列表
    rejection_reason TEXT,
    detected_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### 3.7 系统管理表

```sql
-- 用户表
CREATE TABLE "user" (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(100) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,    -- BCrypt 加密
    role VARCHAR(50) DEFAULT 'USER',
    enabled BOOLEAN DEFAULT TRUE
);

-- 审计日志表
CREATE TABLE audit_log (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(100),
    username VARCHAR(100),
    operation VARCHAR(50),        -- LOGIN / CREATE / UPDATE / DELETE / QUERY
    resource_type VARCHAR(50),
    resource_id VARCHAR(100),
    module VARCHAR(50),           -- AUTH / PROBE / SYNC / QUALITY / ALERT
    level VARCHAR(20),            -- INFO / WARN / ERROR / CRITICAL
    description TEXT,
    ip_address VARCHAR(50),
    request_url VARCHAR(512),
    request_method VARCHAR(10),
    request_params TEXT,
    response_code INT,
    execution_time BIGINT,         -- 执行耗时（毫秒）
    is_exception BOOLEAN DEFAULT FALSE,
    exception_message TEXT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 系统设置表
CREATE TABLE settings (
    id BIGSERIAL PRIMARY KEY,
    setting_key VARCHAR(200) UNIQUE NOT NULL,
    setting_value TEXT,
    category VARCHAR(50),          -- general / appearance / notification / security
    description TEXT
);

-- 告警渠道表
CREATE TABLE alert_channel (
    id BIGSERIAL PRIMARY KEY,
    channel_name VARCHAR(200) NOT NULL,
    channel_type VARCHAR(50),      -- LOG / WEBHOOK
    config TEXT,                    -- JSON 配置
    enabled BOOLEAN DEFAULT TRUE
);
```

---

## 四、Admin 管理端代码详解

### 4.1 配置层 (config)

Admin 共有 **24 个配置类**，负责 Spring Boot 应用的各项配置。

#### 4.1.1 WebConfig — Web 层拦截器链

> [WebConfig.java](apps/probe-admin/src/main/java/com/lixin/probe/config/WebConfig.java)

```

WebConfig 注册了三层 HTTP 拦截器，形成安全过滤链：

```
请求 → RateLimitInterceptor → JwtInterceptor → PermissionInterceptor → Controller
```

| 拦截器 | 作用 | 拦截范围 |
|--------|------|----------|
| `RateLimitInterceptor` | Redis 滑动窗口限流（60s/5次，超限锁定 300s） | 仅 `/api/auth/login` |
| `JwtInterceptor` | JWT Token 验证、Token 黑名单检查、解析用户信息 | 所有 `/api/**` |
| `PermissionInterceptor` | 检查 `@RequirePermission` 注解的 RBAC 权限 | 所有 `/api/**` |

排除路径：Agent 同步端点（CDC、心跳）不经过 JWT 认证，Agent 通过 WebSocket 密钥认证。

#### 4.1.2 WebSocketConfig — WebSocket 端点注册

> [WebSocketConfig.java](apps/probe-admin/src/main/java/com/lixin/probe/config/WebSocketConfig.java)

```

注册三个 WebSocket 端点：

| 端点 | Handler | 用途 | 协议 |
|------|---------|------|------|
| `/ws/file` | `FileProbeWebSocketHandler` | 文件扫描结果上报 | SockJS |
| `/ws/meta` | `MetaProbeWebSocketHandler` | 元数据/控制/心跳 | 原生 WebSocket |
| `/ws/metrics` | `MetricsWebSocketHandler` | 实时指标推送 | 原生 WebSocket |

CORS 配置通过 `cors.allowed-origins` 环境变量控制，支持严格 Origin 验证模式。

#### 4.1.3 DecoratorConfig — 装饰器链组装

> [DecoratorConfig.java](apps/probe-admin/src/main/java/com/lixin/probe/config/DecoratorConfig.java)

```

通过 Spring Bean 装饰器模式，为 `ProbeService` 叠加三层横切关注点：

```
Controller 调用
    → MonitoringProbeServiceDecorator（指标采集）
        → LoggingProbeServiceDecorator（操作日志）
            → CachingProbeServiceDecorator（Redis 缓存）
                → ProbeServiceImpl（核心业务）
```

每层装饰器通过 `probe.decorator.cache/logging/monitoring.enabled` 配置独立开关，默认全部启用。Controller 通过 `@Qualifier("decoratedProbeService")` 注入装饰后的 Bean。

#### 4.1.4 其他配置类

| 配置类 | 职责 |
|--------|------|
| `AsyncConfig` | 异步任务线程池配置 |
| `AuditLogConfig` | 审计日志开关、保留天数、敏感参数列表 |
| `BusinessMetricsConfig` | Prometheus 业务指标注册 |
| `DatabaseSchemaUpdater` | 启动时自动执行 SQL 脚本更新数据库结构 |
| `MybatisPlusConfig` | MyBatis-Plus 分页插件（配置 `PaginationInnerInterceptor(DbType.POSTGRE_SQL)`）、Mapper 扫描路径 |
| `RedisConfig` / `RedisCacheConfig` | Redis 序列化、缓存 TTL 配置 |
| `SwaggerConfig` | SpringDoc / Swagger UI API 文档配置 |
| `RestTemplateConfig` | HTTP 客户端超时、重试配置 |
| `RetryConfig` | Spring Retry 重试策略配置 |

### 4.2 自定义注解 (annotation)

系统定义了 4 个自定义注解，实现声明式编程。

#### 4.2.1 @Audited — 审计日志

> [Audited.java](apps/probe-admin/src/main/java/com/lixin/probe/annotation/Audited.java)

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Audited {
    String operation() default "";     // 操作类型：LOGIN / CREATE / UPDATE / DELETE
    String module() default "";        // 模块：AUTH / PROBE / SYNC / QUALITY
    String description() default "";   // 操作描述
}

// 使用示例
@Audited(operation = "CREATE", module = "Probe", description = "创建探针")
@PostMapping
public Result<Probe> create(@RequestBody ProbeCreateRequest request) { ... }
```

#### 4.2.2 @DistributedLock — 分布式锁

> [DistributedLock.java](apps/probe-admin/src/main/java/com/lixin/probe/annotation/DistributedLock.java)

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DistributedLock {
    String key();                      // 锁键，支持 SpEL：#task.id, #probeKey
    long waitTime() default 5;         // 等待获取锁的最大时间（秒）
    long leaseTime() default 30;       // 持有锁的最大时间（秒）
    TimeUnit timeUnit() default TimeUnit.SECONDS;
}

// 使用示例
@DistributedLock(key = "'detect:' + #probeKey + ':' + #tableName")
public void saveSnapshotAndDetect(String probeKey, String tableName, ...) { ... }
```

基于 Redisson 实现，防止多实例并发执行同一操作（如同一张表的变更检测）。

#### 4.2.3 @RequirePermission — 权限控制

```java
// 使用示例
@RequirePermission(Permissions.PROBE_VIEW)
@GetMapping
public Result<PageResult<Probe>> list(...) { ... }

@RequirePermission(Permissions.PROBE_CREATE)
@PostMapping
public Result<Probe> create(...) { ... }
```

#### 4.2.4 @RateLimit — 接口限流

> [RateLimit.java](apps/probe-admin/src/main/java/com/lixin/probe/annotation/RateLimit.java)

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {
    int permits() default 10;          // 允许次数
    int seconds() default 60;          // 时间窗口（秒）
}
```

### 4.3 AOP 切面 (aspect)

三个 AOP 切面分别处理自定义注解的逻辑。

#### 4.3.1 AuditLogAspect — 审计日志切面

> [AuditLogAspect.java](apps/probe-admin/src/main/java/com/lixin/probe/aspect/AuditLogAspect.java)

```

拦截所有 `@Audited` 注解方法：

1. 方法执行前记录请求参数（排除 `password`、`token`、`secret`、`key` 等敏感字段）
2. 方法执行后记录响应码、执行耗时
3. 异常时记录异常信息
4. 异步写入 `audit_log` 表
5. 参数值最大长度 2000 字符，超长截断

#### 4.3.2 DistributedLockAspect — 分布式锁切面

> [DistributedLockAspect.java](apps/probe-admin/src/main/java/com/lixin/probe/aspect/DistributedLockAspect.java)

```

拦截 `@DistributedLock` 注解方法：

1. 解析 SpEL 表达式获取锁键（如 `detect:AGENT-database:users`）
2. 通过 Redisson 获取分布式锁（`RLock`）
3. 获取锁超时则抛出异常
4. 方法执行完毕或异常后自动释放锁
5. 支持锁的自动续期（看门狗机制）

#### 4.3.3 RateLimitAspect — 限流切面

> [RateLimitAspect.java](apps/probe-admin/src/main/java/com/lixin/probe/aspect/RateLimitAspect.java)

```

拦截 `@RateLimit` 注解方法：

1. 基于 Redis `INCR` + `EXPIRE` 实现滑动窗口计数
2. 超过阈值返回 HTTP 429 Too Many Requests
3. 限流键格式：`rate_limit:{方法名}:{客户端IP}`

### 4.4 实体层 (entity)

系统共定义了 **40 个实体类**，核心实体如下。

#### 4.4.1 Probe — 探针实体

> [Probe.java](apps/probe-admin/src/main/java/com/lixin/probe/entity/Probe.java)

```java
@Data @Builder @NoArgsConstructor @AllArgsConstructor
@TableName("probe")
public class Probe implements Serializable {
    @TableId(type = IdType.AUTO)
    private Long id;

    @Size(max = 100)
    private String probeKey;            // 探针唯一标识

    @NotBlank @Size(min = 2, max = 100)
    private String name;                // 探针名称

    @Pattern(regexp = "^(SYSTEM|FILE|DATABASE)$")
    @TableField(value = "\"type\"")
    private String type;                // 探针类型

    @Pattern(regexp = "^(online|offline|error|disabled)$")
    private String status;              // 连接状态

    @Pattern(regexp = "^(running|stopped)$")
    private String runningStatus;       // 运行状态（独立于连接状态）

    private String agentCode;           // 所属 Agent 编码
    private String hostIp;              // 主机 IP
    private Integer port;               // 端口
    private String version;             // 软件版本
    private String config;              // JSON 配置

    @Min(1) @Max(3600)
    private Integer collectInterval;    // 采集间隔（秒）

    private LocalDateTime lastHeartbeat;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
```

**设计要点**：
- `status` 和 `runningStatus` 分离设计：一个探针可以 WebSocket 在线（`online`）但未在采集数据（`stopped`）
- `agentCode` 字段实现探针与 Agent 的显式关联
- 使用 JSR 303 注解进行参数校验

#### 4.4.2 SyncTask — 同步任务实体

> [SyncTask.java](apps/probe-admin/src/main/java/com/lixin/probe/entity/SyncTask.java)

```java
@TableName("sync_task")
public class SyncTask implements Serializable {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String taskName;
    private String sourceProbeKey;
    private String sourceTableName;
    private String targetType;           // DATABASE / MINIO / API
    private String targetConfig;         // JSON 目标连接配置
    private String syncMode;             // FULL / INCREMENTAL / CHANGE_BASED
    private String cronExpression;       // Cron 表达式
    private String conflictStrategy;     // INSERT / UPSERT / SKIP
    private Boolean qualityCheckEnabled; // 是否启用质量检查
    private Boolean realtimeSyncEnabled; // 是否启用实时同步
    private String realtimeSyncConfig;   // 实时同步配置（JSON）
    private String lastSyncPosition;     // 上次同步位置
    private LocalDateTime lastSyncTime;
    private String lastSyncStatus;       // RUNNING / SUCCESS / FAILED
    private LocalDateTime nextSyncTime;
    private Boolean enabled;
}
```

#### 4.4.3 QualityRule — 质量规则实体

> [QualityRule.java](apps/probe-admin/src/main/java/com/lixin/probe/entity/QualityRule.java)

```java
@TableName("quality_rule")
public class QualityRule implements Serializable {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String ruleName;
    private String probeKey;
    private String databaseName;
    private String tableName;
    private String columnName;
    private String ruleType;       // NOT_NULL / REGEX / RANGE / ENUM / LENGTH / TYPE_CHECK / CUSTOM_SQL
    private String ruleParams;     // JSON 规则参数
    private String severity;       // ERROR / WARNING / INFO
    private Boolean enabled;
    private Boolean autoFix;
    private String fixAction;      // SET_DEFAULT / TRIM / UPPERCASE / LOWERCASE / REPLACE / NULLIFY
    private String fixParams;      // JSON 修复参数
}
```

#### 4.4.4 其他重要实体

| 实体 | 表名 | 说明 |
|------|------|------|
| `Agent` | `agent` | Agent 注册信息 |
| `DatabaseConnection` | `database_connection` | 数据库连接配置（加密存储密码） |
| `FileMetadata` | `file_metadata` | 文件元数据 |
| `FileProbe` | `file_probe` | 文件探针配置 |
| `ChangeLog` | `change_log` | 数据变更日志 |
| `DataSnapshot` | `data_snapshot` | 数据快照 |
| `ChangeAlertConfig` | `change_alert_config` | 变化告警配置 |
| `ChangeAlertRecord` | `change_alert_record` | 告警记录 |
| `AlertChannel` | `alert_channel` | 告警渠道 |
| `AuditLog` | `audit_log` | 审计日志 |
| `User` | `user` | 用户 |
| `Permission` | `permission` | 权限 |
| `Settings` | `settings` | 系统设置 |
| `ProbeGroup` | `probe_group` | 探针分组 |

### 4.5 工厂模式 (factory)

用于创建不同类型的探针实例，采用 **抽象工厂 + 工厂管理器** 模式。

#### 4.5.1 ProbeFactory 接口

> [ProbeFactory.java](apps/probe-admin/src/main/java/com/lixin/probe/factory/ProbeFactory.java)

```java
public interface ProbeFactory {
    Probe createProbe(ProbeCreateRequest request);       // 创建探针
    List<Probe> createProbes(List<ProbeCreateRequest> requests); // 批量创建
    void configureProbe(Probe probe, Map<String, Object> config); // 配置探针
    boolean validateConfig(Map<String, Object> config);           // 验证配置
    String getSupportedType();                                     // 支持的探针类型
}
```

#### 4.5.2 AbstractProbeFactory — 抽象工厂基类

> [AbstractProbeFactory.java](apps/probe-admin/src/main/java/com/lixin/probe/factory/AbstractProbeFactory.java)

```java
public abstract class AbstractProbeFactory implements ProbeFactory {

    @Autowired
    protected ProbeMapper probeMapper;

    @Override
    public Probe createProbe(ProbeCreateRequest request) {
        // 1. 验证请求参数（probeKey 格式、名称长度等）
        validateRequest(request);
        // 2. 检查 probeKey 唯一性
        if (isProbeKeyExists(request.getProbeKey())) {
            throw new IllegalArgumentException("探针Key已存在");
        }
        // 3. 子类实现具体构建逻辑
        Probe probe = buildProbeEntity(request);
        // 4. 应用默认配置（status=offline, collectInterval=60）
        applyDefaultConfig(probe);
        // 5. 持久化
        probeMapper.insert(probe);
        return probe;
    }

    // 模板方法：子类实现
    protected abstract Probe buildProbeEntity(ProbeCreateRequest request);

    // 批量创建采用 fail-safe 策略：单个失败不影响其他
    @Override
    public List<Probe> createProbes(List<ProbeCreateRequest> requests) {
        List<Probe> probes = new ArrayList<>();
        for (ProbeCreateRequest request : requests) {
            try {
                probes.add(createProbe(request));
            } catch (Exception e) {
                log.error("创建探针失败: key={}", request.getProbeKey(), e);
            }
        }
        return probes;
    }
}
```

**子类实现**：`DatabaseProbeFactory` 和 `FileProbeFactory`，分别构建数据库探针和文件探针。

#### 4.5.3 ProbeFactoryManager — 工厂管理器

> [ProbeFactoryManager.java](apps/probe-admin/src/main/java/com/lixin/probe/factory/ProbeFactoryManager.java)

```java
@Component
public class ProbeFactoryManager {
    private final Map<String, ProbeFactory> factories = new HashMap<>();

    @Autowired
    public ProbeFactoryManager(List<ProbeFactory> factoryList) {
        // Spring 自动注入所有 ProbeFactory 实现
        for (ProbeFactory factory : factoryList) {
            factories.put(factory.getSupportedType(), factory);
        }
    }

    public ProbeFactory getFactory(String type) {
        ProbeFactory factory = factories.get(type);
        if (factory == null) {
            throw new IllegalArgumentException("不支持的探针类型: " + type);
        }
        return factory;
    }
}
```

### 4.6 策略模式 (strategy)

用于处理不同类型探针的心跳超时逻辑。

#### 4.6.1 ProbeHeartbeatStrategy 接口

> [ProbeHeartbeatStrategy.java](apps/probe-admin/src/main/java/com/lixin/probe/strategy/ProbeHeartbeatStrategy.java)

```java
public interface ProbeHeartbeatStrategy {
    boolean handleHeartbeat(Probe probe);  // 处理心跳
    boolean isTimeout(Probe probe);         // 检查超时
    int getTimeoutSeconds();                // 超时阈值（秒）
    String getSupportedType();              // 支持的探针类型
}
```

#### 4.6.2 两个实现类

| 实现类 | 超时时间 | 适用场景 |
|--------|----------|----------|
| `DatabaseProbeHeartbeatStrategy` | 60 秒 | 数据库探针需要较高频率心跳 |
| `FileProbeHeartbeatStrategy` | 300 秒（5 分钟） | 文件探针扫描耗时较长，允许较长间隔 |

#### 4.6.3 ProbeStrategyFactory — 策略工厂

> [ProbeStrategyFactory.java](apps/probe-admin/src/main/java/com/lixin/probe/strategy/ProbeStrategyFactory.java)

```java
@Component
public class ProbeStrategyFactory {
    private final Map<String, ProbeHeartbeatStrategy> heartbeatStrategies = new HashMap<>();

    @Autowired
    public ProbeStrategyFactory(List<ProbeHeartbeatStrategy> strategyList) {
        for (ProbeHeartbeatStrategy strategy : strategyList) {
            heartbeatStrategies.put(strategy.getSupportedType(), strategy);
        }
    }

    public ProbeHeartbeatStrategy getHeartbeatStrategy(String probeType) {
        ProbeHeartbeatStrategy strategy = heartbeatStrategies.get(probeType);
        if (strategy == null) {
            return heartbeatStrategies.values().stream().findFirst().orElse(null); // 默认策略
        }
        return strategy;
    }
}
```

### 4.7 WebSocket 通信层 (websocket)

这是 Admin 端最核心的通信层，负责与所有 Agent 的实时双向通信。

#### 4.7.1 MetaProbeWebSocketHandler — Meta 通道处理器

> [MetaProbeWebSocketHandler.java](apps/probe-admin/src/main/java/com/lixin/probe/websocket/MetaProbeWebSocketHandler.java) (~620 行)

```

**职责**：管理 Agent 的 WebSocket 连接、消息加解密、指令下发。

**连接管理**：

```java
// 双键索引：同时按 probeKey 和 agentCode 索引会话
private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();       // probeKey → Session
private final Map<String, WebSocketSession> sessionsByCode = new ConcurrentHashMap<>(); // agentCode → Session
private final Map<String, String> codeToProbeKey = new ConcurrentHashMap<>();           // agentCode → probeKey
```

**连接建立流程**：

```
Agent 连接 ws://admin:8081/ws/meta?code=AGENT&probe_key=xxx&port=58081
    ↓
afterConnectionEstablished()
    ├── 提取 code 和 probe_key 参数
    ├── 存入 sessions / sessionsByCode 映射
    ├── 配置消息缓冲区（10MB 文本 / 50MB 二进制）
    ├── 自动注册 Agent（agentService.registerOrUpdateAgent）
    ├── 更新 Agent 心跳（agentHeartbeatService.updateAgentHeartbeat）
    └── 发送 CONNECTED 确认消息
```

**消息处理流程**：

```
handleTextMessage()
    ├── 解析 JSON：提取 code 和 data 字段
    ├── AES 解密：使用 probeKey 填充为 16 字节密钥
    ├── 解析解密后的消息：提取 type、cmd、payload
    └── 委托 MessageDispatcher.dispatch(session, agentCode, type, cmd, data)
```

**指令下发方法**：

| 方法 | 说明 |
|------|------|
| `sendMessage(probeKey, message)` | 发送原始文本消息 |
| `sendEncryptedMessage(probeKey, code, message)` | AES 加密后发送 |
| `sendControlCommand(probeKey, type, command)` | 发送控制命令（采集/启停/升级） |
| `sendCollectCommand(probeKey, collectType)` | 触发元数据采集 |

**Agent Code 提取算法**：

```java
// probeKey 格式：{AGENT-CODE}-{type}-{random}
// 例：TEST-AGENT-001-database-mnrtl6-udv → TEST-AGENT-001
private String extractAgentCodeFromProbeKey(String probeKey) {
    String[] PROBE_TYPES = {"file", "database", "system", "http", "ping", "port"};
    String[] parts = probeKey.split("-");
    for (int i = 1; i < parts.length; i++) {
        for (String probeType : PROBE_TYPES) {
            if (parts[i].equalsIgnoreCase(probeType)) {
                return join(parts[0..i-1], "-");  // type 之前的部分即为 Agent Code
            }
        }
    }
    return parts[0];
}
```

#### 4.7.2 FileProbeWebSocketHandler — File 通道处理器

> [FileProbeWebSocketHandler.java](apps/probe-admin/src/main/java/com/lixin/probe/websocket/FileProbeWebSocketHandler.java)

```

**与 Meta 通道的区别**：
- 不使用 AES 加密，直接 JSON 明文通信
- 主要用于文件扫描结果上报和文件上传
- 支持SockJS（前端浏览器兼容）

**会话清理机制**：

```java
// 定时清理 30 分钟不活跃的会话
@Scheduled(fixedRate = 300000) // 每 5 分钟
public void cleanupInactiveSessions() {
    sessions.entrySet().removeIf(entry -> {
        long inactiveMinutes = Duration.between(lastActivity.get(entry.getKey()), LocalDateTime.now()).toMinutes();
        if (inactiveMinutes > 30) {
            entry.getValue().close();
            return true;
        }
        return false;
    });
}
```

#### 4.7.3 MessageDispatcher — 消息分发器

> [MessageDispatcher.java](apps/probe-admin/src/main/java/com/lixin/probe/websocket/handler/MessageDispatcher.java)

```

自动发现所有 `MessageHandler` 实现，根据消息类型精准路由：

```java
@Component
public class MessageDispatcher {
    private final List<MessageHandler> handlers;  // 9+ 个 Handler

    public void dispatch(WebSocketSession session, String agentCode, String type, String cmd, Object data) {
        for (MessageHandler handler : handlers) {
            if (handler.canHandle(type, cmd)) {
                handler.handle(session, agentCode, data);
                return;
            }
        }
        log.warn("未找到消息处理器: type={}, cmd={}", type, cmd);
    }
}
```

**已注册的 Handler**：

| Handler | 处理的消息类型 | 功能 |
|---------|---------------|------|
| HeartbeatMessageHandler | HEARTBEAT | 更新探针心跳时间 |
| EnhancedHeartbeatHandler | ENHANCED_HEARTBEAT | 带系统指标的心跳 |
| HandshakeHandler | HANDSHAKE | Agent 握手建立连接 |
| CommandResponseHandler | COMMAND_RESPONSE | 处理 Agent 命令响应 |
| FileScanReportHandler | FILE_SCAN_REPORT | 接收文件扫描结果 |
| TableDataHandler | TABLE_DATA | 接收数据库表数据 |
| LogUploadHandler | LOG_UPLOAD | 接收 Agent 日志 |
| ProbePushHandler | PROBE_PUSH | 接收探针推送数据 |
| ProbeStatusUpdateHandler | PROBE_STATUS_UPDATE | 更新探针状态 |

#### 4.7.4 WebSocketConnectionPool — 连接池

> [WebSocketConnectionPool.java](apps/probe-admin/src/main/java/com/lixin/probe/websocket/WebSocketConnectionPool.java)

```

```java
@Component
public class WebSocketConnectionPool {
    private static final int MAX_CONNECTIONS_PER_PROBE = 3;  // 每个探针最多 3 个连接

    private final ConcurrentHashMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicInteger> probeConnections = new ConcurrentHashMap<>();
    private final AtomicInteger totalConnections = new AtomicInteger(0);

    public boolean addConnection(String probeKey, WebSocketSession session) {
        AtomicInteger count = probeConnections.computeIfAbsent(probeKey, k -> new AtomicInteger(0));
        if (count.incrementAndGet() > MAX_CONNECTIONS_PER_PROBE) {
            count.decrementAndGet();  // 超限拒绝
            return false;
        }
        sessions.put(session.getId(), session);
        totalConnections.incrementAndGet();
        return true;
    }
}
```

### 4.8 业务服务层 (service)

Admin 端共有 **38 个 Service 实现类**，以下详解最核心的几个。

#### 4.8.1 ProbeServiceImpl — 探针管理服务

> [ProbeServiceImpl.java](apps/probe-admin/src/main/java/com/lixin/probe/service/impl/ProbeServiceImpl.java)

```

**核心方法**：

| 方法 | 功能 | 关键逻辑 |
|------|------|----------|
| `create()` | 创建探针 | 校验 probeKey 唯一性、IP 唯一性（SYSTEM 类型）、自动生成 probeKey |
| `delete()` | 删除探针 | 在线探针先发送 STOP 命令，使用乐观锁删除 |
| `batchCreate()` | 批量创建 | fail-safe 策略，单个失败不影响其他 |
| `exportProbesToExcel()` | Excel 导出 | 使用 Apache POI 生成 .xlsx 文件 |
| `exportProbesToJson()` | JSON 导出 | 序列化为 JSON 数组 |

**probeKey 自动生成算法**：

```
格式：AGENT-{type}-{base36(timestamp)}-{random(3位)}
示例：AGENT-database-mnrtl6-udv
```

#### 4.8.2 ChangeDetectionServiceImpl — 变更检测服务

> [ChangeDetectionServiceImpl.java](apps/probe-admin/src/main/java/com/lixin/probe/service/impl/ChangeDetectionServiceImpl.java)

```

这是系统中最复杂的业务服务，实现了 **多层数据变化检测**。

**快照对比检测（saveSnapshotAndDetect）**：

```java
@DistributedLock(key = "'detect:' + #probeKey + ':' + #tableName")
public void saveSnapshotAndDetect(String probeKey, String tableName,
                                   long rowCount, long dataSize, long indexSize,
                                   String checksum, String maxUpdateTime) {
    // 1. 保存新快照
    DataSnapshot snapshot = new DataSnapshot();
    snapshot.setProbeKey(probeKey);
    snapshot.setRowCount(rowCount);
    // ...
    snapshotMapper.insert(snapshot);

    // 2. 获取上一个快照
    DataSnapshot previous = getLastSnapshot(probeKey, tableName);

    // 3. 五维对比
    List<ChangeLog> changes = compareSnapshots(previous, snapshot);

    // 4. 持久化变更日志
    for (ChangeLog change : changes) {
        changeLogMapper.insert(change);
    }

    // 5. 触发告警检查
    changeAlertService.processChangeLogs(changes);
}
```

**五维对比算法（compareSnapshots）**：

| 维度 | 检测方式 | 变更类型 |
|------|----------|----------|
| 行数增加 | `after.rowCount > before.rowCount` | `ROW_INSERT` |
| 行数减少 | `after.rowCount < before.rowCount` | `ROW_DELETE` |
| 数据大小变化 | `after.dataSize != before.dataSize` | `SIZE_CHANGE` |
| 索引大小变化 | `after.indexSize != before.indexSize` | `INDEX_SIZE_CHANGE` |
| 校验和不匹配（行数不变） | `!after.checksum.equals(before.checksum)` | `CHECKSUM_CHANGE` |
| 更新时间变化 | `!after.maxUpdateTime.equals(before.maxUpdateTime)` | `DATA_UPDATE` |

**变更统计查询优化（getChangeStatistics）**：

```java
// 使用 JdbcTemplate 直接 SQL 替代 MyBatis-Plus 查询，提升统计性能
public Map<String, Object> getChangeStatistics(String probeKey) {
    // 1. 聚合查询：按 change_type 分组统计（SQL GROUP BY）
    String sql = "SELECT change_type, COUNT(*) as cnt FROM change_log";
    List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, args);

    // 2. 去重表数统计：SELECT COUNT(DISTINCT table_name)
    // 3. 最近变更时间：SELECT MAX(detected_time)
}
```

> **优化说明**：原实现通过 MyBatis-Plus `selectList` 拉取全部日志再 Java 内存分组，改为 SQL 聚合查询后性能大幅提升。

**CDC 事件处理（processCDCEvents）**：

```java
public void processCDCEvents(String agentCode, List<CDCEvent> events) {
    for (CDCEvent event : events) {
        // 映射操作类型：INSERT → ROW_INSERT, UPDATE → DATA_UPDATE, DELETE → ROW_DELETE
        String changeType = mapEventType(event.getEventType());

        // 持久化变更日志
        ChangeLog changeLog = new ChangeLog();
        changeLog.setChangeType(changeType);
        changeLog.setChangeDetail(JSON.toJSONString(event));
        changeLogMapper.insert(changeLog);

        // 发布 Spring ApplicationEvent，触发实时同步
        applicationEventPublisher.publishEvent(new CDCChangeEvent(this, changeLog));
    }
}
```

#### 4.8.3 QualityFilterEngineImpl — 质量过滤引擎

> [QualityFilterEngineImpl.java](apps/probe-admin/src/main/java/com/lixin/probe/service/impl/QualityFilterEngineImpl.java)

```

**过滤流程**：

```java
public FilterResult filter(List<Map<String, Object>> rows, List<QualityRule> rules) {
    List<Map<String, Object>> passedRows = new ArrayList<>();
    List<QualityViolation> violations = new ArrayList<>();

    for (Map<String, Object> row : rows) {
        for (QualityRule rule : rules) {
            if (!checkRule(row, rule)) {
                violations.add(new QualityViolation(row, rule));
                break;  // 一行有一个规则不通过即标记
            }
        }
        if (violations.isEmpty()) {
            passedRows.add(row);
        }
    }

    return new FilterResult(passedRows, violations);
}
```

**规则检查逻辑（checkRule）**：

```java
private boolean checkRule(Map<String, Object> row, QualityRule rule) {
    Object value = row.get(rule.getColumnName());
    JSONObject params = JSON.parseObject(rule.getRuleParams());

    switch (rule.getRuleType()) {
        case "NOT_NULL":
            return value != null && !value.toString().isEmpty();
        case "REGEX":
            return value == null || Pattern.matches(params.getString("pattern"), value.toString());
        case "RANGE":
            return checkRange(value, params);  // BigDecimal 精确比较
        case "ENUM":
            Set<String> allowed = new HashSet<>(Arrays.asList(params.getString("values").split(",")));
            return value == null || allowed.contains(value.toString().trim());
        case "LENGTH":
            int len = value.toString().length();
            return (params.getInteger("minLength") == null || len >= params.getInteger("minLength"))
                && (params.getInteger("maxLength") == null || len <= params.getInteger("maxLength"));
        case "TYPE_CHECK":
            return checkType(value, params);  // INTEGER / DECIMAL / BOOLEAN / DATE
        default:
            return true;
    }
}
```

#### 4.8.4 SyncTaskServiceImpl — 同步任务服务

> [SyncTaskServiceImpl.java](apps/probe-admin/src/main/java/com/lixin/probe/service/impl/SyncTaskServiceImpl.java)

```

**核心能力**：

| 方法 | 功能 |
|------|------|
| `createSyncTask()` | 创建同步任务，解析 Cron 表达式计算下次执行时间 |
| `executeSync()` | 执行同步：拉取源数据 → 质量过滤 → 写入目标 → 记录日志 |
| `toggleTask()` | 启用/禁用任务 |
| `triggerManualSync()` | 手动触发一次同步 |
| `getDeadLetters()` | 查询死信（失败）任务 |

**同步执行流程**：

```
executeSync(taskId)
    ├── 更新任务状态为 RUNNING
    ├── 从源探针拉取数据（通过 WebSocket 命令 Agent 执行）
    ├── 质量过滤（调用 QualityFilterEngine）
    │   ├── 通过的行 → 写入目标（UPSERT / INSERT / SKIP）
    │   └── 不通过的行 → 写入 aggregation.quality_bad_records
    ├── 记录 sync_log（行数、耗时、状态）
    └── 更新任务状态和同步位置
```

#### 4.8.5 AlertNotificationServiceImpl — 告警通知服务

> [AlertNotificationServiceImpl.java](apps/probe-admin/src/main/java/com/lixin/probe/service/impl/AlertNotificationServiceImpl.java)

```

**告警处理流程**：

```
processChangeLogs(changeLogs)
    ├── 查询匹配的 change_alert_config
    ├── 检查是否超过阈值（threshold_rows）
    ├── 创建 change_alert_record
    └── 发送通知（根据 notify_channels）
        ├── LOG → 记录日志
        └── WEBHOOK → HTTP POST 到配置的 URL
```

> **注**：系统曾支持 EMAIL 通知渠道，现已移除，仅保留 LOG 和 WEBHOOK 两种渠道。

#### 4.8.6 其他重要服务

| 服务 | 职责 |
|------|------|
| `AggregationServiceImpl` | 汇聚库管理：数据源注册、表/列元数据聚合（服务端分页 + 关键字搜索）、文件注册 |
| `FileProbeServiceImpl` | 文件探针管理：下发扫描命令、接收扫描结果、文件元数据存储 |
| `DatabaseMetadataServiceImpl` | 数据库元数据管理：库/表/列信息存储和查询 |
| `DatabaseConnectionServiceImpl` | 数据库连接管理：加密存储连接配置、连接测试 |
| `AuditLogServiceImpl` | 审计日志：记录、查询、归档、清理 |
| `UserServiceImpl` | 用户管理：BCrypt 密码加密、JWT Token 生成/验证、角色查询 |
| `PermissionServiceImpl` | 权限管理：基于 JdbcTemplate 直接 SQL 查询角色-权限关联，带 Redis 缓存 |
| `SettingsServiceImpl` | 系统设置：5 大分类 33 项默认配置，支持重置到默认值 |
| `MetricDataServiceImpl` | 指标数据：时序数据存储和查询 |
| `ChangeAlertServiceImpl` | 变化告警：规则匹配和告警触发 |
| `DataSourceAlertServiceImpl` | 数据源告警：连通性监控和告警 |
| `ProbeMonitorServiceImpl` | 探针监控：状态检测、心跳超时判定 |
| `ProbeExportServiceImpl` | 探针导入导出：JSON/Excel 格式 |
| `ProbeGroupServiceImpl` | 探针分组：分组 CRUD |
| `DeadLetterTaskServiceImpl` | 死信任务：失败数据处理和重试 |
| `DataImportServiceImpl` | 数据导入：CSV/Excel 文件解析入库 |
| `WebhookServiceImpl` | Webhook 管理：事件订阅和推送 |

### 4.9 控制器层 (controller)

Admin 端共有 **37 个 REST 控制器**，提供完整的 CRUD API。

#### 4.9.1 ProbeController — 探针管理 API

> [ProbeController.java](apps/probe-admin/src/main/java/com/lixin/probe/controller/ProbeController.java)

```

| 端点 | 方法 | 功能 | 权限 |
|------|------|------|------|
| `GET /api/probes` | GET | 分页查询探针列表（支持 name/status/type 过滤） | PROBE_VIEW |
| `GET /api/probes/{id}` | GET | 查询探针详情 | PROBE_VIEW |
| `POST /api/probes` | POST | 创建探针 | PROBE_CREATE |
| `PUT /api/probes/{id}` | PUT | 更新探针 | PROBE_UPDATE |
| `DELETE /api/probes/{id}` | DELETE | 删除探针（在线探针先发送 STOP 命令） | PROBE_DELETE |
| `POST /api/probes/{id}/scan` | POST | 触发文件扫描（通过 WebSocket 下发） | PROBE_CONTROL |
| `GET /api/probes/{id}/files` | GET | 获取文件列表 | PROBE_VIEW |
| `POST /api/probes/{id}/upload` | POST | 上传文件到探针扫描路径 | PROBE_UPLOAD |
| `GET /api/probes/{probeKey}/instances` | GET | 获取数据库实例列表 | PROBE_VIEW |
| `POST /api/probes/{probeKey}/collect` | POST | 触发元数据采集 | PROBE_CONTROL |
| `POST /api/probes/test-connection` | POST | 测试数据库连接 | PROBE_VIEW |
| `GET /api/probes/export` | GET | 导出探针为 Excel | PROBE_EXPORT |
| `GET /api/probes/export/json` | GET | 导出探针为 JSON | PROBE_EXPORT |
| `POST /api/probes/import/json` | POST | 从 JSON 导入探针 | PROBE_IMPORT |
| `POST /api/probes/batch` | POST | 批量创建探针 | PROBE_CREATE |

**控制器特点**：
- 注入 `@Qualifier("decoratedProbeService")` 使用装饰器链
- 所有写操作添加 `@Audited` 注解自动审计
- 所有操作添加 `@RequirePermission` 注解进行权限校验
- 使用 `ControllerHelper.safeExecute()` 统一异常包装
- `AuthController` 登录接口返回用户角色和权限列表，前端按角色控制 UI 可见性

#### 4.9.2 其他控制器

| 控制器 | 路径前缀 | 功能 |
|--------|----------|------|
| `AuthController` | `/api/auth` | 登录（含角色/权限返回）、登出、Token 刷新 |
| `SyncTaskController` | `/api/sync-tasks` | 同步任务 CRUD + 执行 |
| `QualityRuleController` | `/api/quality-rules` | 质量规则 CRUD + 检查 |
| `ChangeDetectionController` | `/api/change-detection` | 变更检测查询 |
| `ChangeAlertController` | `/api/change-alerts` | 变化告警配置和记录 |
| `DataSourceAlertController` | `/api/datasource-alerts` | 数据源告警配置 |
| `AlertChannelController` | `/api/alert-channels` | 告警渠道管理 |
| `AggregationController` | `/api/aggregation` | 汇聚数据查询（表列表支持分页 + 关键字搜索） |
| `FileProbeController` | `/api/file-probes` | 文件探针管理 |
| `FileUploadController` | `/api/file-upload` | 文件上传 |
| `DatabaseMetadataController` | `/api/database-metadata` | 数据库元数据查询 |
| `DatabaseConnectionController` | `/api/database-connections` | 数据库连接管理 |
| `AuditLogController` | `/api/audit-logs` | 审计日志查询 |
| `SettingsController` | `/api/settings` | 系统设置 |
| `AgentController` | `/api/agents` | Agent 管理（心跳、CDC 事件接收） |
| `AgentControlController` | `/api/agent-control` | Agent 远程控制 |
| `StatisticsController` | `/api/statistics` | 统计数据 |
| `PermissionController` | `/api/permissions` | 权限管理 |
| `ProbeGroupController` | `/api/probe-groups` | 探针分组 |
| `WebhookController` | `/api/webhooks` | Webhook 管理 |

### 4.10 调度器 (scheduler)

#### AuditLogScheduler — 审计日志定时清理

> [AuditLogScheduler.java](apps/probe-admin/src/main/java/com/lixin/probe/scheduler/AuditLogScheduler.java)

```

```java
@Scheduled(cron = "${audit-log.cleanup-cron:0 0 2 * * ?}")  // 每天凌晨 2 点
public void cleanupExpiredLogs() {
    if (!isAutoCleanupEnabled()) return;
    int retentionDays = getRetentionDays();  // 默认 90 天
    // DELETE FROM audit_log WHERE create_time < NOW() - INTERVAL '90 days'
}

@Scheduled(cron = "${audit-log.archive-cron:0 0 3 * * ?}")  // 每天凌晨 3 点
public void archiveLogs() {
    if (!isArchiveEnabled()) return;
    // 归档 30 天前的日志到文件，支持 GZIP 压缩
}
```

### 4.11 事件引擎 (engine)

#### MetricDataEvent — 指标数据事件

> [MetricDataEvent.java](apps/probe-admin/src/main/java/com/lixin/probe/engine/MetricDataEvent.java)

```java
public class MetricDataEvent extends ApplicationEvent {
    private final String probeKey;
    private final Map<String, Object> metrics;

    public MetricDataEvent(Object source, String probeKey, Map<String, Object> metrics) {
        super(source);
        this.probeKey = probeKey;
        this.metrics = metrics;
    }
}
```

**事件消费链**：

```
MetricDataEvent 发布
    → 持久化到 metric_data 表
    → 通过 MetricsWebSocketHandler 推送到前端 Dashboard
    → 检查是否触发告警阈值
```

同样地，CDC 变更事件也通过 Spring Events 解耦：

```
CDCChangeEvent 发布（ChangeDetectionServiceImpl）
    → ChangeTriggeredSyncListener 监听
    → 查询 realtimeSyncEnabled=true 的同步任务
    → 立即触发同步执行
```

---

## 五、Agent 探针端代码详解

### 5.1 插件系统 (plugin)

插件系统是 Agent 最核心的架构设计，采用 **SPI (Service Provider Interface)** 模式。

#### 5.1.1 插件接口层次

```
                    ┌─────────────────┐
                    │  DatabasePlugin │  关系数据库插件接口
                    └────────┬────────┘
                             │ implements
        ┌────────┬───────────┼───────────┬────────────┐
        │        │           │           │            │
   MySqlPlugin PostgreSQLPlugin OraclePlugin SQLServerPlugin  SQLitePlugin
   DmPlugin(达梦)

                    ┌─────────────────┐
                    │    CDCPlugin    │  变更数据捕获插件接口
                    └────────┬────────┘
                             │ implements
                ┌────────────┴────────────┐
         BinlogCDCPlugin        PostgreSQLCDCPlugin
         (MySQL Binlog)          (PostgreSQL WAL)

                    ┌─────────────────┐
                    │   NoSQLPlugin   │  NoSQL 数据库插件接口
                    └────────┬────────┘
                             │ implements
           ┌─────────┬───────┴────────┐
     MongoPlugin  RedisPlugin  ElasticsearchPlugin

                    ┌─────────────────┐
                    │ MessageQueuePlugin│  消息队列插件接口
                    └────────┬─────────┘
                             │ implements
                      KafkaPlugin

                    ┌─────────────────┐
                    │   FilePlugin    │  文件插件接口
                    └────────┬────────┘

                    ┌─────────────────┐
                    │  HttpApiPlugin  │  HTTP API 插件接口
                    └────────┬────────┘
                             │ implements
                    HttpApiPluginImpl  FtpPlugin
```

#### 5.1.2 DatabasePlugin 接口详解

> [DatabasePlugin.java](apps/probe-agent/src/main/java/com/lixin/probe/agent/plugin/api/DatabasePlugin.java)

```java
public interface DatabasePlugin {

    // ====== 插件元数据 ======
    String getPluginId();           // 唯一标识：如 "mysql-database-plugin"
    String getName();               // 名称：如 "MySQL Database Plugin"
    String getType();               // 类型：固定为 "DATABASE"
    String getVersion();            // 版本：如 "1.0.0"
    String getDbType();             // 数据库类型：如 "mysql"
    String getVersionRange();       // 支持版本：如 "5.7,8.0"
    int getDefaultPort();           // 默认端口：如 3306
    boolean isVersionSupported(String version);

    // ====== 连接管理 ======
    String buildUrl(Map<String, Object> params);   // 构建 JDBC URL
    String getDriverClass();                         // 驱动类名
    Connection getConnection(Map<String, Object> params) throws Exception;

    // ====== 数据探针功能 ======
    CompletableFuture<ProbeResponse.Metadata> getMetadata(Connection conn, ProbeRequest req);
    CompletableFuture<ProbeResponse.DataSize> getDataSize(Connection conn, ProbeRequest req);
    CompletableFuture<ProbeResponse.DataContent> getDataContent(Connection conn, ProbeRequest req);

    // ====== SQL 构建辅助 ======
    String escapeTableName(String fullTableName);   // 表名转义（MySQL用反引号，PG用双引号）
    String getCountSql(boolean isPrecise, Map<String, Object> params);  // 精确/估算行数 SQL
    String getStorageSql(Map<String, Object> params);                   // 存储大小 SQL
    String getTestSql();                            // 连接测试 SQL

    // ====== 数据导出 ======
    default String exportDataAsCsv(Connection conn, String table, String where, List<String> cols) {
        // 默认实现：SELECT * → CSV 格式字符串
    }
}
```

**设计要点**：
- 精确 vs 估算行数分离：`COUNT(*)` 精确但慢，`information_schema.TABLE_ROWS` 快但近似
- 表名转义抽象：不同数据库标识符引号不同（MySQL: `` ` ``、PostgreSQL: `"`、SQL Server: `[`）
- `default` 方法提供基础实现，减少子类重复代码

#### 5.1.3 MySqlPlugin 实现

> [MySqlPlugin.java](apps/probe-agent/src/main/java/com/lixin/probe/agent/plugin/impl/database/MySqlPlugin.java)

```java
@Component
public class MySqlPlugin implements DatabasePlugin {

    @Override
    public Connection getConnection(Map<String, Object> params) throws Exception {
        String url = buildUrl(params);
        String username = (String) params.get("username");
        String password = (String) params.get("password");
        Class.forName("com.mysql.cj.jdbc.Driver");
        return DriverManager.getConnection(url, username, password);
    }

    @Override
    public String buildUrl(Map<String, Object> params) {
        return String.format("jdbc:mysql://%s:%d/%s?useSSL=false&characterEncoding=utf8&serverTimezone=Asia/Shanghai",
            params.get("host"), params.get("port"), params.get("name"));
    }

    @Override
    public CompletableFuture<ProbeResponse.Metadata> getMetadata(Connection conn, ProbeRequest req) {
        return CompletableFuture.supplyAsync(() -> {
            DatabaseMetaData meta = conn.getMetaData();
            // 1. 获取所有 schema
            // 2. 遍历每个 schema 的所有表（getTables）
            // 3. 遍历每张表的所有列（getColumns）
            // 4. 构建树形元数据结构返回
        });
    }

    @Override
    public String escapeTableName(String fullTableName) {
        // 使用反引号转义：table_name → `table_name`
        return "`" + fullTableName.replace(".", "`.`") + "`";
    }
}
```

#### 5.1.4 BinlogCDCPlugin — MySQL Binlog CDC

> [BinlogCDCPlugin.java](apps/probe-agent/src/main/java/com/lixin/probe/agent/plugin/impl/database/BinlogCDCPlugin.java)

```java
@Component
public class BinlogCDCPlugin implements CDCPlugin {

    // 每个 serverId 对应一个 BinaryLogClient 和事件队列
    private final ConcurrentHashMap<String, BinaryLogClient> activeClients = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, BlockingQueue<CDCEvent>> eventQueues = new ConcurrentHashMap<>();

    // 自增 serverId，每个 BinaryLogClient 需要唯一 ID 避免同一 MySQL 上多实例冲突
    private static final AtomicInteger SERVER_ID_SEQ = new AtomicInteger(10000);

    @Override
    public CompletableFuture<List<CDCEvent>> captureChanges(
            Map<String, Object> config, String database, String table,
            String fromPosition, int maxEvents) {

        return CompletableFuture.supplyAsync(() -> {
            // 1. 启动或复用 BinaryLogClient
            BinaryLogClient client = startBinlogClient(config, fromPosition);
            BlockingQueue<CDCEvent> queue = eventQueues.get(clientKey);

            // 2. 从队列拉取事件（5 秒超时）
            List<CDCEvent> events = new ArrayList<>();
            long deadline = System.currentTimeMillis() + 5000;
            while (events.size() < maxEvents && System.currentTimeMillis() < deadline) {
                CDCEvent event = queue.poll(1, TimeUnit.SECONDS);
                if (event != null) events.add(event);
            }
            return events;
        });
    }

    private BinaryLogClient startBinlogClient(Map<String, Object> config, String fromPosition) {
        // 1. 创建 BinaryLogClient，设置 hostname、port、username、password
        //    使用 SERVER_ID_SEQ 自增生成唯一 serverId，避免多实例冲突
        // 2. 设置 binlogFileName 和 binlogPosition（从 fromPosition 解析）
        // 3. 配置 EventDeserializer（日期转 long、二进制转 byte[]）
        // 4. 注册事件监听器：
        //    - TABLE_MAP → 缓存表结构
        //    - WRITE_ROWS → handleWriteEvent()
        //    - UPDATE_ROWS → handleUpdateEvent()
        //    - DELETE_ROWS → handleDeleteEvent()
        // 5. connect() 启动监听
    }

    private void handleWriteEvent(EventData data) {
        WriteRowsEventData writeData = (WriteRowsEventData) data;
        for (Object[] row : writeData.getRows()) {
            CDCEvent event = new CDCEvent();
            event.setEventType("INSERT");
            event.setAfter(mapRowToMap(row, columnNames));  // 列值转为 Map
            event.setBinlogPos(currentBinlogFile + ":" + currentBinlogPos);
            eventQueue.offer(event);
        }
    }
}
```

**关键技术点**：
- **生产者-消费者模式**：Binlog 事件生产到 `BlockingQueue`，`captureChanges` 消费
- **位置追踪**：每个事件携带 `binlogPos`（如 `mysql-bin.000001:154`），支持断点续传
- **列名缓存**：首次 TABLE_MAP 事件时通过 JDBC 查询 `INFORMATION_SCHEMA.COLUMNS` 获取真实列名
- **ConcurrentHashMap** 管理多个 MySQL 实例的客户端

#### 5.1.5 PostgreSQLCDCPlugin — PostgreSQL WAL CDC

> [PostgreSQLCDCPlugin.java](apps/probe-agent/src/main/java/com/lixin/probe/agent/plugin/impl/database/PostgreSQLCDCPlugin.java)

```java
@Component
public class PostgreSQLCDCPlugin implements CDCPlugin {

    @Override
    public CompletableFuture<List<CDCEvent>> captureChanges(...) {
        return CompletableFuture.supplyAsync(() -> {
            // 1. 启动或复用 WAL 流
            PGReplicationStream stream = startWALStream(config, fromPosition);

            // 2. 从队列拉取事件
            List<CDCEvent> events = new ArrayList<>();
            // ... 类似 BinlogCDCPlugin
        });
    }

    private PGReplicationStream startWALStream(Map<String, Object> config, String fromPosition) {
        // 1. 获取复制连接
        Connection conn = DriverManager.getConnection(url + "?replication=database&preferQueryMode=simple");
        PGConnection pgConn = conn.unwrap(PGConnection.class);

        // 2. 自动创建 Publication 和 Replication Slot
        ensurePublication(pgConn, publicationName);    // CREATE PUBLICATION ... FOR ALL TABLES
        ensureReplicationSlot(pgConn, slotName);       // pg_create_logical_replication_slot()

        // 3. 创建复制流
        PGReplicationStream stream = pgConn
            .getReplicationAPI()
            .replicationStream()
            .logical()
            .withSlotName(slotName)
            .withSlotOption("proto_version", "1")
            .withSlotOption("publication_name", publicationName)
            .withStatusInterval(10, TimeUnit.SECONDS)
            .start();

        // 4. 启动后台守护线程读取 WAL
        Thread reader = new Thread(() -> {
            while (running) {
                ByteBuffer msg = stream.readPending();
                if (msg != null) {
                    parseChangeEvent(msg);  // 解析 pgoutput 二进制协议
                }
                stream.setAppliedLSN(stream.getLastReceiveLSN());  // 背压信号
            }
        });
        reader.setDaemon(true);
        reader.start();

        return stream;
    }

    private void parseChangeEvent(ByteBuffer buffer) {
        // 解析 pgoutput 二进制协议：
        // 1. 读取消息类型（I=Insert, U=Update, D=Delete）
        // 2. 读取 relationId → 查找表名
        // 3. 解析元组数据（parseTuple）
        buffer.order(ByteOrder.BIG_ENDIAN);
        char action = (char) buffer.get();
        switch (action) {
            case 'I': parseInsert(buffer); break;
            case 'U': parseUpdate(buffer); break;
            case 'D': parseDelete(buffer); break;
        }
    }

    private Map<String, Object> parseTuple(ByteBuffer buffer, List<String> columnNames) {
        // pgoutput 元组格式：
        // short ncols
        // per column: byte flag + int32 length + byte[length] value
        short ncols = buffer.getShort();
        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 0; i < ncols; i++) {
            byte flag = buffer.get();
            int len = buffer.getInt();
            byte[] valueBytes = new byte[len];
            buffer.get(valueBytes);
            String value = new String(valueBytes, StandardCharsets.UTF_8);
            row.put(columnNames.get(i), value);
        }
        return row;
    }
}
```

**关键技术点**：
- **pgoutput 二进制协议解析**：直接使用 `ByteBuffer` 解析 PostgreSQL 逻辑复制协议，非高级库调用
- **自动创建 Publication 和 Slot**：`ensurePublication()` / `ensureReplicationSlot()` 自动配置
- **LSN 追踪**：通过 `stream.setAppliedLSN()` 向 PostgreSQL 发送背压信号
- **守护线程**：WAL 读取线程设为 daemon，不阻止 JVM 关闭

### 5.2 模块管理 (module)

#### 5.2.1 ModuleManager — 模块生命周期管理器

> [ModuleManager.java](apps/probe-agent/src/main/java/com/lixin/probe/agent/module/ModuleManager.java)

```java
@Service
public class ModuleManager {

    private final ConcurrentHashMap<ProbeType, ProbeModule> moduleMap = new ConcurrentHashMap<>();
    private volatile boolean running = true;

    @PostConstruct
    public void initialize() {
        // 1. 注册所有 ProbeModule Bean
        // 2. 延迟 5 秒等待首次探针同步
        taskExecutor.schedule(this::syncModulesWithProbes, 5, TimeUnit.SECONDS);
    }

    @Scheduled(fixedDelay = 30000) // 每 30 秒
    public void syncModulesWithProbes() {
        // 1. 从 Admin 同步当前应运行的探针类型列表
        Set<ProbeType> currentTypes = probeSyncService.getCurrentProbeTypes();

        // 2. 对比差异：启动新增的模块，停止移除的模块
        if (!currentTypes.equals(lastSyncedTypes)) {
            for (ProbeType type : currentTypes) {
                if (!lastSyncedTypes.contains(type)) {
                    startModule(type);  // 启动新模块
                }
            }
            for (ProbeType type : lastSyncedTypes) {
                if (!currentTypes.contains(type) && type != ProbeType.SYSTEM) {
                    stopModule(type);   // 停止多余模块（SYSTEM 模块永不停止）
                }
            }
            lastSyncedTypes = currentTypes;
        }
    }
}
```

**设计要点**：
- **热插拔**：模块根据 Admin 配置动态启停，无需重启 Agent
- **SYSTEM 模块保护**：系统监控模块永不自动停止，确保基础可观测性
- **30 秒同步周期**：配置变更最多 30 秒生效
- **CDC 联动**：DatabaseModule 启动时自动拉起 `CDCCaptureScheduler`，根据 `database-config.yml` 中 `cdcEnabled=true` 的实例启动 CDC 流；停止时调用 `CDCPlugin.shutdown()` 释放连接

#### 5.2.2 ProbeModule 接口

> [ProbeModule.java](apps/probe-agent/src/main/java/com/lixin/probe/agent/module/ProbeModule.java)

```java
public interface ProbeModule {
    ProbeType getType();
    void start();
    void stop();
    boolean isRunning();
    void onCommand(String cmd, Map<String, Object> params);
}
```

### 5.3 核心服务 (service)

#### 5.3.1 CDCManager — CDC 流生命周期管理

> [CDCManager.java](apps/probe-agent/src/main/java/com/lixin/probe/agent/service/CDCManager.java)

```java
@Service
public class CDCManager {

    private final BlockingQueue<CDCEvent> eventBuffer = new LinkedBlockingQueue<>(50000);
    private volatile boolean running = true;

    // 接收 CDC 事件
    public void enqueueEvent(CDCEvent event) {
        if (!eventBuffer.offer(event)) {
            log.warn("Event buffer full, dropping event");  // 有界队列满则丢弃
        }
    }

    // 每 5 秒批量上报
    @Scheduled(fixedDelay = 5000, initialDelay = 10000)
    public void flushEvents() {
        List<CDCEvent> batch = new ArrayList<>(500);
        eventBuffer.drainTo(batch, 500);  // 最多取 500 条

        // 1. 质量过滤
        List<CDCEvent> goodEvents = new ArrayList<>();
        List<Map<String, Object>> badRecords = new ArrayList<>();
        for (CDCEvent event : batch) {
            List<QualityRuleDTO> rules = qualityRuleSyncService.getRules("", event.getDatabase(), event.getTable());
            List<QualityRuleDTO> violations = qualityValidator.validate(row, db, table, rules);
            if (violations.isEmpty()) {
                goodEvents.add(event);
            } else {
                badRecords.add(buildBadRecord(event, violations));
            }
        }

        // 2. 上报合格事件
        if (!goodEvents.isEmpty()) {
            restTemplate.postForObject(adminUrl + "/api/agents/" + agentCode + "/cdc-events", goodEvents, String.class);
        }

        // 3. 上报坏记录
        if (!badRecords.isEmpty()) {
            reportBadRecords(badRecords);
        }

        // 4. 发送失败时重新入队（最多 100 条）
    }
}
```

**设计亮点**：
- **有界缓冲区（50,000）**：`offer()` 非阻塞，满则丢弃，不阻塞 CDC 流
- **质量门控 CDC**：CDC 事件在 Agent 端即进行质量校验，不合格数据不入汇聚库
- **批量上报（500 条/批）**：减少 HTTP 请求次数
- **最佳努力重试**：失败时最多 100 条重新入队

#### 5.3.2 QualityValidator — Agent 端质量校验

> [QualityValidator.java](apps/probe-agent/src/main/java/com/lixin/probe/agent/service/QualityValidator.java)

```java
@Component
public class QualityValidator {

    public List<QualityRuleDTO> validate(Map<String, Object> row, String db, String table, List<QualityRuleDTO> rules) {
        List<QualityRuleDTO> violations = new ArrayList<>();
        for (QualityRuleDTO rule : rules) {
            if (!matchesScope(rule, db, table)) continue;
            if (!checkRule(row, rule)) violations.add(rule);
        }
        return violations;
    }

    private boolean checkRule(Map<String, Object> row, QualityRuleDTO rule) {
        Object value = row.get(rule.getColumnName());
        JSONObject params = JSON.parseObject(rule.getRuleParams());

        switch (rule.getRuleType()) {
            case "NOT_NULL":   return value != null && !value.toString().isEmpty();
            case "REGEX":      return value == null || Pattern.matches(params.getString("pattern"), value.toString());
            case "RANGE":      return checkRange(value, params);    // BigDecimal 精确比较
            case "ENUM":       /* 白名单匹配 */
            case "LENGTH":     return checkLength(value, params);
            case "TYPE_CHECK": return checkType(value, params);     // INTEGER/DECIMAL/BOOLEAN/DATE
            default:           return true;
        }
    }
}
```

#### 5.3.3 DatabaseService — 数据库元数据采集

> [DatabaseService.java](apps/probe-agent/src/main/java/com/lixin/probe/agent/service/DatabaseService.java)

```java
@Service
public class DatabaseService {

    // 通过插件系统采集数据库元数据
    public ProbeResponse.Metadata collectMetadata(Map<String, Object> dbConfig) {
        String dbType = (String) dbConfig.get("type");
        DatabasePlugin plugin = getPlugin(dbType);  // 根据类型查找插件

        try (Connection conn = plugin.getConnection(dbConfig)) {
            ProbeRequest request = new ProbeRequest();
            request.setDatabase((String) dbConfig.get("name"));

            CompletableFuture<ProbeResponse.Metadata> future = plugin.getMetadata(conn, request);
            return future.get(60, TimeUnit.SECONDS);  // 60 秒超时
        }
    }

    private DatabasePlugin getPlugin(String dbType) {
        // 从插件注册表查找
        return pluginRegistry.getPlugin(dbType)
            .orElseThrow(() -> new IllegalArgumentException("不支持的数据库类型: " + dbType));
    }
}
```

#### 5.3.4 FileService — 文件系统扫描

> [FileService.java](apps/probe-agent/src/main/java/com/lixin/probe/agent/service/FileService.java)

```java
@Service
public class FileService {

    public List<FileMetadata> scanDirectory(String path, FileScanConfig config) {
        List<FileMetadata> results = new ArrayList<>();
        Files.walkFileTree(Paths.get(path), new SimpleFileVisitor<Path>() {
            int currentDepth = 0;

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (config.getMaxDepth() > 0 && currentDepth >= config.getMaxDepth())
                    return FileVisitResult.SKIP_SUBTREE;
                currentDepth++;
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                // 过滤：扩展名、文件大小、隐藏文件
                if (matchesFilter(file, config)) {
                    FileMetadata meta = new FileMetadata();
                    meta.setFileName(file.getFileName().toString());
                    meta.setFilePath(file.toString());
                    meta.setFileSize(attrs.size());
                    meta.setLastModified(attrs.lastModifiedTime().toMillis());
                    if (config.isCalculateMd5()) {
                        meta.setFileMd5(DigestUtils.md5Hex(Files.newInputStream(file)));
                    }
                    results.add(meta);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return results;
    }
}
```

### 5.4 WebSocket 客户端 (websocket)

#### 5.4.1 ConnectionManager — 连接管理器

> [ConnectionManager.java](apps/probe-agent/src/main/java/com/lixin/probe/agent/websocket/ConnectionManager.java)

```java
public class ConnectionManager {

    private final StandardWebSocketClient client;
    private WebSocketSession session;
    private final ConnectionListener listener;

    public interface ConnectionListener {
        void onConnected(WebSocketSession session);
        void onConnectionFailed(Throwable ex);
        void onClosed(CloseStatus closeStatus);
    }

    public CompletableFuture<WebSocketSession> connect(String url, WebSocketHandler handler) {
        // 优先使用 Spring 6.1+ 的 doHandshakeAsync
        // 回退到 deprecated 的 doHandshake（兼容性）
    }
}
```

#### 5.4.2 MessageHandler — 消息处理器

> [MessageHandler.java](apps/probe-agent/src/main/java/com/lixin/probe/agent/websocket/MessageHandler.java)

```java
@Component
public class MessageHandler {

    private final ExecutorService commandExecutor = Executors.newFixedThreadPool(4);

    public void handleMessage(String rawMessage) {
        // 1. 解析 JSON
        // 2. 检测是否加密 → AES 解密
        // 3. 根据 cmd 分发到对应处理器：

        switch (Command.valueOf(cmd)) {
            case PROBE:      // 触发元数据采集
            case FILE_PROBE: // 触发文件扫描
            case START:      // 启动探针
            case STOP:       // 停止探针
            case RESTART:    // 重启探针
            case UPDATE_DB_CONFIG: // 更新数据库配置
            case CONFIG_UPDATE:    // 更新探针配置
            case TABLE_DATA:       // 查询表数据
            case SHUTDOWN:         // 关闭 Agent
            case UPGRADE:          // 远程升级
        }

        // 所有命令在独立线程池执行，不阻塞 WebSocket 线程
        commandExecutor.submit(() -> handleCommand(cmd, data));
    }
}
```

#### 5.4.3 ReconnectionPolicy — 重连策略

> [ReconnectionPolicy.java](apps/probe-agent/src/main/java/com/lixin/probe/agent/websocket/ReconnectionPolicy.java)

```java
public class ReconnectionPolicy {
    private static final long[] BACKOFF_INTERVALS = {
        1000, 2000, 5000, 10000, 30000, 60000  // 指数退避
    };
    private int attemptCount = 0;

    public long getNextDelay() {
        int index = Math.min(attemptCount, BACKOFF_INTERVALS.length - 1);
        return BACKOFF_INTERVALS[index];
    }

    public void reset() { attemptCount = 0; }
}
```

### 5.5 服务发现 (discovery)

#### ProbeDiscoveryClient — UDP 自动发现

> [ProbeDiscoveryClient.java](apps/probe-agent/src/main/java/com/lixin/probe/agent/discovery/ProbeDiscoveryClient.java)

```java
@Component
public class ProbeDiscoveryClient {

    @PostConstruct
    public void init() {
        socket = new DatagramSocket();
        socket.setBroadcast(true);
        socket.setSoTimeout(5000);
    }

    public DiscoveryResponse discover() {
        // 1. 构建发现消息（Agent ID、版本、IP、能力列表）
        AgentDiscoveryMessage message = buildDiscoveryMessage();
        // 能力列表包含：SYSTEM（系统监控）、DATABASE（数据库监控）、FILE（文件监控）

        // 2. 发送 UDP 广播
        byte[] data = JSON.toJSONString(message).getBytes(UTF_8);
        socket.send(new DatagramPacket(data, data.length, adminAddress, discoveryPort));

        // 3. 等待响应（5 秒超时）
        byte[] buffer = new byte[8192];
        DatagramPacket response = new DatagramPacket(buffer, buffer.length);
        socket.receive(response);

        return JSON.parseObject(new String(response.getData()), DiscoveryResponse.class);
    }
}
```

### 5.6 系统指标采集 (collector)

#### SystemMetricsCollector — 多维度系统监控

> [SystemMetricsCollector.java](apps/probe-agent/src/main/java/com/lixin/probe/agent/collector/SystemMetricsCollector.java)

```java
@Component
public class SystemMetricsCollector {

    // 采集 7 大类 30+ 项指标
    public Map<String, Object> collectMetrics() {
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.putAll(collectCpuMetrics());      // CPU 使用率、1/5/15 分钟负载
        metrics.putAll(collectMemoryMetrics());    // 物理内存、Swap
        metrics.putAll(collectDiskMetrics());      // 磁盘使用率（支持 WSL 环境）
        metrics.putAll(collectNetworkMetrics());   // 网络 RX/TX 速率
        metrics.putAll(collectJvmMetrics());       // 堆/非堆内存、线程数、类加载数
        metrics.putAll(collectOsMetrics());        // 进程数、运行时间
        metrics.putAll(collectProcessMetrics());   // PID、JVM 运行时间、打开文件描述符
        return metrics;
    }

    // 网络速率计算：使用 LRU 缓存上次快照，计算差值
    private Map<String, Object> collectNetworkMetrics() {
        LinkedHashMap<String, Long> prevSnapshot = networkCache.get("last");
        long rxBytesPerSec = (currentRxBytes - prevRxBytes) / intervalSeconds;
        long txBytesPerSec = (currentTxBytes - prevTxBytes) / intervalSeconds;
    }

    // WSL 环境特殊处理：OSHI 在 WSL 下磁盘数据不准，使用 df 命令替代
    private boolean detectWslEnvironment() {
        return Files.readString(Path.of("/proc/version")).toLowerCase().contains("microsoft");
    }
}
```

---

## 六、Web 前端代码详解

### 6.1 项目架构

```
probe-web/src/
├── api/                    # 23 个 API 模块（每个后端控制器对应一个）
│   ├── request.js          #   Axios 实例（JWT 拦截器、Token 刷新）
│   ├── probe.js            #   探针 API
│   ├── syncTask.js         #   同步任务 API
│   ├── qualityRule.js      #   质量规则 API
│   └── ...
├── views/                  # 页面组件
│   ├── dashboard/          #   首页概览
│   ├── collection/         #   数据采集（探针列表、Agent 管理、数据库详情）
│   ├── sync/               #   同步任务、失败数据、数据汇聚
│   ├── quality/            #   质量规则、变更检测、告警记录
│   ├── monitoring/         #   实时监控、数据统计
│   └── system/             #   系统管理（审计日志、设置、文件上传等）
├── layouts/                # 布局组件
│   ├── MainLayout.vue      #   主布局（顶栏 + 侧边栏 + 内容区）
│   ├── Sidebar.vue         #   侧边导航栏（按菜单项级别 RBAC 过滤）
│   └── TopNav.vue          #   顶部导航栏（按角色动态切换系统链接）
├── components/             # 公共组件
│   ├── common/
│   │   ├── DataTable.vue   #   通用数据表格
│   │   ├── GlassCard.vue   #   毛玻璃卡片
│   │   ├── PageHeader.vue  #   页面标题
│   │   └── StatCard.vue    #   统计卡片
│   ├── charts/
│   │   └── TrendChart.vue  #   趋势图（ECharts）
│   ├── ProbeCard.vue       #   探针卡片
│   ├── RealtimeChart.vue   #   实时折线图
│   └── StatusBar.vue       #   状态栏
├── composables/            # 组合式函数
│   ├── useECharts.js       #   ECharts 生命周期管理
│   ├── usePolling.js       #   轮询 Hook
│   └── useTheme.js         #   主题切换
├── router/
│   └── index.js            # 路由配置（6 大模块 20+ 路由）
├── store/
│   └── index.js            # 全局状态（Vue 3 reactive，非 Pinia）
├── utils/
│   ├── request.js → api/request.js
│   ├── websocket.js        #   WebSocket 客户端（事件驱动）
│   ├── export.js           #   导出工具
│   ├── probeStatus.js      #   探针状态工具
│   └── ...
├── main.js                 # 入口文件
└── App.vue                 # 根组件
```

### 6.2 路由系统

> [index.js (router)](apps/probe-web/src/router/index.js)

```javascript
const routes = [
  { path: '/login', component: () => import('@/views/Login.vue') },
  { path: '/', redirect: '/dashboard' },

  // ===== Dashboard =====
  { path: '/dashboard', component: () => import('@/views/dashboard/DashboardView.vue') },

  // ===== Collection（数据采集）=====
  { path: '/collection/probes', component: () => import('@/views/collection/ProbeList.vue') },
  { path: '/collection/agents', component: () => import('@/views/collection/AgentManager.vue') },
  { path: '/collection/groups', component: () => import('@/views/collection/ProbeGroupManage.vue') },
  { path: '/collection/database/:probeKey', component: () => import('@/views/collection/DatabaseTablesView.vue') },
  { path: '/collection/database-probe/:probeKey', component: () => import('@/views/collection/DatabaseProbeDetail.vue') },

  // ===== Sync（数据同步）=====
  { path: '/sync/tasks', component: () => import('@/views/sync/SyncTask.vue') },
  { path: '/sync/dead-letter', component: () => import('@/views/sync/DeadLetterTask.vue') },
  { path: '/sync/aggregation', component: () => import('@/views/sync/DataAggregation.vue') },

  // ===== Quality（质量管理）=====
  { path: '/quality/rules', component: () => import('@/views/quality/QualityRule.vue') },
  { path: '/quality/changes', component: () => import('@/views/quality/ChangeDetection.vue') },
  { path: '/quality/alerts', component: () => import('@/views/quality/ChangeAlert.vue') },
  { path: '/quality/datasource-alerts', component: () => import('@/views/quality/DataSourceAlert.vue') },

  // ===== Monitoring（监控）=====
  { path: '/monitoring/realtime', component: () => import('@/views/monitoring/MonitorDashboard.vue') },
  { path: '/monitoring/statistics', component: () => import('@/views/monitoring/DataStatistics.vue') },

  // ===== System（系统管理）=====
  { path: '/system/audit-logs', component: () => import('@/views/system/AuditLog.vue') },
  { path: '/system/settings', component: () => import('@/views/system/Settings.vue') },
  { path: '/system/file-upload', component: () => import('@/views/system/FileUpload.vue') },
  // ... 更多

  // 向后兼容重定向（~20 条旧路由映射到新路径）
  { path: '/probes', redirect: '/collection/probes' },
  // ...
]

// 路由守卫：JWT Token 验证
router.beforeEach((to, from, next) => {
  const isLogin = localStorage.getItem('isLogin') === 'true'
  const token = localStorage.getItem('token')
  if (to.path !== '/login' && !(isLogin && token)) {
    next('/login')
  } else if (to.path === '/login' && isLogin && token) {
    next('/dashboard')
  } else if (to.meta.requireAdmin) {
    const roles = JSON.parse(localStorage.getItem('roles') || '[]')
    if (roles.includes('ROLE_ADMIN')) next()
    else { ElMessage.warning('无权限访问该页面'); next('/dashboard') }
  } else {
    next()
  }
})
```

### 6.3 HTTP 请求层

> [request.js](apps/probe-web/src/api/request.js)

```javascript
const request = axios.create({
  baseURL: '/api',
  timeout: 10000
})

// 请求拦截器：JWT Token 自动注入
request.interceptors.request.use(config => {
  const token = localStorage.getItem('token')
  if (token) config.headers['Authorization'] = `Bearer ${token}`
  return config
})

// 响应拦截器：Token 自动刷新 + Blob 下载处理
request.interceptors.response.use(
  response => {
    if (response.config.responseType === 'blob') return response  // 文件下载直通
    if (response.data.code === 200) return response.data
    ElMessage.error(response.data.message || '请求失败')
    return Promise.reject(new Error(response.data.message))
  },
  async error => {
    if (error.response?.status === 401 && !error.config._retry) {
      const refreshToken = localStorage.getItem('refreshToken')
      if (!refreshToken) return handleLoginExpired()

      // 防止并发刷新
      if (isRefreshing) {
        return new Promise((resolve, reject) => {
          failedQueue.push({ resolve, reject })
        }).then(token => {
          error.config.headers['Authorization'] = `Bearer ${token}`
          return request(error.config)
        })
      }

      // 尝试刷新 Token
      isRefreshing = true
      const response = await axios.post('/api/auth/refresh', { refreshToken })
      if (response.data.code === 200) {
        const { accessToken, refreshToken: newRefreshToken } = response.data.data
        localStorage.setItem('token', accessToken)
        localStorage.setItem('refreshToken', newRefreshToken)
        failedQueue.forEach(p => p.resolve(accessToken))  // 重放排队请求
        failedQueue = []
        error.config.headers['Authorization'] = `Bearer ${accessToken}`
        return request(error.config)
      }
    }
  }
)
```

### 6.4 登录与权限（RBAC）

登录流程包含完整的角色-权限传递链：

```
Login.vue handleLogin()
    → POST /api/auth/login { username, password }
    → AuthController：验证密码 → 生成 JWT → 查询角色和权限
    → 返回 { accessToken, refreshToken, userInfo: { roles, permissions, ... } }
    → 前端存储：localStorage['token/refreshToken/roles/permissions/isLogin']
    → Sidebar.vue：按 item.adminOnly 逐项过滤菜单（非整组隐藏）
    → TopNav.vue：系统链接按角色指向不同页面（admin→升级页，普通→设置页）
    → router beforeEach：meta.requireAdmin=true 的路由拦截非管理员
    → Settings.vue：安全/系统标签仅管理员可见
    → ProbeList.vue：读取 permissions 判断按钮可见性
```

**关键设计**：
- 后端 `AuthController` 返回 HTTP 语义状态码（401=认证失败，403=账户禁用）
- 角色标识使用 `ROLE_ADMIN` 前缀，与 Spring Security 约定一致
- 路由级 RBAC：`meta: { requireAdmin: true }` + `router.beforeEach` 守卫拦截非管理员
- 前端 `store` 从 `localStorage` 动态加载角色/权限，非硬编码

### 6.5 状态管理

> [index.js (store)](apps/probe-web/src/store/index.js)

```javascript
// 手写响应式 Store（非 Pinia），基于 Vue 3 reactive

import { reactive, computed } from 'vue'
import { probeApi } from '@/api/probe'

const state = reactive({
  // 用户信息（从 localStorage 动态加载）
  user: {
    id: null,
    username: '',
    realName: '',
    email: '',
    avatar: '',
    department: 'dev',
    position: '',
    roles: JSON.parse(localStorage.getItem('roles') || '[]'),
    permissions: JSON.parse(localStorage.getItem('permissions') || '[]')
  },

  // 系统设置
  settings: { general: {}, appearance: {}, notification: {}, security: {}, system: {} },
  statistics: { totalProbes: 0, onlineProbes: 0, totalSyncTasks: 0 },
  notifications: [],
  loading: false
})

const getters = {
  isLoggedIn: computed(() => !!state.user.id),
  isAdmin: computed(() => state.user.roles.includes('ROLE_ADMIN')),
  isDarkTheme: computed(() => state.settings.appearance?.theme === 'dark'),
  systemHealthy: computed(() => {
    return state.statistics.totalProbes === 0 ||
           (state.statistics.onlineProbes / state.statistics.totalProbes) > 0.5
  })
}

const actions = {
  setUser(user) { state.user = user },
  updateSettings(category, values) {
    state.settings[category] = { ...state.settings[category], ...values }
    localStorage.setItem('probeSettings', JSON.stringify(state.settings))
  },
  loadSettings() {
    const saved = localStorage.getItem('probeSettings')
    if (saved) state.settings = JSON.parse(saved)
  },
  logout() {
    state.user = null
    localStorage.removeItem('token')
    localStorage.removeItem('refreshToken')
    localStorage.removeItem('isLogin')
    localStorage.removeItem('roles')
    localStorage.removeItem('permissions')
  }
}

// 自动加载持久化设置
actions.loadSettings()

export function useStore() { return { state, ...getters, ...actions } }
```

**设计要点**：
- 用户角色和权限从 `localStorage` 动态加载，而非硬编码
- `isAdmin` 判断使用 `ROLE_ADMIN` 角色标识（Spring Security 角色前缀）
- 登录时 `AuthController` 返回完整角色列表和权限集合，前端持久化到 `localStorage`

### 6.6 页面组件详解

#### 6.6.1 DashboardView — 首页概览

> [DashboardView.vue](apps/probe-web/src/views/dashboard/DashboardView.vue)

```

**页面结构**：

```
┌─────────────────────────────────────────────────┐
│ PageHeader: "系统概览"                            │
├─────┬─────┬─────┬─────┬─────┬───────────────────┤
│探针数│在线数│同步数│汇聚源│汇聚表│活跃告警          │
│ StatCard × 6（响应式网格，6→3→2 列）            │
├───────────────────────┬─────────────────────────┤
│ 采集趋势（面积图）     │ 健康趋势（堆叠面积图）    │
│ TrendChart             │ TrendChart              │
├───────────────────────┼─────────────────────────┤
│ 最近告警列表           │ 同步任务状态              │
│ GlassCard              │ GlassCard               │
└───────────────────────┴─────────────────────────┘
```

**关键逻辑**：

```javascript
// 并行加载所有数据（Promise.allSettled 容错）
const loadDashboard = async () => {
  const [probesRes, syncRes, alertRes, aggRes] = await Promise.allSettled([
    probeApi.getProbes(),
    syncTaskApi.getSyncStatistics(),
    changeAlertApi.getRecentAlerts(),
    aggregationApi.getAggregationStats()
  ])
  // 单个 API 失败不阻塞整个 Dashboard
}

// 相对时间格式化
const formatTime = (time) => {
  const diff = Date.now() - new Date(time).getTime()
  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return Math.floor(diff / 60000) + ' 分钟前'
  return Math.floor(diff / 3600000) + ' 小时前'
}
```

#### 6.6.2 ProbeList — 探针列表（最复杂的页面）

> [ProbeList.vue](apps/probe-web/src/views/collection/ProbeList.vue)

```

**页面功能**：
- 三种类型探针的统一管理（SYSTEM / DATABASE / FILE）
- 探针 CRUD + 控制命令（启动/停止/重启）
- 文件浏览器（表格视图 + 树形视图）
- 数据库实例切换 + 表结构查看
- JSON 导入导出
- 39+ 项系统指标展示

**文件浏览器**：

```javascript
// 树形视图：懒加载目录内容
const loadTreeNode = async (node, resolve) => {
  const files = await fileProbeApi.getFiles(probeId, {
    path: node.data?.path || '/',
    page: 1,
    pageSize: 100
  })
  resolve(files.map(f => ({
    label: f.fileName,
    path: f.filePath,
    isDir: f.fileExtension === null,
    size: f.fileSize,
    children: f.fileExtension === null ? [] : undefined  // 目录才可展开
  })))
}
```

#### 6.6.3 SyncTask — 同步任务管理

> [SyncTask.vue](apps/probe-web/src/views/sync/SyncTask.vue)

```

**页面结构**：
- 顶部统计卡片（总任务数/启用/禁用/成功/失败）
- 任务列表（el-table）+ 创建/编辑弹窗
- 每行操作：启用开关、手动触发、编辑、删除、查看日志

**同步模式说明**：

| 模式 | 说明 | 触发方式 |
|------|------|----------|
| FULL | 全量同步 | Cron 定时 |
| INCREMENTAL | 增量同步（基于位置） | Cron 定时 |
| CHANGE_BASED | 变更驱动同步 | CDC 事件触发 |

#### 6.6.4 QualityRule — 质量规则管理

> [QualityRule.vue](apps/probe-web/src/views/quality/QualityRule.vue)

```

**页面结构**：
- Tab 切换：质量规则 / 坏记录
- 规则 CRUD + 作用域选择（探针/数据库/表/列）
- 规则类型：NOT_NULL / REGEX / RANGE / ENUM / LENGTH / TYPE_CHECK
- 严重级别：ERROR / WARNING / INFO（颜色编码）
- 即时检查按钮 + CSV 导出

#### 6.6.5 MonitorDashboard — 实时监控

> [MonitorDashboard.vue](apps/probe-web/src/views/monitoring/MonitorDashboard.vue)

```

这是前端性能优化最复杂的页面。

**三级数据加载策略**：

```
1. 快速加载探针列表（< 1 秒渲染骨架屏）
2. 后台异步加载每个探针的详细指标（非阻塞）
3. 缓存命中时立即展示，然后刷新
```

**性能优化**：

| 技术 | 说明 |
|------|------|
| 虚拟滚动 | 探针数 > 50 时使用 `vue-virtual-scroller`，仅渲染可见卡片 |
| 批量加载 | 指标每 3 个探针一批，批次间隔 100ms |
| 客户端缓存 | Map 缓存，10 秒 TTL，最大 100 条，LRU 淘汰 |
| WebSocket 推送 | 实时指标更新，无需轮询 |
| 可配置刷新 | 10s / 30s / 60s 自动刷新，持久化到 localStorage |

**告警过滤**：

```javascript
// 点击告警徽章过滤探针列表
const alertFilters = reactive({ disk: false, memory: false, cpu: false, network: false })
const filteredProbes = computed(() => {
  return probes.value.filter(p => {
    if (alertFilters.disk && p.metrics?.diskUsage <= 80) return false
    if (alertFilters.memory && p.metrics?.memoryUsage <= 85) return false
    if (alertFilters.cpu && p.metrics?.cpuUsage <= 90) return false
    return true
  })
})
```

### 6.7 组合式函数 (composables)

#### useECharts — ECharts 生命周期管理

> [useECharts.js](apps/probe-web/src/composables/useECharts.js)

```javascript
import { shallowRef, onMounted, onUnmounted } from 'vue'
import * as echarts from 'echarts'

export function useECharts(options = {}) {
  const chartInstance = shallowRef(null)  // shallowRef 避免深度响应式

  const setOption = (option) => {
    if (chartInstance.value) chartInstance.value.setOption(option, true)
  }

  const resize = () => chartInstance.value?.resize()

  onMounted(() => {
    if (options.el) {
      chartInstance.value = echarts.init(options.el)
      if (options.option) setOption(options.option)
      if (options.autoResize !== false) {
        window.addEventListener('resize', resize)
      }
    }
  })

  onUnmounted(() => {
    chartInstance.value?.dispose()
    window.removeEventListener('resize', resize)
  })

  return { chartInstance, setOption, resize, dispose: () => chartInstance.value?.dispose() }
}
```

### 6.8 工具函数 (utils)

#### 设置管理工具 (settings.js)

> [settings.js](apps/probe-web/src/utils/settings.js)

```javascript
// 提供前后端设置同步：flatten/unflatten 转换 + API 集成

// 嵌套 → 扁平（后端格式）
export function flattenSettings(nested) {
  const flat = {}
  for (const [category, values] of Object.entries(nested)) {
    for (const [key, value] of Object.entries(values)) {
      flat[`${category}.${key}`] = Array.isArray(value) ? JSON.stringify(value) : String(value)
    }
  }
  return flat
}

// 扁平 → 嵌套（前端格式）
export function unflattenSettings(flatMap) {
  const result = JSON.parse(JSON.stringify(DEFAULT_SETTINGS))
  for (const [key, value] of Object.entries(flatMap)) {
    const dotIndex = key.indexOf('.')
    const category = key.substring(0, dotIndex)
    const prop = key.substring(dotIndex + 1)
    // 自动类型转换：JSON 字符串 → 原生类型
    result[category][prop] = JSON.parse(value)
  }
  return result
}

// API 集成：优先从后端加载，失败回退本地缓存
export async function loadSettingsFromAPI() { ... }
export async function saveSettingsToAPI(settings) { ... }
export async function resetSettingsFromAPI(category) { ... }
```

**设计要点**：
- 前端嵌套结构与后端扁平 `key-value` 格式自动转换
- API 调用失败时自动降级到 `localStorage` 缓存
- `SettingsServiceImpl` 后端定义了 5 大分类 33 项默认配置

> [websocket.js](apps/probe-web/src/utils/websocket.js)

```javascript
export class ProbeWebSocket {
  constructor(url, options = {}) {
    this.url = url
    this.maxReconnectAttempts = options.maxReconnectAttempts || 10
    this.reconnectDelay = options.reconnectDelay || 3000
    this.listeners = new Map()  // 事件监听器
  }

  // 事件驱动
  on(event, callback) {
    if (!this.listeners.has(event)) this.listeners.set(event, [])
    this.listeners.get(event).push(callback)
  }

  // 消息类型路由
  handleMessage(message) {
    const data = JSON.parse(message.data)
    switch (data.type) {
      case 'connected':      this.emit('connected', data); break
      case 'databaseMetadata': this.emit('databaseMetadata', data); break
      case 'networkPing':    this.emit('networkPing', data); break
      case 'fileScan':       this.emit('fileScan', data); break
      case 'heartbeat':      this.emit('heartbeat', data); break
      case 'error':          this.emit('error', data); break
      default:
        if (data.cmd) this.emit(data.cmd.toLowerCase(), data)
    }
  }

  // 发送命令
  sendCommand(cmd, payload) {
    this.ws.send(JSON.stringify({
      type: 'COMMAND', cmd, probeKey: this.probeKey, payload, timestamp: Date.now()
    }))
  }

  // 自动重连
  reconnect() {
    if (this.manualClose || this.reconnectAttempts >= this.maxReconnectAttempts) return
    setTimeout(() => {
      this.reconnectAttempts++
      this.connect()
    }, this.reconnectDelay)
  }
}
```

---

## 七、设计模式总结

### 7.1 设计模式总览

| 设计模式 | 应用位置 | 解决的问题 |
|----------|----------|-----------|
| **策略 + 工厂** | `ProbeHeartbeatStrategy` + `ProbeStrategyFactory` | 不同探针类型的心跳超时逻辑 |
| **策略（插件）** | `DatabasePlugin` / `CDCPlugin` 10+ 实现 | 多数据库类型可扩展适配 |
| **抽象工厂** | `ProbeFactory` + `AbstractProbeFactory` + `ProbeFactoryManager` | 不同探针类型的创建逻辑 |
| **装饰器** | `ProbeService` 三层装饰器 | 监控/日志/缓存可开关叠加 |
| **观察者** | `CDCChangeEvent` / `MetricDataEvent`（Spring Events） | 检测 → 同步 → 告警解耦 |
| **责任链** | `MessageDispatcher` + 9 个 `MessageHandler` | WebSocket 消息精准路由 |
| **拦截过滤器** | `RateLimit` → `JWT` → `Permission` 三层拦截器 | HTTP 请求安全链 |
| **生产者-消费者** | CDCManager `BlockingQueue` | CDC 事件缓冲与批量上报 |
| **快照对比** | `ChangeDetectionServiceImpl.compareSnapshots()` | 时序状态对比检测变更 |
| **规则引擎** | `QualityValidator` | 六维数据质量校验 |
| **命令模式** | WebSocket 控制指令 | 采集/扫描/启停/升级远程命令 |
| **模板方法** | `AbstractProbeFactory.createProbe()` | 探针创建通用流程，子类定制细节 |
| **单例（Bean）** | 所有 Spring `@Component` / `@Service` | 统一生命周期管理 |
| **适配器** | `ConnectionManager.toCompletableFuture()` | 兼容新旧 Spring API |
| **事件发射器** | 前端 `ProbeWebSocket` 类 | 前端 WebSocket 消息类型路由 |

### 7.2 SOLID 原则体现

| 原则 | 体现 |
|------|------|
| **S — 单一职责** | `MetaProbeWebSocketHandler` 只管连接和加解密，业务委托 `MessageDispatcher` |
| **O — 开闭原则** | 新增数据库类型只需实现 `DatabasePlugin` 接口，无需修改核心代码 |
| **L — 里氏替换** | 所有 `DatabasePlugin` 实现可互相替换 |
| **I — 接口隔离** | `DatabasePlugin`、`CDCPlugin`、`FilePlugin` 接口独立 |
| **D — 依赖倒置** | Controller 依赖 `ProbeService` 接口，不依赖具体实现 |

---

## 八、数据流全景

### 8.1 探针注册与通信

```
Agent                          Admin Service              PostgreSQL
  │                                │                         │
  ├── UDP Discovery ──────────────>│                         │
  │   (agentId, version, IP,       │                         │
  │    capabilities)               ├── Create Probe Record ─>│
  │                                │                         │
  │<── DiscoveryResponse ─────────┤                         │
  │    (websocketUrl, probeKey)    │                         │
  │                                │                         │
  ├── WebSocket Connect ──────────>│                         │
  │   ws://admin:8081/ws/meta      ├── Register Agent ─────>│
  │   ?code=AGENT&probe_key=xxx    │                         │
  │                                │                         │
  └── heartbeat (30s) ────────────>│                         │
                                   └── Update Heartbeat ────>│
```

### 8.2 元数据采集流程

```
前端按钮「采集」
    → POST /api/probes/{probeKey}/collect
    → ProbeController
    → MetaProbeWebSocketHandler.sendCollectCommand()
    → WebSocket 发送 COMMAND:PROBE 到 Agent
    → Agent MessageHandler 接收
    → DatabaseService.collectMetadata()
    → MySqlPlugin.getMetadata()  // 通过 DatabasePlugin 接口
    → JDBC 查询 DatabaseMetaData
    → WebSocket 上报 metadata
    → Admin MessageDispatcher 路由到对应 Handler
    → DatabaseMetadataServiceImpl 存储
    → 前端刷新
```

### 8.3 CDC 变更捕获流程

```
MySQL Binlog 变更
    → BinlogCDCPlugin.handleWriteEvent()
    → BlockingQueue.offer(CDCEvent)
    → CDCManager.flushEvents()（每 5 秒）
        → QualityValidator.validate()（Agent 端质量过滤）
            → 合格事件 → HTTP POST /api/agents/{code}/cdc-events
            → 不合格记录 → HTTP POST /api/agents/{code}/quality-bad-records
    → Admin ChangeDetectionServiceImpl.processCDCEvents()
        → 持久化 change_log
        → 发布 CDCChangeEvent（Spring Event）
    → ChangeTriggeredSyncListener 监听
        → 查询 realtimeSyncEnabled=true 的同步任务
        → 触发实时同步
```

### 8.4 数据同步流程

```
定时触发（Cron）或 CDC 触发
    → SyncTaskServiceImpl.executeSync()
        → 通过 WebSocket 命令 Agent 拉取源数据
        → Agent DatabasePlugin.getDataContent()
        → Admin QualityFilterEngine.filter()
            → 通过的行 → 写入目标（UPSERT/INSERT/SKIP）
            → 不通过的行 → 写入 aggregation.quality_bad_records
        → 记录 sync_log
        → 更新 sync_task.lastSyncPosition
```

---

## 九、安全设计

### 9.1 认证与授权

```
┌─────────────────────────────────────────────────┐
│                    前端                          │
│  localStorage 存储: token, refreshToken, isLogin │
│  Axios 拦截器自动注入 Authorization: Bearer {JWT} │
└──────────────────────┬──────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────┐
│                Admin 后端                        │
│                                                  │
│  请求 → RateLimitInterceptor（登录限流）           │
│       → JwtInterceptor（Token 验证 + 黑名单检查）  │
│       → PermissionInterceptor（@RequirePermission）│
│       → Controller                               │
│                                                  │
│  JWT 双 Token 机制：                              │
│  - AccessToken：8 小时过期                        │
│  - RefreshToken：自动刷新，无感续期                │
│  - 并发刷新排队：failedQueue 防重复刷新            │
└──────────────────────────────────────────────────┘
```

### 9.2 数据加密

| 场景 | 算法 | 说明 |
|------|------|------|
| WebSocket 消息 | AES-128 | Admin ↔ Agent 通信加密，密钥为 probeKey 填充 16 字节 |
| 数据库密码存储 | AES | `DatabaseConnection` 实体的密码字段加密存储 |
| 用户密码 | BCrypt | `UserServiceImpl` 使用 BCrypt 哈希存储 |
| Agent 认证 | probe.key | Agent 通过密钥向 Admin 验证身份 |

### 9.3 其他安全措施

| 措施 | 实现 |
|------|------|
| SQL 注入防护 | MyBatis-Plus 参数化查询 + `isValidSqlIdentifier()` 正则校验 |
| XSS 防护 | 前端输入转义 + 后端参数校验 |
| CSRF 防护 | JWT Token 认证（非 Cookie） |
| 操作审计 | `@Audited` 注解自动记录所有敏感操作 |
| 分布式锁 | `@DistributedLock` 防止并发重复操作 |
| 接口限流 | `@RateLimit` + Redis 滑动窗口 |
| WebSocket Origin 验证 | `WebSocketOriginInterceptor` 防止跨域 WebSocket 连接 |
| 敏感参数过滤 | 审计日志不记录 password/token/secret/key 字段的值 |

---

## 十、部署架构

### 10.1 Docker Compose 服务编排

```yaml
# 6 个服务
services:
  postgres:16-alpine    # 主数据库（健康检查 + 资源限制 1G）
  mysql:8.0             # 测试源库（自动初始化 SQL）
  redis:7-alpine        # 缓存 + 分布式锁
  admin                 # Spring Boot（依赖 postgres + redis 健康）
  agent                 # Spring Boot（依赖 admin 健康）
  web                   # Nginx（依赖 admin 健康）
```

**服务依赖链**：

```
postgres ──(healthy)──> admin ──(healthy)──> agent
redis ────(healthy)──>   │                    │
                         └──(healthy)──> web
mysql ──────────────────> (Agent 通过 JDBC 直连)
```

### 10.2 端口分配

| 服务 | 端口 | 说明 |
|------|------|------|
| Admin | 8081 | REST API + WebSocket |
| Agent | 58081 | Agent HTTP + 健康检查 |
| Web (dev) | 5173 | Vite 开发服务器 |
| PostgreSQL | 5432 | 主数据库 |
| MySQL | 3306 | 测试源数据库 |
| Redis | 6379 | 缓存 |
| MinIO | 9000 | 对象存储 |
| UDP Discovery | 9090 | Agent 自动发现 |
| UDP Metrics | 9999 | UDP 指标上报 |

### 10.3 Kubernetes 部署

项目提供 Helm Chart（`helm/probe-manager/`），支持 Kubernetes 集群部署。

---

> 文档生成时间：2026-05-18
> 项目版本：1.0.0
