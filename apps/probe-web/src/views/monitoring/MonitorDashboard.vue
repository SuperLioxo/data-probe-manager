<template>
  <div class="monitor-dashboard-v2">
    <!-- Header -->
    <div class="dashboard-header-v2">
      <div class="header-left">
        <h1 class="page-title">
          <el-icon class="title-icon"><DataAnalysis /></el-icon>
          系统探针监控
        </h1>
        <p class="page-subtitle">实时监控探针状态和系统性能指标</p>
        <div class="last-update" v-if="lastUpdateTime">
          <el-icon><Clock /></el-icon>
          更新于 {{ formatTime(lastUpdateTime) }}
        </div>
      </div>
      <div class="header-actions">
        <!-- 自动刷新控制卡片 -->
        <div class="refresh-control-card">
          <div class="refresh-toggle-section">
            <div
              class="refresh-toggle-btn"
              :class="{ 'active': autoRefresh }"
              @click="toggleAutoRefresh"
            >
              <div class="toggle-icon">
                <el-icon><component :is="autoRefresh ? 'VideoPause' : 'VideoPlay'" /></el-icon>
              </div>
              <div class="toggle-text">
                <div class="toggle-label">自动刷新</div>
                <div class="toggle-status">{{ autoRefresh ? '已开启' : '已关闭' }}</div>
              </div>
            </div>
          </div>

          <div class="refresh-interval-section">
            <div class="interval-label">刷新间隔</div>
            <div class="interval-chips">
              <div
                class="interval-chip"
                :class="{ 'active': refreshInterval === 10, 'disabled': !autoRefresh }"
                @click="autoRefresh && changeRefreshInterval(10)"
              >
                10秒
              </div>
              <div
                class="interval-chip"
                :class="{ 'active': refreshInterval === 30, 'disabled': !autoRefresh }"
                @click="autoRefresh && changeRefreshInterval(30)"
              >
                30秒
              </div>
              <div
                class="interval-chip"
                :class="{ 'active': refreshInterval === 60, 'disabled': !autoRefresh }"
                @click="autoRefresh && changeRefreshInterval(60)"
              >
                60秒
              </div>
            </div>
          </div>
        </div>

        <!-- 手动刷新按钮 -->
        <el-button
          type="primary"
          :icon="Refresh"
          :loading="loading"
          @click="handleRefresh"
          class="manual-refresh-btn"
          size="large"
        >
          刷新
        </el-button>
      </div>
    </div>

    <!-- Overview Stats -->
    <div class="overview-section">
      <div class="stats-container">
        <div class="stat-cards-grid">
          <div class="stat-card stat-total">
            <div class="stat-icon">
              <el-icon><Monitor /></el-icon>
            </div>
            <div class="stat-content">
              <div class="stat-value">{{ stats.total }}</div>
              <div class="stat-label">探针总数</div>
            </div>
          </div>
          <div class="stat-card stat-online">
            <div class="stat-icon">
              <el-icon><SuccessFilled /></el-icon>
            </div>
            <div class="stat-content">
              <div class="stat-value">{{ stats.online }}</div>
              <div class="stat-label">在线</div>
            </div>
          </div>
          <div class="stat-card stat-offline">
            <div class="stat-icon">
              <el-icon><WarningFilled /></el-icon>
            </div>
            <div class="stat-content">
              <div class="stat-value">{{ stats.offline }}</div>
              <div class="stat-label">离线</div>
            </div>
          </div>
        </div>

        <!-- Alert Summary -->
        <div class="alert-summary">
          <div class="alert-item" :class="{ 'has-alert': alerts.disk > 0 }" @click="filterByAlert('disk')">
            <el-icon><FolderOpened /></el-icon>
            <span>磁盘告警: {{ alerts.disk }}</span>
          </div>
          <div class="alert-item" :class="{ 'has-alert': alerts.memory > 0 }" @click="filterByAlert('memory')">
            <el-icon><Memo /></el-icon>
            <span>内存告警: {{ alerts.memory }}</span>
          </div>
          <div class="alert-item" :class="{ 'has-alert': alerts.cpu > 0 }" @click="filterByAlert('cpu')">
            <el-icon><Cpu /></el-icon>
            <span>CPU告警: {{ alerts.cpu }}</span>
          </div>
          <div class="alert-item" :class="{ 'has-alert': alerts.network > 0 }" @click="filterByAlert('network')">
            <el-icon><Connection /></el-icon>
            <span>网络异常: {{ alerts.network }}</span>
          </div>
        </div>

        <!-- AGENT Switcher -->
        <div class="agent-switcher-section">
          <div class="agent-switcher-title">AGENT服务</div>
          <div class="agent-switcher">
            <el-button
              :type="currentAgent === 'default' ? 'primary' : 'default'"
              @click="switchAgent('default')"
              class="agent-btn"
            >
              <el-icon><Monitor /></el-icon>
              默认AGENT
            </el-button>
            <el-button
              :type="currentAgent === 'agent2' ? 'primary' : 'default'"
              @click="switchAgent('agent2')"
              class="agent-btn"
              disabled
            >
              <el-icon><Monitor /></el-icon>
              AGENT 2
            </el-button>
            <el-button
              :type="currentAgent === 'agent3' ? 'primary' : 'default'"
              @click="switchAgent('agent3')"
              class="agent-btn"
              disabled
            >
              <el-icon><Monitor /></el-icon>
              AGENT 3
            </el-button>
            <el-button
              :type="currentAgent === 'agent4' ? 'primary' : 'default'"
              @click="switchAgent('agent4')"
              class="agent-btn"
              disabled
            >
              <el-icon><Monitor /></el-icon>
              AGENT 4
            </el-button>
            <el-button
              :type="currentAgent === 'agent5' ? 'primary' : 'default'"
              @click="switchAgent('agent5')"
              class="agent-btn"
              disabled
            >
              <el-icon><Monitor /></el-icon>
              AGENT 5
            </el-button>
          </div>
        </div>
      </div>
    </div>

    <!-- Performance Metrics Bar -->
    <div v-if="probeList.length > 0" class="performance-metrics-bar">
      <!-- 后台加载提示 -->
      <div v-if="metricsLoading" class="metrics-loading-tip">
        <el-icon class="is-loading"><Loading /></el-icon>
        <span>正在加载监控数据...</span>
      </div>

      <div class="perf-item">
        <el-icon><Timer /></el-icon>
        <span>渲染: {{ performanceMetrics.renderTime }}ms</span>
      </div>
      <div class="perf-item">
        <el-icon><Download /></el-icon>
        <span>数据: {{ performanceMetrics.dataFetchTime }}ms</span>
      </div>
      <div class="perf-item">
        <el-icon><Cpu /></el-icon>
        <span>内存: {{ performanceMetrics.memoryUsage }}MB</span>
      </div>
      <div class="perf-item" :class="{ 'active': performanceMetrics.cacheHitRate > 50 }">
        <el-icon><FolderOpened /></el-icon>
        <span>缓存: {{ performanceMetrics.cacheHitRate }}%</span>
      </div>
      <div class="perf-item" :class="{
        'active': webSocketStatus === 'connected',
        'error': webSocketStatus === 'error'
      }">
        <el-icon><Connection /></el-icon>
        <span>WebSocket: {{ webSocketStatus }}</span>
      </div>
      <div class="perf-item" :class="{ 'active': performanceMetrics.usingVirtualScroll }">
        <el-icon><Grid /></el-icon>
        <span>虚拟滚动: {{ performanceMetrics.usingVirtualScroll ? 'ON' : 'OFF' }}</span>
      </div>
    </div>

    <!-- Filter Bar -->
    <div class="filter-bar" v-if="currentFilter">
      <el-tag closable @close="clearFilter" type="warning" size="large">
        当前筛选: {{ getFilterText(currentFilter) }}
      </el-tag>
    </div>

    <!-- Probe Cards Grid (常规渲染) -->
    <div
      v-if="!performanceMetrics.usingVirtualScroll"
      v-loading="loading"
      class="probe-cards-grid"
    >
      <div
        v-for="probe in filteredProbeList"
        :key="probe.id"
        class="probe-card"
        :class="{
          'probe-offline': probe.status?.toLowerCase() !== 'online',
          'has-alerts': hasAlerts(probe)
        }"
      >
        <!-- Probe Header -->
        <div class="probe-header">
          <div class="probe-info">
            <div class="probe-status" :class="getStatusClass(probe.status)"></div>
            <div>
              <h3 class="probe-name">{{ probe.name }}</h3>
              <p class="probe-key">{{ probe.probeKey }}</p>
            </div>
          </div>
        </div>

        <!-- Critical Metrics (Top Row) -->
        <div class="metrics-critical">
          <div class="metric-mini" :class="getMetricClass(probe.cpuUsage, 90)">
            <div class="metric-label">CPU</div>
            <div class="metric-value">{{ probe.cpuUsage?.toFixed(1) || 0 }}%</div>
            <div class="metric-bar">
              <div class="metric-bar-fill" :style="{ width: (probe.cpuUsage || 0) + '%' }"></div>
            </div>
          </div>
          <div class="metric-mini" :class="getMetricClass(probe.memoryUsage, 85)">
            <div class="metric-label">内存</div>
            <div class="metric-value">{{ probe.memoryUsage?.toFixed(1) || 0 }}%</div>
            <div class="metric-bar">
              <div class="metric-bar-fill" :style="{ width: (probe.memoryUsage || 0) + '%' }"></div>
            </div>
          </div>
          <div class="metric-mini" :class="getMetricClass(probe.diskUsage, 80)">
            <div class="metric-label">磁盘</div>
            <div class="metric-value">{{ probe.diskUsage?.toFixed(1) || 0 }}%</div>
            <div class="metric-bar">
              <div class="metric-bar-fill" :style="{ width: (probe.diskUsage || 0) + '%' }"></div>
            </div>
          </div>
          <div class="metric-mini" :class="getNetworkMetricClass(probe)">
            <div class="metric-label">网络</div>
            <div class="metric-value-small">
              <div>↓{{ formatRate(probe.networkIn || 0) }}</div>
              <div>↑{{ formatRate(probe.networkOut || 0) }}</div>
            </div>
          </div>
        </div>

        <!-- System Resources Section -->
        <div class="metrics-section">
          <div class="section-title">系统资源</div>
          <div class="metrics-grid">
            <!-- CPU Detail -->
            <div class="metric-detail">
              <span class="metric-label">核心数</span>
              <span class="metric-value">{{ probe.cpuCores || '-' }}</span>
            </div>
            <div class="metric-detail">
              <span class="metric-label">负载 (1/5/15min)</span>
              <span class="metric-value">
                {{ probe.cpuLoad1min?.toFixed(2) || '-' }} /
                {{ probe.cpuLoad5min?.toFixed(2) || '-' }} /
                {{ probe.cpuLoad15min?.toFixed(2) || '-' }}
              </span>
            </div>
            <!-- Memory Detail -->
            <div class="metric-detail">
              <span class="metric-label">内存使用</span>
              <span class="metric-value">{{ formatBytes(probe.memoryUsed || 0) }} / {{ formatBytes(probe.memoryTotal || 0) }}</span>
            </div>
            <div class="metric-detail">
              <span class="metric-label">可用内存</span>
              <span class="metric-value">{{ formatBytes(probe.memoryAvailable || 0) }}</span>
            </div>
            <!-- Disk Detail -->
            <div class="metric-detail">
              <span class="metric-label">磁盘使用</span>
              <span class="metric-value">{{ formatBytes(probe.diskUsed || 0) }} / {{ formatBytes(probe.diskTotal || 0) }}</span>
            </div>
            <!-- Network Detail -->
            <div class="metric-detail">
              <span class="metric-label">累计流量</span>
              <span class="metric-value">
                ↓{{ formatBytes(probe.networkRxBytes || 0) }}
                ↑{{ formatBytes(probe.networkTxBytes || 0) }}
              </span>
            </div>
            <div class="metric-detail">
              <span class="metric-label">网络错误</span>
              <span class="metric-value" :class="{ 'error': (probe.networkRxErrors || 0) > 0 || (probe.networkTxErrors || 0) > 0 }">
                RX: {{ probe.networkRxErrors || 0 }} / TX: {{ probe.networkTxErrors || 0 }}
              </span>
            </div>
          </div>
        </div>

        <!-- JVM Metrics Section -->
        <div class="metrics-section" v-if="probe.jvmHeapUsed || probe.jvmThreadCount">
          <div class="section-title">JVM指标</div>
          <div class="metrics-grid">
            <div class="metric-detail">
              <span class="metric-label">堆内存</span>
              <span class="metric-value">
                {{ probe.jvmHeapUsed?.toFixed(2) || 0 }} / {{ probe.jvmHeapMax?.toFixed(2) || 0 }} MB
                <span class="metric-sub">({{ probe.jvmHeapUsage?.toFixed(1) || 0 }}%)</span>
              </span>
            </div>
            <div class="metric-detail">
              <span class="metric-label">线程</span>
              <span class="metric-value">{{ probe.jvmThreadCount || 0 }} (峰值: {{ probe.jvmThreadPeak || 0 }})</span>
            </div>
            <div class="metric-detail">
              <span class="metric-label">已加载类</span>
              <span class="metric-value">{{ probe.jvmClassLoaded?.toLocaleString() || 0 }}</span>
            </div>
            <div class="metric-detail">
              <span class="metric-label">JVM内存</span>
              <span class="metric-value">
                总计: {{ probe.jvmTotalMemory?.toFixed(0) || 0 }} MB
                <span class="metric-sub">空闲: {{ probe.jvmFreeMemory?.toFixed(0) || 0 }} MB</span>
              </span>
            </div>
          </div>
        </div>

        <!-- OS & Process Section -->
        <div class="metrics-section" v-if="probe.osProcessCount || probe.processCpuUsage">
          <div class="section-title">系统 & 进程</div>
          <div class="metrics-grid">
            <div class="metric-detail">
              <span class="metric-label">进程/线程</span>
              <span class="metric-value">{{ probe.osProcessCount || 0 }} / {{ probe.osThreadCount || 0 }}</span>
            </div>
            <div class="metric-detail">
              <span class="metric-label">系统运行时间</span>
              <span class="metric-value">{{ formatUptime(probe.osUptimeSeconds) }}</span>
            </div>
            <div class="metric-detail">
              <span class="metric-label">进程CPU</span>
              <span class="metric-value">{{ probe.processCpuUsage?.toFixed(2) || 0 }}%</span>
            </div>
            <div class="metric-detail">
              <span class="metric-label">进程内存</span>
              <span class="metric-value">{{ probe.processMemoryResident?.toFixed(2) || 0 }} MB</span>
            </div>
            <div class="metric-detail">
              <span class="metric-label">JVM运行时间</span>
              <span class="metric-value">{{ formatUptime(probe.processJvmUptime) }}</span>
            </div>
            <div class="metric-detail">
              <span class="metric-label">进程ID</span>
              <span class="metric-value">{{ probe.processId || '-' }}</span>
            </div>
          </div>
        </div>

        <!-- Footer -->
        <div class="probe-footer">
          <div class="last-heartbeat">
            <el-icon><Clock /></el-icon>
            {{ probe.lastHeartbeat || '未上报' }}
          </div>
        </div>
      </div>
    </div>

    <!-- Virtual Scroller (大量探针时启用) -->
    <div
      v-if="performanceMetrics.usingVirtualScroll"
      v-loading="loading"
      class="probe-cards-virtual"
    >
      <DynamicScroller
        :items="filteredProbeList"
        :min-item-size="550"
        class="scroller"
        key-field="id"
      >
        <template #default="{ item: probe, index, active }">
          <DynamicScrollerItem
            :item="probe"
            :active="active"
            :data-index="index"
            class="probe-card-wrapper"
          >
            <div
              class="probe-card"
              :class="{
                'probe-offline': probe.status?.toLowerCase() !== 'online',
                'has-alerts': hasAlerts(probe)
              }"
            >
              <!-- Probe Header -->
              <div class="probe-header">
                <div class="probe-info">
                  <div class="probe-status" :class="getStatusClass(probe.status)"></div>
                  <div>
                    <h3 class="probe-name">{{ probe.name }}</h3>
                    <p class="probe-key">{{ probe.probeKey }}</p>
                  </div>
                </div>
              </div>

              <!-- Critical Metrics -->
              <div class="metrics-critical">
                <div class="metric-mini" :class="getMetricClass(probe.cpuUsage, 90)">
                  <div class="metric-label">CPU</div>
                  <div class="metric-value">{{ probe.cpuUsage?.toFixed(1) || 0 }}%</div>
                  <div class="metric-bar">
                    <div class="metric-bar-fill" :style="{ width: (probe.cpuUsage || 0) + '%' }"></div>
                  </div>
                </div>
                <div class="metric-mini" :class="getMetricClass(probe.memoryUsage, 85)">
                  <div class="metric-label">内存</div>
                  <div class="metric-value">{{ probe.memoryUsage?.toFixed(1) || 0 }}%</div>
                  <div class="metric-bar">
                    <div class="metric-bar-fill" :style="{ width: (probe.memoryUsage || 0) + '%' }"></div>
                  </div>
                </div>
                <div class="metric-mini" :class="getMetricClass(probe.diskUsage, 80)">
                  <div class="metric-label">磁盘</div>
                  <div class="metric-value">{{ probe.diskUsage?.toFixed(1) || 0 }}%</div>
                  <div class="metric-bar">
                    <div class="metric-bar-fill" :style="{ width: (probe.diskUsage || 0) + '%' }"></div>
                  </div>
                </div>
                <div class="metric-mini" :class="getNetworkMetricClass(probe)">
                  <div class="metric-label">网络</div>
                  <div class="metric-value-small">
                    <div>↓{{ formatRate(probe.networkIn || 0) }}</div>
                    <div>↑{{ formatRate(probe.networkOut || 0) }}</div>
                  </div>
                </div>
              </div>

              <!-- Footer -->
              <div class="probe-footer">
                <div class="last-heartbeat">
                  <el-icon><Clock /></el-icon>
                  {{ probe.lastHeartbeat || '未上报' }}
                </div>
              </div>
            </div>
          </DynamicScrollerItem>
        </template>
      </DynamicScroller>
    </div>

    <!-- Empty State -->
    <div v-if="!loading && filteredProbeList.length === 0" class="empty-state">
      <el-icon class="empty-icon"><Monitor /></el-icon>
      <p>没有找到符合条件的探针</p>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import {
  Refresh, DataAnalysis, Monitor, SuccessFilled, WarningFilled,
  CircleCloseFilled, FolderOpened, Memo, Cpu, Connection, Clock,
  Timer, Download, Grid, VideoPlay, VideoPause, Loading
} from '@element-plus/icons-vue'
import { probeApi } from '@/api/probe'
import { getProbeMetricsSummary } from '@/api/metrics'
import * as fileProbeApi from '@/api/fileProbe'
import metricsWebSocket from '@/utils/metricsWebSocket'

