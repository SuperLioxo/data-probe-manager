#!/bin/bash

# Data Probe Manager 一键启动脚本
# 同时启动 Admin、Web、Agent 三个服务
#
# 使用方法:
#   ./start-all.sh              # 启动所有服务（默认）
#   ./start-all.sh --dev        # 开发模式（跳过Web，只启动Admin和Agent）
#   ./start-all.sh --backend    # 只启动后端服务（Admin + Agent）
#   ./start-all.sh --web        # 只启动Web前端
#   ./start-all.sh --admin      # 只启动Admin服务
#   ./start-all.sh --agent      # 只启动Agent服务

set -e

# 获取脚本目录
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"

# 加载 .env 文件中的环境变量
ENV_FILE="$PROJECT_DIR/.env"
if [ -f "$ENV_FILE" ]; then
    set -a
    source "$ENV_FILE"
    set +a
    echo "✓ 已加载环境变量: $ENV_FILE"
else
    echo "⚠️  未找到 .env 文件: $ENV_FILE"
    echo "  请复制模板: cp .env.example .env"
fi

# 解析命令行参数
MODE="all"
SERVICES=""

while [[ $# -gt 0 ]]; do
    case $1 in
        --dev)
            MODE="dev"
            shift
            ;;
        --backend)
            SERVICES="backend"
            shift
            ;;
        --web)
            SERVICES="web"
            shift
            ;;
        --admin)
            SERVICES="admin"
            shift
            ;;
        --agent)
            SERVICES="agent"
            shift
            ;;
        -h|--help)
            echo "使用方法: $0 [选项]"
            echo ""
            echo "选项:"
            echo "  --dev        开发模式（跳过Web，只启动Admin和Agent）"
            echo "  --backend    只启动后端服务（Admin + Agent）"
            echo "  --web        只启动Web前端"
            echo "  --admin      只启动Admin服务"
            echo "  --agent      只启动Agent服务"
            echo "  -h, --help   显示此帮助信息"
            echo ""
            echo "示例:"
            echo "  $0              # 启动所有服务"
            echo "  $0 --dev        # 开发模式（Admin + Agent）"
            echo "  $0 --backend    # 只启动后端"
            echo "  $0 --admin      # 只启动Admin"
            exit 0
            ;;
        *)
            echo "未知参数: $1"
            echo "使用 -h 查看帮助信息"
            exit 1
            ;;
    esac
done

# 配置
LOG_DIR="${LOG_DIR:-$PROJECT_DIR/logs}"
ADMIN_PORT=8081
WEB_PORT=5173
AGENT_PORT=58081

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# 创建日志目录
mkdir -p "$LOG_DIR"

echo "========================================"
echo "  Data Probe Manager 一键启动"
echo "========================================"
echo ""
echo -e "${BLUE}日志目录:${NC} $LOG_DIR"
echo -e "${BLUE}工作目录:${NC} $PROJECT_DIR"
echo ""

# ========================================
# 0. 启动数据库服务
# ========================================

echo -e "${BLUE}=== 启动数据库服务 ===${NC}"

# 检查是否需要跳过数据库启动
if [ "$SKIP_DATABASES" = "true" ]; then
    echo -e "${YELLOW}跳过数据库启动（SKIP_DATABASES=true）${NC}"
else
    # 检查并启动PostgreSQL数据库
    if command -v systemctl >/dev/null 2>&1 && systemctl list-units --type=service | grep -q postgresql; then
        # 检查PostgreSQL服务状态
        if systemctl is-active --quiet postgresql; then
            echo -e "${GREEN}✓${NC} PostgreSQL服务运行中"
        else
            echo -e "${YELLOW}⚠️  PostgreSQL服务未运行，正在启动...${NC}"
            sudo systemctl start postgresql
            if [ $? -eq 0 ]; then
                echo -e "${GREEN}✓${NC} PostgreSQL服务启动成功"
            else
                echo -e "${RED}✗${NC} PostgreSQL服务启动失败"
            fi
        fi
    else
        echo -e "${YELLOW}⚠️  未找到PostgreSQL服务，跳过数据库启动${NC}"
    fi
fi

