<template>
  <div class="change-alert-page">
    <div class="page-header">
      <h2>变化告警</h2>
      <el-button type="primary" @click="showCreateDialog">
        <el-icon><Plus /></el-icon> 新建告警规则
      </el-button>
    </div>

    <!-- 统计 -->
    <el-row :gutter="16" class="stats-row">
      <el-col :span="4" v-for="stat in statCards" :key="stat.label">
        <el-card shadow="never" class="stat-card">
          <div class="stat-value" :style="{ color: stat.color }">{{ stat.value }}</div>
          <div class="stat-label">{{ stat.label }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-tabs v-model="activeTab">
      <!-- 告警规则 -->
      <el-tab-pane label="告警规则" name="configs">
        <el-card shadow="never">
          <el-table :data="configs" v-loading="loadingConfigs" stripe>
            <el-table-column prop="id" label="ID" width="60" />
            <el-table-column prop="alertName" label="规则名称" min-width="160" />
            <el-table-column prop="probeKey" label="探针Key" min-width="140" show-overflow-tooltip />
            <el-table-column prop="tableName" label="表名" min-width="120" show-overflow-tooltip />
            <el-table-column prop="changeTypes" label="变化类型" min-width="180" show-overflow-tooltip />
            <el-table-column prop="thresholdRows" label="阈值行数" width="100" align="right" />
            <el-table-column prop="alertLevel" label="级别" width="90" align="center">
              <template #default="{ row }">
                <el-tag size="small" :type="row.alertLevel === 'CRITICAL' ? 'danger' : 'warning'">{{ row.alertLevel }}</el-tag>
              </template>
            </el-table-column>
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
        </el-card>
      </el-tab-pane>

      <!-- 告警记录 -->
      <el-tab-pane label="告警记录" name="records">
        <el-card shadow="never">
          <div class="filter-bar">
            <el-input v-model="recordFilter.probeKey" placeholder="探针Key" clearable style="width: 160px" @clear="loadRecords" @keyup.enter="loadRecords" />
            <el-select v-model="recordFilter.status" placeholder="状态" clearable style="width: 100px" @change="loadRecords">
              <el-option label="待处理" value="PENDING" />
              <el-option label="已解决" value="RESOLVED" />
            </el-select>
            <el-button @click="loadRecords">查询</el-button>
          </div>
          <el-table :data="records" v-loading="loadingRecords" stripe>
            <el-table-column prop="id" label="ID" width="60" />
            <el-table-column prop="probeKey" label="探针" min-width="120" />
            <el-table-column prop="tableName" label="表名" min-width="120" />
            <el-table-column prop="changeType" label="变化类型" width="140">
              <template #default="{ row }">
                <el-tag size="small" effect="plain">{{ row.changeType }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="affectedRows" label="影响行数" width="100" align="right" />
            <el-table-column prop="alertLevel" label="级别" width="80" align="center">
              <template #default="{ row }">
                <el-tag size="small" :type="row.alertLevel === 'CRITICAL' ? 'danger' : 'warning'">{{ row.alertLevel }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="80" align="center">
              <template #default="{ row }">
                <el-tag size="small" :type="row.status === 'PENDING' ? 'danger' : 'success'">
                  {{ row.status === 'PENDING' ? '待处理' : '已解决' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createdTime" label="告警时间" width="160" />
            <el-table-column prop="changeDetail" label="详情" min-width="200" show-overflow-tooltip />
          </el-table>
          <div class="pagination-wrap">
            <el-pagination v-model:current-page="recordPageNum" :page-size="recordPageSize" :total="recordTotal" layout="total, prev, pager, next" @current-change="loadRecords" />
          </div>
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <!-- 创建/编辑告警规则对话框 -->
    <el-dialog v-model="dialogVisible" :title="editingConfig ? '编辑告警规则' : '新建告警规则'" width="500px" destroy-on-close>
      <el-form :model="form" label-width="90px">
        <el-form-item label="规则名称" required>
          <el-input v-model="form.alertName" placeholder="告警规则名称" />
        </el-form-item>
        <el-form-item label="探针Key">
          <el-input v-model="form.probeKey" placeholder="留空匹配所有探针" />
        </el-form-item>
        <el-form-item label="表名">
          <el-input v-model="form.tableName" placeholder="留空匹配所有表" />
        </el-form-item>
        <el-form-item label="变化类型">
          <el-input v-model="form.changeTypes" placeholder="如: ROW_INSERT,ROW_DELETE,SIZE_CHANGE" />
        </el-form-item>
        <el-form-item label="阈值行数">
          <el-input-number v-model="form.thresholdRows" :min="1" :max="10000000" />
        </el-form-item>
        <el-form-item label="告警级别">
          <el-select v-model="form.alertLevel" style="width: 100%">
            <el-option label="警告" value="WARNING" />
            <el-option label="严重" value="CRITICAL" />
          </el-select>
        </el-form-item>
        <el-form-item label="通知方式">
          <el-select v-model="form.notifyChannels" style="width: 100%">
            <el-option label="日志" value="LOG" />
            <el-option label="WebSocket推送" value="WEBSOCKET" />
            <el-option label="邮件" value="EMAIL" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitForm" :loading="submitting">{{ editingConfig ? '保存' : '创建' }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getChangeAlertConfigs, createChangeAlertConfig, updateChangeAlertConfig, deleteChangeAlertConfig, getChangeAlertRecords, getChangeAlertStatistics } from '../../api/changeAlert'

const activeTab = ref('configs')
const loadingConfigs = ref(false)
const loadingRecords = ref(false)
const configs = ref([])
const records = ref([])
const stats = ref({ total: 0, pending: 0, resolved: 0, configCount: 0 })

const recordFilter = reactive({ probeKey: '', status: '' })
const recordPageNum = ref(1)
const recordPageSize = ref(20)
const recordTotal = ref(0)

const statCards = computed(() => [
  { label: '总告警', value: stats.value.total, color: '#F56C6C' },
  { label: '待处理', value: stats.value.pending, color: '#E6A23C' },
  { label: '已解决', value: stats.value.resolved, color: '#67C23A' },
  { label: '活跃规则', value: stats.value.configCount, color: '#409EFF' },
])

const dialogVisible = ref(false)
const editingConfig = ref(null)
const submitting = ref(false)
const form = reactive({ alertName: '', probeKey: '', tableName: '', changeTypes: '', thresholdRows: 100, alertLevel: 'WARNING', notifyChannels: 'LOG' })

onMounted(() => {
  loadConfigs()
  loadStats()
  loadRecords()
})

async function loadConfigs() {
  loadingConfigs.value = true
  try {
    const res = await getChangeAlertConfigs({ pageNum: 1, pageSize: 50 })
    configs.value = res.data?.records || []
  } catch (e) { console.error(e) } finally { loadingConfigs.value = false }
}

async function loadRecords() {
  loadingRecords.value = true
  try {
    const res = await getChangeAlertRecords({ probeKey: recordFilter.probeKey, status: recordFilter.status, pageNum: recordPageNum.value, pageSize: recordPageSize.value })
    records.value = res.data?.records || []
    recordTotal.value = res.data?.total || 0
  } catch (e) { console.error(e) } finally { loadingRecords.value = false }
}

async function loadStats() {
  try {
    const res = await getChangeAlertStatistics()
    if (res.data) stats.value = { ...stats.value, ...res.data }
  } catch (e) { console.error(e) }
}

function showCreateDialog() {
  editingConfig.value = null
  Object.assign(form, { alertName: '', probeKey: '', tableName: '', changeTypes: '', thresholdRows: 100, alertLevel: 'WARNING', notifyChannels: 'LOG' })
  dialogVisible.value = true
}

function editConfig(row) {
  editingConfig.value = row
  Object.assign(form, row)
  dialogVisible.value = true
}

async function submitForm() {
  if (!form.alertName) { ElMessage.warning('请填写规则名称'); return }
  submitting.value = true
  try {
    if (editingConfig.value) {
      await updateChangeAlertConfig(editingConfig.value.id, form)
      ElMessage.success('更新成功')
    } else {
      await createChangeAlertConfig(form)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    loadConfigs()
    loadStats()
  } catch (e) { ElMessage.error('操作失败') } finally { submitting.value = false }
}

async function toggleConfig(row) {
  try {
    await updateChangeAlertConfig(row.id, row)
  } catch (e) { row.enabled = !row.enabled; ElMessage.error('操作失败') }
}

async function deleteConfig(id) {
  try {
    await deleteChangeAlertConfig(id)
    ElMessage.success('删除成功')
    loadConfigs()
    loadStats()
  } catch (e) { ElMessage.error('删除失败') }
}
</script>

<style scoped>
.change-alert-page { padding: 0; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.page-header h2 { margin: 0; font-size: 18px; }
.stats-row { margin-bottom: 16px; }
.stat-card { text-align: center; padding: 12px; }
.stat-value { font-size: 28px; font-weight: 700; line-height: 1.2; }
.stat-label { font-size: 13px; color: #909399; margin-top: 4px; }
.filter-bar { display: flex; gap: 12px; margin-bottom: 16px; flex-wrap: wrap; }
.pagination-wrap { display: flex; justify-content: flex-end; margin-top: 12px; }
</style>
