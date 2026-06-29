#!/usr/bin/env bash
# Nacos 配置批量导入脚本 (Linux / macOS)
# 用途：通过 Nacos API 批量导入 docker/nacos/config/ 目录下的所有 .yml 配置文件
# 使用方法：在项目根目录执行 bash docker/nacos/import-configs.sh
#
# 为什么用 API 而不是 ZIP？
# Nacos 的 ZIP 导入功能对目录结构有严格要求（必须有 DEFAULT_GROUP 文件夹），
# 容易报"未读取到合法数据"错误。用 API 直接导入更稳定可靠。

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
CONFIG_DIR="$SCRIPT_DIR/config"
NACOS_URL="http://localhost:8976/nacos/v1/cs/configs"
NACOS_HEALTH_URL="http://localhost:8976/nacos/v1/console/health/liveness"
NAMESPACE="dev"
GROUP="DEFAULT_GROUP"
TYPE="yaml"

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo "=========================================="
echo "  Nacos 配置批量导入工具"
echo "=========================================="
echo "配置目录: $CONFIG_DIR"
echo "Nacos地址: $NACOS_URL"
echo "命名空间: $NAMESPACE"
echo ""

# 检查 Nacos 是否在线
if ! curl -s --connect-timeout 5 --max-time 5 "$NACOS_HEALTH_URL" > /dev/null 2>&1; then
    echo -e "${RED}[ERROR]${NC} Nacos 服务无法访问，请先启动：docker-compose up -d"
    exit 1
fi
echo -e "${GREEN}[OK]${NC} Nacos 服务在线"

# 获取所有 .yml 配置文件
yml_files=()
while IFS= read -r -d '' file; do
    yml_files+=("$file")
done < <(find "$CONFIG_DIR" -maxdepth 1 -name "*.yml" -print0 | sort -z)

if [ ${#yml_files[@]} -eq 0 ]; then
    echo "未找到 .yml 配置文件于 $CONFIG_DIR"
    exit 1
fi

echo "找到 ${#yml_files[@]} 个配置文件"
echo ""

success=0
fail=0

for file in "${yml_files[@]}"; do
    data_id=$(basename "$file")
    content=$(cat "$file")

    response=$(curl -s -X POST "$NACOS_URL" \
        -H "Content-Type: application/x-www-form-urlencoded; charset=utf-8" \
        --data-urlencode "dataId=$data_id" \
        --data-urlencode "group=$GROUP" \
        --data-urlencode "tenant=$NAMESPACE" \
        --data-urlencode "type=$TYPE" \
        --data-urlencode "content=$content")

    if [ "$response" = "true" ]; then
        echo -e "  ${GREEN}[OK]${NC}   $data_id"
        ((success++)) || true
    else
        echo -e "  ${RED}[FAIL]${NC} $data_id - 返回: $response"
        ((fail++)) || true
    fi
done

echo ""
echo "=========================================="
echo -e "  导入完成: ${GREEN}$success${NC} 成功, ${RED}$fail${NC} 失败"
echo "=========================================="

if [ "$fail" -eq 0 ]; then
    echo ""
    echo "验证：打开 http://localhost:8976/nacos"
    echo "切换到 dev 命名空间，应该看到 $success 个配置文件"
fi
