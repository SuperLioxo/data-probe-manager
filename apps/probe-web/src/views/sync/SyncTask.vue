<template>
  <div class="sync-task-page">
    <div class="page-header">
      <h2>数据自动同步</h2>
      <el-button type="primary" @click="showCreateDialog">
        <el-icon><Plus /></el-icon> 新建任务
      </el-button>
    </div>

    <!-- 统计卡片 -->
    <el-row :gutter="16" class="stats-row">
      <el-col :span="4" v-for="stat in statCards" :key="stat.label">
        <el-card shadow="never" class="stat-card">
          <div class="stat-value" :style="{ color: stat.color }">{{ stat.value }}</div>
          <div class="stat-label">{{ stat.label }}</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 任务列表 -->
    <el-card shadow="never">
      <div class="filter-bar">
        <el-input v-model="filters.probeKey" placeholder="探针Key" clearable style="width: 180px" @clear="loadTasks" @keyup.enter="loadTasks" />
        <el-select v-model="filters.status" placeholder="状态" clearable style="width: 120px" @change="loadTasks">
          <el-option label="成功" value="SUCCESS" />
          <el-option label="失败" value="FAILED" />
          <el-option label="运行中" value="RUNNING" />
        </el-select>
        <el-button @click="loadTasks">查询</el-button>
      </div>

      <el-table :data="tasks" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="taskName" label="任务名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="sourceProbeKey" label="源探针" min-width="120" show-overflow-tooltip />
        <el-table-column prop="sourceTableName" label="源表" min-width="120" show-overflow-tooltip />
        <el-table-column prop="targetType" label="目标类型" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="targetTypeTag(row.targetType)">{{ row.targetType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="syncMode" label="同步模式" width="110">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ syncModeLabel(row.syncMode) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="lastSyncStatus" label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="statusTag(row.lastSyncStatus)">{{ statusLabel(row.lastSyncStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="enabled" label="启用" width="70" align="center">
          <template #default="{ row }">
            <el-switch v-model="row.enabled" size="small" @change="toggleTask(row)" />
          </template>
        </el-table-column>
        <el-table-column prop="cronExpression" label="Cron" width="100" show-overflow-tooltip />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="triggerTask(row)" :disabled="row.lastSyncStatus === 'RUNNING'">执行</el-button>
            <el-button link type="primary" size="small" @click="viewLogs(row)">日志</el-button>
            <el-button link type="primary" size="small" @click="editTask(row)">编辑</el-button>
            <el-popconfirm title="确定删除？" @confirm="deleteTask(row.id)">
              <template #reference>
                <el-button link type="danger" size="small">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @size-change="loadTasks"
          @current-change="loadTasks"
        />
      </div>
    </el-card>

    <!-- 创建/编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="editingTask ? '编辑同步任务' : '新建同步任务'" width="560px" destroy-on-close>
      <el-form :model="form" label-width="100px">
        <el-form-item label="任务名称" required>
          <el-input v-model="form.taskName" placeholder="输入任务名称" />
        </el-form-item>
        <el-form-item label="源探针Key" required>
          <el-input v-model="form.sourceProbeKey" placeholder="输入探针Key" />
        </el-form-item>
        <el-form-item label="源表名">
          <el-input v-model="form.sourceTableName" placeholder="可选，不填则同步全库" />
        </el-form-item>
        <el-form-item label="目标类型" required>
          <el-select v-model="form.targetType" style="width: 100%">
            <el-option label="数据库" value="DATABASE" />
            <el-option label="MinIO" value="MINIO" />
            <el-option label="API" value="API" />
          </el-select>
        </el-form-item>
        <el-form-item label="目标配置" required>
          <el-input v-model="form.targetConfig" type="textarea" :rows="3" placeholder='JSON格式，如: {"url":"jdbc:mysql://...","username":"...","password":"..."}' />
        </el-form-item>
        <el-form-item label="同步模式">
          <el-select v-model="form.syncMode" style="width: 100%">
            <el-option label="全量同步" value="FULL" />
            <el-option label="增量同步" value="INCREMENTAL" />
            <el-option label="基于变化" value="CHANGE_BASED" />
          </el-select>
        </el-form-item>
        <el-form-item label="冲突策略">
          <el-select v-model="form.conflictStrategy" style="width: 100%">
            <el-option label="插入" value="INSERT" />
            <el-option label="更新插入" value="UPSERT" />
            <el-option label="跳过" value="SKIP" />
          </el-select>
        </el-form-item>
        <el-form-item label="Cron表达式">
          <el-input v-model="form.cronExpression" placeholder="如: 0 0 */2 * * ?（每2小时）" />
        </el-form-item>
        <el-form-item label="质量检查">
          <el-switch v-model="form.qualityCheckEnabled" active-text="同步前执行质量过滤" />
        </el-form-item>
        <el-form-item label="实时同步">
          <el-switch v-model="form.realtimeSyncEnabled" active-text="CDC变更时自动触发同步" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitForm" :loading="submitting">{{ editingTask ? '保存' : '创建' }}</el-button>
      </template>
    </el-dialog>

    <!-- 日志对话框 -->
    <el-dialog v-model="logDialogVisible" title="同步执行日志" width="800px" destroy-on-close>
      <el-table :data="logs" v-loading="logsLoading" stripe size="small">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="syncMode" label="模式" width="90" />
        <el-table-column prop="startTime" label="开始时间" width="160" />
        <el-table-column prop="endTime" label="结束时间" width="160" />
        <el-table-column prop="status" label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="statusTag(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="rowsProcessed" label="处理行数" width="90" align="right" />
        <el-table-column prop="rowsFailed" label="失败行数" width="90" align="right" />
        <el-table-column prop="errorMessage" label="错误信息" min-width="200" show-overflow-tooltip />
      </el-table>
      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="logPageNum"
          :page-size="logPageSize"
          :total="logTotal"
          layout="total, prev, pager, next"
          @current-change="loadLogs"
        />
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onBeforeUnmount, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { getSyncTasks, createSyncTask, updateSyncTask, deleteSyncTask, toggleSyncTask, triggerSync, getSyncLogs, getSyncStatistics } from '../../api/syncTask'

const loading = ref(false)
const tasks = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)

const filters = reactive({ probeKey: '', status: '' })

const stats = ref({ total: 0, enabled: 0, disabled: 0, success: 0, failed: 0, running: 0, totalLogs: 0 })

const statCards = computed(() => [
  { label: '总任务', value: stats.value.total, color: '#409EFF' },
  { label: '已启用', value: stats.value.enabled, color: '#67C23A' },
  { label: '已禁用', value: stats.value.disabled, color: '#909399' },
  { label: '成功', value: stats.value.success, color: '#67C23A' },
  { label: '失败', value: stats.value.failed, color: '#F56C6C' },
  { label: '总日志', value: stats.value.totalLogs, color: '#409EFF' },
])

const dialogVisible = ref(false)
const editingTask = ref(null)
const submitting = ref(false)
const form = reactive({
  taskName: '', sourceProbeKey: '', sourceTableName: '', targetType: 'DATABASE',
  targetConfig: '', syncMode: 'INCREMENTAL', conflictStrategy: 'UPSERT', cronExpression: '',
  qualityCheckEnabled: false, realtimeSyncEnabled: false
})

const logDialogVisible = ref(false)
const logs = ref([])
const logLoading = ref(false)
const logPageNum = ref(1)
const logPageSize = ref(10)
const logTotal = ref(0)
const logTaskId = ref(null)

const pendingTimers = []

onMounted(() => {
  loadTasks()
  loadStats()
})

async function loadTasks() {
  loading.value = true
  try {
    const res = await getSyncTasks({ probeKey: filters.probeKey, status: filters.status, pageNum: pageNum.value, pageSize: pageSize.value })
    tasks.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
}

async function loadStats() {
  try {
    const res = await getSyncStatistics()
    if (res.data) stats.value = { ...stats.value, ...res.data }
  } catch (e) {
    console.error(e)
  }
}

function showCreateDialog() {
  editingTask.value = null
  Object.assign(form, { taskName: '', sourceProbeKey: '', sourceTableName: '', targetType: 'DATABASE', targetConfig: '', syncMode: 'INCREMENTAL', conflictStrategy: 'UPSERT', cronExpression: '', qualityCheckEnabled: false, realtimeSyncEnabled: false })
  dialogVisible.value = true
}

function editTask(row) {
  editingTask.value = row
  Object.assign(form, { taskName: row.taskName, sourceProbeKey: row.sourceProbeKey, sourceTableName: row.sourceTableName, targetType: row.targetType, targetConfig: row.targetConfig, syncMode: row.syncMode, conflictStrategy: row.conflictStrategy, cronExpression: row.cronExpression, qualityCheckEnabled: row.qualityCheckEnabled || false, realtimeSyncEnabled: row.realtimeSyncEnabled || false })
  dialogVisible.value = true
}

async function submitForm() {
  if (!form.taskName || !form.sourceProbeKey || !form.targetConfig) {
    ElMessage.warning('请填写必填项')
    return
  }
  submitting.value = true
  try {
    if (editingTask.value) {
      await updateSyncTask(editingTask.value.id, form)
      ElMessage.success('更新成功')
    } else {
      await createSyncTask(form)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    loadTasks()
    loadStats()
  } catch (e) {
    ElMessage.error('操作失败')
  } finally {
    submitting.value = false
  }
}

async function toggleTask(row) {
  try {
    await toggleSyncTask(row.id, row.enabled)
    ElMessage.success(row.enabled ? '已启用' : '已禁用')
    loadStats()
  } catch (e) {
    row.enabled = !row.enabled
    ElMessage.error('操作失败')
  }
}

async function triggerTask(row) {
  try {
    await triggerSync(row.id)
    ElMessage.success('已触发同步')
    const timer = setTimeout(loadTasks, 1000)
    pendingTimers.push(timer)
  } catch (e) {
    ElMessage.error('触发失败')
  }
}

async function deleteTask(id) {
  try {
    await deleteSyncTask(id)
    ElMessage.success('删除成功')
    loadTasks()
    loadStats()
  } catch (e) {
    ElMessage.error('删除失败')
  }
}

async function viewLogs(row) {
  logTaskId.value = row.id
  logPageNum.value = 1
  logDialogVisible.value = true
  await loadLogs()
}

async function loadLogs() {
  logLoading.value = true
  try {
    const res = await getSyncLogs({ taskId: logTaskId.value, pageNum: logPageNum.value, pageSize: logPageSize.value })
    logs.value = res.data?.records || []
    logTotal.value = res.data?.total || 0
  } catch (e) {
    console.error(e)
  } finally {
    logLoading.value = false
  }
}

function statusTag(status) {
  const map = { SUCCESS: 'success', FAILED: 'danger', RUNNING: 'warning' }
  return map[status] || 'info'
}

function statusLabel(status) {
  const map = { SUCCESS: '成功', FAILED: '失败', RUNNING: '运行中' }
  return map[status] || status || '-'
}

function syncModeLabel(mode) {
  const map = { FULL: '全量', INCREMENTAL: '增量', CHANGE_BASED: '变化' }
  return map[mode] || mode
}

function targetTypeTag(type) {
  const map = { DATABASE: '', MINIO: 'warning', API: 'success' }
  return map[type] || 'info'
}

onBeforeUnmount(() => {
  pendingTimers.forEach(id => clearTimeout(id))
  pendingTimers.length = 0
})
</script>

<style scoped>
.sync-task-page { padding: 0; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.page-header h2 { margin: 0; font-size: 18px; }
.stats-row { margin-bottom: 16px; }
.stat-card { text-align: center; padding: 12px; }
.stat-value { font-size: 28px; font-weight: 700; line-height: 1.2; }
.stat-label { font-size: 13px; color: #909399; margin-top: 4px; }
.filter-bar { display: flex; gap: 12px; margin-bottom: 16px; flex-wrap: wrap; }
.pagination-wrap { display: flex; justify-content: flex-end; margin-top: 12px; }
</style>
