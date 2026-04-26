<template>
  <div class="stats-page">
    <!-- 顶栏 -->
    <header class="stats-header">
      <div class="header-left">
        <h1 class="page-title">
          <el-icon><DataAnalysis /></el-icon>
          数据统计
        </h1>
        <span class="page-desc">实时监控探针性能与资源使用</span>
      </div>
      <div class="header-right">
        <div class="agent-selector" v-if="systemProbes.length > 0">
          <el-select
            v-model="selectedSystemProbeKey"
            placeholder="选择Agent"
            @change="onSystemProbeChange"
            size="default"
            style="width: 200px"
          >
            <el-option
              v-for="probe in systemProbes"
              :key="probe.probeKey"
              :label="probe.name"
              :value="probe.probeKey"
            >
              <div class="agent-option">
                <span class="dot" :class="probe.status"></span>
                <span class="agent-name">{{ probe.name }}</span>
                <span class="agent-status-text">{{ probe.status === 'online' ? '在线' : '离线' }}</span>
              </div>
            </el-option>
          </el-select>
        </div>
        <el-button type="primary" :icon="Refresh" @click="refreshAll" :loading="loading" plain size="default">
          刷新
        </el-button>
        <span class="last-update" v-if="lastUpdateTime">
          {{ formatTime(lastUpdateTime) }}
        </span>
      </div>
    </header>

    <!-- 指标概览 -->
    <section class="metrics-row">
      <div class="metric-card">
        <div class="metric-icon metric-icon--total">
          <el-icon><Monitor /></el-icon>
        </div>
        <div class="metric-body">
          <span class="metric-value">{{ overview.totalProbes }}</span>
          <span class="metric-label">探针总数</span>
        </div>
      </div>
      <div class="metric-card">
        <div class="metric-icon metric-icon--online">
          <el-icon><CircleCheck /></el-icon>
        </div>
        <div class="metric-body">
          <span class="metric-value metric-value--green">{{ overview.onlineProbes }}</span>
          <span class="metric-label">在线</span>
        </div>
      </div>
      <div class="metric-card">
        <div class="metric-icon metric-icon--healthy">
          <el-icon><SuccessFilled /></el-icon>
        </div>
        <div class="metric-body">
          <span class="metric-value metric-value--emerald">{{ overview.healthyProbes || 0 }}</span>
          <span class="metric-label">健康</span>
        </div>
      </div>
      <div class="metric-card">
        <div class="metric-icon metric-icon--warn">
          <el-icon><Warning /></el-icon>
        </div>
        <div class="metric-body">
          <span class="metric-value metric-value--amber">{{ overview.warningCount || 0 }}</span>
          <span class="metric-label">告警</span>
        </div>
      </div>
      <div class="metric-card">
        <div class="metric-icon metric-icon--off">
          <el-icon><CircleClose /></el-icon>
        </div>
        <div class="metric-body">
          <span class="metric-value metric-value--red">{{ overview.offlineProbes || 0 }}</span>
          <span class="metric-label">离线</span>
        </div>
      </div>
      <div class="metric-card">
        <div class="metric-icon metric-icon--rate">
          <el-icon><DataLine /></el-icon>
        </div>
        <div class="metric-body">
          <span class="metric-value">{{ overview.healthRate }}%</span>
          <span class="metric-label">健康率</span>
        </div>
      </div>
    </section>

    <!-- 主内容区 -->
    <section class="main-grid">
      <!-- 左侧：Agent 状态 + 告警 -->
      <aside class="sidebar">
        <div class="sidebar-card agent-card">
          <div class="sidebar-card__head">
            <span class="sidebar-card__title">
              <el-icon><Monitor /></el-icon>
              Agent 状态
            </span>
            <span v-if="selectedProbeName" class="sidebar-card__sub">{{ selectedProbeName }}</span>
          </div>
          <div class="agent-body">
            <div class="agent-badge" :class="agentStatus.class">
              <el-icon class="agent-badge__icon"><component :is="agentStatus.icon" /></el-icon>
              <span class="agent-badge__text">{{ agentStatus.text }}</span>
            </div>
            <div class="agent-meta">
              <div class="agent-meta__row">
                <span class="agent-meta__label">运行时长</span>
                <span class="agent-meta__value">{{ agentStatus.uptime }}</span>
              </div>
              <div class="agent-meta__row">
                <span class="agent-meta__label">最后更新</span>
                <span class="agent-meta__value">{{ formatTime(lastUpdateTime) }}</span>
              </div>
            </div>
          </div>
        </div>

        <div class="sidebar-card alert-card">
          <div class="sidebar-card__head">
            <span class="sidebar-card__title">
              <el-icon><Bell /></el-icon>
              告警统计
            </span>
          </div>
          <div ref="alertTypeChartRef" class="alert-chart-wrap"></div>
        </div>
      </aside>

      <!-- 右侧：趋势图 -->
      <div class="trend-panel">
        <div class="trend-panel__head">
          <span class="trend-panel__title">
            <el-icon><TrendCharts /></el-icon>
            实时资源趋势
            <span class="trend-panel__hint">最近 1 小时</span>
          </span>
          <el-radio-group v-model="trendMetric" size="small" @change="updateTrendChart">
            <el-radio-button value="cpu">CPU</el-radio-button>
            <el-radio-button value="memory">内存</el-radio-button>
            <el-radio-button value="disk">磁盘</el-radio-button>
            <el-radio-button value="network">网络</el-radio-button>
          </el-radio-group>
        </div>
        <div ref="trendChartRef" class="trend-chart-wrap"></div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'