// ========== 性能监控 ==========
const performanceMetrics = reactive({
  renderTime: 0,
  dataFetchTime: 0,
  memoryUsage: 0,
  probeCount: 0,
  usingVirtualScroll: false,
  cacheHitRate: 0
})

let performanceTimer = null

// 监控内存使用
const monitorMemory = () => {
  if (performance.memory) {
    performanceMetrics.memoryUsage = Math.round(performance.memory.usedJSHeapSize / 1024 / 1024)
  }
}

// 定期更新性能指标
const updatePerformanceMetrics = () => {
  monitorMemory()
  performanceMetrics.probeCount = probeList.value.length
  performanceMetrics.usingVirtualScroll = probeList.value.length > 50
}

// ========== 数据缓存策略 ==========
const metricsCache = new Map()
const CACHE_TTL = 10000 // 10秒缓存
let cacheHits = 0
let cacheMisses = 0

const getCachedMetrics = (probeId) => {
  const cached = metricsCache.get(probeId)
  if (cached && Date.now() - cached.timestamp < CACHE_TTL) {
    cacheHits++
    return cached.data
  }
  cacheMisses++
  return null
}

const setCachedMetrics = (probeId, data) => {
  metricsCache.set(probeId, {
    data,
    timestamp: Date.now()
  })
  // 限制缓存大小，最多缓存100个探针
  if (metricsCache.size > 100) {
    const firstKey = metricsCache.keys().next().value
    metricsCache.delete(firstKey)
  }
}

