<template>
  <div class="datasource-alert-page">
    <div class="page-header">
      <h2>数据源告警</h2>
      <el-button type="primary" @click="showCreateDialog">
        <el-icon><Plus /></el-icon> 新建告警规则
      </el-button>
    </div>

    <!-- 统计 -->
    <el-row :gutter="16" class="stats-row">
      <el-col :span="6" v-for="stat in statCards" :key="stat.label">
        <el-card shadow="never" class="stat-card">
          <div class="stat-value" :style="{ color: stat.color }">{{ stat.value }}</div>
          <div class="stat-label">{{ stat.label }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-tabs v-model="activeTab">
      <!-- 告警配置 -->
      <el-tab-pane label="告警配置" name="configs">
        <el-card shadow="never">
          <el-table :data="configs" v-loading="loadingConfigs" stripe>
            <el-table-column prop="id" label="ID" width="60" />
            <el-table-column prop="configName" label="配置名称" min-width="140" />
            <el-table-column prop="probeKey" label="探针Key" min-width="140" show-overflow-tooltip />
            <el-table-column prop="consecutiveFailures" label="连续失败阈值" width="120" align="center" />
            <el-table-column prop="timeoutThresholdMs" label="超时(ms)" width="100" align="center" />
            <el-table-column prop="alertLevel" label="级别" width="90" align="center">
              <template #default="{ row }">
                <el-tag size="small" :type="row.alertLevel === 'CRITICAL' ? 'danger' : 'warning'">{{ row.alertLevel }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="notifyChannels" label="通知渠道" min-width="100" show-overflow-tooltip />
            <el-table-column prop="enabled" label="启用" width="70" align="center">
              <template #default="{ row }">
                <el-switch v-model="row.enabled" size="small" @change="toggleConfig(row)" />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="120" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="editConfig(row)">编辑</el-button>
                <el-popconfirm title="确定删除？" @confirm="deleteConfig(row.id)">
                  <template #reference>
                    <el-button link type="danger" size="small">删除</el-button>
                  </template>
                </el-popconfirm>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination
            v-model:current-page="configPage.pageNum"
            :page-size="configPage.pageSize"
            :total="configPage.total"
            layout="total, prev, pager, next"
            @current-change="loadConfigs"
            style="margin-top: 12px; justify-content: flex-end"
          />
        </el-card>
      </el-tab-pane>

      <!-- 告警记录 -->
      <el-tab-pane label="告警记录" name="records">
        <el-card shadow="never">
          <div class="filter-bar" style="display: flex; gap: 8px; margin-bottom: 12px;">
            <el-input v-model="recordFilter.probeKey" placeholder="探针Key" clearable style="width: 160px" @clear="loadRecords" @keyup.enter="loadRecords" />
            <el-select v-model="recordFilter.status" placeholder="状态" clearable style="width: 100px" @change="loadRecords">
              <el-option label="待处理" value="PENDING" />
              <el-option label="已恢复" value="RESOLVED" />
            </el-select>
            <el-button type="primary" @click="loadRecords">查询</el-button>
          </div>
          <el-table :data="records" v-loading="loadingRecords" stripe>
            <el-table-column prop="id" label="ID" width="60" />
            <el-table-column prop="probeKey" label="探针Key" min-width="140" show-overflow-tooltip />
            <el-table-column prop="datasourceName" label="数据源" min-width="120" show-overflow-tooltip />
            <el-table-column prop="status" label="异常类型" width="100" align="center">
              <template #default="{ row }">
                <el-tag size="small" type="danger">{{ row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="consecutiveCount" label="连续次数" width="90" align="center" />
            <el-table-column prop="latencyMs" label="延迟(ms)" width="90" align="center" />
            <el-table-column prop="errorMessage" label="错误信息" min-width="160" show-overflow-tooltip />
            <el-table-column prop="alertLevel" label="告警级别" width="90" align="center">
              <template #default="{ row }">
                <el-tag size="small" :type="row.alertLevel === 'CRITICAL' ? 'danger' : 'warning'">{{ row.alertLevel }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="alertStatus" label="处理状态" width="90" align="center">
              <template #default="{ row }">
                <el-tag size="small" :type="row.alertStatus === 'PENDING' ? 'warning' : 'success'">{{ row.alertStatus === 'PENDING' ? '待处理' : '已恢复' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createdTime" label="触发时间" width="160" />
          </el-table>
          <el-pagination
            v-model:current-page="recordPage.pageNum"
            :page-size="recordPage.pageSize"
            :total="recordPage.total"
            layout="total, prev, pager, next"
            @current-change="loadRecords"
            style="margin-top: 12px; justify-content: flex-end"
          />
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <!-- 新建/编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="editingConfig ? '编辑告警配置' : '新建告警配置'" width="500px">
      <el-form :model="formData" label-width="110px">
        <el-form-item label="配置名称" required>
          <el-input v-model="formData.configName" placeholder="输入配置名称" />
        </el-form-item>
        <el-form-item label="探针Key">
          <el-input v-model="formData.probeKey" placeholder="留空则匹配所有探针" />
        </el-form-item>
        <el-form-item label="超时阈值(ms)">
          <el-input-number v-model="formData.timeoutThresholdMs" :min="1000" :max="60000" :step="1000" />
        </el-form-item>
        <el-form-item label="连续失败次数">
          <el-input-number v-model="formData.consecutiveFailures" :min="1" :max="100" />
        </el-form-item>
        <el-form-item label="告警级别">
          <el-select v-model="formData.alertLevel">
            <el-option label="警告" value="WARNING" />
            <el-option label="严重" value="CRITICAL" />
          </el-select>
        </el-form-item>
        <el-form-item label="通知渠道">
          <el-input v-model="formData.notifyChannels" placeholder="如 LOG,WEBSOCKET,EMAIL" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitConfig" :loading="submitting">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import {
  getDataSourceAlertConfigs, createDataSourceAlertConfig,
  updateDataSourceAlertConfig, deleteDataSourceAlertConfig,
  getDataSourceAlertRecords, getDataSourceAlertStatistics
} from '@/api/datasourceAlert'

const activeTab = ref('configs')
const configs = ref([])
const records = ref([])
const loadingConfigs = ref(false)
const loadingRecords = ref(false)
const submitting = ref(false)
const dialogVisible = ref(false)
const editingConfig = ref(null)
const stats = ref({ total: 0, pending: 0, resolved: 0, configCount: 0 })

const configPage = ref({ pageNum: 1, pageSize: 10, total: 0 })
const recordPage = ref({ pageNum: 1, pageSize: 10, total: 0 })
const recordFilter = ref({ probeKey: '', status: '' })

const formData = ref({
  configName: '', probeKey: '', timeoutThresholdMs: 5000,
  consecutiveFailures: 3, alertLevel: 'WARNING', notifyChannels: 'LOG'
})

const statCards = computed(() => [
  { label: '告警总数', value: stats.value.total || 0, color: '#409EFF' },
  { label: '待处理', value: stats.value.pending || 0, color: '#E6A23C' },
  { label: '已恢复', value: stats.value.resolved || 0, color: '#67C23A' },
  { label: '活跃规则', value: stats.value.configCount || 0, color: '#909399' }
])

async function loadConfigs() {
  loadingConfigs.value = true
  try {
    const { code, data } = await getDataSourceAlertConfigs({
      pageNum: configPage.value.pageNum, pageSize: configPage.value.pageSize
    })
    if (code === 200) {
      configs.value = data.records
      configPage.value.total = data.total
    }
  } finally { loadingConfigs.value = false }
}

async function loadRecords() {
  loadingRecords.value = true
  try {
    const { code, data } = await getDataSourceAlertRecords({
      pageNum: recordPage.value.pageNum, pageSize: recordPage.value.pageSize,
      ...recordFilter.value
    })
    if (code === 200) {
      records.value = data.records
      recordPage.value.total = data.total
    }
  } finally { loadingRecords.value = false }
}

async function loadStatistics() {
  const { code, data } = await getDataSourceAlertStatistics()
  if (code === 200) stats.value = data
}

function showCreateDialog() {
  editingConfig.value = null
  formData.value = {
    configName: '', probeKey: '', timeoutThresholdMs: 5000,
    consecutiveFailures: 3, alertLevel: 'WARNING', notifyChannels: 'LOG'
  }
  dialogVisible.value = true
}

function editConfig(row) {
  editingConfig.value = row
  formData.value = { ...row }
  dialogVisible.value = true
}

async function submitConfig() {
  if (!formData.value.configName) return ElMessage.warning('请输入配置名称')
  submitting.value = true
  try {
    const { code } = editingConfig.value
      ? await updateDataSourceAlertConfig(editingConfig.value.id, formData.value)
      : await createDataSourceAlertConfig(formData.value)
    if (code === 200) {
      ElMessage.success(editingConfig.value ? '更新成功' : '创建成功')
      dialogVisible.value = false
      loadConfigs()
    }
  } finally { submitting.value = false }
}

async function deleteConfig(id) {
  const { code } = await deleteDataSourceAlertConfig(id)
  if (code === 200) { ElMessage.success('删除成功'); loadConfigs() }
}

async function toggleConfig(row) {
  await updateDataSourceAlertConfig(row.id, row)
}

onMounted(() => {
  loadConfigs()
  loadRecords()
  loadStatistics()
})
</script>

<style scoped>
.datasource-alert-page { padding: 20px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.page-header h2 { margin: 0; font-size: 18px; }
.stats-row { margin-bottom: 16px; }
.stat-card { text-align: center; }
.stat-value { font-size: 24px; font-weight: 600; }
.stat-label { font-size: 12px; color: #909399; margin-top: 4px; }
</style>
