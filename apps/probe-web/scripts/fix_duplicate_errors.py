#!/usr/bin/env python3
"""
移除 Vue 组件中重复的 ElMessage.error
request.js 已经会自动显示错误消息，所以组件中的 catch 块不应该再显示
"""

import re
import os
import shutil

def fix_duplicate_errors(file_path):
    """修复单个文件中的重复错误提示"""
    with open(file_path, 'r', encoding='utf-8') as f:
        content = f.read()

    original_content = content

    # 模式1: ElMessage.error('xxx失败') -> console.error('xxx失败:', error)
    patterns = [
        (r"ElMessage\.error\('JSON导出失败'\)", "console.error('JSON导出失败:', error)"),
        (r"ElMessage\.error\('导出失败'\)", "console.error('导出失败:', error)"),
        (r"ElMessage\.error\('获取文件列表失败'\)", "console.error('获取文件列表失败:', error)"),
        (r"ElMessage\.error\('下载文件失败'\)", "console.error('下载文件失败:', error)"),
        (r"ElMessage\.error\('删除文件失败'\)", "console.error('删除文件失败:', error)"),
        (r"ElMessage\.error\('触发扫描失败'\)", "console.error('触发扫描失败:', error)"),
    ]

    for pattern, replacement in patterns:
        content = re.sub(pattern, replacement, content)

    # 模式2: ElMessage.error(error.message || 'xxx') -> console.error('xxx:', error)
    content = re.sub(
        r"ElMessage\.error\(error\.message \|\| '触发扫描失败'\)",
        "console.error('触发扫描失败:', error)",
        content
    )

    # 模式3: ElMessage.error(response?.message || 'xxx') -> console.error('xxx:', error)
    content = re.sub(
        r"ElMessage\.error\(response\.message \|\| '删除失败'\)",
        "console.error('删除失败:', error)",
        content
    )

    # 模式4: ElMessage.error(`xxx: ${error.message || 'xxx'}`) -> console.error('xxx:', error)
    content = re.sub(
        r"ElMessage\.error\(`刷新失败: \$\{error\.message \|\| '未知错误'\}`\)",
        "console.error('刷新失败:', error)",
        content
    )
    content = re.sub(
        r"ElMessage\.error\(`获取数据库详情失败: \$\{error\.message \|\| '未知错误'\}`\)",
        "console.error('获取数据库详情失败:', error)",
        content
    )
    content = re.sub(
        r"ElMessage\.error\(`获取表数据失败: \$\{error\.message \|\| '未知错误'\}`\)",
        "console.error('获取表数据失败:', error)",
        content
    )
    content = re.sub(
        r"ElMessage\.error\(`删除失败: \$\{error\.message \|\| '未知错误'\}`\)",
        "console.error('删除失败:', error)",
        content
    )

    # 模式5: ElMessage.error('xxx：' + (error.message || 'xxx'))
    content = re.sub(
        r"ElMessage\.error\('切换实例失败：' \+ \(error\.message \|\| '未知错误'\)\)",
        "console.error('切换实例失败:', error)",
        content
    )

    # 模式6: ElMessage.error(`${commandText}探针失败: ${result.message || 'xxx'}`)
    content = re.sub(
        r"ElMessage\.error\(`\$\{commandText\}探针失败: \$\{result\.message \|\| '未知错误'\}`\)",
        "console.error(`${commandText}探针失败:`, error)",
        content
    )
    content = re.sub(
        r"ElMessage\.error\(`\$\{commandText\}探针失败: \$\{error\.message \|\| '网络错误'\}`\)",
        "console.error(`${commandText}探针失败:`, error)",
        content
    )

    # 模式7: ElMessage.error(result?.message || 'xxx')
    content = re.sub(
        r"ElMessage\.error\(result\?\.message \|\| '创建失败'\)",
        "console.error('创建失败:', result?.message)",
        content
    )

    # 模式8: ElMessage.error(isEdit ? '更新失败' : '创建失败')
    content = re.sub(
        r"ElMessage\.error\(isEdit\.value \? '更新失败' : '创建失败'\)",
        "console.error(isEdit.value ? '更新失败:' : '创建失败:', error)",
        content
    )

    # 模式9: ElMessage.error(response.message || 'xxx')
    content = re.sub(
        r"ElMessage\.error\(response\.message \|\| '切换实例失败'\)",
        "console.error('切换实例失败:', error)",
        content
    )
    content = re.sub(
        r"ElMessage\.error\(response\.message \|\| '获取表数据失败'\)",
        "console.error('获取表数据失败:', error)",
        content
    )

    # 只在内容改变时写入文件
    if content != original_content:
        # 备份原文件
        backup_path = file_path + '.bak2'
        shutil.copy2(file_path, backup_path)

        with open(file_path, 'w', encoding='utf-8') as f:
            f.write(content)

        return True
    return False

def main():
    """主函数"""
    views_dir = '/home/ovo/Workspace/data-probe-manager/apps/probe-web/src/views'

    # 需要处理的文件列表
    files = [
        'ProbeList.vue',
        'DataManager.vue',
        'FileProbeList.vue',
        'ProbeGroupManage.vue',
        'AgentManager.vue',
        'ProbeControl.vue',
        'MonitorDashboard.vue',
        'DataStatistics.vue',
        'DatabaseProbeDetail.vue',
        'DatabaseTablesView.vue',
        'DashboardEnhanced.vue',
    ]

    print("开始处理文件...")
    modified_count = 0

    for filename in files:
        file_path = os.path.join(views_dir, filename)

        if os.path.exists(file_path):
            if fix_duplicate_errors(file_path):
                print(f"✓ {filename} - 已修改")
                modified_count += 1
            else:
                print(f"- {filename} - 无需修改")
        else:
            print(f"✗ {filename} - 文件不存在")

    print(f"\n处理完成！共修改 {modified_count} 个文件")
    print("备份文件已保存为 .bak2")

if __name__ == '__main__':
    main()