// 组件挂载状态标志
const isMounted = ref(true)
import {
  DataAnalysis, Monitor, CircleCheck, Warning, CircleClose,
  TrendCharts, Bell, Refresh, SuccessFilled, DataLine
} from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import { probeApi } from '@/api/probe'
import dayjs from 'dayjs'

const loading = ref(false)
const lastUpdateTime = ref(null)
const trendMetric = ref('cpu')

// 概览数据
const overview = reactive({
  totalProbes: 0,
  onlineProbes: 0,
  offlineProbes: 0,
  healthyProbes: 0,
  warningCount: 0,
  healthRate: 0
})

// AGENT状态
const agentStatus = reactive({
  text: '未知',
  class: 'status-unknown',
  icon: 'Warning',
  uptime: '-'
})

// 图表实例
let trendChart = null
let alertTypeChart = null

// 图表引用
const trendChartRef = ref(null)
const alertTypeChartRef = ref(null)

// 探针数据
const allProbes = ref([])
const systemProbes = ref([]) // 系统探针列表
const selectedSystemProbeKey = ref('') // 当前选中的系统探针

// 当前选中的探针名称
const selectedProbeName = computed(() => {
  const selectedProbe = systemProbes.value.find(p => p.probeKey === selectedSystemProbeKey.value)
  return selectedProbe ? selectedProbe.name : ''
})

