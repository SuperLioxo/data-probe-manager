#!/bin/bash
# 移除 Vue 组件中重复的 ElMessage.error
# 这些错误在 request.js 的响应拦截器中已经显示了

cd /home/ovo/Workspace/data-probe-manager/apps/probe-web/src/views

# 定义要处理的文件
files=(
  "ProbeList.vue"
  "DataManager.vue"
  "FileProbeList.vue"
  "ProbeGroupManage.vue"
  "AgentManager.vue"
  "ProbeControl.vue"
  "MonitorDashboard.vue"
  "DataStatistics.vue"
  "DatabaseProbeDetail.vue"
  "DatabaseTablesView.vue"
  "DashboardEnhanced.vue"
)

# 常见的重复错误消息模式
patterns=(
  "ElMessage.error('导出失败')"
  "ElMessage.error('获取.*失败')"
  "ElMessage.error('加载.*失败')"
  "ElMessage.error('删除.*失败')"
  "ElMessage.error('创建.*失败')"
  "ElMessage.error('更新.*失败')"
  "ElMessage.error('刷新.*失败')"
  "ElMessage.error('下载.*失败')"
  "ElMessage.error('上传.*失败')"
  "ElMessage.error(\`.*失败:\${.*}\`)"
)

echo "开始移除重复的错误提示..."

for file in "${files[@]}"; do
  if [ -f "$file" ]; then
    echo "处理文件: $file"

    # 备份文件
    cp "$file" "${file}.bak"

    # 移除 catch 块中的 ElMessage.error，替换为 console.error
    sed -i "s/ElMessage.error('导出失败')/console.error('导出失败:', error)/g" "$file"
    sed -i "s/ElMessage.error('获取文件列表失败')/console.error('获取文件列表失败:', error)/g" "$file"
    sed -i "s/ElMessage.error('下载文件失败')/console.error('下载文件失败:', error)/g" "$file"
    sed -i "s/ElMessage.error('删除文件失败')/console.error('删除文件失败:', error)/g" "$file"
    sed -i "s/ElMessage.error('删除.*失败:\${.*}')/console.error('删除失败:', error)/g" "$file"
    sed -i "s/ElMessage.error('创建失败')/console.error('创建失败:', error)/g" "$file"
    sed -i "s/ElMessage.error('更新失败')/console.error('更新失败:', error)/g" "$file"
    sed -i "s/ElMessage.error('刷新失败')/console.error('刷新失败:', error)/g" "$file"

    # 移除更复杂的模式 - 使用 perl 进行多行替换
    perl -i -pe 's/ElMessage\.error\(`获取.*失败: \$\{error\.message \|\| '"'"'未知错误'"'"'\}`)/console.error("获取失败:", error)/g' "$file"
    perl -i -pe 's/ElMessage\.error\(`刷新失败: \$\{error\.message \|\| '"'"'未知错误'"'"'\}`)/console.error("刷新失败:", error)/g' "$file"
    perl -i -pe 's/ElMessage\.error\(`切换.*失败: \$\{error\.message \|\| '"'"'未知错误'"'"'\}`)/console.error("切换失败:", error)/g' "$file"
    perl -i -pe 's/ElMessage\.error\(`\$\{commandText\}探针失败: \$\{result\.message \|\| '"'"'未知错误'"'"'\}`)/console.error(`${commandText}探针失败:`, error)/g' "$file"
    perl -i -pe 's/ElMessage\.error\(`\$\{commandText\}探针失败: \$\{error\.message \|\| '"'"'网络错误'"'"'\}`)/console.error(`${commandText}探针失败:`, error)/g' "$file"

    echo "  ✓ 完成"
  else
    echo "  ✗ 文件不存在: $file"
  fi
done

echo ""
echo "处理完成！"
echo "如果需要恢复，可以使用 .bak 文件"
