<template>
  <div class="quality-rule">
    <div class="page-header">
      <div class="header-left">
        <h2>数据质量过滤</h2>
        <span class="subtitle">配置和管理数据质量规则</span>
      </div>
      <div class="header-right">
        <el-button type="primary" @click="showCreateDialog">新建规则</el-button>
        <el-button :icon="Refresh" @click="loadData">刷新</el-button>
      </div>
    </div>

    <!-- 统计卡片 -->
    <div class="stats-row">
      <el-card shadow="never" class="stat-card">
        <div class="stat-value">{{ stats.totalRules || 0 }}</div>
        <div class="stat-label">规则总数</div>
      </el-card>
      <el-card shadow="never" class="stat-card">
        <div class="stat-value">{{ stats.enabledRules || 0 }}</div>
        <div class="stat-label">已启用</div>
      </el-card>
      <el-card shadow="never" class="stat-card">
        <div class="stat-value">{{ stats.totalViolations || 0 }}</div>
        <div class="stat-label">违规记录</div>
      </el-card>
    </div>

    <el-tabs v-model="activeTab">
      <el-tab-pane label="质量规则" name="rules">
        <el-card shadow="never">
          <el-table :data="rules" v-loading="loading" stripe>
            <el-table-column prop="ruleName" label="规则名称" min-width="180" />
            <el-table-column prop="tableName" label="表名" width="150" />
            <el-table-column prop="columnName" label="列名" width="130" />
            <el-table-column prop="ruleType" label="规则类型" width="130">
              <template #default="{ row }">
                <el-tag size="small">{{ getRuleTypeLabel(row.ruleType) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="ruleParams" label="规则参数" min-width="200" show-overflow-tooltip />
            <el-table-column prop="severity" label="严重级别" width="100">
              <template #default="{ row }">
                <el-tag :type="row.severity === 'ERROR' ? 'danger' : row.severity === 'WARNING' ? 'warning' : 'info'" size="small">
              {{ row.severity }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="enabled" label="状态" width="80">
          <template #default="{ row }">
            <el-switch v-model="row.enabled" @change="toggleRule(row)" size="small" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" link @click="checkRule(row)">检查</el-button>
            <el-button size="small" link @click="showEditDialog(row)">编辑</el-button>
            <el-popconfirm title="确定删除此规则？" @confirm="handleDelete(row)">
              <template #reference>
                <el-button size="small" type="danger" link>删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination">
        <el-pagination v-model:current-page="pageNum" v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50]" :total="total" layout="total, sizes, prev, pager, next"
          @size-change="loadRules" @current-change="loadRules" />
      </div>
      </el-card>
      </el-tab-pane>

      <el-tab-pane label="不合格记录" name="badRecords">
        <el-card shadow="never">
          <div class="filter-bar">
            <el-input v-model="badRecordFilter.tableName" placeholder="表名" clearable style="width: 160px" />
            <el-button @click="loadBadRecords">查询</el-button>
            <el-button @click="exportBadRecords">导出 CSV</el-button>
          </div>
          <el-table :data="badRecords" v-loading="badRecordsLoading" stripe size="small">
            <el-table-column prop="id" label="ID" width="60" />
            <el-table-column prop="sync_task_id" label="同步任务" width="90" />
            <el-table-column prop="table_name" label="表名" width="140" />
            <el-table-column prop="row_data" label="行数据" min-width="250" show-overflow-tooltip>
              <template #default="{ row }">{{ typeof row.row_data === 'object' ? JSON.stringify(row.row_data) : row.row_data }}</template>
            </el-table-column>
            <el-table-column prop="violated_rules" label="违反规则" min-width="200" show-overflow-tooltip>
              <template #default="{ row }">{{ typeof row.violated_rules === 'object' ? JSON.stringify(row.violated_rules) : row.violated_rules }}</template>
            </el-table-column>
            <el-table-column prop="rejection_reason" label="原因" min-width="200" show-overflow-tooltip />
            <el-table-column prop="detected_at" label="检测时间" width="170" />
          </el-table>
          <div class="pagination" v-if="badRecords.length">
            <el-pagination v-model:current-page="badRecordPage" :page-size="20" :total="badRecordTotal" layout="total, prev, pager, next" @current-change="loadBadRecords" />
          </div>
        </el-card>
      </el-tab-pane>
    </el-tabs>
    <el-dialog v-model="dialogVisible" :title="editingRule.id ? '编辑规则' : '新建规则'" width="600px" destroy-on-close>
      <el-form :model="editingRule" label-width="100px">
        <el-form-item label="规则名称" required>
          <el-input v-model="editingRule.ruleName" placeholder="输入规则名称" />
        </el-form-item>
        <el-form-item label="探针">
          <el-select v-model="editingRule.probeKey" placeholder="选择探针" clearable style="width: 100%">
            <el-option v-for="p in probes" :key="p.probeKey" :label="p.name" :value="p.probeKey" />
          </el-select>
        </el-form-item>
        <el-form-item label="表名">
          <el-input v-model="editingRule.tableName" placeholder="输入表名（留空表示所有表）" />
        </el-form-item>
        <el-form-item label="列名">
          <el-input v-model="editingRule.columnName" placeholder="输入列名（留空表示所有列）" />
        </el-form-item>
        <el-form-item label="规则类型" required>
          <el-select v-model="editingRule.ruleType" style="width: 100%">
            <el-option label="非空校验" value="NOT_NULL" />
            <el-option label="正则匹配" value="REGEX" />
            <el-option label="数值范围" value="RANGE" />
            <el-option label="枚举值" value="ENUM" />
            <el-option label="长度限制" value="LENGTH" />
            <el-option label="类型检查" value="TYPE_CHECK" />
            <el-option label="自定义SQL" value="CUSTOM_SQL" />
          </el-select>
        </el-form-item>
        <el-form-item label="规则参数" required>
          <el-input v-model="editingRule.ruleParams" type="textarea" :rows="3"
            placeholder='JSON格式，如: {"min": 0, "max": 100}' />
        </el-form-item>
        <el-form-item label="严重级别">
          <el-radio-group v-model="editingRule.severity">
            <el-radio-button value="ERROR">ERROR</el-radio-button>
            <el-radio-button value="WARNING">WARNING</el-radio-button>
            <el-radio-button value="INFO">INFO</el-radio-button>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave" :loading="saving">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { getQualityRules, createQualityRule, updateQualityRule, deleteQualityRule, checkQualityRule, getQualityStatistics } from '@/api/qualityRule'
import qualityFilterApi from '@/api/qualityFilter'
import { probeApi } from '@/api/probe'

const probes = ref([])
const rules = ref([])
const stats = ref({})
const loading = ref(false)
const activeTab = ref('rules')
const badRecords = ref([])
const badRecordsLoading = ref(false)
const badRecordPage = ref(1)
const badRecordTotal = ref(0)
const badRecordFilter = reactive({ tableName: '' })
const saving = ref(false)
const pageNum = ref(1)
const pageSize = ref(20)
const total = ref(0)

const dialogVisible = ref(false)
const editingRule = reactive({
  id: null, ruleName: '', probeKey: '', databaseName: '', tableName: '',
  columnName: '', ruleType: 'NOT_NULL', ruleParams: '{}', severity: 'WARNING', enabled: true
})

const RULE_TYPES = {
  NOT_NULL: '非空校验', REGEX: '正则匹配', RANGE: '数值范围',
  ENUM: '枚举值', LENGTH: '长度限制', TYPE_CHECK: '类型检查', CUSTOM_SQL: '自定义SQL'
}

const getRuleTypeLabel = (type) => RULE_TYPES[type] || type

const loadProbes = async () => {
  const res = await probeApi.getList({ pageNum: 1, pageSize: 100 })
  if (res.code === 200) probes.value = res.data?.records || []
}

const loadStats = async () => {
  const res = await getQualityStatistics()
  if (res.code === 200) stats.value = res.data || {}
}

const loadRules = async () => {
  loading.value = true
  const res = await getQualityRules({ pageNum: pageNum.value, pageSize: pageSize.value })
  if (res.code === 200 && res.data) {
    rules.value = res.data.records || []
    total.value = res.data.total || 0
  }
  loading.value = false
}

const loadData = () => { loadStats(); loadRules() }

async function loadBadRecords() {
  badRecordsLoading.value = true
  try {
    const res = await qualityFilterApi.getBadRecords({
      tableName: badRecordFilter.tableName || undefined,
      pageNum: badRecordPage.value,
      pageSize: 20
    })
    badRecords.value = res.data || []
    badRecordTotal.value = res.data?.length || 0
  } catch (e) {
    console.error('加载不合格记录失败', e)
    badRecords.value = []
  } finally {
    badRecordsLoading.value = false
  }
}

function exportBadRecords() {
  const params = new URLSearchParams()
  if (badRecordFilter.tableName) params.set('tableName', badRecordFilter.tableName)
  window.open('/api/quality-rules/bad-records/export?' + params.toString())
}

const showCreateDialog = () => {
  Object.assign(editingRule, { id: null, ruleName: '', probeKey: '', databaseName: '', tableName: '', columnName: '', ruleType: 'NOT_NULL', ruleParams: '{}', severity: 'WARNING', enabled: true })
  dialogVisible.value = true
}

const showEditDialog = (row) => {
  Object.assign(editingRule, row)
  dialogVisible.value = true
}

const handleSave = async () => {
  saving.value = true
  try {
    if (editingRule.id) {
      await updateQualityRule(editingRule.id, editingRule)
      ElMessage.success('规则已更新')
    } else {
      await createQualityRule(editingRule)
      ElMessage.success('规则已创建')
    }
    dialogVisible.value = false
    loadData()
  } catch (e) {
    ElMessage.error('保存失败')
  }
  saving.value = false
}

const handleDelete = async (row) => {
  await deleteQualityRule(row.id)
  ElMessage.success('规则已删除')
  loadData()
}

const toggleRule = async (row) => {
  await updateQualityRule(row.id, row)
  ElMessage.success(row.enabled ? '规则已启用' : '规则已禁用')
}

const checkRule = async (row) => {
  ElMessage.info('正在执行质量检查...')
  try {
    const res = await checkQualityRule(row.id)
    if (res.code === 200) {
      const results = res.data || []
      if (results.length === 0) {
        ElMessage.success('质量检查通过')
      } else {
        ElMessage.warning(`发现 ${results.length} 条违规记录`)
      }
    }
  } catch (e) {
    ElMessage.error('质量检查失败')
  }
}

onMounted(() => { loadProbes(); loadData() })
</script>

<style scoped>
.quality-rule { padding: 20px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.page-header h2 { margin: 0; font-size: 20px; }
.page-header .subtitle { color: #909399; font-size: 13px; margin-left: 12px; }
.header-right { display: flex; gap: 12px; }
.stats-row { display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px; margin-bottom: 20px; }
.stat-card { text-align: center; }
.stat-card :deep(.el-card__body) { padding: 16px 20px; }
.stat-value { font-size: 28px; font-weight: 600; color: #409eff; }
.stat-label { font-size: 13px; color: #909399; margin-top: 4px; }
.table-header { display: flex; justify-content: space-between; align-items: center; }
.pagination { display: flex; justify-content: flex-end; margin-top: 16px; }
</style>