// 获取探针数据
const fetchAllProbes = async () => {
  try {
    loading.value = true

    // 获取所有系统探针列表（使用大pageSize获取所有探针）
    const res = await probeApi.getList({ pageNum: 1, pageSize: 1000 })

    if (res.code === 200 && res.data?.records) {
      const probes = res.data.records

      console.log('%c========== [数据统计] 探针数据加载 ==========', 'color: #409eff; font-weight: bold')
      console.log('%c总探针数:', 'color: #67c23a;', probes.length)
      console.log('%c所有探针:', 'color: #67c23a;', probes.map(p => ({ name: p.name, type: p.type, status: p.status })))

      // 存储探针数据供图表使用
      allProbes.value = probes

      // 过滤出系统探针（SYSTEM类型）
      systemProbes.value = probes.filter(p => p.type === 'SYSTEM')

      console.log('%c系统探针数:', 'color: #e6a23c;', systemProbes.value.length)
      console.log('%c系统探针列表:', 'color: #e6a23c;', systemProbes.value.map(p => ({ name: p.name, probeKey: p.probeKey, status: p.status })))

      // 为所有在线的探针获取指标数据
      await fetchMetricsForProbes(probes)

      // 如果还没有选中的系统探针，默认选择第一个在线的系统探针
      if (!selectedSystemProbeKey.value && systemProbes.value.length > 0) {
        const defaultProbe = systemProbes.value.find(p => p.status === 'online') || systemProbes.value[0]
        selectedSystemProbeKey.value = defaultProbe.probeKey
        console.log('%c默认选中的系统探针:', 'color: #f56c6c;', defaultProbe.name, '(', defaultProbe.probeKey, ')')
      }

      if (probes.length > 0) {
        // 使用当前选中的系统探针的数据用于显示详细指标
        const selectedProbe = systemProbes.value.find(p => p.probeKey === selectedSystemProbeKey.value)
        const displayProbe = selectedProbe || systemProbes.value[0]

        console.log('%c当前显示的探针:', 'color: #909399;', displayProbe ? displayProbe.name : '无')

        if (!displayProbe) {
          console.warn('%c⚠️ 没有找到可用的系统探针', 'color: #e6a23c;')
          loading.value = false
          return
        }

        const metrics = displayProbe.latestMetrics || {}

        console.log('%c探针指标数据:', 'color: #67c23a;', metrics)

        // 更新AGENT状态
        updateAgentStatus(displayProbe.status, metrics)

        // 更新概览数据（统计所有探针）
        updateAllProbesOverview(probes)

        // 更新图表
        updateAllCharts()
      } else {
        console.warn('%c⚠️ 没有探针数据', 'color: #e6a23c;')
      }
    }

    lastUpdateTime.value = new Date()
  } catch (error) {
    console.error('获取探针数据失败:', error)
    ElMessage.error('获取探针数据失败')
  } finally {
    loading.value = false
  }
}

// 为探针获取指标数据
const fetchMetricsForProbes = async (probes) => {
  console.log('%c========== [指标数据] 开始获取探针指标 ==========', 'color: #409eff; font-weight: bold')

  // 获取所有在线的探针
  const onlineProbes = probes.filter(p => p.status === 'online')
  console.log('%c在线探针数:', 'color: #67c23a;', onlineProbes.length)

  // 为每个在线的探针获取指标数据
  for (const probe of onlineProbes) {
    try {
      console.log('%c正在获取探针指标:', 'color: #67c23a;', probe.name, '(', probe.probeKey, ')')

      const res = await fetch(`/api/metrics/probe/${probe.id}/summary`, {
        headers: {
          'Authorization': `Bearer ${localStorage.getItem('token')}`
        }
      }).then(response => response.json())

      if (res.code === 200 && res.data) {
        // 将指标数据附加到探针对象上
        probe.latestMetrics = {
          cpuUsage: res.data.cpuUsage || 0,
          memoryUsage: res.data.memoryUsage || 0,
          memoryUsed: res.data.memoryUsed || 0,
          memoryTotal: res.data.memoryTotal || 0,
          diskUsage: res.data.diskUsage || 0,
          diskUsed: res.data.diskUsed || 0,
          diskTotal: res.data.diskTotal || 0,
          cpuCores: res.data.cpuCores || 0,
          osUptimeSeconds: res.data.osUptimeSeconds || 0,
          jvmHeapUsage: res.data.jvmHeapUsage,
          jvmHeapMax: res.data.jvmHeapMax,
          jvmThreadCount: res.data.jvmThreadCount,
          jvmClassLoaded: res.data.jvmClassLoaded,
          osProcessCount: res.data.osProcessCount,
          osThreadCount: res.data.osThreadCount,
          processId: res.data.processId
        }
        console.log('%c✓ 探针指标获取成功:', 'color: #67c23a;', probe.name, probe.latestMetrics)
      } else {
        console.warn('%c⚠️ 探针指标获取失败:', 'color: #e6a23c;', probe.name, res.message)
        probe.latestMetrics = {}
      }
    } catch (error) {
      console.error('%c✗ 探针指标获取异常:', 'color: #f56c6c;', probe.name, error)
      probe.latestMetrics = {}
    }
  }

  console.log('%c========== [指标数据] 所有探针指标获取完成 ==========', 'color: #409eff; font-weight: bold')
}

// 当选择的系统探针改变时
const onSystemProbeChange = () => {
  fetchAllProbes()
}