const clearExpiredCache = () => {
  const now = Date.now()
  for (const [key, value] of metricsCache.entries()) {
    if (now - value.timestamp >= CACHE_TTL) {
      metricsCache.delete(key)
    }
  }
}

// WebSocket状态
const useWebSocket = ref(true)  // 启用WebSocket实时推送
const webSocketStatus = ref('disconnected')

const loading = ref(false)
const metricsLoading = ref(false) // 追踪后台指标数据加载状态
const probeList = ref([]) // 只用于显示的系统探针
const allProbeList = ref([]) // 用于告警统计的所有探针
const autoRefresh = ref(false)
const refreshInterval = ref(30)
const currentFilter = ref('')
const lastUpdateTime = ref(null)
let refreshTimer = null

// localStorage 键名
const STORAGE_KEYS = {
  AUTO_REFRESH: 'monitor_auto_refresh',
  REFRESH_INTERVAL: 'monitor_refresh_interval'
}

// 从 localStorage 加载设置
const loadSettings = () => {
  try {
    const savedAutoRefresh = localStorage.getItem(STORAGE_KEYS.AUTO_REFRESH)
    const savedInterval = localStorage.getItem(STORAGE_KEYS.REFRESH_INTERVAL)

    if (savedAutoRefresh !== null) {
      autoRefresh.value = savedAutoRefresh === 'true'
    }
    if (savedInterval !== null) {
      const interval = parseInt(savedInterval)
      if ([10, 30, 60].includes(interval)) {
        refreshInterval.value = interval
      }
    }
  } catch (error) {
    console.warn('Failed to load settings from localStorage:', error)
  }
}

// 保存设置到 localStorage
const saveSettings = () => {
  try {
    localStorage.setItem(STORAGE_KEYS.AUTO_REFRESH, String(autoRefresh.value))
    localStorage.setItem(STORAGE_KEYS.REFRESH_INTERVAL, String(refreshInterval.value))
  } catch (error) {
    console.warn('Failed to save settings to localStorage:', error)
  }
}

// AGENT切换
const currentAgent = ref('default')

const stats = reactive({
  total: 0,
  online: 0,
  offline: 0,
  error: 0
})

