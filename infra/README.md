# Infrastructure

基础设施启动/停止脚本。

## 目录结构

```
infra/
└── scripts/
    ├── start-all.sh       # 一键启动（支持多种模式）
    └── stop-services.sh   # 一键停止所有服务
```

## 使用方法

```bash
# 启动所有服务（Admin + Web + Agent）
./infra/scripts/start-all.sh

# 开发模式（Admin + Agent，跳过前端）
./infra/scripts/start-all.sh --dev

# 单独启动
./infra/scripts/start-all.sh --admin
./infra/scripts/start-all.sh --agent
./infra/scripts/start-all.sh --web

# 停止所有服务
./infra/scripts/stop-services.sh
```

## 环境变量

脚本从项目根目录 `.env` 文件加载环境变量，未设置的变量使用默认值：

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `DB_HOST` | localhost | PostgreSQL 主机 |
| `DB_PORT` | 5432 | PostgreSQL 端口 |
| `DB_NAME` | probe_db | 数据库名 |
| `DB_USERNAME` | probe_user | 数据库用户 |
| `DB_PASSWORD` | probe_pass | 数据库密码 |
| `MINIO_ENDPOINT` | http://localhost:9000 | MinIO 地址 |
| `JWT_SECRET` | (随机生成) | JWT 密钥 |

## 服务端口

| 服务 | 端口 |
|------|------|
| Admin | 8081 |
| Web | 5173 |
| Agent | 58081 |