// 更新AGENT状态
const updateAgentStatus = (status, metrics) => {
  if (status === 'online') {
    const cpu = metrics.cpuUsage || 0
    const memory = metrics.memoryUsage || 0
    const disk = metrics.diskUsage || 0

    if (cpu >= 80 || memory >= 80 || disk >= 80) {
      agentStatus.text = '告警'
      agentStatus.class = 'status-warning'
      agentStatus.icon = 'Warning'
    } else {
      agentStatus.text = '在线'
      agentStatus.class = 'status-online'
      agentStatus.icon = 'CircleCheck'
    }

    agentStatus.uptime = metrics.osUptimeSeconds ? formatUptime(metrics.osUptimeSeconds) : '-'
  } else if (status === 'offline') {
    agentStatus.text = '离线'
    agentStatus.class = 'status-offline'
    agentStatus.icon = 'CircleClose'
    agentStatus.uptime = '-'
  } else {
    agentStatus.text = '未知'
    agentStatus.class = 'status-unknown'
    agentStatus.icon = 'Warning'
    agentStatus.uptime = '-'
  }
}

// 更新所有探针概览
const updateAllProbesOverview = (probes) => {
  overview.totalProbes = probes.length
  overview.onlineProbes = probes.filter(p => p.status === 'online').length
  overview.offlineProbes = probes.filter(p => p.status === 'offline').length

  // 统计健康和告警探针
  let healthyCount = 0
  let warningCount = 0

  probes.forEach(probe => {
    if (probe.status === 'online' && probe.latestMetrics) {
      const metrics = probe.latestMetrics
      const cpu = metrics.cpuUsage || 0
      const memory = metrics.memoryUsage || 0
      const disk = metrics.diskUsage || 0
      const isHealthy = cpu < 80 && memory < 80 && disk < 80

      if (isHealthy) {
        healthyCount++
      } else {
        warningCount++
      }
    }
  })

  overview.healthyProbes = healthyCount
  overview.warningCount = warningCount
  overview.healthRate = overview.onlineProbes > 0
    ? Math.round((healthyCount / overview.onlineProbes) * 100)
    : 0
}

// 更新资源使用率分布图（已删除）
// 更新探针健康状态图（已删除）

// 更新实时趋势图
const updateTrendChart = () => {
  if (!trendChart || !isMounted.value) {
    console.warn('%c⚠️ 趋势图表未初始化或组件已卸载', 'color: #e6a23c;')
    return
  }

  console.log('%c========== [趋势图] 更新趋势图 ==========', 'color: #409eff; font-weight: bold')
  console.log('%c当前指标:', 'color: #67c23a;', trendMetric.value)

  // 生成模拟趋势数据（实际应该从后端获取历史数据）
  const now = Date.now()
  const timestamps = []
  const avgValues = []
  const maxValues = []

  for (let i = 60; i >= 0; i--) {
    const time = new Date(now - i * 60000)
    timestamps.push(dayjs(time).format('HH:mm'))
    avgValues.push(Math.random() * 40 + 20)
    maxValues.push(Math.random() * 30 + 50)
  }

  const option = {
    tooltip: {
      trigger: 'axis',
      axisPointer: {
        type: 'cross'
      }
    },
    legend: {
      data: ['平均使用率', '峰值使用率'],
      top: 10
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '3%',
      top: '60px',
      containLabel: true
    },
    xAxis: {
      type: 'category',
      data: timestamps,
      boundaryGap: false,
      axisLabel: {
        rotate: 45,
        fontSize: 10
      }
    },
    yAxis: {
      type: 'value',
      name: '使用率 (%)',
      max: 100,
      axisLabel: {
        formatter: '{value}%'
      }
    },
    series: [
      {
        name: '平均使用率',
        type: 'line',
        data: avgValues,
        smooth: true,
        itemStyle: { color: '#3b82f6' },
        areaStyle: { opacity: 0.1 }
      },
      {
        name: '峰值使用率',
        type: 'line',
        data: maxValues,
        smooth: true,
        itemStyle: { color: '#f59e0b' },
        areaStyle: { opacity: 0.1 }
      }
    ]
  }

  trendChart.setOption(option, true)
}