const alerts = reactive({
  disk: 0,
  memory: 0,
  cpu: 0,
  network: 0
})

const filteredProbeList = computed(() => {
  if (!currentFilter.value) return probeList.value

  return probeList.value.filter(probe => {
    const status = (probe.status || '').toLowerCase()
    if (status !== 'online') return false

    switch (currentFilter.value) {
      case 'disk':
        return (probe.diskUsage || 0) > 80
      case 'memory':
        return (probe.memoryUsage || 0) > 85
      case 'cpu':
        return (probe.cpuUsage || 0) > 90
      case 'network':
        return (probe.networkRxErrors || 0) > 0 || (probe.networkTxErrors || 0) > 0
      default:
        return true
    }
  })
})

const toggleAutoRefresh = () => {
  autoRefresh.value = !autoRefresh.value
  saveSettings() // 保存设置

  if (autoRefresh.value) {
    startAutoRefresh()
    ElMessage.success({
      message: '自动刷新已开启',
      duration: 2000,
      showClose: true
    })
  } else {
    stopAutoRefresh()
    ElMessage.info({
      message: '自动刷新已关闭',
      duration: 2000,
      showClose: true
    })
  }
}

const changeRefreshInterval = (interval) => {
  refreshInterval.value = interval
  saveSettings() // 保存设置

  if (autoRefresh.value) {
    stopAutoRefresh()
    startAutoRefresh()
  }
  ElMessage.success({
    message: `刷新间隔已设置为 ${interval} 秒`,
    duration: 2000,
    showClose: true
  })
}

const startAutoRefresh = () => {
  stopAutoRefresh()
  refreshTimer = setInterval(() => {
    fetchData()
  }, refreshInterval.value * 1000)
}

const stopAutoRefresh = () => {
  if (refreshTimer) {
    clearInterval(refreshTimer)
    refreshTimer = null
  }
}

const filterByAlert = (type) => {
  currentFilter.value = type
  ElMessage.info(`已筛选: ${getFilterText(type)}`)
}

const getFilterText = (type) => {
  const textMap = {
    disk: '磁盘告警',
    memory: '内存告警',
    cpu: 'CPU告警',
    network: '网络异常'
  }
  return textMap[type] || type
}

const clearFilter = () => {
  currentFilter.value = ''
  ElMessage.info('已清除筛选')
}

// AGENT切换
const switchAgent = (agent) => {
  if (agent === 'default') {
    currentAgent.value = agent
    ElMessage.success('已切换到默认AGENT')
    // 重新加载数据
    handleRefresh()
  } else {
    ElMessage.info(`${agent.toUpperCase()} 功能开发中，敬请期待...`)
  }
}

// 格式化运行时间（秒转天/小时/分钟）
const formatUptime = (seconds) => {
  if (!seconds) return '-'
  const days = Math.floor(seconds / 86400)
  const hours = Math.floor((seconds % 86400) / 3600)
  const minutes = Math.floor((seconds % 3600) / 60)

  if (days > 0) {
    return `${days}天${hours}小时`
  } else if (hours > 0) {
    return `${hours}小时${minutes}分钟`
  } else {
    return `${minutes}分钟`
  }
}

