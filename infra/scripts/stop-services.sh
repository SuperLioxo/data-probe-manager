#!/bin/bash

# 停止所有服务脚本
# 用于停止Admin、Web、Agent三个服务

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

echo "========================================"
echo "  停止所有服务"
echo "========================================"
echo ""

# 获取脚本目录
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# 停止函数
stop_service() {
    local service_name=$1
    local process_pattern=$2
    local script_name=$3

    echo -e "${BLUE}停止${service_name}...${NC}"

    if pgrep -f "$process_pattern" > /dev/null; then
        # 使用脚本停止（如果存在）或直接杀死进程
        if [ -f "$SCRIPT_DIR/$script_name" ]; then
            "$SCRIPT_DIR/$script_name" --stop 2>/dev/null || true
        fi

        # 强制停止
        pkill -f "$process_pattern"
        sleep 2

        # 再次强制停止
        if pgrep -f "$process_pattern" > /dev/null; then
            pkill -9 -f "$process_pattern"
            sleep 1
        fi

        echo -e "${GREEN}  ✓ ${service_name}已停止${NC}"
    else
        echo -e "${YELLOW}  ○ ${service_name}未在运行${NC}"
    fi
    echo ""
}

# 停止各个服务
stop_service "Web前端" "vite.*probe-web" "start-web.sh"
stop_service "Agent服务" "probe-agent" "start-agent.sh"
stop_service "Admin服务" "probe-admin.*spring-boot:run" "start-admin.sh"

# 清理PID文件
PROJECT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
LOG_DIR="$PROJECT_DIR/logs"

echo -e "${BLUE}清理PID文件...${NC}"
rm -f "$LOG_DIR/web.pid" "$LOG_DIR/agent.pid" "$LOG_DIR/admin.pid"
echo -e "${GREEN}✓ PID文件已清理${NC}"
echo ""

echo -e "${GREEN}========================================"
echo "  所有服务已停止"
echo "========================================${NC}"
echo ""
echo "重新启动服务:"
echo "  $SCRIPT_DIR/start-all.sh"
echo ""