// 更新告警类型统计图
const updateAlertTypeChart = () => {
  if (!alertTypeChart || !isMounted.value) {
    console.warn('%c⚠️ 告警图表未初始化或组件已卸载', 'color: #e6a23c;')
    return
  }

  console.log('%c========== [告警统计] 更新告警图表 ==========', 'color: #409eff; font-weight: bold')

  // 只统计当前选中的AGENT系统探针的告警
  const selectedProbe = systemProbes.value.find(p => p.probeKey === selectedSystemProbeKey.value)

  console.log('%c选中的探针:', 'color: #67c23a;', selectedProbe ? selectedProbe.name : '无')

  let cpuAlert = 0
  let memoryAlert = 0
  let diskAlert = 0

  if (selectedProbe && selectedProbe.status === 'online' && selectedProbe.latestMetrics) {
    // 只统计选中探针的资源使用情况
    const metrics = selectedProbe.latestMetrics
    console.log('%c探针指标:', 'color: #67c23a;', {
      cpu: metrics.cpuUsage,
      memory: metrics.memoryUsage,
      disk: metrics.diskUsage
    })

    cpuAlert = metrics.cpuUsage >= 80 ? 1 : 0
    memoryAlert = metrics.memoryUsage >= 80 ? 1 : 0
    diskAlert = metrics.diskUsage >= 80 ? 1 : 0

    console.log('%c告警统计:', 'color: #f56c6c;', {
      cpuAlert,
      memoryAlert,
      diskAlert
    })
  } else {
    console.warn('%c⚠️ 探针离线或无指标数据', 'color: #e6a23c;')
  }

  const data = [
    { name: 'CPU告警', value: cpuAlert, itemStyle: { color: '#ef4444' } },
    { name: '内存告警', value: memoryAlert, itemStyle: { color: '#f59e0b' } },
    { name: '磁盘告警', value: diskAlert, itemStyle: { color: '#3b82f6' } }
  ].filter(d => d.value > 0)

  const option = {
    tooltip: {
      trigger: 'item',
      formatter: '{b}: {c}'
    },
    series: [{
      type: 'pie',
      radius: '65%',
      center: ['50%', '50%'],
      data: data.length > 0 ? data : [{ name: '无告警', value: 1, itemStyle: { color: '#e5e7eb' } }],
      itemStyle: {
        borderRadius: 8,
        borderColor: '#fff',
        borderWidth: 2
      },
      label: {
        fontSize: 13,
        fontWeight: 'bold'
      }
    }]
  }

  alertTypeChart.setOption(option, true)
}

// 更新Top 5资源占用图（已删除）
// 更新探针类型分布图（已删除）

// 更新所有图表
const updateAllCharts = () => {
  if (!isMounted.value) {
    console.warn('%c⚠️ 组件已卸载，跳过图表更新', 'color: #e6a23c;')
    return
  }

  nextTick(() => {
    if (isMounted.value) {
      updateTrendChart()
      updateAlertTypeChart()
    }
  })
}

// 刷新所有数据
const refreshAll = () => {
  fetchAllProbes()
}

// 格式化时间
const formatTime = (time) => {
  return dayjs(time).format('YYYY-MM-DD HH:mm:ss')
}

// 格式化字节数
const formatBytes = (bytes) => {
  if (!bytes) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return (bytes / Math.pow(k, i)).toFixed(2) + ' ' + sizes[i]
}

// 格式化运行时长
const formatUptime = (seconds) => {
  if (!seconds) return '-'
  const days = Math.floor(seconds / 86400)
  const hours = Math.floor((seconds % 86400) / 3600)
  const minutes = Math.floor((seconds % 3600) / 60)

  if (days > 0) {
    return `${days}天 ${hours}小时`
  } else if (hours > 0) {
    return `${hours}小时 ${minutes}分钟`
  } else {
    return `${minutes}分钟`
  }
}

