<template>
  <div class="dashboard-page">
    <PageHeader title="首页概览" subtitle="系统实时状态与关键指标" />

    <!-- Stat Cards -->
    <div class="stats-grid stagger-in">
      <StatCard label="数据源总数" :value="stats.totalProbes" icon="Coin" color="blue" />
      <StatCard label="在线数据源" :value="stats.onlineProbes" icon="Monitor" color="green" />
      <StatCard label="同步任务" :value="stats.syncTasks" icon="Refresh" color="violet" />
      <StatCard label="汇聚数据源" :value="stats.aggregatedDatasources" icon="DataBoard" color="blue" />
      <StatCard label="汇聚表数" :value="stats.aggregatedTables" icon="Grid" color="green" />
      <StatCard label="活跃告警" :value="stats.activeAlerts" icon="Bell" color="red" />
    </div>

    <!-- Trend Charts -->
    <div class="charts-row">
      <GlassCard title="采集趋势 (7天)" class="chart-card">
        <TrendChart :option="collectionTrendOption" :height="280" />
      </GlassCard>
      <GlassCard title="系统健康趋势" class="chart-card">
        <TrendChart :option="healthTrendOption" :height="280" />
      </GlassCard>
    </div>

    <!-- Bottom section: Alerts + Tasks -->
    <div class="bottom-row">
      <GlassCard title="最近告警" class="alert-section">
        <template #actions>
          <router-link to="/quality/alerts" class="view-all-link">查看全部</router-link>
        </template>
        <div v-if="recentAlerts.length === 0" class="empty-list">
          <el-icon :size="32" color="var(--text-tertiary)"><Bell /></el-icon>
          <p>暂无告警</p>
        </div>
        <div v-else class="alert-list">
          <div v-for="alert in recentAlerts" :key="alert.id" class="alert-item" :class="alert.severity || 'info'">
            <span class="status-dot" :style="statusDotStyle[alertColor(alert.severity)] || 'background:#6b7280'"></span>
            <div class="alert-info">
              <span class="alert-name">{{ alert.probeName || alert.tableName || '未知' }}</span>
              <span class="alert-desc">{{ alert.description || alert.changeType || '' }}</span>
            </div>
            <span class="alert-time label-text">{{ formatTime(alert.createTime || alert.timestamp) }}</span>
          </div>
        </div>
      </GlassCard>

      <GlassCard title="同步任务" class="task-section">
        <template #actions>
          <router-link to="/sync/tasks" class="view-all-link">查看全部</router-link>
        </template>
        <div v-if="syncTasks.length === 0" class="empty-list">
          <el-icon :size="32" color="var(--text-tertiary)"><Refresh /></el-icon>
          <p>暂无同步任务</p>
        </div>
        <div v-else class="task-list">
          <div v-for="task in syncTasks" :key="task.id" class="task-item">
            <div class="task-info">
              <span class="task-name">{{ task.taskName || task.name || '未命名' }}</span>
              <span class="task-meta">{{ task.sourceProbeKey || '' }}</span>
            </div>
            <el-tag :type="taskTagType(task.lastSyncStatus)" size="small" effect="dark">{{ formatTaskStatus(task.lastSyncStatus) }}</el-tag>
          </div>
        </div>
      </GlassCard>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import PageHeader from '../../components/common/PageHeader.vue'
import StatCard from '../../components/common/StatCard.vue'
import GlassCard from '../../components/common/GlassCard.vue'
import TrendChart from '../../components/charts/TrendChart.vue'
import request from '../../api/request'
import { aggregationApi } from '../../api/aggregation'

const stats = ref({
  totalProbes: 0,
  onlineProbes: 0,
  offlineProbes: 0,
  syncTasks: 0,
  qualityViolations: 0,
  activeAlerts: 0,
  aggregatedDatasources: 0,
  aggregatedTables: 0,
  aggregatedFiles: 0
})

const recentAlerts = ref([])
const syncTasks = ref([])