const handleRefresh = async () => {
  loading.value = true
  try {
    await fetchData()
    ElMessage.success('数据已刷新')
  } catch (error) {
    ElMessage.error('刷新失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

const getStatusClass = (status) => {
  const normalizedStatus = (status || '').toLowerCase()
  return {
    'status-online': normalizedStatus === 'online',
    'status-offline': normalizedStatus === 'offline',
    'status-error': normalizedStatus === 'error'
  }
}

const getMetricClass = (value, threshold) => {
  if (!value) return 'metric-normal'
  if (value >= threshold) return 'metric-critical'
  if (value >= threshold * 0.8) return 'metric-warning'
  return 'metric-normal'
}

const getNetworkMetricClass = (probe) => {
  const hasErrors = (probe.networkRxErrors || 0) > 0 || (probe.networkTxErrors || 0) > 0
  return hasErrors ? 'metric-critical' : 'metric-normal'
}

const hasAlerts = (probe) => {
  const status = (probe.status || '').toLowerCase()
  if (status !== 'online') return false
  return (probe.diskUsage || 0) > 80 ||
         (probe.memoryUsage || 0) > 85 ||
         (probe.cpuUsage || 0) > 90 ||
         (probe.networkRxErrors || 0) > 0 ||
         (probe.networkTxErrors || 0) > 0
}

const formatBytes = (bytes) => {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return (bytes / Math.pow(k, i)).toFixed(1) + ' ' + sizes[i]
}

const formatRate = (bytesPerSecond) => {
  if (bytesPerSecond === 0) return '0 B/s'
  const k = 1024
  const sizes = ['B/s', 'KB/s', 'MB/s', 'GB/s']
  const i = Math.floor(Math.log(bytesPerSecond) / Math.log(k))
  return (bytesPerSecond / Math.pow(k, i)).toFixed(2) + ' ' + sizes[i]
}

const formatTime = (date) => {
  if (!date) return '-'
  const d = new Date(date)
  const hours = String(d.getHours()).padStart(2, '0')
  const minutes = String(d.getMinutes()).padStart(2, '0')
  const seconds = String(d.getSeconds()).padStart(2, '0')
  return `${hours}:${minutes}:${seconds}`
}

const fetchData = async () => {
  const startTime = performance.now()
  loading.value = true
  try {
    // 获取所有类型的探针数据用于告警统计
    const [systemResult, fileResult] = await Promise.all([
      probeApi.getList({ pageNum: 1, pageSize: 1000 }),
      fileProbeApi.getPage({ pageNum: 1, pageSize: 1000 })
    ])

    const allProbes = []
    if (systemResult.code === 200) {
      const systemRecords = systemResult.data.records || []
      allProbes.push(...systemRecords)
    }
    if (fileResult.code === 200) {
      const fileRecords = fileResult.data.records || []
      allProbes.push(...fileRecords)
    }

    // 只保留 SYSTEM 类型的探针用于显示
    const systemProbes = allProbes.filter(p => (p.type || 'SYSTEM') === 'SYSTEM')

    probeList.value = systemProbes
    allProbeList.value = allProbes

    // 统计数据基于所有探针
    stats.total = allProbes.length
    stats.online = allProbes.filter(p => p.status?.toLowerCase() === 'online').length
    stats.offline = allProbes.filter(p => p.status?.toLowerCase() === 'offline').length
    stats.error = allProbes.filter(p => p.status?.toLowerCase() === 'error').length

    // 先结束 loading，让用户立即看到探针列表
    loading.value = false

    // 在后台异步加载详细指标数据，不阻塞界面显示
    fetchMetricsData().then(() => {
      lastUpdateTime.value = new Date()

      // 记录数据获取时间
      const endTime = performance.now()
      performanceMetrics.dataFetchTime = Math.round(endTime - startTime)
      updatePerformanceMetrics()
    }).catch(error => {
      console.error('加载探针指标失败:', error)
      // 不显示错误消息，因为用户已经能看到探针列表了
    })
  } catch (error) {
    console.error('获取探针数据失败:', error)
    ElMessage.error('获取探针数据失败')
    loading.value = false
  }
}

const fetchMetricsData = async () => {
  metricsLoading.value = true
  const onlineProbes = probeList.value.filter(p => {
    const status = (p.status || '').toLowerCase()
    return status === 'online'
  })

  // 清理过期缓存
  clearExpiredCache()

  // 先应用缓存数据，立即显示
  onlineProbes.forEach(probe => {
    const cached = getCachedMetrics(probe.id)
    if (cached) {
      Object.assign(probe, cached)
    }
  })

  // 分批加载探针指标数据，每批最多3个探针，减少并发压力
  const BATCH_SIZE = 3
  for (let i = 0; i < onlineProbes.length; i += BATCH_SIZE) {
    const batch = onlineProbes.slice(i, i + BATCH_SIZE)

    const metricsPromises = batch.map(async (probe) => {
      try {
        const response = await getProbeMetricsSummary(probe.id)
        if (response.code === 200 && response.data) {
          const data = response.data

          // CPU
          probe.cpuUsage = data.cpuUsage || 0
          probe.cpuCores = data.cpuCores || null
          probe.cpuLoad1min = data.cpuLoad1min || null
          probe.cpuLoad5min = data.cpuLoad5min || null
          probe.cpuLoad15min = data.cpuLoad15min || null

          // Memory
          probe.memoryUsage = data.memoryUsage || null
          probe.memoryTotal = data.memoryTotal || null
          probe.memoryAvailable = data.memoryAvailable || null
          probe.memoryUsed = data.memoryUsed || null

          // Disk
          probe.diskUsage = data.diskUsage || null
          probe.diskUsed = data.diskUsed || null
          probe.diskTotal = data.diskTotal || null

          // Network
          probe.networkIn = data.networkRxRate || 0
          probe.networkOut = data.networkTxRate || 0
          probe.networkRxBytes = data.networkRxBytes || null
          probe.networkTxBytes = data.networkTxBytes || null
          probe.networkRxErrors = data.networkRxErrors || 0
          probe.networkTxErrors = data.networkTxErrors || 0

          // JVM
          probe.jvmHeapUsed = data.jvmHeapUsed || null
          probe.jvmHeapMax = data.jvmHeapMax || null
          probe.jvmHeapUsage = data.jvmHeapUsage || null
          probe.jvmThreadCount = data.jvmThreadCount || null
          probe.jvmThreadPeak = data.jvmThreadPeak || null
          probe.jvmClassLoaded = data.jvmClassLoaded || null
          probe.jvmTotalMemory = data.jvmTotalMemory || null
          probe.jvmFreeMemory = data.jvmFreeMemory || null

          // OS
          probe.osProcessCount = data.osProcessCount || null
          probe.osThreadCount = data.osThreadCount || null
          probe.osUptimeSeconds = data.osUptimeSeconds || null

          // Process
          probe.processCpuUsage = data.processCpuUsage || null
          probe.processMemoryResident = data.processMemoryResident || null
          probe.processJvmUptime = data.processJvmUptime || null
          probe.processId = data.processId || null

          // 存入缓存
          setCachedMetrics(probe.id, {
            cpuUsage: probe.cpuUsage,
            cpuCores: probe.cpuCores,
            cpuLoad1min: probe.cpuLoad1min,
            cpuLoad5min: probe.cpuLoad5min,
            cpuLoad15min: probe.cpuLoad15min,
            memoryUsage: probe.memoryUsage,
            memoryTotal: probe.memoryTotal,
            memoryAvailable: probe.memoryAvailable,
            memoryUsed: probe.memoryUsed,
            diskUsage: probe.diskUsage,
            diskUsed: probe.diskUsed,
            diskTotal: probe.diskTotal,
            networkIn: probe.networkIn,
            networkOut: probe.networkOut,
            networkRxBytes: probe.networkRxBytes,
            networkTxBytes: probe.networkTxBytes,
            networkRxErrors: probe.networkRxErrors,
            networkTxErrors: probe.networkTxErrors,
            jvmHeapUsed: probe.jvmHeapUsed,
            jvmHeapMax: probe.jvmHeapMax,
            jvmHeapUsage: probe.jvmHeapUsage,
            jvmThreadCount: probe.jvmThreadCount,
            jvmThreadPeak: probe.jvmThreadPeak,
            jvmClassLoaded: probe.jvmClassLoaded,
            jvmTotalMemory: probe.jvmTotalMemory,
            jvmFreeMemory: probe.jvmFreeMemory,
            osProcessCount: probe.osProcessCount,
            osThreadCount: probe.osThreadCount,
            osUptimeSeconds: probe.osUptimeSeconds,
            processCpuUsage: probe.processCpuUsage,
            processMemoryResident: probe.processMemoryResident,
            processJvmUptime: probe.processJvmUptime,
            processId: probe.processId
          })
        } else {
          resetProbeMetrics(probe)
        }
      } catch (error) {
        console.warn(`获取探针 ${probe.name} 的监控数据失败:`, error)
        resetProbeMetrics(probe)
      }
    })

    // 使用 Promise.allSettled 等待当前批次完成
    await Promise.allSettled(metricsPromises)

    // 每批之间稍微延迟，减轻服务器压力
    if (i + BATCH_SIZE < onlineProbes.length) {
      await new Promise(resolve => setTimeout(resolve, 100))
    }
  }

  probeList.value.forEach(probe => {
    const status = (probe.status || '').toLowerCase()
    if (status !== 'online') {
      resetProbeMetrics(probe)
    }
  })

  calculateAlerts()

  // 更新缓存命中率
  const total = cacheHits + cacheMisses
  performanceMetrics.cacheHitRate = total > 0 ? Math.round((cacheHits / total) * 100) : 0

  metricsLoading.value = false
}

const resetProbeMetrics = (probe) => {
  probe.cpuUsage = 0
  probe.cpuCores = null
  probe.cpuLoad1min = null
  probe.cpuLoad5min = null
  probe.cpuLoad15min = null
  probe.memoryUsage = 0
  probe.memoryTotal = 0
  probe.memoryAvailable = 0
  probe.memoryUsed = 0
  probe.diskUsage = 0
  probe.diskUsed = 0
  probe.diskTotal = 0
  probe.networkIn = 0
  probe.networkOut = 0
  probe.networkRxBytes = 0
  probe.networkTxBytes = 0
  probe.networkRxErrors = 0
  probe.networkTxErrors = 0
  probe.jvmHeapUsed = null
  probe.jvmHeapMax = null
  probe.jvmHeapUsage = null
  probe.jvmThreadCount = null
  probe.jvmThreadPeak = null
  probe.jvmClassLoaded = null
  probe.jvmTotalMemory = null
  probe.jvmFreeMemory = null
  probe.osProcessCount = null
  probe.osThreadCount = null
  probe.osUptimeSeconds = null
  probe.processCpuUsage = null
  probe.processMemoryResident = null
  probe.processJvmUptime = null
  probe.processId = null
}

const calculateAlerts = () => {
  alerts.disk = 0
  alerts.memory = 0
  alerts.cpu = 0
  alerts.network = 0

  // 统计所有在线探针的告警（包括系统探针和文件探针）
  allProbeList.value.forEach(probe => {
    const status = (probe.status || '').toLowerCase()
    if (status !== 'online') return

    if ((probe.diskUsage || 0) > 80) alerts.disk++
    if ((probe.memoryUsage || 0) > 85) alerts.memory++
    if ((probe.cpuUsage || 0) > 90) alerts.cpu++
    if ((probe.networkRxErrors || 0) > 0 || (probe.networkTxErrors || 0) > 0) alerts.network++
  })
}

onMounted(async () => {
  // 加载持久化设置
  loadSettings()

  // 启动性能监控
  performanceTimer = setInterval(updatePerformanceMetrics, 2000)

  const renderStart = performance.now()
  await fetchData()
  await nextTick()
  const renderEnd = performance.now()

  performanceMetrics.renderTime = Math.round(renderEnd - renderStart)
  updatePerformanceMetrics()

  // 如果启用了自动刷新，启动定时器
  if (autoRefresh.value) {
    startAutoRefresh()
  }

  // 初始化WebSocket连接
  if (useWebSocket.value) {
    initWebSocket()
  }
})

onUnmounted(() => {
  stopAutoRefresh()

  // 停止性能监控
  if (performanceTimer) {
    clearInterval(performanceTimer)
  }

  // 断开WebSocket
  if (useWebSocket.value) {
    metricsWebSocket.disconnect()
  }
})

// ========== WebSocket 管理 ==========
const initWebSocket = () => {
  // 从localStorage获取token
  const token = localStorage.getItem('token')
  if (!token) {
    console.warn('No token found, skipping WebSocket connection')
    return
  }

  // 监听连接状态
  metricsWebSocket.on('connected', () => {
    webSocketStatus.value = 'connected'
    console.log('WebSocket已连接')

    // 订阅所有在线探针的指标
    const onlineProbes = probeList.value.filter(p => p.status?.toLowerCase() === 'online')
    if (onlineProbes.length > 0) {
      const probeIds = onlineProbes.map(p => p.id)
      metricsWebSocket.subscribeProbes(probeIds)
    }
  })

  // 监听指标更新
  metricsWebSocket.on('metrics', (data) => {
    if (data && data.probeId) {
      updateProbeMetrics(data.probeId, data.metrics)
    }
  })

  // 监听状态更新
  metricsWebSocket.on('status', (data) => {
    if (data && data.probeId) {
      updateProbeStatus(data.probeId, data.status)
    }
  })

  // 监听告警
  metricsWebSocket.on('alert', (data) => {
    if (data) {
      handleAlert(data)
    }
  })

  // 监听断开连接
  metricsWebSocket.on('disconnected', () => {
    webSocketStatus.value = 'disconnected'
    console.log('WebSocket已断开')
  })

  // 监听错误
  metricsWebSocket.on('error', (data) => {
    webSocketStatus.value = 'error'
    console.error('WebSocket错误:', data.message)
  })

  // 连接WebSocket
  metricsWebSocket.connect(token)
}

const updateProbeMetrics = (probeId, metrics) => {
  const probe = probeList.value.find(p => p.id === probeId)
  if (probe) {
    // 更新探针指标
    Object.assign(probe, metrics)

    // 更新缓存
    setCachedMetrics(probeId, metrics)
  }
}

const updateProbeStatus = (probeId, status) => {
  const probe = probeList.value.find(p => p.id === probeId)
  if (probe) {
    probe.status = status

    // 更新统计（基于所有探针）
    stats.online = allProbeList.value.filter(p => p.status?.toLowerCase() === 'online').length
    stats.offline = allProbeList.value.filter(p => p.status?.toLowerCase() === 'offline').length
    stats.error = allProbeList.value.filter(p => p.status?.toLowerCase() === 'error').length
  }
}

const handleAlert = (alert) => {
  // 显示告警通知
  ElMessage.warning({
    message: `告警: ${alert.message}`,
    duration: 5000,
    showClose: true
  })
}
</script>

<style scoped>
/* ========================================
   DESIGN TOKENS - 设计变量
   ======================================== */
.monitor-dashboard-v2 {
  /* 间距系统 - 8px基准网格 */
  --spacing-xs: 4px;
  --spacing-sm: 8px;
  --spacing-md: 16px;
  --spacing-lg: 24px;
  --spacing-xl: 32px;
  --spacing-2xl: 48px;

  /* 圆角 */
  --radius-sm: 6px;
  --radius-md: 8px;
  --radius-lg: 12px;
  --radius-xl: 16px;

  /* 阴影系统 */
  --shadow-sm: 0 1px 2px rgba(0, 0, 0, 0.05);
  --shadow-md: 0 2px 8px rgba(0, 0, 0, 0.08);
  --shadow-lg: 0 8px 16px rgba(0, 0, 0, 0.1);
  --shadow-xl: 0 12px 24px rgba(0, 0, 0, 0.12);

  /* 色彩系统 */
  --color-primary: #3B82F6;
  --color-primary-dark: #2563EB;
  --color-success: #10B981;
  --color-warning: #F59E0B;
  --color-danger: #EF4444;

  padding: var(--spacing-lg);
  width: 100%;
  background: #F8FAFC;
  min-height: 100vh;
  font-family: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
  line-height: 1.5;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
}

/* ========================================
   Header - 页眉
   ======================================== */
.dashboard-header-v2 {
  margin-bottom: var(--spacing-xl);
}

.dashboard-header-v2 {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: var(--spacing-lg);
  padding: 18px 24px;
  background: #ffffff;
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-md);
  border: 1px solid #E2E8F0;
}

.header-left .page-title {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  margin: 0;
  font-size: 20px;
  font-weight: 700;
  color: #0f172a;
  letter-spacing: -0.01em;
  line-height: 1.2;
}

.header-left .title-icon {
  font-size: 22px;
  color: #3B82F6;
}

.header-left .page-subtitle {
  margin: 4px 0 0 0;
  font-size: 13px;
  color: #94a3b8;
  font-weight: 400;
}

.header-left .last-update {
  display: inline-flex;
  align-items: center;
  gap: var(--spacing-xs);
  margin-top: 8px;
  padding: var(--spacing-xs) var(--spacing-sm);
  font-size: 12px;
  color: #94a3b8;
  background: #f8fafc;
  border-radius: var(--radius-md);
  border: 1px solid #e2e8f0;
  font-weight: 500;
}

.header-actions {
  display: flex;
  gap: 16px;
  align-items: center;
}

/* 自动刷新控制卡片 */
.refresh-control-card {
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 14px 20px;
  background: #f8fafc;
  border-radius: 10px;
  border: 1px solid #e2e8f0;
  transition: all 0.2s ease;
  cursor: default;
}

.refresh-control-card:hover {
  border-color: #cbd5e1;
}

/* 切换按钮 */
.refresh-toggle-section {
  display: flex;
  align-items: center;
}

.refresh-toggle-btn {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 20px;
  background: #F8FAFC;
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
  border: 2px solid #E2E8F0;
  min-width: 190px;
  user-select: none;
}

.refresh-toggle-btn:hover {
  background: #F1F5F9;
  border-color: var(--color-primary);
  box-shadow: 0 4px 12px rgba(59, 130, 246, 0.15);
  transform: translateY(-2px);
}

.refresh-toggle-btn:active {
  transform: scale(0.98);
}

.refresh-toggle-btn.active {
  background: linear-gradient(135deg, #10B981 0%, #34D399 100%);
  border-color: transparent;
  box-shadow: 0 6px 20px rgba(16, 185, 129, 0.35);
}

.toggle-icon {
  width: 38px;
  height: 38px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #EFF6FF;
  color: var(--color-primary);
  font-size: 18px;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  box-shadow: 0 2px 4px rgba(59, 130, 246, 0.1);
}

.refresh-toggle-btn.active .toggle-icon {
  background: rgba(255, 255, 255, 0.25);
  color: #ffffff;
  box-shadow: 0 2px 8px rgba(255, 255, 255, 0.2);
}

.toggle-text {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.toggle-label {
  font-size: 14px;
  font-weight: 600;
  color: #1E293B;
  letter-spacing: -0.01em;
}

.toggle-status {
  font-size: 12px;
  color: #64748B;
  font-weight: 500;
}

.refresh-toggle-btn.active .toggle-label {
  color: #ffffff;
}

.refresh-toggle-btn.active .toggle-status {
  color: rgba(255, 255, 255, 0.95);
}

/* 刷新间隔选择区域 */
.refresh-interval-section {
  display: flex;
  flex-direction: column;
  padding-left: 20px;
  border-left: 2px solid #E2E8F0;
}

.interval-chips {
  display: flex;
  gap: 10px;
}

.interval-label {
  color: #475569;
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 8px;
  letter-spacing: -0.01em;
}

.interval-chip {
  padding: 8px 16px;
  background: #F8FAFC;
  border: 2px solid #E2E8F0;
  border-radius: 10px;
  color: #1E293B;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
  text-align: center;
  min-width: 56px;
  letter-spacing: -0.01em;
}

.interval-chip:hover {
  background: #EFF6FF;
  border-color: var(--color-primary);
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(59, 130, 246, 0.15);
}

.interval-chip.active {
  background: #3b82f6;
  border-color: #3b82f6;
  color: #ffffff;
  box-shadow: 0 2px 8px rgba(37, 99, 235, 0.3);
}

.interval-chip.disabled {
  opacity: 0.35;
  cursor: not-allowed;
}

.interval-chip.disabled:hover {
  transform: none;
  background: #F8FAFC;
  box-shadow: none;
}

/* 手动刷新按钮 */
.manual-refresh-btn {
  font-weight: 600;
  padding: 10px 24px;
  border-radius: 8px;
}

.manual-refresh-btn:active {
  transform: translateY(0);
  box-shadow: 0 2px 8px rgba(37, 99, 235, 0.3);
}

.manual-refresh-btn .el-icon {
  font-size: 16px;
}

/* ========================================
   Overview Section - 概览区域
   ======================================== */
.overview-section {
  margin-bottom: var(--spacing-xl);
}

.stats-container {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-lg);
}

.stat-cards-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: var(--spacing-lg);
}

.stat-card {
  display: flex;
  align-items: center;
  gap: var(--spacing-lg);
  padding: 20px;
  background: #ffffff;
  border: 1px solid #E2E8F0;
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-md);
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  cursor: default;
}

.stat-card:hover {
  transform: translateY(-4px);
  box-shadow: var(--shadow-lg);
  border-color: var(--color-primary);
}

.stat-card .stat-icon {
  width: 56px;
  height: 56px;
  border-radius: var(--radius-lg);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  color: #ffffff;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

.stat-total .stat-icon {
  background: #3b82f6;
}

.stat-online .stat-icon {
  background: #10b981;
}

.stat-offline .stat-icon {
  background: #f59e0b;
}

.stat-alerts .stat-icon {
  background: #ef4444;
}

.stat-card .stat-content {
  flex: 1;
}

.stat-card .stat-value {
  font-size: 32px;
  font-weight: 700;
  color: #1E293B;
  line-height: 1.1;
  margin-bottom: var(--spacing-xs);
  letter-spacing: -0.03em;
}

.stat-card .stat-label {
  font-size: 13px;
  font-weight: 600;
  color: #64748B;
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

/* ========================================
   Alert Summary - 告警摘要
   ======================================== */
.alert-summary {
  display: flex;
  gap: var(--spacing-md);
  padding: 20px;
  background: #ffffff;
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-md);
  border: 1px solid #E2E8F0;
}

.alert-item {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  padding: 10px var(--spacing-md);
  border-radius: var(--radius-lg);
  background: #F8FAFC;
  border: 2px solid #E2E8F0;
  font-size: 14px;
  font-weight: 600;
  color: #64748B;
  cursor: pointer;
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
  user-select: none;
}

.alert-item:hover {
  background: #EFF6FF;
  border-color: var(--color-primary);
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(59, 130, 246, 0.15);
}

.alert-item:active {
  transform: translateY(0);
}

.alert-item.has-alert {
  background: #FEF2F2;
  color: var(--color-danger);
  border-color: #FECACA;
  font-weight: 700;
  box-shadow: 0 2px 8px rgba(239, 68, 68, 0.1);
}

.alert-item.has-alert:hover {
  background: #FEE2E2;
  border-color: var(--color-danger);
  box-shadow: 0 6px 16px rgba(239, 68, 68, 0.2);
}

/* AGENT Switcher Section */
.agent-switcher-section {
  padding: 20px;
  background: #ffffff;
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-md);
  border: 1px solid #E2E8F0;
}

.agent-switcher-title {
  font-size: 14px;
  font-weight: 700;
  color: #1E293B;
  margin-bottom: 16px;
  letter-spacing: -0.01em;
  display: flex;
  align-items: center;
  gap: 8px;
}

.agent-switcher-title::before {
  content: '';
  width: 4px;
  height: 16px;
  background: linear-gradient(135deg, var(--color-primary-dark) 0%, var(--color-primary) 100%);
  border-radius: 2px;
}

.agent-switcher {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.agent-switcher .agent-btn {
  flex: 1;
  min-width: 120px;
  background: #f1f5f9;
  border: 1px solid #e2e8f0;
  color: #475569;
  transition: all 0.2s ease;
  font-size: 13px;
  font-weight: 500;
  border-radius: 8px;
  padding: 10px 14px;
}

.agent-switcher .agent-btn:hover:not(:disabled) {
  background: #e2e8f0;
  border-color: #cbd5e1;
}

.agent-switcher .agent-btn.el-button--primary {
  background: #3b82f6;
  border-color: #3b82f6;
  color: #ffffff;
  box-shadow: 0 2px 8px rgba(37, 99, 235, 0.3);
}

.agent-switcher .agent-btn.el-button--primary:hover {
  background: #2563eb;
  border-color: #2563eb;
}

/* Filter Bar */
.filter-bar {
  margin-bottom: 16px;
}

/* ========================================
   Probe Cards - 探针卡片
   ======================================== */
.probe-cards-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: var(--spacing-lg);
}

.probe-card {
  background: #ffffff;
  border: 1px solid #E2E8F0;
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-md);
  overflow: hidden;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  cursor: pointer;
  position: relative;
}