// 初始化图表
const initCharts = () => {
  if (!isMounted.value) {
    console.warn('%c⚠️ 组件已卸载，跳过图表初始化', 'color: #e6a23c;')
    return
  }

  console.log('%c========== [图表初始化] 开始初始化图表 ==========', 'color: #409eff; font-weight: bold')
  nextTick(() => {
    if (!isMounted.value) {
      console.warn('%c⚠️ 组件已卸载，跳过图表初始化', 'color: #e6a23c;')
      return
    }

    console.log('%c趋势图DOM引用:', 'color: #67c23a;', trendChartRef.value)
    console.log('%c告警图DOM引用:', 'color: #67c23a;', alertTypeChartRef.value)

    if (trendChartRef.value) {
      trendChart = echarts.init(trendChartRef.value)
      console.log('%c✓ 趋势图初始化成功', 'color: #67c23a;')
    } else {
      console.error('%c✗ 趋势图DOM引用为空', 'color: #f56c6c;')
    }

    if (alertTypeChartRef.value) {
      alertTypeChart = echarts.init(alertTypeChartRef.value)
      console.log('%c✓ 告警图初始化成功', 'color: #67c23a;')
    } else {
      console.error('%c✗ 告警图DOM引用为空', 'color: #f56c6c;')
    }

    // 窗口大小变化时重绘
    window.addEventListener('resize', () => {
      if (isMounted.value) {
        trendChart?.resize()
        alertTypeChart?.resize()
      }
    })
  })
}

onMounted(() => {
  // 设置组件挂载状态
  isMounted.value = true

  fetchAllProbes()
  initCharts()
})

onUnmounted(() => {
  // 设置组件已卸载标志
  isMounted.value = false

  // 清理ECharts实例
  if (trendChart) {
    trendChart.dispose()
    trendChart = null
  }
  if (alertTypeChart) {
    alertTypeChart.dispose()
    alertTypeChart = null
  }
})
</script>

<style scoped lang="scss">
/* ================================================================
   Data Statistics — Dashboard Layout
   ================================================================ */

$bg: #f1f5f9;
$card: #ffffff;
$radius: 12px;
$shadow: 0 1px 3px rgba(0, 0, 0, 0.04), 0 1px 2px rgba(0, 0, 0, 0.03);
$shadow-hover: 0 4px 12px rgba(0, 0, 0, 0.07);

$text-primary: #0f172a;
$text-secondary: #475569;
$text-muted: #94a3b8;

$blue: #3b82f6;
$blue-soft: #eff6ff;
$green: #10b981;
$green-soft: #ecfdf5;
$emerald: #059669;
$amber: #f59e0b;
$amber-soft: #fffbeb;
$red: #ef4444;
$red-soft: #fef2f2;

.stats-page {
  padding: 20px 24px;
  background: $bg;
  min-height: calc(100vh - 64px);
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* ---------- Header ---------- */
.stats-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 18px 24px;
  background: $card;
  border-radius: $radius;
  box-shadow: $shadow;
}

.header-left {
  display: flex;
  align-items: baseline;
  gap: 14px;
}

.page-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 20px;
  font-weight: 700;
  color: $text-primary;
  margin: 0;

  .el-icon { font-size: 22px; color: $blue; }
}

.page-desc {
  font-size: 13px;
  color: $text-muted;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}

.agent-option {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;

  .dot {
    width: 7px;
    height: 7px;
    border-radius: 50%;
    flex-shrink: 0;

    &.online  { background: $green; }
    &.offline { background: $text-muted; }
  }

  .agent-name { flex: 1; font-size: 13px; color: $text-primary; }
  .agent-status-text { font-size: 12px; color: $text-muted; }
}

.last-update {
  font-size: 12px;
  color: $text-muted;
  white-space: nowrap;
  padding-left: 6px;
  border-left: 1px solid #e2e8f0;
}

/* ---------- Metrics Row ---------- */
.metrics-row {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 12px;
}

.metric-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 16px 18px;
  background: $card;
  border-radius: $radius;
  box-shadow: $shadow;
  transition: box-shadow 200ms ease, transform 200ms ease;
  cursor: default;

  &:hover {
    box-shadow: $shadow-hover;
    transform: translateY(-1px);
  }
}