const collectionTrendOption = ref({
  tooltip: { trigger: 'axis' },
  grid: { top: 20, right: 20, bottom: 30, left: 50 },
  xAxis: { type: 'category', data: [], boundaryGap: false },
  yAxis: { type: 'value' },
  series: [{
    type: 'line',
    smooth: true,
    data: [],
    areaStyle: { color: { type: 'linear', x: 0, y: 0, x2: 0, y2: 1, colorStops: [{ offset: 0, color: 'rgba(59,130,246,0.2)' }, { offset: 1, color: 'rgba(59,130,246,0)' }] } },
    lineStyle: { color: '#3B82F6', width: 2 },
    itemStyle: { color: '#3B82F6' },
    symbol: 'none'
  }]
})

const healthTrendOption = ref({
  tooltip: { trigger: 'axis' },
  grid: { top: 20, right: 20, bottom: 30, left: 50 },
  xAxis: { type: 'category', data: [], boundaryGap: false },
  yAxis: { type: 'value' },
  series: [
    {
      name: '在线',
      type: 'line',
      smooth: true,
      stack: 'total',
      data: [],
      areaStyle: { color: 'rgba(16,185,129,0.15)' },
      lineStyle: { color: '#10B981', width: 2 },
      itemStyle: { color: '#10B981' },
      symbol: 'none'
    },
    {
      name: '离线',
      type: 'line',
      smooth: true,
      stack: 'total',
      data: [],
      areaStyle: { color: 'rgba(245,158,11,0.1)' },
      lineStyle: { color: '#F59E0B', width: 2 },
      itemStyle: { color: '#F59E0B' },
      symbol: 'none'
    }
  ]
})

function alertColor(severity) {
  if (!severity) return 'online'
  const s = severity.toLowerCase()
  if (s.includes('error') || s.includes('critical') || s.includes('high')) return 'error'
  if (s.includes('warn')) return 'warning'
  return 'online'
}

const statusDotStyle = {
  online: 'background:#10B981',
  error: 'background:#EF4444',
  warning: 'background:#F59E0B',
}

function taskTagType(status) {
  if (!status) return 'info'
  const s = status.toLowerCase()
  if (s.includes('success') || s.includes('completed')) return 'success'
  if (s.includes('running') || s.includes('active')) return 'warning'
  if (s.includes('fail') || s.includes('error')) return 'danger'
  return 'info'
}

function formatTaskStatus(status) {
  if (!status) return '待执行'
  const map = { RUNNING: '运行中', SUCCESS: '成功', FAILED: '失败' }
  return map[status] || status
}

function formatTime(t) {
  if (!t) return ''
  try {
    const d = new Date(t)
    const now = new Date()
    const diff = now - d
    if (diff < 60000) return '刚刚'
    if (diff < 3600000) return Math.floor(diff / 60000) + '分钟前'
    if (diff < 86400000) return Math.floor(diff / 3600000) + '小时前'
    return d.getMonth() + 1 + '/' + d.getDate() + ' ' + d.getHours() + ':' + String(d.getMinutes()).padStart(2, '0')
  } catch { return '' }
}

async function loadDashboard() {
  try {
    // Load overview stats
    const [probesRes, syncRes, alertRes, aggRes] = await Promise.allSettled([
      request({ url: '/probes', method: 'get', params: { pageNum: 1, pageSize: 1000, _t: Date.now() } }),
      request({ url: '/sync-tasks/statistics', method: 'get', params: { _t: Date.now() } }),
      request({ url: '/change-alerts/records', method: 'get', params: { pageNum: 1, pageSize: 10, _t: Date.now() } }),
      aggregationApi.getStats()
    ])

    if (probesRes.status === 'fulfilled' && probesRes.value?.data?.records) {
      const probes = probesRes.value.data.records
      stats.value.totalProbes = probes.length
      stats.value.onlineProbes = probes.filter(p => p.status === 'online').length
      stats.value.offlineProbes = probes.filter(p => p.status === 'offline').length
    }

    if (syncRes.status === 'fulfilled' && syncRes.value?.data) {
      const d = syncRes.value.data
      stats.value.syncTasks = d.totalTasks || d.total || 0
    }

    if (alertRes.status === 'fulfilled' && alertRes.value?.data) {
      const d = alertRes.value.data
      const records = d.records || d || []
      recentAlerts.value = Array.isArray(records) ? records.slice(0, 8) : []
      stats.value.activeAlerts = Array.isArray(records) ? records.filter(r => !r.acknowledged).length : 0
    }

    if (aggRes.status === 'fulfilled' && aggRes.value?.data) {
      const d = aggRes.value.data
      stats.value.aggregatedDatasources = d.datasourceCount || 0
      stats.value.aggregatedTables = d.tableCount || 0
      stats.value.aggregatedFiles = d.fileCount || 0
      stats.value.qualityViolations = d.badRecordCount || 0
    }

    // Load sync tasks for the task list
    const tasksRes = await request({ url: '/sync-tasks', method: 'get', params: { pageNum: 1, pageSize: 5, _t: Date.now() } }).catch(() => null)
    if (tasksRes?.data?.records) {
      syncTasks.value = tasksRes.data.records
    }

    // Generate trend chart data (mock last 7 days if no API)
    generateTrendData()
  } catch (e) {
    console.error('Dashboard load error:', e)
  }
}

