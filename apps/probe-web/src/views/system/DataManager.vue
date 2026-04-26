<template>
  <div class="data-manager">
    <el-card class="manager-card">
      <template #header>
        <div class="card-header">
          <el-icon><DataBoard /></el-icon>
          <span>虚拟数据管理器</span>
        </div>
      </template>

      <!-- 数据统计 -->
      <el-row :gutter="16" class="stats-row">
        <el-col :span="6">
          <div class="stat-item stat-primary">
            <div class="stat-value">{{ stats.totalProbes }}</div>
            <div class="stat-label">探针总数</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-item stat-success">
            <div class="stat-value">{{ stats.onlineProbes }}</div>
            <div class="stat-label">在线</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-item stat-warning">
            <div class="stat-value">{{ stats.offlineProbes }}</div>
            <div class="stat-label">离线</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-item stat-danger">
            <div class="stat-value">{{ stats.errorProbes }}</div>
            <div class="stat-label">异常</div>
          </div>
        </el-col>
      </el-row>

      <!-- 操作按钮 -->
      <div class="actions-bar">
        <el-button type="primary" @click="showCreateDialog = true">
          <el-icon><Plus /></el-icon>
          添加探针
        </el-button>
        <el-button type="success" @click="batchUpdateMetrics">
          <el-icon><Refresh /></el-icon>
          更新监控数据
        </el-button>
        <el-button :type="autoRefreshEnabled ? 'success' : 'info'" @click="toggleAutoRefresh">
          <el-icon><Clock /></el-icon>
          {{ autoRefreshEnabled ? '自动刷新中' : '启用自动刷新' }}
        </el-button>
        <el-button type="warning" @click="randomChangeStatus">
          <el-icon><Shuffle /></el-icon>
          随机变更状态
        </el-button>
        <el-button type="danger" @click="clearAllData">
          <el-icon><Delete /></el-icon>
          清空所有数据
        </el-button>
        <el-button @click="exportData">
          <el-icon><Download /></el-icon>
          导出数据
        </el-button>
      </div>

      <!-- 探针列表 -->
      <el-table
        :data="probes"
        border
        stripe
        max-height="500"
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="55" />
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="name" label="探针名称" width="150" />
        <el-table-column prop="probeKey" label="探针标识" width="150" />
        <el-table-column prop="type" label="类型" width="120">
          <template #default="{ row }">
            <el-tag :type="getTypeColor(row.type)" size="small">
              {{ getTypeLabel(row.type) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)" effect="light" size="small">
              {{ getStatusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="cpuUsage" label="CPU" width="100">
          <template #default="{ row }">
            <el-progress
              :percentage="row.cpuUsage || 0"
              :status="getProgressStatus(row.cpuUsage || 0)"
              :show-text="false"
              :stroke-width="8"
            />
            <span class="metric-text">{{ (row.cpuUsage || 0).toFixed(1) }}%</span>
          </template>
        </el-table-column>
        <el-table-column prop="memoryUsage" label="内存" width="120">
          <template #default="{ row }">
            <span>{{ formatMemory(row.memoryUsageMB || row.memoryUsage) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="networkIn" label="网络下载" width="120">
          <template #default="{ row }">
            <span>{{ formatRate(row.networkIn || 0) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="networkOut" label="网络上传" width="120">
          <template #default="{ row }">
            <span>{{ formatRate(row.networkOut || 0) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="cpuLoad" label="系统负载" width="120">
          <template #default="{ row }">
            <span v-if="row.cpuLoad1min">{{ row.cpuLoad1min.toFixed(2) }}</span>
            <span v-else>-</span>
            <span v-if="row.cpuLoad1min" style="font-size: 12px; color: #999;"> (1min)</span>
          </template>
        </el-table-column>
        <el-table-column prop="hostIp" label="IP地址" width="140" />
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button-group>
              <el-button size="small" type="primary" link @click="handleEdit(row)">
                编辑
              </el-button>
              <el-button size="small" type="success" link @click="handleUpdateMetrics(row)">
                更新数据
              </el-button>
              <el-button size="small" type="danger" link @click="handleDelete(row)">
                删除
              </el-button>
            </el-button-group>
          </template>
        </el-table-column>
      </el-table>

      <!-- 批量操作 -->
      <div class="batch-actions" v-if="selectedProbes.length > 0">
        <el-alert
          :title="`已选择 ${selectedProbes.length} 个探针`"
          type="info"
          :closable="false"
        >
          <template #default>
            <el-button-group>
              <el-button size="small" type="success" @click="batchSetStatus('online')">
                批量上线
              </el-button>
              <el-button size="small" type="warning" @click="batchSetStatus('offline')">
                批量离线
              </el-button>
              <el-button size="small" type="danger" @click="batchDelete">
                批量删除
              </el-button>
            </el-button-group>
          </template>
        </el-alert>
      </div>
    </el-card>

    <!-- 创建/编辑对话框 -->
    <el-dialog
      v-model="showCreateDialog"
      :title="isEdit ? '编辑探针' : '添加探针'"
      width="600px"
      :close-on-click-modal="false"
    >
      <el-form :model="form" :rules="rules" ref="formRef" label-width="110px">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="探针名称" prop="name">
              <el-input v-model="form.name" placeholder="请输入探针名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="探针类型" prop="type">
              <el-select
                v-model="form.type"
                placeholder="请选择探针类型"
                style="width: 100%"
                :disabled="isEdit"
              >
                <el-option label="系统监控" value="SYSTEM" />
                <el-option label="文件监控" value="FILE" />
                <el-option label="数据库监控" value="DATABASE" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="主机IP" prop="hostIp">
              <el-input v-model="form.hostIp" placeholder="请输入IP地址" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="端口" prop="port">
              <el-input-number v-model="form.port" :min="1" :max="65535" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="采集间隔(秒)">
              <el-input-number v-model="form.collectInterval" :min="10" :max="3600" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="初始状态">
              <el-select v-model="form.status" style="width: 100%">
                <el-option label="在线" value="online" />
                <el-option label="离线" value="offline" />
                <el-option label="异常" value="error" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="showCreateDialog = false">取消</el-button>
          <el-button type="primary" @click="handleSubmit" :loading="submitting">
            确定
          </el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { probeApi } from '@/api/probe'
import { getProbeMetricsSummary } from '@/api/metrics'
import { formatRate } from '@/utils'

const probes = ref([])
const selectedProbes = ref([])
const showCreateDialog = ref(false)
const isEdit = ref(false)
const submitting = ref(false)
const formRef = ref()

const form = reactive({
  id: null,
  name: '',
  probeKey: '',
  type: 'SYSTEM',
  hostIp: '',
  port: 58081,
  collectInterval: 60,
  status: 'offline'
})

const rules = {
  type: [{ required: true, message: '请选择探针类型', trigger: 'change' }],
  name: [{ required: true, message: '请输入探针名称', trigger: 'blur' }],
  hostIp: [{ required: true, message: '请输入主机IP', trigger: 'blur' }]
}

// 统计数据 - 修复：统一使用小写状态比较
const stats = computed(() => ({
  totalProbes: probes.value.length,
  onlineProbes: probes.value.filter(p => p.status?.toLowerCase() === 'online').length,
  offlineProbes: probes.value.filter(p => p.status?.toLowerCase() === 'offline').length,
  errorProbes: probes.value.filter(p => p.status?.toLowerCase() === 'error').length
}))

// 辅助方法
const getTypeColor = (type) => {
  const colorMap = {
    SYSTEM: 'primary',
    DATABASE: 'success',
    FILE: 'warning',
    CUSTOM: 'info'
  }
  return colorMap[type] || 'info'
}

const getTypeLabel = (type) => {
  const labelMap = {
    SYSTEM: '系统监控',
    DATABASE: '数据库监控',
    FILE: '文件监控',
    CUSTOM: '自定义'
  }
  return labelMap[type] || type
}

const getStatusType = (status) => {
  const typeMap = {
    online: 'success',
    offline: 'info',
    error: 'danger'
  }
  return typeMap[status] || 'info'
}

const getStatusText = (status) => {
  const textMap = {
    online: '在线',
    offline: '离线',
    error: '异常'
  }
  return textMap[status] || status
}

const getProgressStatus = (percentage) => {
  if (percentage >= 90) return 'exception'
  if (percentage >= 70) return 'warning'
  return 'success'
}

// 格式化内存显示 - 修复：将字节转换为合适的单位
const formatMemory = (bytes) => {
  if (!bytes || bytes === 0) return '0 MB'
  if (typeof bytes === 'number' && bytes < 1024) {
    // 如果已经是MB（小于1024），直接显示
    return `${bytes} MB`
  }
  const mb = Math.round(bytes / 1024 / 1024)
  if (mb < 1024) {
    return `${mb} MB`
  }
  const gb = (mb / 1024).toFixed(2)
  return `${gb} GB`
}

// 自动刷新定时器
let refreshTimer = null
const autoRefreshEnabled = ref(true)
const refreshInterval = 30000 // 30秒自动刷新

// 启动自动刷新
const startAutoRefresh = () => {
  if (refreshTimer) return
  refreshTimer = setInterval(() => {
    if (autoRefreshEnabled.value) {
      loadData()
    }
  }, refreshInterval)
}

// 停止自动刷新
const stopAutoRefresh = () => {
  if (refreshTimer) {
    clearInterval(refreshTimer)
    refreshTimer = null
  }
}

// 切换自动刷新
const toggleAutoRefresh = () => {
  autoRefreshEnabled.value = !autoRefreshEnabled.value
  ElMessage.info(autoRefreshEnabled.value ? '已启用自动刷新' : '已禁用自动刷新')
}

// 加载数据
const loadData = async () => {
  try {
    const { code, data } = await probeApi.getList({ pageNum: 1, pageSize: 100 })
    if (code === 200) {
      probes.value = data.records || []

      // 自动加载在线探针的metrics数据 - 修复：统一状态大小写处理
      const onlineProbes = probes.value.filter(p => p.status?.toLowerCase() === 'online')
      for (const probe of onlineProbes) {
        try {
          const response = await getProbeMetricsSummary(probe.id)
          if (response.code === 200 && response.data) {
            Object.assign(probe, {
              cpuUsage: response.data.cpuUsage || 0,
              memoryUsage: response.data.memoryUsed || 0,
              memoryUsageMB: response.data.memoryUsed ? Math.round(response.data.memoryUsed / 1024 / 1024) : 0,
              diskUsage: response.data.diskUsage || 0,
              networkIn: response.data.networkRxRate || 0,
              networkOut: response.data.networkTxRate || 0,
              cpuLoad1min: response.data.cpuLoad1min,
              cpuLoad5min: response.data.cpuLoad5min,
              cpuLoad15min: response.data.cpuLoad15min
            })
          }
        } catch (error) {
          console.warn(`加载探针 ${probe.name} 的metrics失败:`, error)
        }
      }
    }
  } catch (error) {
    console.error('加载探针数据失败:', error)
    ElMessage.error('加载探针数据失败')
  }
}

// 处理选择变化
const handleSelectionChange = (selection) => {
  selectedProbes.value = selection
}

// 编辑探针
const handleEdit = (row) => {
  isEdit.value = true
  Object.assign(form, row)
  showCreateDialog.value = true
}

// 更新单个探针的监控数据 - 修复：添加内存转换
const handleUpdateMetrics = async (row) => {
  try {
    const response = await getProbeMetricsSummary(row.id)
    if (response.code === 200 && response.data) {
      Object.assign(row, {
        cpuUsage: response.data.cpuUsage || 0,
        memoryUsage: response.data.memoryUsed || 0,
        memoryUsageMB: response.data.memoryUsed ? Math.round(response.data.memoryUsed / 1024 / 1024) : 0,
        diskUsage: response.data.diskUsage || 0,
        networkIn: response.data.networkRxRate || 0,
        networkOut: response.data.networkTxRate || 0,
        cpuLoad1min: response.data.cpuLoad1min,
        cpuLoad5min: response.data.cpuLoad5min,
        cpuLoad15min: response.data.cpuLoad15min
      })
      ElMessage.success(`已更新 ${row.name} 的监控数据`)
    } else {
      ElMessage.warning(`获取 ${row.name} 的监控数据失败`)
    }
  } catch (error) {
    console.error('更新监控数据失败:', error)
    ElMessage.error('更新监控数据失败')
  }
}

// 批量更新监控数据 - 修复：统一状态大小写处理
const batchUpdateMetrics = async () => {
  try {
    const onlineProbes = probes.value.filter(probe => probe.status?.toLowerCase() === 'online')

    for (const probe of onlineProbes) {
      const response = await getProbeMetricsSummary(probe.id)
      if (response.code === 200 && response.data) {
        Object.assign(probe, {
          cpuUsage: response.data.cpuUsage || 0,
          memoryUsage: response.data.memoryUsed || 0,
          memoryUsageMB: response.data.memoryUsed ? Math.round(response.data.memoryUsed / 1024 / 1024) : 0,
          diskUsage: response.data.diskUsage || 0,
          networkIn: response.data.networkRxRate || 0,
          networkOut: response.data.networkTxRate || 0,
          cpuLoad1min: response.data.cpuLoad1min,
          cpuLoad5min: response.data.cpuLoad5min,
          cpuLoad15min: response.data.cpuLoad15min
        })
      }
    }

    ElMessage.success('已更新所有在线探针的监控数据')
  } catch (error) {
    console.error('批量更新监控数据失败:', error)
    ElMessage.error('批量更新监控数据失败')
  }
}

// 随机变更状态 - 已移除，不应随机改变探针状态
// 此函数使用假数据，已禁用
const randomChangeStatus = () => {
  ElMessage.warning('随机变更状态功能已禁用，请使用真实操作')
}

// 批量设置状态 - 修复：统一使用小写状态
const batchSetStatus = async (status) => {
  try {
    const lowerStatus = status.toLowerCase()
    for (const probe of selectedProbes.value) {
      // 调用 API 更新探针状态
      await probeApi.update(probe.id, { status: lowerStatus })
      probe.status = lowerStatus

      // 如果设置为在线，获取监控数据
      if (lowerStatus === 'online') {
        const response = await getProbeMetricsSummary(probe.id)
        if (response.code === 200 && response.data) {
          Object.assign(probe, {
            cpuUsage: response.data.cpuUsage || 0,
            memoryUsage: response.data.memoryUsed || 0,
            memoryUsageMB: response.data.memoryUsed ? Math.round(response.data.memoryUsed / 1024 / 1024) : 0,
            diskUsage: response.data.diskUsage || 0,
            networkIn: response.data.networkRxRate || 0,
            networkOut: response.data.networkTxRate || 0
          })
        }
      }
    }

    ElMessage.success(`已将 ${selectedProbes.value.length} 个探针设置为${getStatusText(upperStatus)}`)
    selectedProbes.value = []
  } catch (error) {
    console.error('批量设置状态失败:', error)
    ElMessage.error('批量设置状态失败')
  }
}

// 批量删除
const batchDelete = async () => {
  try {
    await ElMessageBox.confirm(
      `确认删除选中的 ${selectedProbes.value.length} 个探针吗？`,
      '批量删除确认',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )

    selectedProbes.value.forEach(probe => {
      const index = probes.value.findIndex(p => p.id === probe.id)
      if (index > -1) {
        probes.value.splice(index, 1)
      }
    })

    ElMessage.success('删除成功')
    selectedProbes.value = []
  } catch (error) {
    // 用户取消
  }
}

// 删除单个探针
const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm(
      `确认删除探针【${row.name}】吗？`,
      '删除确认',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )

    const index = probes.value.findIndex(p => p.id === row.id)
    if (index > -1) {
      probes.value.splice(index, 1)
      ElMessage.success('删除成功')
    }
  } catch (error) {
    // 用户取消
  }
}

// 清空所有数据
const clearAllData = async () => {
  try {
    await ElMessageBox.confirm(
      '确认清空所有探针数据吗？此操作不可恢复！',
      '清空数据',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )

    probes.value = []
    ElMessage.success('已清空所有数据')
  } catch (error) {
    // 用户取消
  }
}

// 导出数据
const exportData = () => {
  const dataStr = JSON.stringify(probes.value, null, 2)
  const blob = new Blob([dataStr], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `probes-data-${Date.now()}.json`
  link.click()
  URL.revokeObjectURL(url)
  ElMessage.success('数据导出成功')
}

// 提交表单
const handleSubmit = async () => {
  try {
    await formRef.value.validate()

    submitting.value = true

    if (isEdit.value) {
      // 更新
      const { code, data } = await probeApi.update(form.id, form)
      if (code === 200) {
        const index = probes.value.findIndex(p => p.id === form.id)
        if (index > -1) {
          probes.value[index] = { ...probes.value[index], ...data }
        }
        ElMessage.success('更新成功')
      } else {
        console.error('更新失败:', error)
      }
    } else {
      // 创建
      const { code, data } = await probeApi.create({
        ...form,
        probeKey: `probe-${Date.now()}`
      })
      if (code === 200) {
        probes.value.push(data)
        ElMessage.success('添加成功')
      } else {
        ElMessage.error('添加失败')
      }
    }

    showCreateDialog.value = false
  } catch (error) {
    // 验证失败
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  loadData()
  startAutoRefresh() // 启动自动刷新
})

onUnmounted(() => {
  stopAutoRefresh() // 清理定时器
})
</script>

<style scoped lang="scss">
.data-manager {
  padding: 20px;

  .manager-card {
    .card-header {
      display: flex;
      align-items: center;
      gap: 8px;
      font-size: 16px;
      font-weight: 600;
    }

    .stats-row {
      margin-bottom: 20px;

      .stat-item {
        padding: 16px;
        border-radius: 8px;
        text-align: center;

        .stat-value {
          font-size: 28px;
          font-weight: bold;
          margin-bottom: 8px;
        }

        .stat-label {
          font-size: 13px;
          color: #909399;
        }

        &.stat-primary {
          background: linear-gradient(135deg, #409eff 0%, #66b1ff 100%);
          color: #fff;
          .stat-label { color: rgba(255, 255, 255, 0.8); }
        }

        &.stat-success {
          background: linear-gradient(135deg, #67c23a 0%, #85ce61 100%);
          color: #fff;
          .stat-label { color: rgba(255, 255, 255, 0.8); }
        }

        &.stat-warning {
          background: linear-gradient(135deg, #e6a23c 0%, #ebb563 100%);
          color: #fff;
          .stat-label { color: rgba(255, 255, 255, 0.8); }
        }

        &.stat-danger {
          background: linear-gradient(135deg, #f56c6c 0%, #f78989 100%);
          color: #fff;
          .stat-label { color: rgba(255, 255, 255, 0.8); }
        }
      }
    }

    .actions-bar {
      margin-bottom: 20px;
      display: flex;
      gap: 12px;
      flex-wrap: wrap;
    }

    .metric-text {
      font-size: 12px;
      color: #606266;
      margin-left: 8px;
    }

    .batch-actions {
      margin-top: 16px;
    }

    .dialog-footer {
      display: flex;
      justify-content: flex-end;
      gap: 10px;
    }
  }
}
</style>