.probe-card:hover {
  box-shadow: var(--shadow-xl);
  transform: translateY(-6px);
  border-color: var(--color-primary);
}

.probe-card:active {
  transform: translateY(-3px);
}

.probe-card.probe-offline {
  opacity: 0.6;
  cursor: not-allowed;
}

.probe-card.probe-offline:hover {
  transform: none;
  box-shadow: var(--shadow-sm);
}

.probe-card.has-alerts {
  border-color: var(--color-danger);
  border-width: 2px;
}

.probe-card.has-alerts:hover {
  border-color: #DC2626;
  box-shadow: 0 16px 40px rgba(239, 68, 68, 0.15);
}

/* Probe Header */
.probe-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 24px;
  border-bottom: 1px solid #e2e8f0;
}

.probe-info {
  display: flex;
  align-items: center;
  gap: 16px;
}

.probe-status {
  width: 14px;
  height: 14px;
  border-radius: 50%;
  flex-shrink: 0;
}

.probe-status.status-online {
  background: var(--color-success);
  box-shadow: 0 0 12px rgba(16, 185, 129, 0.5);
}

.probe-status.status-offline {
  background: #94a3b8;
}

.probe-status.status-error {
  background: var(--color-danger);
  box-shadow: 0 0 12px rgba(239, 68, 68, 0.5);
}

