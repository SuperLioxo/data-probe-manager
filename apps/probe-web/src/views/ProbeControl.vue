<template>
  <div class="probe-control">
    <el-card class="header-card">
      <div class="header">
        <h2>探针远程控制</h2>
        <el-button @click="loadProbes" :loading="loading">
          <el-icon><Refresh /></el-icon>
          刷新
        </el-button>
      </div>
      <p class="description">远程控制探针的启动、停止和配置</p>
    </el-card>

    <el-card class="table-card">
      <el-table :data="tableData" v-loading="loading" stripe>
        <el-table-column prop="name" label="探针名称" width="180" />
        <el-table-column prop="probeKey" label="探针Key" width="200" show-overflow-tooltip />
        <el-table-column prop="type" label="类型" width="120">
          <template #default="{ row }">
            <el-tag :type="getProbeTypeColor(row.type)">
              {{ getProbeTypeName(row.type) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="连接状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getConnectionStatusColor(row.status)">
              {{ getConnectionStatusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="runningStatus" label="运行状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getRunningStatusColor(row.runningStatus)">
              {{ getRunningStatusText(row.runningStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="hostIp" label="IP地址" width="140" />
        <el-table-column prop="port" label="端口" width="100" />
        <el-table-column prop="lastHeartbeat" label="最后心跳" width="180">
          <template #default="{ row }">
            {{ formatDate(row.lastHeartbeat) }}
          </template>
        </el-table-column>
        <el-table-column prop="version" label="版本" width="100" />
        <el-table-column label="操作" width="300" fixed="right">
          <template #default="{ row }">
            <el-button
              size="small"
              type="success"
              @click="handleStart(row)"
              :loading="row.startLoading"
              :disabled="row.status === 'offline' || row.runningStatus === 'running'"
            >
              <el-icon><VideoPlay /></el-icon>
              启动
            </el-button>
            <el-button
              size="small"
              type="warning"
              @click="handleStop(row)"
              :loading="row.stopLoading"
              :disabled="row.status === 'offline' || row.runningStatus === 'stopped'"
            >
              <el-icon><VideoPause /></el-icon>
              停止
            </el-button>
            <el-button
              size="small"
              @click="handleRestart(row)"
              :loading="row.restartLoading"
              :disabled="row.status === 'offline'"
            >
              <el-icon><RefreshRight /></el-icon>
              重启
            </el-button>
            <el-dropdown @command="(cmd) => handleMoreCommand(cmd, row)">
              <el-button size="small">
                更多<el-icon><ArrowDown /></el-icon>
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="config">
                    <el-icon><Setting /></el-icon>
                    配置
                  </el-dropdown-item>
                  <el-dropdown-item command="status">
                    <el-icon><InfoFilled /></el-icon>
                    状态详情
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 配置对话框 -->
    <el-dialog
      v-model="configDialogVisible"
      title="探针配置"
      width="600px"
      @close="handleConfigDialogClose"
    >
      <el-form :model="configForm" ref="configFormRef" label-width="140px">
        <el-form-item label="探针Key">
          <el-input v-model="configForm.probeKey" disabled />
        </el-form-item>
        <el-form-item label="采集间隔（秒）">
          <el-input-number
            v-model="configForm.collectInterval"
            :min="1"
            :max="3600"
            :step="1"
          />
          <span class="unit">秒</span>
        </el-form-item>
        <el-form-item label="系统监控模块">
          <el-switch v-model="configForm.modules.system" />
        </el-form-item>
        <el-form-item label="数据库模块">
          <el-switch v-model="configForm.modules.database" />
        </el-form-item>
        <el-form-item label="文件模块">
          <el-switch v-model="configForm.modules.file" />
        </el-form-item>
        <el-divider>自定义配置</el-divider>
        <el-form-item label="自定义配置">
          <el-input
            v-model="customConfigJson"
            type="textarea"
            :rows="6"
            placeholder='{"customKey": "customValue"}'
          />
          <div class="form-tip">JSON格式，可选。用于添加自定义配置项</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="configDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleUpdateConfig" :loading="configSubmitLoading">
          更新配置
        </el-button>
      </template>
    </el-dialog>

    <!-- 操作日志对话框 -->
    <el-dialog v-model="logDialogVisible" title="操作日志" width="700px">
      <el-timeline>
        <el-timeline-item
          v-for="(log, index) in operationLogs"
          :key="index"
          :timestamp="log.time"
          :type="log.type"
        >
          <div class="log-content">
            <strong>{{ log.action }}</strong>
            <span class="log-probe">{{ log.probeName }}</span>
            <p>{{ log.message }}</p>
          </div>
        </el-timeline-item>
      </el-timeline>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Refresh,
  VideoPlay,
  VideoPause,
  RefreshRight,
  ArrowDown,
  Setting,
  InfoFilled
} from '@element-plus/icons-vue'
import { getList } from '@/api/probe'
import {
  startProbe,
  stopProbe,
  restartProbe,
  updateProbeConfig
} from '@/api/probeControl'

// 响应式数据
const loading = ref(false)
const tableData = ref([])
const configDialogVisible = ref(false)
const logDialogVisible = ref(false)
const configSubmitLoading = ref(false)
const configFormRef = ref(null)
const currentProbe = ref(null)
const customConfigJson = ref('{}')

// 操作日志
const operationLogs = ref([])

// 配置表单
const configForm = reactive({
  probeKey: '',
  collectInterval: 10,
  modules: {
    system: true,
    database: false,
    file: false
  },
  customConfig: {}
})

// 刷新定时器
let refreshTimer = null

// 获取探针类型名称
const getProbeTypeName = (type) => {
  const typeMap = {
    'SYSTEM': '系统监控',
    'DATABASE': '数据库',
    'FILE': '文件'
  }
  return typeMap[type] || type
}

// 获取探针类型颜色
const getProbeTypeColor = (type) => {
  const colorMap = {
    'SYSTEM': 'success',
    'DATABASE': 'warning',
    'FILE': 'info'
  }
  return colorMap[type] || ''
}

// 获取连接状态文本
const getConnectionStatusText = (status) => {
  if (!status) return '未知'
  const statusMap = {
    'online': '已连接',
    'offline': '未连接',
    'error': '错误',
    'disabled': '已禁用'
  }
  return statusMap[status.toLowerCase()] || status
}

// 获取连接状态颜色
const getConnectionStatusColor = (status) => {
  if (!status) return 'info'
  const colorMap = {
    'online': 'success',
    'offline': 'danger',
    'error': 'warning',
    'disabled': 'info'
  }
  return colorMap[status.toLowerCase()] || 'info'
}

// 获取运行状态文本
const getRunningStatusText = (runningStatus) => {
  if (!runningStatus) return '未知'
  const statusMap = {
    'running': '运行中',
    'stopped': '已停止'
  }
  return statusMap[runningStatus.toLowerCase()] || runningStatus
}

// 获取运行状态颜色
const getRunningStatusColor = (runningStatus) => {
  if (!runningStatus) return 'info'
  const colorMap = {
    'running': 'success',
    'stopped': 'info'
  }
  return colorMap[runningStatus.toLowerCase()] || 'info'
}

// 保留旧方法以兼容性
const getStatusText = (status) => {
  return getConnectionStatusText(status)
}

const getStatusColor = (status) => {
  return getConnectionStatusColor(status)
}

// 格式化日期
const formatDate = (dateStr) => {
  if (!dateStr) return '-'
  const date = new Date(dateStr)
  return date.toLocaleString('zh-CN')
}

// 加载探针列表
const loadProbes = async () => {
  loading.value = true
  try {
    const response = await getList({ pageNum: 1, pageSize: 100 })
    if (response.code === 200) {
      tableData.value = (response.data.records || []).map(probe => ({
        ...probe,
        startLoading: false,
        stopLoading: false,
        restartLoading: false
      }))
    }
  } catch (error) {
    ElMessage.error('加载探针列表失败：' + (error.message || '未知错误'))
  } finally {
    loading.value = false
  }
}

// 添加操作日志
const addLog = (action, probe, message, type = 'primary') => {
  operationLogs.value.unshift({
    time: new Date().toLocaleString('zh-CN'),
    action,
    probeName: probe.name,
    message,
    type
  })
  // 保留最近20条
  if (operationLogs.value.length > 20) {
    operationLogs.value = operationLogs.value.slice(0, 20)
  }
}

// 启动探针
const handleStart = async (row) => {
  row.startLoading = true
  try {
    await startProbe(row.probeKey)
    ElMessage.success(`探针"${row.name}"启动命令已发送`)
    addLog('启动', row, '启动命令已发送到探针', 'success')
    // 延迟刷新以等待状态更新
    setTimeout(() => loadProbes(), 2000)
  } catch (error) {
    ElMessage.error('启动失败：' + (error.message || '未知错误'))
    addLog('启动失败', row, error.message || '未知错误', 'danger')
  } finally {
    row.startLoading = false
  }
}

// 停止探针
const handleStop = async (row) => {
  ElMessageBox.confirm(
    `确定要停止探针"${row.name}"吗？`,
    '确认停止',
    {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    }
  ).then(async () => {
    row.stopLoading = true
    try {
      await stopProbe(row.probeKey)
      ElMessage.success(`探针"${row.name}"停止命令已发送`)
      addLog('停止', row, '停止命令已发送到探针', 'warning')
      setTimeout(() => loadProbes(), 2000)
    } catch (error) {
      ElMessage.error('停止失败：' + (error.message || '未知错误'))
      addLog('停止失败', row, error.message || '未知错误', 'danger')
    } finally {
      row.stopLoading = false
    }
  })
}

// 重启探针
const handleRestart = async (row) => {
  ElMessageBox.confirm(
    `确定要重启探针"${row.name}"吗？`,
    '确认重启',
    {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    }
  ).then(async () => {
    row.restartLoading = true
    try {
      await restartProbe(row.probeKey)
      ElMessage.success(`探针"${row.name}"重启命令已发送`)
      addLog('重启', row, '重启命令已发送到探针', 'warning')
      setTimeout(() => loadProbes(), 2000)
    } catch (error) {
      ElMessage.error('重启失败：' + (error.message || '未知错误'))
      addLog('重启失败', row, error.message || '未知错误', 'danger')
    } finally {
      row.restartLoading = false
    }
  })
}

// 更多命令
const handleMoreCommand = (command, row) => {
  currentProbe.value = row
  if (command === 'config') {
    // 打开配置对话框
    configForm.probeKey = row.probeKey
    configForm.collectInterval = row.config?.collectInterval || 10
    configForm.modules = {
      system: row.config?.modules?.system?.enabled ?? true,
      database: row.config?.modules?.database?.enabled ?? false,
      file: row.config?.modules?.file?.enabled ?? false
    }
    customConfigJson.value = JSON.stringify(row.config?.customConfig || {}, null, 2)
    configDialogVisible.value = true
  } else if (command === 'status') {
    // 打开日志对话框
    logDialogVisible.value = true
  }
}

// 更新配置
const handleUpdateConfig = async () => {
  // 解析自定义配置JSON
  try {
    if (customConfigJson.value.trim()) {
      configForm.customConfig = JSON.parse(customConfigJson.value)
    } else {
      configForm.customConfig = {}
    }
  } catch (error) {
    ElMessage.error('自定义配置JSON格式错误：' + error.message)
    return
  }

  configSubmitLoading.value = true
  try {
    const configData = {
      collectInterval: configForm.collectInterval,
      modules: {
        system: { enabled: configForm.modules.system },
        database: { enabled: configForm.modules.database },
        file: { enabled: configForm.modules.file }
      },
      ...configForm.customConfig
    }

    await updateProbeConfig(configForm.probeKey, configData)
    ElMessage.success('配置更新成功')
    addLog('配置更新', currentProbe.value, '探针配置已更新', 'success')
    configDialogVisible.value = false
    setTimeout(() => loadProbes(), 2000)
  } catch (error) {
    ElMessage.error('配置更新失败：' + (error.message || '未知错误'))
  } finally {
    configSubmitLoading.value = false
  }
}

// 配置对话框关闭
const handleConfigDialogClose = () => {
  configFormRef.value?.resetFields()
}

// 页面加载
onMounted(() => {
  loadProbes()
  // 定时刷新探针状态（每30秒）
  refreshTimer = setInterval(() => {
    loadProbes()
  }, 30000)
})

// 页面卸载
onUnmounted(() => {
  if (refreshTimer) {
    clearInterval(refreshTimer)
  }
})
</script>

<style scoped>
.probe-control {
  padding: 20px;
}

.header-card {
  margin-bottom: 20px;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header h2 {
  margin: 0 0 10px 0;
  font-size: 24px;
  color: #303133;
}

.description {
  margin: 0;
  color: #909399;
  font-size: 14px;
}

.table-card {
  min-height: 400px;
}

.unit {
  margin-left: 8px;
  color: #909399;
  font-size: 14px;
}

.form-tip {
  color: #909399;
  font-size: 12px;
  margin-top: 4px;
}

.log-content {
  line-height: 1.6;
}

.log-content strong {
  color: #303133;
}

.log-probe {
  margin: 0 8px;
  color: #409eff;
  font-weight: 500;
}

.log-content p {
  margin: 4px 0 0 0;
  color: #606266;
  font-size: 14px;
}
</style>
