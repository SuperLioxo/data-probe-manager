# Data Probe Manager (数据探针管理系统)

<div align="center">

**分布式数据采集与汇聚管理平台**

[![Java](https://img.shields.io/badge/Java-21-orange)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.2-green)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue)](https://www.postgresql.org/)
[![Vue.js](https://img.shields.io/badge/Vue.js-3.4-brightgreen)](https://vuejs.org/)
[![Element Plus](https://img.shields.io/badge/Element%20Plus-2.5-blue)](https://element-plus.org/)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

</div>

---

## 项目定位

将分散在各业务端、服务器、文件系统中的数据，通过探针自动采集、质量过滤、实时同步，统一汇聚到管理系统后端，实现集中查看、查询与管理，全程不影响原始数据源正常运行。

---

## 功能模块

| 模块 | 说明 |
|------|------|
| **数据源管理** | 统一管理各类数据源（关系数据库、文件系统），加密存储连接信息，支持连接测试 |
| **元数据提取** | 自动提取结构信息（库表字段/文件属性），不读取业务数据 |
| **数据变化检测 (CDC)** | MySQL Binlog / PostgreSQL WAL 实时监听行级变更，支持 INSERT/UPDATE/DELETE |
| **数据质量过滤** | 完整性、格式、范围、枚举、长度、类型六维校验，不合格数据自动隔离 |
| **数据自动同步** | 实时同步（CDC 触发）与定时同步（Cron 调度），同步前自动质量过滤 |
| **文件上传** | 结构化文件解析入汇聚库，普通文件存 MinIO/本地 |
| **状态监测** | 探针心跳检测、数据源连通性监控，异常告警，支持多渠道通知 |
| **多数据源适配** | 插件化架构，支持 MySQL、PostgreSQL、Oracle、SQL Server、SQLite、达梦、MongoDB、Redis、ES 等 |

---

## 系统架构

```
┌─────────────────────────────────────────────────────────────────┐
│                         Web Frontend                            │
│                   (Vue.js 3 + Element Plus)                     │
│                       Port: 5173 (dev)                          │
└──────────────────────────────┬──────────────────────────────────┘
                               │ HTTP/WebSocket
┌──────────────────────────────▼──────────────────────────────────┐
│                        Admin Service                            │
│                      (Spring Boot 3.4.2)                        │
│                         Port: 8081                              │
│                                                                 │
│  探针管理 · 告警引擎 · JWT认证 · WebSocket服务器                  │
│  汇聚库管理 · 质量过滤 · 同步调度 · CDC事件处理 · 审计日志        │
└──────────┬──────────────────────────────────┬───────────────────┘
           │                                  │
    ┌──────▼──────┐                  ┌────────▼────────┐
    │ PostgreSQL  │                  │  Infrastructure │
    │ Port: 5432  │                  │  Redis · MinIO  │
    └─────────────┘                  └─────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                         Agent Service                           │
│                   (数据采集客户端, Port: 58081)                   │
│                                                                 │
│  Binlog CDC · WAL CDC · 文件扫描 · 数据库元数据采集              │
│  WebSocket客户端 · 插件管理 · 质量过滤 · 心跳上报                 │
└─────────────────────────────────────────────────────────────────┘
```

### 技术栈

| 层级 | 技术 |
|------|------|
| **Admin** | Java 21, Spring Boot 3.4.2, PostgreSQL, MyBatis-Plus, JWT, Spring WebSocket, Druid, Redisson |
| **Agent** | Spring Boot 3.4.2, mysql-binlog-connector-java, OSHI, Netty 4.1, 多数据库 JDBC 驱动, MinIO |
| **Web** | Vue 3.4, Vite 5.4, Element Plus 2.5, ECharts 5.5, Axios, Vue Router, Sass |
| **基础设施** | PostgreSQL 16, Redis 7, MinIO, Docker Compose |

---

## 快速开始

### 环境要求

- Java 21+, Node.js 18+, Maven 3.8+, PostgreSQL 15+

### 方式一：脚本启动（开发环境）

```bash
# 一键启动（含数据库初始化）
./infra/scripts/start-all.sh

# 灵活启动
./infra/scripts/start-all.sh --dev        # 开发模式（Admin + Agent）
./infra/scripts/start-all.sh --admin      # 只启动 Admin
./infra/scripts/start-all.sh --agent      # 只启动 Agent
./infra/scripts/start-all.sh --web        # 只启动前端

# 停止
./infra/scripts/stop-services.sh
```

> 启动脚本自动执行 `apps/probe-admin/src/main/resources/sql/` 下的 SQL 文件。设置 `SKIP_DATABASES=true` 可跳过。

### 方式二：Docker Compose（推荐生产环境）

```bash
# 1. 配置环境变量
cp .env.example .env
# 编辑 .env 填写密码和密钥

# 2. 启动所有服务（含 PostgreSQL、MySQL、Redis、Admin、Agent、Web）
docker compose up -d

# 3. 查看状态
docker compose ps
docker compose logs -f admin
```

Docker Compose 包含完整的 6 个服务：PostgreSQL、MySQL（测试库）、Redis、Admin、Agent、Web。MySQL 会自动执行 `init/mysql/` 下的初始化 SQL。

### 访问

| 服务 | 地址 |
|------|------|
| Web 界面 | http://localhost:5173 |
| Admin API | http://localhost:8081 |
| Admin Health | http://localhost:8081/actuator/health |
| Agent Health | http://localhost:58081/api/health |

默认账号：**admin / admin123**

---

## 项目结构

```
data-probe-manager/
├── apps/
│   ├── probe-admin/                # Admin 服务 (Spring Boot)
│   ├── probe-agent/                # Agent 服务 (数据采集)
│   └── probe-web/                  # Web 前端 (Vue.js)
├── config/
│   ├── app/                        # 应用配置
│   ├── infra/                      # 基础设施配置
│   └── security/                   # 安全配置（JWT、加密密钥）
├── infra/scripts/                  # 启动/停止脚本
├── init/mysql/                     # MySQL 测试库初始化 SQL
├── monitoring/                     # 监控配置（Prometheus）
├── helm/                           # Kubernetes Helm Charts
├── tests/                          # 测试
├── docker-compose.yml              # Docker Compose 编排
├── .env                            # 环境变量
└── pom.xml                         # Maven 父 POM
```

### Admin (`apps/probe-admin`)

```
src/main/java/com/lixin/probe/
├── config/              # 配置类（数据源、WebSocket、安全、Redis）
├── controller/          # REST 控制器（探针、数据库、文件、同步、告警、Agent管理）
├── entity/              # 数据库实体
├── mapper/              # MyBatis-Plus 映射器
├── service/             # 业务逻辑层（接口 + impl）
├── websocket/           # WebSocket 服务端（Meta/File 双通道）
├── engine/              # 告警规则引擎
├── scheduler/           # 同步任务调度器
├── strategy/            # 策略模式（探针类型适配）
├── factory/             # 工厂模式
├── udp/                 # UDP 通信（发现/数据上报）
├── timeseries/          # 时序数据处理
└── exception/           # 全局异常处理

src/main/resources/
├── application.yml      # 主配置
└── sql/                 # 数据库建表脚本（24个）
```

### Agent (`apps/probe-agent`)

```
src/main/java/com/lixin/probe/agent/
├── config/              # 配置管理（多路径 database-config.yml 加载）
├── controller/          # REST 控制器（健康检查、数据库扫描、文件操作）
├── service/             # 业务服务（文件扫描、数据库采集、系统监控）
├── plugin/              # 插件系统
│   ├── api/             #   DatabasePlugin 接口
│   └── impl/database/   #   PostgreSQL/MySQL/Oracle... 实现
├── websocket/           # WebSocket 客户端（自动重连、心跳）
├── collector/           # 系统指标收集（CPU/内存/磁盘/网络）
├── discovery/           # UDP 服务发现与自动注册
├── connection/          # 数据库连接池管理
└── sync/                # 数据同步机制

src/main/resources/
├── application.yml      # Agent 配置
└── database-config.yml  # 数据库连接配置（classpath 兜底）
```

### Web (`apps/probe-web`)

```
src/
├── api/                 # API 客户端（Axios + JWT 拦截器）
├── views/
│   ├── dashboard/       #   仪表板（探针概览、同步任务、告警）
│   ├── collection/      #   数据采集（探针列表、数据库详情、文件探针）
│   ├── sync/            #   同步任务管理
│   ├── quality/         #   质量规则与坏记录
│   ├── monitoring/      #   监控与告警
│   └── system/          #   系统管理（用户、审计日志、设置）
├── layouts/             # 页面布局组件
├── composables/         # 组合式函数（useApiData, useWebSocket, useTheme）
├── router/              # 路由配置（权限守卫）
├── store/               # 状态管理
├── components/          # 公共组件
└── utils/               # 工具函数（探针状态、导出、WebSocket）
```

---

## 数据流

### 总体流程

```
原始数据源                  Agent (探针端)                    Admin (管理端)
─────────                  ─────────────                    ─────────────

MySQL ─── Binlog CDC ───> BinlogCDCPlugin ──┐
PostgreSQL ── WAL CDC ──> PostgreSQLCDCPlugin ┤
                                            ├─> CDCManager ─质量过滤─> ChangeDetection
文件系统 ──── 文件扫描 ──> FileService ───────┤        │                  │
数据库 ──── 元数据提取 ──> DatabaseService ──┤        ▼                  │
数据源 ──── 心跳检测 ───> HeartbeatService ─┘   坏记录上报             │
                                                                      ▼
                                                            质量过滤引擎 (Admin)
                                                                      │
                                                            ┌─────────┤
                                                            ▼         ▼
                                                     汇聚库(Aggregation)  不合格记录
                                                            │
                                                            ▼
                                                     前端展示 · 告警通知
```

### 探针注册与通信

```
Agent                         Admin Service              PostgreSQL
  │                                │                         │
  ├── UDP Discovery ──────────────>│                         │
  │                                ├── Create Probe Record ─>│
  │<── Probe Created (probeKey) ───┤                         │
  │                                │                         │
  ├── WebSocket Connect ──────────>│                         │
  │                                ├── Update Status ───────>│
  └── heartbeat (30s) ────────────>│                         │
                                   └── Update Heartbeat ────>│
```

探针在线需同时满足：状态为 `online` + 最近 5 分钟内有心跳。

---

## 核心实现

### WebSocket 双通道通信

Admin 与 Agent 之间通过两条 WebSocket 通道通信：
- **Meta 通道** (`/ws/meta`)：元数据上报、控制指令下发、心跳
- **File 通道** (`/ws/file`)：文件上传、文件扫描结果上报

Admin 按 `agentCode` 管理连接，向指定 Agent 下发控制命令；Agent 端自动重连。

### 数据库连接管理

Agent 通过 `database-config.yml` 配置多个数据库实例，支持多路径查找策略：

```
JAR 同级目录 → 当前工作目录 → classpath
```

确保无论从哪个目录启动 Agent，都能找到配置文件。

### 文件扫描

Agent `FileService` 递归扫描配置目录，支持全文件类型（`*`）、深度限制、大小过滤。Admin 通过 WebSocket 下发扫描指令，Agent 扫描后上报结果，Admin 存入 `file_metadata` 表。

### 安全

- JWT Token + RBAC 权限控制
- 数据库密码等敏感配置加密存储（`config/security/`）
- Agent 认证密钥（`probe.key`）
- 操作审计日志

---

## API 参考

### 认证

```bash
curl -X POST http://localhost:8081/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'
```

### 核心端点

| 端点 | 方法 | 说明 |
|------|------|------|
| `/api/auth/login` | POST | 用户登录 |
| `/api/probes` | GET/POST/PUT/DELETE | 探针 CRUD（支持 type 过滤） |
| `/api/probes/{probeKey}/instances` | GET | 数据库实例列表 |
| `/api/probes/{probeKey}/selected-instance` | POST | 保存选中的数据库实例 |
| `/api/probes/{probeKey}/collect` | POST | 触发元数据采集 |
| `/api/probes/{id}/scan` | POST | 触发文件扫描 |
| `/api/probes/{id}/files` | GET | 文件列表 |
| `/api/probes/test-connection` | POST | 测试数据库连接 |
| `/api/database-metadata/{probeKey}/tables` | GET | 数据库表列表 |
| `/api/database-metadata/{probeKey}/table-data` | GET | 查询表数据 |
| `/api/database-connections` | GET/POST | 数据库连接管理 |
| `/api/file-probes/{id}/scan` | POST | 触发文件探针扫描 |
| `/api/file-upload` | POST | 文件上传 |
| `/api/aggregation/tables` | GET | 汇聚表列表 |
| `/api/sync-tasks` | GET/POST/PUT/DELETE | 同步任务管理 |
| `/api/sync-tasks/dead-letters` | GET | 死信任务查询 |
| `/api/quality-rules` | GET/POST/PUT/DELETE | 质量规则管理 |
| `/api/datasource-alerts/configs` | GET/POST/PUT/DELETE | 数据源告警配置 |
| `/api/change-alerts/configs` | GET/POST/PUT/DELETE | 变化告警配置 |
| `/api/alert-channels` | GET/POST/PUT/DELETE | 告警渠道管理 |
| `/api/audit-logs` | GET | 审计日志 |

---

## 环境变量

通过 `.env` 文件配置，主要变量：

| 变量 | 说明 | 必须 |
|------|------|------|
| `POSTGRES_PASSWORD` | PostgreSQL 密码 | Docker 部署必须 |
| `JWT_SECRET` | JWT 签名密钥 | Docker 部署必须 |
| `META_ENCRYPTION_KEY` | 元数据加密密钥 | Docker 部署必须 |
| `FILE_ENCRYPTION_KEY` | 文件加密密钥 | Docker 部署必须 |
| `AGENT_KEY` | Agent 认证密钥 | Docker 部署必须 |
| `MINIO_ACCESS_KEY` | MinIO Access Key | 文件上传需要 |
| `MINIO_SECRET_KEY` | MinIO Secret Key | 文件上传需要 |
| `DB_HOST` | PostgreSQL 地址（默认 localhost） | 否 |
| `DB_PORT` | PostgreSQL 端口（默认 5432） | 否 |
| `DB_NAME` | 数据库名（默认 probe_db） | 否 |

脚本启动时，未设置的可选变量使用默认值，JWT/加密密钥自动生成临时值。

---

## CDC 配置

### MySQL

```sql
-- my.cnf
[mysqld]
server-id = 1
log_bin = mysql-bin
binlog_format = ROW
binlog_row_image = FULL
```

### PostgreSQL

```sql
-- postgresql.conf
wal_level = logical
max_replication_slots = 4

CREATE PUBLICATION probe_cdc FOR ALL TABLES;
```

---

## 开发

### 构建

```bash
# 编译 Admin
cd apps/probe-admin && mvn clean package -DskipTests

# 编译 Agent
cd apps/probe-agent && mvn clean package -DskipTests

# 构建前端
cd apps/probe-web && npm install && npm run build
```

### 端口分配

| 服务 | 端口 |
|------|------|
| Admin | 8081 |
| Agent | 58081 |
| Web (dev) | 5173 |
| PostgreSQL | 5432 |
| MySQL (测试) | 3306 |
| Redis | 6379 |
| MinIO | 9000 |

### 常见问题

**端口占用**：`ss -tuln | grep -E '8081|5173|58081'` 检查占用

**数据库连接失败**：确认 PostgreSQL 运行在 5432，`probe_user` / `probe_pass`

**JWT Token 过期**：清除浏览器 localStorage 后重新登录

**Agent 找不到数据库配置**：确认 `database-config.yml` 在 Agent JAR 同级目录或 `src/main/resources/` 下

---

## License

[MIT](LICENSE)