.probe-name {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  color: #1E293B;
  letter-spacing: -0.02em;
}

.probe-key {
  margin: 4px 0 0 0;
  font-size: 13px;
  color: #64748B;
  font-family: 'JetBrains Mono', 'Courier New', monospace;
  font-weight: 500;
}

/* Critical Metrics */
.metrics-critical {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
  padding: 24px;
  background: #f8fafc;
  border-bottom: 1px solid #e2e8f0;
}

.metric-mini {
  text-align: center;
}

.metric-mini .metric-label {
  font-size: 12px;
  color: #64748B;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin-bottom: 8px;
}

.metric-mini .metric-value {
  font-size: 28px;
  font-weight: 700;
  color: #1E293B;
  margin-bottom: 10px;
  letter-spacing: -0.02em;
}

.metric-mini .metric-bar {
  width: 100%;
  height: 8px;
  background: #e2e8f0;
  border-radius: 4px;
  overflow: hidden;
  box-shadow: inset 0 1px 2px rgba(0, 0, 0, 0.05);
}

.metric-mini .metric-bar-fill {
  height: 100%;
  background: linear-gradient(90deg, var(--color-success) 0%, #34D399 100%);
  border-radius: 4px;
  transition: width 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.metric-mini.metric-warning .metric-bar-fill {
  background: linear-gradient(90deg, var(--color-warning) 0%, #FBBF24 100%);
}

.metric-mini.metric-critical .metric-bar-fill {
  background: linear-gradient(90deg, var(--color-danger) 0%, #F87171 100%);
}

.metric-mini.metric-critical .metric-value {
  color: #DC2626;
}

.metric-mini .metric-value-small {
  font-size: 13px;
  color: #1E293B;
  line-height: 1.5;
  font-weight: 500;
}

.metric-mini .metric-value-small div {
  margin: 3px 0;
}

/* Metrics Sections */
.metrics-section {
  padding: 20px 24px;
  border-bottom: 1px solid #e2e8f0;
}

.metrics-section:last-child {
  border-bottom: none;
}

.section-title {
  font-size: 13px;
  font-weight: 700;
  color: #64748B;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin-bottom: 16px;
}

.metrics-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
}

.metric-detail {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  background: #f8fafc;
  border-radius: 10px;
  font-size: 14px;
  transition: all 0.2s ease;
}

.metric-detail:hover {
  background: #f1f5f9;
}

.metric-detail .metric-label {
  color: #64748B;
  font-weight: 500;
  flex-shrink: 0;
}

.metric-detail .metric-value {
  color: #1E293B;
  font-weight: 700;
  text-align: right;
}

.metric-detail .metric-value .metric-sub {
  font-size: 11px;
  color: #94a3b8;
  font-weight: 400;
  margin-left: 4px;
}

.metric-detail .metric-value.error {
  color: #dc2626;
}

/* Probe Footer */
.probe-footer {
  padding: 12px 20px;
  background: #f8fafc;
  border-top: 1px solid #e2e8f0;
}

.last-heartbeat {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #94a3b8;
}

/* Empty State */
.empty-state {
  text-align: center;
  padding: 80px 20px;
  color: #94a3b8;
}

.empty-state .empty-icon {
  font-size: 64px;
  margin-bottom: 16px;
}

.empty-state p {
  font-size: 16px;
  margin: 0;
}

/* Performance Metrics Bar */
.performance-metrics-bar {
  display: flex;
  gap: 16px;
  padding: 16px 20px;
  margin-bottom: 20px;
  background: linear-gradient(135deg, #1e293b 0%, #334155 100%);
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  flex-wrap: wrap;
}

.metrics-loading-tip {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  background: rgba(59, 130, 246, 0.2);
  border-radius: 8px;
  color: #93c5fd;
  font-size: 13px;
  font-weight: 500;
  animation: pulse 2s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% {
    opacity: 1;
  }
  50% {
    opacity: 0.7;
  }
}

.perf-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  background: rgba(255, 255, 255, 0.1);
  border-radius: 8px;
  color: #e2e8f0;
  font-size: 13px;
  font-weight: 500;
  backdrop-filter: blur(10px);
  transition: all 0.2s ease;
}

.perf-item:hover {
  background: rgba(255, 255, 255, 0.15);
  transform: translateY(-1px);
}

.perf-item.active {
  background: rgba(16, 185, 129, 0.2);
  color: #34d399;
  border: 1px solid rgba(16, 185, 129, 0.3);
}

.perf-item.error {
  background: rgba(239, 68, 68, 0.2);
  color: #f87171;
  border: 1px solid rgba(239, 68, 68, 0.3);
}

.perf-item .el-icon {
  font-size: 16px;
}

/* Virtual Scroller */
.probe-cards-virtual {
  height: calc(100vh - 350px);
  min-height: 500px;
}

.probe-cards-virtual .scroller {
  height: 100%;
  overflow-y: auto;
}

.probe-card-wrapper {
  padding: 0;
  margin: 0;
  box-sizing: border-box;
}

/* Responsive Design */
@media (min-width: 1920px) {
  .probe-cards-grid {
    grid-template-columns: 1fr;
    gap: 20px;
  }
}

@media (max-width: 1440px) {
  .probe-cards-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 1024px) {
  .monitor-dashboard-v2 {
    padding: 16px;
  }

  .dashboard-header-v2 {
    flex-direction: column;
    gap: 16px;
    padding: 20px;
  }

  .header-actions {
    width: 100%;
    justify-content: space-between;
  }

  .probe-cards-grid {
    grid-template-columns: 1fr;
  }

  .metrics-critical {
    grid-template-columns: repeat(2, 1fr);
  }

  .stat-cards-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .alert-summary {
    flex-wrap: wrap;
  }

  .agent-switcher {
    justify-content: flex-start;
  }
}

@media (max-width: 768px) {
  .stat-cards-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .alert-summary {
    flex-wrap: wrap;
  }

  .agent-switcher {
    gap: 6px;
  }

  .agent-switcher .agent-btn {
    min-width: 80px;
    font-size: 12px;
    padding: 8px 12px;
  }

  .metrics-grid {
    grid-template-columns: 1fr;
  }

  .metrics-critical {
    grid-template-columns: repeat(2, 1fr);
    gap: 12px;
  }
}

@media (max-width: 480px) {
  .stat-cards-grid {
    grid-template-columns: 1fr;
  }

  .agent-switcher {
    display: grid;
    grid-template-columns: repeat(2, 1fr);
    gap: 8px;
  }

  .agent-switcher .agent-btn {
    width: 100%;
    min-width: auto;
  }

  .probe-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }

  .metrics-critical {
    grid-template-columns: 1fr 1fr;
  }
}

/* 响应式优化 - 平板 */
@media (max-width: 1024px) {
  .dashboard-header-v2 {
    flex-direction: column;
    gap: 20px;
  }

  .header-actions {
    width: 100%;
    justify-content: space-between;
  }

  .refresh-control-card {
    flex-wrap: wrap;
    padding: 12px 16px;
  }

  .probe-cards-grid {
    grid-template-columns: 1fr;
  }
}

/* 响应式优化 - 手机 */
@media (max-width: 768px) {
  .monitor-dashboard-v2 {
    padding: 16px;
  }

  .dashboard-header-v2 {
    padding: 20px;
    margin-bottom: var(--spacing-lg);
  }

  .header-content {
    flex-direction: column;
    gap: var(--spacing-md);
  }

  .header-actions {
    width: 100%;
    flex-wrap: wrap;
  }

  .header-left .page-title {
    font-size: 20px;
  }

  .title-icon {
    font-size: 24px !important;
  }

  .stat-cards-grid {
    grid-template-columns: repeat(2, 1fr);
    gap: var(--spacing-sm);
  }

  .stat-card {
    padding: var(--spacing-sm);
  }

  .stat-card .stat-value {
    font-size: 24px;
  }

  .alert-summary {
    flex-wrap: wrap;
  }

  .probe-cards-grid {
    grid-template-columns: 1fr;
    gap: var(--spacing-md);
  }
}
</style>