echo ""

# ========================================
# 1. 设置环境变量
# ========================================

echo -e "${BLUE}=== 设置环境变量 ===${NC}"

# 数据库配置
export DB_PORT=${DB_PORT:-5432}
export DB_HOST=${DB_HOST:-localhost}
export DB_PASSWORD=${DB_PASSWORD:-probe_pass}
export DB_NAME=${DB_NAME:-probe_db}
export DB_USERNAME=${DB_USERNAME:-probe_user}

# Redis 配置
export REDIS_HOST=${REDIS_HOST:-localhost}
export REDIS_PORT=${REDIS_PORT:-6379}

# MinIO 配置
export MINIO_ENDPOINT=${MINIO_ENDPOINT:-http://localhost:9000}
export MINIO_PUBLIC_URL=${MINIO_PUBLIC_URL:-http://localhost:9000}
export MINIO_ACCESS_KEY=${MINIO_ACCESS_KEY:-minioadmin}
export MINIO_SECRET_KEY=${MINIO_SECRET_KEY:-minioadmin}

# 安全密钥（如果未设置则生成临时随机值用于开发）
if [ -z "$JWT_SECRET" ]; then
    export JWT_SECRET="dev-$(openssl rand -hex 32)"
    echo -e "${YELLOW}⚠️  JWT_SECRET 未设置，已生成临时密钥（重启后失效）${NC}"
fi

if [ -z "$META_ENCRYPTION_KEY" ]; then
    export META_ENCRYPTION_KEY="$(openssl rand -base64 32)"
    echo -e "${YELLOW}⚠️  META_ENCRYPTION_KEY 未设置，已生成临时密钥（重启后失效）${NC}"
fi

if [ -z "$FILE_ENCRYPTION_KEY" ]; then
    export FILE_ENCRYPTION_KEY="$(openssl rand -base64 32)"
    echo -e "${YELLOW}⚠️  FILE_ENCRYPTION_KEY 未设置，已生成临时密钥（重启后失效）${NC}"
fi

# Agent 配置
if [ -z "$PROBE_KEY" ]; then
    export PROBE_KEY="dev-key-$(openssl rand -hex 8)"
fi

# WebSocket 配置
export WS_META_URL="ws://localhost:${ADMIN_PORT}/ws/meta"
export WS_FILE_URL="ws://localhost:${ADMIN_PORT}/ws/file"

# WebSocket 安全配置
export WS_ALLOWED_ORIGINS="ws://localhost:${ADMIN_PORT},ws://127.0.0.1:${ADMIN_PORT}"
export WS_BLOCK_UNLISTED="false"
export WS_ENABLE_ORIGIN_LOGGING="true"
export WS_STRICT_VALIDATION="false"

# CORS 配置
export CORS_ALLOWED_ORIGINS="http://localhost:${WEB_PORT},http://127.0.0.1:${WEB_PORT}"
export CORS_ALLOW_CREDENTIALS="true"

echo -e "${GREEN}✓${NC} 环境变量已设置"
echo ""

# ========================================
# 2. 检查端口占用
# ========================================

echo -e "${BLUE}=== 检查端口占用 ===${NC}"

check_port() {
    local port=$1
    local service=$2

    # 使用ss或lsof检查端口
    if command -v ss &> /dev/null; then
        if ss -tuln | grep -q ":$port "; then
            echo -e "${YELLOW}⚠️  端口 $port 已被占用 ($service)${NC}"
            # 尝试找到并终止进程
            local pid=$(lsof -ti:$port 2>/dev/null || echo "")
            if [ -n "$pid" ]; then
                echo -e "${YELLOW}  发现进程 PID: $pid${NC}"
                read -p "  是否终止占用进程? (y/n) " -n 1 -r
                echo
                if [[ $REPLY =~ ^[Yy]$ ]]; then
                    kill -9 $pid 2>/dev/null || true
                    sleep 1
                    echo -e "${GREEN}  ✓ 端口 $port 已清理${NC}"
                else
                    echo -e "${RED}  ✗ 启动取消${NC}"
                    exit 1
                fi
            fi
        fi
    elif command -v lsof &> /dev/null; then
        if lsof -Pi :$port -sTCP:LISTEN -t >/dev/null 2>&1; then
            echo -e "${YELLOW}⚠️  端口 $port 已被占用 ($service)${NC}"
            local pid=$(lsof -ti:$port 2>/dev/null || echo "")
            if [ -n "$pid" ]; then
                echo -e "${YELLOW}  发现进程 PID: $pid${NC}"
                read -p "  是否终止占用进程? (y/n) " -n 1 -r
                echo
                if [[ $REPLY =~ ^[Yy]$ ]]; then
                    kill -9 $pid 2>/dev/null || true
                    sleep 1
                    echo -e "${GREEN}  ✓ 端口 $port 已清理${NC}"
                else
                    echo -e "${RED}  ✗ 启动取消${NC}"
                    exit 1
                fi
            fi
        fi
    fi
}

# 检查所有端口（根据模式决定检查哪些端口）
if [ "$MODE" = "dev" ] || [ "$MODE" = "all" ] || [ "$SERVICES" = "backend" ] || [ "$SERVICES" = "admin" ]; then
    check_port $ADMIN_PORT "Admin"
fi
if [ "$MODE" = "all" ] || [ "$SERVICES" = "web" ]; then
    check_port $WEB_PORT "Web"
fi
if [ "$MODE" = "dev" ] || [ "$MODE" = "all" ] || [ "$SERVICES" = "backend" ] || [ "$SERVICES" = "agent" ]; then
    check_port $AGENT_PORT "Agent"
fi

echo -e "${GREEN}✓${NC} 所有端口可用"
echo ""

# ========================================
# 3. 初始化数据库表结构
# ========================================

INIT_DB=false
if [ "$MODE" = "all" ] || [ "$MODE" = "dev" ] || [ "$SERVICES" = "backend" ] || [ "$SERVICES" = "admin" ]; then
    INIT_DB=true
fi

if [ "$INIT_DB" = true ] && [ "$SKIP_DATABASES" != "true" ]; then
    echo -e "${BLUE}=== 初始化数据库表结构 ===${NC}"

    SQL_DIR="$PROJECT_DIR/apps/probe-admin/src/main/resources/sql"

    if command -v psql &> /dev/null; then
        DB_INIT_OK=true
        for sql_file in "$SQL_DIR"/*.sql; do
            if [ -f "$sql_file" ]; then
                sql_name=$(basename "$sql_file")
                if PGPASSWORD="$DB_PASSWORD" psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USERNAME" -d "$DB_NAME" -f "$sql_file" > /dev/null 2>&1; then
                    echo -e "${GREEN}  ✓${NC} $sql_name"
                else
                    echo -e "${YELLOW}  ○${NC} $sql_name (已存在或跳过)"
                fi
            fi
        done
        echo -e "${GREEN}✓${NC} 数据库初始化完成"
    else
        echo -e "${YELLOW}⚠️  未找到 psql 命令，跳过数据库初始化${NC}"
        echo -e "${YELLOW}  请手动执行: apps/probe-admin/src/main/resources/sql/*.sql${NC}"
    fi
    echo ""
fi

# ========================================
# 4. 启动 Admin 服务
# ========================================

# 判断是否启动Admin服务
START_ADMIN=false
if [ "$MODE" = "all" ] || [ "$MODE" = "dev" ] || [ "$SERVICES" = "backend" ] || [ "$SERVICES" = "admin" ]; then
    START_ADMIN=true
fi

if [ "$START_ADMIN" = true ]; then

echo -e "${BLUE}=== 启动 Admin 服务 ===${NC}"

cd "$PROJECT_DIR/apps/probe-admin"

# 检查是否需要编译
if [ ! -d "target/classes" ]; then
    echo -e "${YELLOW}首次启动 Admin，正在编译...${NC}"
    mvn clean compile -DskipTests -q
fi

# 启动 Admin
nohup mvn spring-boot:run -Dspring-boot.run.fork=false -DskipTests=true \
    > "$LOG_DIR/admin.log" 2>&1 &

ADMIN_PID=$!
ADMIN_PID_SET=true
echo "Admin 服务已启动 (PID: $ADMIN_PID, 端口: $ADMIN_PORT)"
echo "  日志: $LOG_DIR/admin.log"

# 等待 Admin 启动
if [ "$START_WEB" = false ] && [ "$START_AGENT" = false ]; then
    echo -n "等待 Admin 启动..."
else
    echo -n "等待 Admin 启动..."
fi
ADMIN_READY=false
for i in {1..60}; do
    if curl -sf "http://localhost:${ADMIN_PORT}/actuator/health" > /dev/null 2>&1; then
        ADMIN_READY=true
        echo -e "\n${GREEN}✓ Admin 服务就绪${NC}"
        break
    fi
    echo -n "."
    sleep 1
done

if [ "$ADMIN_READY" = false ]; then
    echo -e "\n${RED}✗ Admin 服务启动超时${NC}"
    echo "  请检查日志: tail -f $LOG_DIR/admin.log"
    exit 1
fi

cd "$PROJECT_DIR"
echo ""
fi  # END of Admin service startup

# ========================================
# 5. 启动 Web 前端
# ========================================

# 判断是否启动Web服务
START_WEB=false
if [ "$MODE" = "all" ] || [ "$SERVICES" = "web" ]; then
    START_WEB=true
fi

if [ "$START_WEB" = true ]; then
    echo -e "${BLUE}=== 启动 Web 前端 ===${NC}"

cd "$PROJECT_DIR/apps/probe-web"

# 检查 node_modules
if [ ! -d "node_modules" ]; then
    echo -e "${YELLOW}首次启动 Web，正在安装依赖...${NC}"
    npm install --silent
fi

# 启动 Web
nohup npm run dev > "$LOG_DIR/web.log" 2>&1 &

WEB_PID=$!
echo "Web 前端已启动 (PID: $WEB_PID, 端口: $WEB_PORT)"
echo "  日志: $LOG_DIR/web.log"

# 等待 Web 启动
echo -n "等待 Web 启动..."
WEB_READY=false
for i in {1..30}; do
    if curl -sf "http://localhost:${WEB_PORT}" > /dev/null 2>&1; then
        WEB_READY=true
        echo -e "\n${GREEN}✓ Web 前端就绪${NC}"
        break
    fi
    echo -n "."
    sleep 1
done

if [ "$WEB_READY" = false ]; then
    echo -e "\n${YELLOW}⚠️  Web 前端启动中，请稍后访问${NC}"
fi

cd "$PROJECT_DIR"
echo ""
fi  # END of Web service startup

# ========================================
# 6. 启动 Agent 服务
# ========================================

# 判断是否启动Agent服务
START_AGENT=false
if [ "$MODE" = "all" ] || [ "$MODE" = "dev" ] || [ "$SERVICES" = "backend" ] || [ "$SERVICES" = "agent" ]; then
    START_AGENT=true
fi

if [ "$START_AGENT" = true ]; then
    echo -e "${BLUE}=== 启动 Agent 服务 ===${NC}"

cd "$PROJECT_DIR/apps/probe-agent"
	# 检查 JAR 是否存在，不存在则编译打包
	AGENT_JAR="target/probe-agent-1.0.0.jar"
	if [ ! -f "$AGENT_JAR" ]; then
	    echo -e "${YELLOW}首次启动 Agent，正在编译打包...${NC}"
	    mvn clean package -DskipTests -q
	fi

		# 启动 Agent（直接使用 JAR，更高效）
		nohup java -jar "$AGENT_JAR" \
		    > "$LOG_DIR/agent.log" 2>&1 &

AGENT_PID=$!
echo "Agent 服务已启动 (PID: $AGENT_PID, 端口: $AGENT_PORT)"
echo "  日志: $LOG_DIR/agent.log"

# 等待 Agent 启动
echo -n "等待 Agent 启动..."
AGENT_READY=false
for i in {1..60}; do
    if curl -sf "http://localhost:${AGENT_PORT}/api/health" > /dev/null 2>&1; then
        AGENT_READY=true
        echo -e "\n${GREEN}✓ Agent 服务就绪${NC}"
        break
    fi
    echo -n "."
    sleep 1
done

if [ "$AGENT_READY" = false ]; then
    echo -e "\n${YELLOW}⚠️  Agent 服务启动中，请检查日志${NC}"
    echo "  日志: tail -f $LOG_DIR/agent.log"
fi

cd "$PROJECT_DIR"
echo ""
fi  # END of Agent service startup

# ========================================
# 7. 显示启动结果
# ========================================

echo "========================================"

echo -e "${GREEN}  服务启动完成！${NC}"
echo "========================================"
echo ""
echo -e "${BLUE}已启动的服务:${NC}"
if [ "$START_ADMIN" = true ]; then
    echo "  ✓ Admin:  http://localhost:${ADMIN_PORT}"
fi
if [ "$START_WEB" = true ]; then
    echo "  ✓ Web:    http://localhost:${WEB_PORT}"
fi
if [ "$START_AGENT" = true ]; then
    echo "  ✓ Agent:  http://localhost:${AGENT_PORT}"
fi
echo ""

if [ "$START_ADMIN" = true ] || [ "$START_WEB" = true ]; then
    echo -e "${BLUE}API 文档:${NC}"
    echo "  • http://localhost:${ADMIN_PORT}/doc.html"
    echo ""
fi

echo -e "${BLUE}日志文件:${NC}"
if [ "$START_ADMIN" = true ]; then
    echo "  • Admin: tail -f $LOG_DIR/admin.log"
fi
if [ "$START_WEB" = true ]; then
    echo "  • Web:   tail -f $LOG_DIR/web.log"
fi
if [ "$START_AGENT" = true ]; then
    echo "  • Agent: tail -f $LOG_DIR/agent.log"
fi
echo ""

if [ "$START_ADMIN" = true ]; then
    echo -e "${BLUE}进程信息:${NC}"
    echo "  • Admin PID: $ADMIN_PID"
fi
if [ "$START_WEB" = true ]; then
    echo "  • Web PID:  $WEB_PID"
fi
if [ "$START_AGENT" = true ]; then
    echo "  • Agent PID: $AGENT_PID"
fi
echo ""

echo -e "${BLUE}停止服务:${NC}"
echo "  • 停止所有: ./infra/scripts/stop-services.sh"
if [ "$START_ADMIN" = true ] && [ -n "$ADMIN_PID" ]; then
    echo "  • 停止 Admin: kill $ADMIN_PID"
fi
if [ "$START_WEB" = true ] && [ -n "$WEB_PID" ]; then
    echo "  • 停止 Web: kill $WEB_PID"
fi
if [ "$START_AGENT" = true ] && [ -n "$AGENT_PID" ]; then
    echo "  • 停止 Agent: kill $AGENT_PID"
fi
echo ""

if [ "$MODE" = "dev" ]; then
    echo -e "${YELLOW}开发模式提示:${NC}"
    echo "  • Web 前端未启动，如需前端请使用: ./infra/scripts/start-all.sh"
    echo "  • 或者在前端目录运行: cd apps/probe-web && npm run dev"
    echo ""
fi

echo -e "${BLUE}监控:${NC}"
echo "  • Admin Actuator: curl http://localhost:${ADMIN_PORT}/actuator/health"
echo "  • Agent Health:   curl http://localhost:${AGENT_PORT}/api/health"
echo ""

# 保存 PID 到文件，方便后续管理
if [ "$START_ADMIN" = true ] && [ -n "$ADMIN_PID" ]; then
    echo "$ADMIN_PID" > "$LOG_DIR/admin.pid"
fi
if [ "$START_WEB" = true ] && [ -n "$WEB_PID" ]; then
    echo "$WEB_PID" > "$LOG_DIR/web.pid"
fi
if [ "$START_AGENT" = true ] && [ -n "$AGENT_PID" ]; then
    echo "$AGENT_PID" > "$LOG_DIR/agent.pid"
fi

if [ "$START_ADMIN" = true ] || [ "$START_WEB" = true ] || [ "$START_AGENT" = true ]; then
    echo -e "${GREEN}PID 文件已保存到 $LOG_DIR/*.pid${NC}"
fi
echo "========================================"