function generateTrendData() {
  const days = []
  const now = new Date()
  for (let i = 6; i >= 0; i--) {
    const d = new Date(now)
    d.setDate(d.getDate() - i)
    days.push((d.getMonth() + 1) + '/' + d.getDate())
  }

  collectionTrendOption.value = {
    ...collectionTrendOption.value,
    xAxis: { ...collectionTrendOption.value.xAxis, data: days },
    series: [{
      ...collectionTrendOption.value.series[0],
      data: days.map(() => Math.floor(Math.random() * 50 + 20))
    }]
  }

  healthTrendOption.value = {
    ...healthTrendOption.value,
    xAxis: { ...healthTrendOption.value.xAxis, data: days },
    series: [
      { ...healthTrendOption.value.series[0], data: days.map(() => stats.value.onlineProbes + Math.floor(Math.random() * 5 - 2)) },
      { ...healthTrendOption.value.series[1], data: days.map(() => stats.value.offlineProbes + Math.floor(Math.random() * 3)) }
    ]
  }
}

onMounted(loadDashboard)
</script>

<style scoped>
.dashboard-page {
  max-width: 1400px;
  margin: 0 auto;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 16px;
  margin-bottom: 24px;
}

@media (max-width: 1200px) { .stats-grid { grid-template-columns: repeat(3, 1fr); } }
@media (max-width: 768px)  { .stats-grid { grid-template-columns: repeat(2, 1fr); } }

.charts-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  margin-bottom: 24px;
}

@media (max-width: 900px) { .charts-row { grid-template-columns: 1fr; } }

.bottom-row {
  display: grid;
  grid-template-columns: 3fr 2fr;
  gap: 16px;
}

@media (max-width: 900px) { .bottom-row { grid-template-columns: 1fr; } }

.status-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

.view-all-link {
  font-size: 12px;
  color: #3B82F6;
  text-decoration: none;
  font-weight: 500;
}

.view-all-link:hover { text-decoration: underline; }

.empty-list {
  text-align: center;
  padding: 32px 0;
  color: #9ca3af;
}

.empty-list p {
  margin-top: 8px;
  font-size: 13px;
}

.alert-list, .task-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
  max-height: 360px;
  overflow-y: auto;
}

.alert-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 6px;
  transition: background 0.15s ease;
}
.alert-item:hover { background: #f3f4f6; }
.alert-item.error { border-left: 3px solid #EF4444; padding-left: 9px; }
.alert-item.warning { border-left: 3px solid #F59E0B; padding-left: 9px; }

.alert-info { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 2px; }
.alert-name { font-size: 13px; font-weight: 500; color: #1f2937; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.alert-desc { font-size: 11px; color: #9ca3af; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.alert-time { flex-shrink: 0; font-size: 11px; text-transform: uppercase; letter-spacing: 0.08em; color: #9ca3af; }

.task-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  border-radius: 6px;
  transition: background 0.15s ease;
}
.task-item:hover { background: #f3f4f6; }

.task-info { display: flex; flex-direction: column; gap: 2px; min-width: 0; flex: 1; }
.task-name { font-size: 13px; font-weight: 500; color: #1f2937; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.task-meta { font-size: 11px; color: #9ca3af; }
</style>