.metric-icon {
  width: 42px;
  height: 42px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  flex-shrink: 0;

  &--total   { background: $blue-soft;  color: $blue; }
  &--online  { background: $green-soft;  color: $green; }
  &--healthy { background: #ecfdf5; color: $emerald; }
  &--warn    { background: $amber-soft;  color: $amber; }
  &--off     { background: $red-soft;    color: $red; }
  &--rate    { background: #f0f0ff; color: #6366f1; }
}

.metric-body {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.metric-value {
  font-size: 22px;
  font-weight: 700;
  color: $text-primary;
  line-height: 1.2;

  &--green   { color: $green; }
  &--emerald { color: $emerald; }
  &--amber   { color: $amber; }
  &--red     { color: $red; }
}

.metric-label {
  font-size: 12px;
  color: $text-muted;
  font-weight: 500;
}

/* ---------- Main Grid ---------- */
.main-grid {
  display: grid;
  grid-template-columns: 300px 1fr;
  gap: 16px;
  flex: 1;
  min-height: 400px;
}

/* ---------- Sidebar ---------- */
.sidebar {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.sidebar-card {
  background: $card;
  border-radius: $radius;
  box-shadow: $shadow;
  display: flex;
  flex-direction: column;
  overflow: hidden;

  &__head {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 16px 20px 0;
  }

  &__title {
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 14px;
    font-weight: 600;
    color: $text-primary;

    .el-icon { font-size: 16px; color: $text-muted; }
  }

  &__sub {
    font-size: 12px;
    color: $text-muted;
    font-weight: 400;
  }
}

/* Agent Card */
.agent-card {
  flex: 0 0 auto;
}

.agent-body {
  padding: 16px 20px 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.agent-badge {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 16px;
  border-radius: 10px;

  &__icon { font-size: 24px; }
  &__text { font-size: 15px; font-weight: 700; }

  &.status-online {
    background: linear-gradient(135deg, $green-soft 0%, #d1fae5 100%);
    .agent-badge__icon { color: $green; }
    .agent-badge__text { color: #065f46; }
  }
  &.status-offline {
    background: linear-gradient(135deg, $red-soft 0%, #fee2e2 100%);
    .agent-badge__icon { color: $red; }
    .agent-badge__text { color: #991b1b; }
  }
  &.status-warning {
    background: linear-gradient(135deg, $amber-soft 0%, #fef3c7 100%);
    .agent-badge__icon { color: $amber; }
    .agent-badge__text { color: #92400e; }
  }
  &.status-unknown {
    background: #f8fafc;
    .agent-badge__icon { color: $text-muted; }
    .agent-badge__text { color: $text-secondary; }
  }
}

.agent-meta {
  display: flex;
  flex-direction: column;
  gap: 8px;

  &__row {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 8px 12px;
    background: #f8fafc;
    border-radius: 8px;
  }

  &__label { font-size: 12px; color: $text-muted; }
  &__value { font-size: 13px; color: $text-primary; font-weight: 600; }
}

/* Alert Card */
.alert-card {
  flex: 1;
  min-height: 0;
}

.alert-chart-wrap {
  flex: 1;
  min-height: 180px;
  padding: 0 12px 12px;
}

/* ---------- Trend Panel ---------- */
.trend-panel {
  background: $card;
  border-radius: $radius;
  box-shadow: $shadow;
  display: flex;
  flex-direction: column;
  overflow: hidden;

  &__head {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 16px 20px;
    flex-shrink: 0;
  }

  &__title {
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 14px;
    font-weight: 600;
    color: $text-primary;

    .el-icon { font-size: 16px; color: $text-muted; }
  }

  &__hint {
    font-size: 12px;
    font-weight: 400;
    color: $text-muted;
    margin-left: 4px;
  }
}

.trend-chart-wrap {
  flex: 1;
  min-height: 300px;
  padding: 0 16px 16px;
}

/* ---------- Responsive ---------- */
@media (max-width: 1280px) {
  .metrics-row {
    grid-template-columns: repeat(3, 1fr);
  }
}

@media (max-width: 1024px) {
  .main-grid {
    grid-template-columns: 1fr;
  }

  .sidebar {
    flex-direction: row;
  }

  .sidebar-card {
    flex: 1;
  }
}

@media (max-width: 768px) {
  .stats-page { padding: 12px; gap: 12px; }

  .stats-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
    padding: 16px 18px;
  }

  .header-left {
    flex-direction: column;
    gap: 4px;
  }

  .metrics-row {
    grid-template-columns: repeat(2, 1fr);
  }

  .sidebar {
    flex-direction: column;
  }

  .metric-card { padding: 12px 14px; }
  .metric-value { font-size: 18px; }
}
</style>
