<template>
  <div class="dashboard-enhanced">
    <!-- 实时状态栏 -->
    <StatusBar />

    <!-- 概览统计卡片 -->
    <section :class="['stats-row', 'responsive-grid']" aria-label="系统统计">
      <article class="stat-card stat-card-primary" role="region" aria-label="探针总数统计">
        <div class="stat-content">
          <el-icon class="stat-icon" aria-hidden="true"><Monitor /></el-icon>
          <div class="stat-info">
            <div class="stat-value">{{ systemStats.totalProbes }}</div>
            <div class="stat-label">探针总数</div>
          </div>
        </div>
      </article>
      <article class="stat-card stat-card-success" role="region" aria-label="在线探针统计">
        <div class="stat-content">
          <el-icon class="stat-icon" aria-hidden="true"><SuccessFilled /></el-icon>
          <div class="stat-info">
            <div class="stat-value">{{ systemStats.onlineProbes }}</div>
            <div class="stat-label">在线探针</div>
          </div>
        </div>
      </article>
      <article class="stat-card stat-card-warning" role="region" aria-label="离线探针统计">
        <div class="stat-content">
          <el-icon class="stat-icon" aria-hidden="true"><WarningFilled /></el-icon>
          <div class="stat-info">
            <div class="stat-value">{{ systemStats.offlineProbes }}</div>
            <div class="stat-label">离线探针</div>
          </div>
        </div>
      </article>
      <article class="stat-card stat-card-danger" role="region" aria-label="活跃告警统计">
        <div class="stat-content">
          <el-icon class="stat-icon" aria-hidden="true"><Bell /></el-icon>
          <div class="stat-info">
            <div class="stat-value">{{ systemStats.activeAlerts }}</div>
            <div class="stat-label">活跃告警</div>
          </div>
        </div>
      </article>
    </section>

    <!-- 系统资源监控 -->
    <el-card class="charts-card">
      <template #header>
        <div class="card-header">
          <h2 class="card-title">系统资源监控</h2>
          <el-button
            type="primary"
            size="small"
            :icon="Refresh"
            :loading="refreshing"
            @click="handleRefresh"
            aria-label="刷新系统资源数据"
          >
            刷新数据
          </el-button>
        </div>
      </template>
      <el-row :gutter="20" class="responsive-grid">
        <el-col :xs="24" :sm="24" :md="12" :lg="12" :xl="12">
          <RealtimeChart
            title="CPU 系统资源"
            metric="cpu"
            height="300px"
            @data-update="handleCpuUpdate"
            ref="cpuChartRef"
          />
        </el-col>
        <el-col :xs="24" :sm="24" :md="12" :lg="12" :xl="12">
          <RealtimeChart
            title="内存 系统资源"
            metric="memory"
            height="300px"
            @data-update="handleMemoryUpdate"
            ref="memoryChartRef"
          />
        </el-col>
      </el-row>
    </el-card>

    <!-- 探针卡片展示 -->
    <el-card class="probe-cards-card">
      <template #header>
        <div class="card-header">
          <h2 class="card-title">探针状态</h2>
          <el-radio-group v-model="viewMode" size="small" role="radiogroup" aria-label="视图模式">
            <el-radio-button value="card">卡片视图</el-radio-button>
            <el-radio-button value="list">列表视图</el-radio-button>
          </el-radio-group>
        </div>
      </template>

      <!-- 卡片视图 -->
      <div v-if="viewMode === 'card'" class="probe-cards-view">
        <el-row :gutter="16" class="responsive-grid">
          <el-col
            :xs="24"
            :sm="12"
            :md="8"
            :lg="8"
            :xl="6"
            v-for="probe in probes"
            :key="probe.id"
          >
            <ProbeCard
              :probe="probe"
              :is-selected="selectedProbeId === probe.id"
              @click="handleProbeSelect"
              @view="handleProbeView"
              @edit="handleProbeEdit"
              @delete="handleProbeDelete"
              @monitor="handleProbeMonitor"
            />
          </el-col>
        </el-row>
      </div>

      <!-- 列表视图 -->
      <div v-else class="table-wrapper" role="region" :aria-label="'探针列表，共' + probes.length + '条'">
        <el-table
          :data="probes"
          border
          stripe
          @row-click="handleProbeView"
          class="responsive-table"
        >
          <el-table-column prop="name" label="探针名称" min-width="140" show-overflow-tooltip />
          <el-table-column prop="probeKey" label="探针标识" min-width="140" show-overflow-tooltip />
          <el-table-column prop="type" label="类型" width="120">
            <template #default="{ row }">
              <el-tag :type="getTypeColor(row.type)">
                {{ getTypeLabel(row.type) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="getStatusType(row.status)" effect="dark">
                {{ getStatusText(row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="cpuUsage" label="CPU使用率" width="150">
            <template #default="{ row }">
              <el-progress
                :percentage="row.cpuUsage || 0"
                :status="getProgressStatus(row.cpuUsage || 0)"
              />
            </template>
          </el-table-column>
          <el-table-column prop="networkIn" label="网络下载" width="130">
            <template #default="{ row }">
              <span>{{ formatRate(row.networkIn || 0) }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="networkOut" label="网络上传" width="130">
            <template #default="{ row }">
              <span>{{ formatRate(row.networkOut || 0) }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="cpuLoad1min" label="系统负载" width="140">
            <template #default="{ row }">
              <span v-if="row.cpuLoad1min">{{ row.cpuLoad1min.toFixed(2) }}</span>
              <span v-else>-</span>
              <span v-if="row.cpuLoad1min" style="font-size: 12px; color: #999;"> (1min)</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" class-name="action-column">
            <template #default="{ row }">
              <div class="action-cell" @click.stop>
                <el-button
                  size="small"
                  type="success"
                  link
                  @click="handleProbeMonitor(row)"
                  :aria-label="'监控探针' + row.name"
                >
                  监控
                </el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import StatusBar from '@/components/StatusBar.vue'
import RealtimeChart from '@/components/RealtimeChart.vue'
import ProbeCard from '@/components/ProbeCard.vue'
import { probeApi } from '@/api/probe'
import { getProbeMetricsSummary } from '@/api/metrics'
import { formatRate } from '@/utils'

const viewMode = ref('card')
const selectedProbeId = ref(null)
const probes = ref([])
const refreshing = ref(false)
const cpuChartRef = ref(null)
const memoryChartRef = ref(null)

const systemStats = reactive({
  totalProbes: 0,
  onlineProbes: 0,
  offlineProbes: 0,
  activeAlerts: 0
})

// 刷新系统资源数据
const handleRefresh = async () => {
  refreshing.value = true
  try {
    // 刷新探针列表和指标数据
    await fetchProbes()
    ElMessage.success('系统资源数据已刷新')

    // 触发图表组件刷新
    if (cpuChartRef.value && cpuChartRef.value.refresh) {
      cpuChartRef.value.refresh()
    }
    if (memoryChartRef.value && memoryChartRef.value.refresh) {
      memoryChartRef.value.refresh()
    }
  } catch (error) {
    console.error('刷新失败:', error)
    ElMessage.error('刷新失败，请稍后重试')
  } finally {
    refreshing.value = false
  }
}

// 获取探针列表
const fetchProbes = async () => {
  try {
    const { code, data } = await probeApi.getList({ pageNum: 1, pageSize: 100 })
    if (code === 200) {
      const records = data.records || []

      // 初始化探针数据
      probes.value = records.map(probe => ({
        ...probe,
        cpuUsage: 0,
        memoryUsage: 0,
        diskUsage: 0,
        networkIn: 0,
        networkOut: 0
      }))

      // 更新统计
      systemStats.totalProbes = records.length
      systemStats.onlineProbes = records.filter(p => p.status === 'online').length
      systemStats.offlineProbes = records.filter(p => p.status === 'offline').length

      // 获取在线探针的实时指标数据
      await fetchProbesMetrics()
    }
  } catch (error) {
    console.error('获取探针列表失败:', error)
  }
}

// 获取探针指标数据
const fetchProbesMetrics = async () => {
  const onlineProbes = probes.value.filter(p => p.status === 'online')

  // 并行获取所有在线探针的指标数据
  const metricsPromises = onlineProbes.map(async (probe) => {
    try {
      const response = await getProbeMetricsSummary(probe.id)
      if (response.code === 200 && response.data) {
        probe.cpuUsage = response.data.cpuUsage || 0
        probe.memoryUsage = response.data.memoryUsed || 0
        probe.diskUsage = response.data.diskUsage || 0
        probe.networkIn = response.data.networkRxRate || 0
        probe.networkOut = response.data.networkTxRate || 0
        probe.cpuLoad1min = response.data.cpuLoad1min
        probe.cpuLoad5min = response.data.cpuLoad5min
        probe.cpuLoad15min = response.data.cpuLoad15min
      }
    } catch (error) {
      console.warn(`获取探针 ${probe.name} 的监控数据失败:`, error)
    }
  })

  await Promise.all(metricsPromises)
}

// 处理探针选择
const handleProbeSelect = (probe) => {
  selectedProbeId.value = probe.id
  ElMessage.info(`选中探针: ${probe.name}`)
}

// 处理查看详情
const handleProbeView = (probe) => {
  ElMessage.info(`查看探针详情: ${probe.name}`)
}

// 处理编辑
const handleProbeEdit = (probe) => {
  ElMessage.info(`编辑探针: ${probe.name}`)
}

// 处理删除
const handleProbeDelete = (probe) => {
  ElMessage.warning(`删除探针: ${probe.name}`)
}

// 处理监控
const handleProbeMonitor = (probe) => {
  ElMessage.success(`跳转到监控页面: ${probe.name}`)
}

// 处理CPU数据更新
const handleCpuUpdate = (data) => {
}

// 处理内存数据更新
const handleMemoryUpdate = (data) => {
}

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
    error: 'danger',
    ONLINE: 'success',
    OFFLINE: 'info',
    ERROR: 'danger'
  }
  return typeMap[status] || 'info'
}

const getStatusText = (status) => {
  const textMap = {
    online: '在线',
    offline: '离线',
    error: '异常',
    ONLINE: '在线',
    OFFLINE: '离线',
    ERROR: '异常'
  }
  return textMap[status] || status
}

const getProgressStatus = (percentage) => {
  if (percentage >= 90) return 'exception'
  if (percentage >= 70) return 'warning'
  return 'success'
}

let refreshTimer = null

onMounted(() => {
  fetchProbes()
  // 每30秒刷新一次探针列表
  refreshTimer = setInterval(fetchProbes, 30000)
})

onUnmounted(() => {
  if (refreshTimer) {
    clearInterval(refreshTimer)
  }
})
</script>

<style scoped lang="scss">
.dashboard-enhanced {
  padding: 20px;
  max-width: 100%;
  overflow-x: hidden;
}

/* 统计卡片网格 - 响应式 */
.stats-row {
  margin-bottom: 20px;
  display: grid;
  gap: 20px;
  grid-template-columns: repeat(4, 1fr);

  @media (max-width: 1200px) {
    grid-template-columns: repeat(2, 1fr);
  }

  @media (max-width: 576px) {
    grid-template-columns: 1fr;
    gap: 12px;
  }
}

.stat-card {
  cursor: default;
  transition: all 0.2s ease;

  &:hover {
    transform: translateY(-2px);
    box-shadow: var(--shadow-md);
  }

  :deep(.el-card__body) {
    padding: 20px;
  }

  .stat-content {
    display: flex;
    align-items: center;
    gap: 16px;

    .stat-icon {
      font-size: 36px;
      width: 60px;
      height: 60px;
      display: flex;
      align-items: center;
      justify-content: center;
      border-radius: 8px;
      flex-shrink: 0;
    }

    .stat-info {
      flex: 1;
      min-width: 0;

      .stat-value {
        font-size: 28px;
        font-weight: bold;
        margin-bottom: 4px;
        line-height: 1.2;

        @media (max-width: 768px) {
          font-size: 24px;
        }
      }

      .stat-label {
        font-size: 14px;
        color: #909399;

        @media (max-width: 576px) {
          font-size: 13px;
        }
      }
    }
  }

  &.stat-card-primary {
    .stat-icon {
      background: linear-gradient(135deg, #409eff 0%, #66b1ff 100%);
      color: #fff;
    }
    .stat-value { color: #409eff; }
  }

  &.stat-card-success {
    .stat-icon {
      background: linear-gradient(135deg, #67c23a 0%, #85ce61 100%);
      color: #fff;
    }
    .stat-value { color: #67c23a; }
  }

  &.stat-card-warning {
    .stat-icon {
      background: linear-gradient(135deg, #e6a23c 0%, #ebb563 100%);
      color: #fff;
    }
    .stat-value { color: #e6a23c; }
  }

  &.stat-card-danger {
    .stat-icon {
      background: linear-gradient(135deg, #f56c6c 0%, #f78989 100%);
      color: #fff;
    }
    .stat-value { color: #f56c6c; }
  }
}

.charts-card {
  margin-bottom: 20px;

  .card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: 16px;

    .card-title {
      margin: 0;
      font-size: 16px;
      font-weight: 600;
    }
  }
}

.probe-cards-card {
  .card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    flex-wrap: wrap;
    gap: 16px;

    .card-title {
      margin: 0;
      font-size: 16px;
      font-weight: 600;
    }
  }

  .probe-cards-view {
    :deep(.el-col) {
      margin-bottom: 16px;
    }
  }
}

/* 表格包装器 - 移动端优化 */
.table-wrapper {
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;

  @media (max-width: 768px) {
    position: relative;

    &::after {
      content: '← 滑动查看更多 →';
      position: absolute;
      bottom: 8px;
      right: 8px;
      font-size: 11px;
      color: var(--text-tertiary);
      background: rgba(255, 255, 255, 0.9);
      padding: 4px 8px;
      border-radius: 4px;
      pointer-events: none;
      box-shadow: var(--shadow-sm);
    }
  }
}

/* 表格行点击样式 */
:deep(.el-table) {
  .el-table__row {
    cursor: pointer;
    transition: background-color 0.2s ease;
  }

  .el-table__row:hover {
    background-color: #f5f7fa !important;
  }

  .action-column {
    cursor: default;
  }

  th.el-table__cell {
    cursor: default;
  }
}

/* 响应式字体大小 */
@media (max-width: 1200px) {
  :deep(.el-table) {
    font-size: 14px;
  }
}

@media (max-width: 768px) {
  .dashboard-enhanced {
    padding: 12px;
  }

  :deep(.el-table) {
    font-size: 13px;
  }

  :deep(.el-table__cell) {
    padding: 8px 4px;
  }

  :deep(.el-tag) {
    font-size: 12px;
  }

  .stat-card {
    :deep(.el-card__body) {
      padding: 16px;
    }

    .stat-content {
      gap: 12px;

      .stat-icon {
        width: 48px;
        height: 48px;
        font-size: 28px;
      }
    }
  }
}

@media (max-width: 576px) {
  .dashboard-enhanced {
    padding: 8px;
  }

  :deep(.el-table) {
    font-size: 12px;
  }

  :deep(.el-table__cell) {
    padding: 6px 2px;
  }

  :deep(.el-tag) {
    font-size: 11px;
  }

  .stat-card {
    :deep(.el-card__body) {
      padding: 12px;
    }

    .stat-content {
      gap: 8px;

      .stat-icon {
        width: 40px;
        height: 40px;
        font-size: 24px;
      }
    }
  }
}
</style>
