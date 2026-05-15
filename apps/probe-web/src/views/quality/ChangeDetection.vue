<template>
  <div class="change-detection">
    <!-- 页面头部 -->
    <div class="page-header">
      <div class="header-left">
        <h2>数据变化探测</h2>
        <span class="subtitle">监控数据源变更情况</span>
      </div>
      <div class="header-right">
        <el-select v-model="selectedProbe" placeholder="选择探针" clearable style="width: 240px" @change="loadData">
          <el-option v-for="probe in probes" :key="probe.probeKey" :label="probe.name" :value="probe.probeKey" />
        </el-select>
        <el-button type="primary" @click="handleDetect" :loading="detecting" :disabled="!selectedProbe">
          <el-icon><View /></el-icon> 检测变更
        </el-button>
        <el-button :icon="Refresh" @click="loadData" :loading="loading">刷新</el-button>
      </div>
    </div>

    <!-- 统计卡片 -->
    <div class="stats-row">
      <el-card shadow="never" class="stat-card">
        <div class="stat-value">{{ statistics.totalChanges || 0 }}</div>
        <div class="stat-label">总变化次数</div>
      </el-card>
      <el-card shadow="never" class="stat-card" v-for="(count, type) in statistics.byType" :key="type">
        <div class="stat-value">{{ count }}</div>
        <div class="stat-label">{{ getChangeTypeLabel(type) }}</div>
      </el-card>
      <el-card shadow="never" class="stat-card">
        <div class="stat-value">{{ statistics.affectedTables || 0 }}</div>
        <div class="stat-label">涉及表数量</div>
      </el-card>
    </div>

    <!-- 变化日志表格 -->
    <el-card shadow="never" class="table-card">
      <template #header>
        <div class="table-header">
          <span>变化日志</span>
          <div class="table-filters">
            <el-select v-model="filterType" placeholder="变化类型" clearable style="width: 160px" @change="loadLogs">
              <el-option label="行插入" value="ROW_INSERT" />
              <el-option label="行更新" value="ROW_UPDATE" />
              <el-option label="行删除" value="ROW_DELETE" />
              <el-option label="数据更新" value="DATA_UPDATE" />
              <el-option label="大小变化" value="SIZE_CHANGE" />
              <el-option label="索引变化" value="INDEX_SIZE_CHANGE" />
              <el-option label="CDC事件" value="CDC_EVENT" />
            </el-select>
          </div>
        </div>
      </template>

      <el-table :data="logs" v-loading="loading" stripe style="width: 100%">
        <el-table-column prop="detectedTime" label="检测时间" width="180">
          <template #default="{ row }">{{ formatTime(row.detectedTime) }}</template>
        </el-table-column>
        <el-table-column prop="probeKey" label="探针" width="220" show-overflow-tooltip />
        <el-table-column prop="databaseName" label="数据库" width="140" />
        <el-table-column prop="tableName" label="表名" width="160" />
        <el-table-column prop="changeType" label="变化类型" width="140">
          <template #default="{ row }">
            <el-tag :type="getTagType(row.changeType)" size="small">{{ getChangeTypeLabel(row.changeType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="affectedRows" label="影响行数" width="100" align="right" />
        <el-table-column label="变化详情" min-width="300">
          <template #default="{ row }">
            <div v-if="isCDCEvent(row)" class="cdc-detail">
              <el-tag :type="getOperationTagType(row)" size="small" effect="dark" class="op-tag">{{ getOperationLabel(row) }}</el-tag>
              <el-button link type="primary" size="small" @click="showDiff(row)">查看变更对比</el-button>
            </div>
            <div v-else-if="row.changeDetail" class="change-detail">
              <span v-for="(value, key) in parseDetail(row.changeDetail)" :key="key" class="detail-item">
                <span class="detail-key">{{ key }}:</span>
                <span class="detail-value">{{ value }}</span>
              </span>
            </div>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination">
        <el-pagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          layout="total, sizes, prev, pager, next"
          @size-change="loadLogs"
          @current-change="loadLogs"
        />
      </div>
    </el-card>

    <!-- CDC 行级变更对比对话框 -->
    <el-dialog v-model="diffVisible" title="行级变更对比" width="700px" destroy-on-close>
      <template v-if="diffData">
        <div class="diff-header">
          <el-tag :type="getOperationTagType(diffData)" effect="dark">{{ getOperationLabel(diffData) }}</el-tag>
          <span class="diff-meta">{{ diffData.databaseName }}.{{ diffData.tableName }}</span>
          <span class="diff-time">{{ formatTime(diffData.detectedTime) }}</span>
        </div>
        <div class="diff-container">
          <div class="diff-side diff-before">
            <h4>变更前</h4>
            <div v-if="diffBefore && Object.keys(diffBefore).length" class="diff-rows">
              <div v-for="(val, key) in diffBefore" :key="key" class="diff-row" :class="{ changed: diffAfter && diffAfter[key] !== val }">
                <span class="diff-key">{{ key }}</span>
                <span class="diff-val">{{ val === null ? 'NULL' : val }}</span>
              </div>
            </div>
            <div v-else class="diff-empty">无数据（INSERT操作）</div>
          </div>
          <div class="diff-side diff-after">
            <h4>变更后</h4>
            <div v-if="diffAfter && Object.keys(diffAfter).length" class="diff-rows">
              <div v-for="(val, key) in diffAfter" :key="key" class="diff-row" :class="{ changed: diffBefore && diffBefore[key] !== val }">
                <span class="diff-key">{{ key }}</span>
                <span class="diff-val">{{ val === null ? 'NULL' : val }}</span>
              </div>
            </div>
            <div v-else class="diff-empty">无数据（DELETE操作）</div>
          </div>
        </div>
        <div v-if="diffPosition" class="diff-footer">
          <span class="diff-pos">Binlog位置: {{ diffPosition }}</span>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { Refresh, View } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { getChangeLogs, getChangeStatistics, getRecentChanges, triggerDetection } from '@/api/changeDetection'
import { probeApi } from '@/api/probe'
import dayjs from 'dayjs'

const probes = ref([])
const selectedProbe = ref('')
const filterType = ref('')
const loading = ref(false)
const detecting = ref(false)
const logs = ref([])
const statistics = ref({})
const pageNum = ref(1)
const pageSize = ref(20)
const total = ref(0)

const diffVisible = ref(false)
const diffData = ref(null)
const diffBefore = ref(null)
const diffAfter = ref(null)
const diffPosition = ref('')

const getChangeTypeLabel = (type) => {
  const map = { ROW_INSERT: '行插入', ROW_UPDATE: '行更新', ROW_DELETE: '行删除', DATA_UPDATE: '数据更新', SIZE_CHANGE: '大小变化', INDEX_SIZE_CHANGE: '索引变化', SCHEMA_CHANGE: '结构变更', COUNT_CHANGE: '数量变化', CHECKSUM_CHANGE: '校验变化', CDC_EVENT: 'CDC事件' }
  return map[type] || type
}

const isCDCEvent = (row) => {
  if (!row.changeDetail) return false
  const d = parseDetail(row.changeDetail)
  return d && (d.operation || d.beforeData || d.afterData)
}

const getOperationLabel = (row) => {
  const d = parseDetail(row.changeDetail)
  const op = d?.operation
  const map = { INSERT: '插入', UPDATE: '更新', DELETE: '删除' }
  return map[op] || op || row.changeType
}

const getOperationTagType = (row) => {
  const d = parseDetail(row.changeDetail)
  const op = d?.operation
  const map = { INSERT: 'success', UPDATE: 'warning', DELETE: 'danger' }
  return map[op] || 'info'
}

const showDiff = (row) => {
  const d = parseDetail(row.changeDetail)
  diffData.value = row
  diffBefore.value = d?.beforeData || null
  diffAfter.value = d?.afterData || null
  diffPosition.value = d?.cdcPosition || ''
  diffVisible.value = true
}

const getTagType = (type) => {
  const map = { ROW_INSERT: 'success', ROW_DELETE: 'danger', DATA_UPDATE: 'warning', SIZE_CHANGE: 'info', INDEX_SIZE_CHANGE: '', SCHEMA_CHANGE: 'warning' }
  return map[type] || 'info'
}

const formatTime = (time) => {
  return time ? dayjs(time).format('YYYY-MM-DD HH:mm:ss') : '-'
}

const parseDetail = (detail) => {
  try { return typeof detail === 'string' ? JSON.parse(detail) : detail } catch { return {} }
}

const loadProbes = async () => {
  try {
    const res = await probeApi.getList({ pageNum: 1, pageSize: 100 })
    if (res.code === 200) {
      probes.value = res.data?.records || []
    }
  } catch (e) { console.error('加载探针列表失败', e) }
}

const loadStatistics = async () => {
  try {
    const res = await getChangeStatistics(selectedProbe.value)
    if (res.code === 200) {
      statistics.value = res.data || {}
    }
  } catch (e) { console.error('加载统计失败', e) }
}

const loadLogs = async () => {
  loading.value = true
  try {
    const res = await getChangeLogs({
      probeKey: selectedProbe.value || undefined,
      changeType: filterType.value || undefined,
      pageNum: pageNum.value,
      pageSize: pageSize.value
    })
    if (res.code === 200 && res.data) {
      logs.value = res.data.records || []
      total.value = res.data.total || 0
    }
  } catch (e) { console.error('加载日志失败', e) }
  finally { loading.value = false }
}

const handleDetect = async () => {
  if (!selectedProbe.value) {
    ElMessage.warning('请先选择探针')
    return
  }
  detecting.value = true
  try {
    const res = await triggerDetection(selectedProbe.value)
    if (res.code === 200) {
      const changes = res.data || []
      if (changes.length > 0) {
        ElMessage.success(`检测到 ${changes.length} 项变更`)
      } else {
        ElMessage.info('未检测到变更')
      }
      loadData()
    } else {
      ElMessage.error(res.message || '检测失败')
    }
  } catch (e) {
    ElMessage.error('检测失败: ' + (e.message || '未知错误'))
  } finally {
    detecting.value = false
  }
}

const loadData = () => {
  Promise.all([loadStatistics(), loadLogs()])
}

onMounted(async () => {
  await loadProbes()
  loadData()
})
</script>

<style scoped>
.change-detection {
  padding: 20px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.page-header h2 {
  margin: 0;
  font-size: 20px;
}

.page-header .subtitle {
  color: #909399;
  font-size: 13px;
  margin-left: 12px;
}

.header-right {
  display: flex;
  gap: 12px;
}

.stats-row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: 16px;
  margin-bottom: 20px;
}

.stat-card {
  text-align: center;
}

.stat-card :deep(.el-card__body) {
  padding: 16px 20px;
}

.stat-value {
  font-size: 28px;
  font-weight: 600;
  color: #409eff;
}

.stat-label {
  font-size: 13px;
  color: #909399;
  margin-top: 4px;
}

.table-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.table-filters {
  display: flex;
  gap: 8px;
}

.change-detail {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.detail-item {
  font-size: 12px;
}

.detail-key {
  color: #909399;
  margin-right: 4px;
}

.detail-value {
  color: #303133;
  font-family: 'JetBrains Mono', monospace;
}

.text-muted {
  color: #c0c4cc;
}

.pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.cdc-detail { display: flex; align-items: center; gap: 8px; }
.op-tag { font-size: 11px; }

.diff-header { display: flex; align-items: center; gap: 12px; margin-bottom: 16px; }
.diff-meta { color: #606266; font-size: 14px; font-weight: 500; }
.diff-time { color: #909399; font-size: 12px; }

.diff-container { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
.diff-side h4 { margin: 0 0 8px 0; font-size: 13px; color: #909399; }
.diff-rows { background: #f5f7fa; border-radius: 6px; padding: 8px; }
.diff-row { display: flex; justify-content: space-between; padding: 4px 8px; border-radius: 3px; font-size: 12px; font-family: 'JetBrains Mono', monospace; }
.diff-row.changed { background: #fdf6ec; }
.diff-before .diff-row.changed { background: #fef0f0; }
.diff-after .diff-row.changed { background: #f0f9eb; }
.diff-key { color: #606266; }
.diff-val { color: #303133; max-width: 200px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.diff-empty { color: #c0c4cc; font-size: 12px; padding: 16px; text-align: center; background: #f5f7fa; border-radius: 6px; }
.diff-footer { margin-top: 12px; }
.diff-pos { color: #909399; font-size: 11px; font-family: monospace; }
</style>
