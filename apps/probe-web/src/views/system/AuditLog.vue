<template>
  <div class="audit-log-container">
    <!-- 页面标题和操作 -->
    <div class="page-header">
      <div class="page-title">
        <el-icon><Document /></el-icon>
        <span>审计日志</span>
      </div>
      <div class="page-actions">
        <el-button type="primary" @click="showStatistics = true">
          <el-icon><DataAnalysis /></el-icon>
          统计分析
        </el-button>
        <el-button type="success" @click="handleExport" :loading="exporting">
          <el-icon><Download /></el-icon>
          导出Excel
        </el-button>
      </div>
    </div>

    <!-- 查询表单 -->
    <el-card class="search-card" shadow="hover">
      <el-form :inline="true" :model="queryForm" class="search-form">
        <el-form-item label="用户ID">
          <el-input
            v-model="queryForm.userId"
            placeholder="请输入用户ID"
            clearable
            prefix-icon="User"
            style="width: 150px"
          />
        </el-form-item>
        <el-form-item label="操作类型">
          <el-select v-model="queryForm.operation" placeholder="请选择操作类型" clearable style="width: 140px">
            <el-option label="创建" value="CREATE" />
            <el-option label="更新" value="UPDATE" />
            <el-option label="删除" value="DELETE" />
            <el-option label="查询" value="QUERY" />
            <el-option label="登录" value="LOGIN" />
            <el-option label="登出" value="LOGOUT" />
            <el-option label="权限变更" value="PERMISSION_CHANGE" />
            <el-option label="配置变更" value="CONFIG_CHANGE" />
            <el-option label="批量操作" value="BATCH" />
          </el-select>
        </el-form-item>
        <el-form-item label="模块">
          <el-input
            v-model="queryForm.module"
            placeholder="请输入模块名称"
            clearable
            style="width: 150px"
          />
        </el-form-item>
        <el-form-item label="日志级别">
          <el-select v-model="queryForm.level" placeholder="请选择级别" clearable style="width: 120px">
            <el-option label="INFO" value="INFO" />
            <el-option label="WARN" value="WARN" />
            <el-option label="ERROR" value="ERROR" />
            <el-option label="CRITICAL" value="CRITICAL" />
          </el-select>
        </el-form-item>
        <el-form-item label="关键词">
          <el-input
            v-model="queryForm.keyword"
            placeholder="搜索描述、用户名、模块"
            clearable
            prefix-icon="Search"
            style="width: 200px"
          />
        </el-form-item>
        <el-form-item label="时间范围">
          <el-date-picker
            v-model="dateRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            format="YYYY-MM-DD HH:mm:ss"
            value-format="YYYY-MM-DDTHH:mm:ss"
            style="width: 360px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">
            <el-icon><Search /></el-icon>
            查询
          </el-button>
          <el-button @click="handleReset">
            <el-icon><RefreshLeft /></el-icon>
            重置
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 日志列表表格 -->
    <el-card class="table-card" shadow="hover">
      <el-table
        :data="tableData"
        style="width: 100%"
        v-loading="loading"
        border
        stripe
        @row-click="handleRowClick"
      >
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column prop="userId" label="用户ID" width="100" align="center" show-overflow-tooltip />
        <el-table-column prop="username" label="用户名" width="120" align="center" show-overflow-tooltip />
        <el-table-column prop="operation" label="操作类型" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="getOperationType(row.operation)" size="small">
              {{ getOperationLabel(row.operation) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="level" label="级别" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="getLevelType(row.level)" size="small">
              {{ row.level }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="module" label="模块" width="120" align="center">
          <template #default="{ row }">
            <el-tag type="info" size="small">{{ row.module || '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="操作描述" min-width="180" show-overflow-tooltip />
        <el-table-column prop="responseCode" label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.responseCode === 200 ? 'success' : 'danger'" size="small">
              {{ row.responseCode }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="isException" label="异常" width="70" align="center">
          <template #default="{ row }">
            <el-icon v-if="row.isException" color="#f56c6c" :size="20"><CircleClose /></el-icon>
            <el-icon v-else color="#67c23a" :size="20"><CircleCheck /></el-icon>
          </template>
        </el-table-column>
        <el-table-column prop="executionTime" label="耗时" width="90" align="center">
          <template #default="{ row }">
            <span :class="{ 'slow-execution': row.executionTime > 1000 }">
              {{ row.executionTime }}ms
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="操作时间" width="170" align="center" />
        <el-table-column label="操作" width="100" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click.stop="handleViewDetail(row)">
              <el-icon><View /></el-icon>
              详情
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination-container">
        <el-pagination
          v-model:current-page="pagination.pageNum"
          v-model:page-size="pagination.pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="pagination.total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
        />
      </div>
    </el-card>

    <!-- 详情对话框 -->
    <el-dialog
      v-model="detailDialogVisible"
      title="审计日志详情"
      width="800px"
      :close-on-click-modal="false"
    >
      <el-descriptions :column="2" border v-if="currentLog">
        <el-descriptions-item label="日志ID">{{ currentLog.id }}</el-descriptions-item>
        <el-descriptions-item label="用户ID">{{ currentLog.userId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="用户名">{{ currentLog.username || '-' }}</el-descriptions-item>
        <el-descriptions-item label="操作类型">
          <el-tag :type="getOperationType(currentLog.operation)" size="small">
            {{ getOperationLabel(currentLog.operation) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="日志级别">
          <el-tag :type="getLevelType(currentLog.level)" size="small">
            {{ currentLog.level }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="模块">{{ currentLog.module || '-' }}</el-descriptions-item>
        <el-descriptions-item label="操作描述" :span="2">{{ currentLog.description || '-' }}</el-descriptions-item>
        <el-descriptions-item label="请求URL" :span="2">{{ currentLog.requestUrl || '-' }}</el-descriptions-item>
        <el-descriptions-item label="请求方法">{{ currentLog.requestMethod || '-' }}</el-descriptions-item>
        <el-descriptions-item label="响应状态码">
          <el-tag :type="currentLog.responseCode === 200 ? 'success' : 'danger'" size="small">
            {{ currentLog.responseCode }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="响应消息" :span="2">{{ currentLog.responseMessage || '-' }}</el-descriptions-item>
        <el-descriptions-item label="执行耗时">{{ currentLog.executionTime }}ms</el-descriptions-item>
        <el-descriptions-item label="IP地址">{{ currentLog.ipAddress || '-' }}</el-descriptions-item>
        <el-descriptions-item label="是否异常">
          <el-tag :type="currentLog.isException ? 'danger' : 'success'" size="small">
            {{ currentLog.isException ? '是' : '否' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="异常消息" :span="2" v-if="currentLog.isException">
          <el-text type="danger">{{ currentLog.exceptionMessage || '-' }}</el-text>
        </el-descriptions-item>
        <el-descriptions-item label="请求参数" :span="2">
          <pre class="json-preview">{{ formatJson(currentLog.requestParams) }}</pre>
        </el-descriptions-item>
        <el-descriptions-item label="用户代理" :span="2">{{ currentLog.userAgent || '-' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间" :span="2">{{ currentLog.createTime }}</el-descriptions-item>
        <el-descriptions-item label="业务类型" v-if="currentLog.businessType">{{ currentLog.businessType }}</el-descriptions-item>
        <el-descriptions-item label="业务ID" v-if="currentLog.businessId">{{ currentLog.businessId }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>

    <!-- 统计对话框 -->
    <el-dialog
      v-model="showStatistics"
      title="日志统计分析"
      width="900px"
      :close-on-click-modal="false"
    >
      <div v-loading="loadingStats">
        <el-form :inline="true" class="stats-form">
          <el-form-item label="统计时间范围">
            <el-date-picker
              v-model="statsDateRange"
              type="datetimerange"
              range-separator="至"
              start-placeholder="开始时间"
              end-placeholder="结束时间"
              format="YYYY-MM-DD HH:mm:ss"
              value-format="YYYY-MM-DDTHH:mm:ss"
              style="width: 360px"
            />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="fetchStatistics">查询统计</el-button>
          </el-form-item>
        </el-form>

        <div v-if="statistics">
          <el-row :gutter="20" class="stats-summary">
            <el-col :span="6">
              <el-card class="stat-card">
                <div class="stat-value">{{ statistics.total || 0 }}</div>
                <div class="stat-label">总日志数</div>
              </el-card>
            </el-col>
            <el-col :span="6">
              <el-card class="stat-card">
                <div class="stat-value">{{ statistics.exceptionCount || 0 }}</div>
                <div class="stat-label">异常数量</div>
              </el-card>
            </el-col>
            <el-col :span="6">
              <el-card class="stat-card">
                <div class="stat-value">{{ Object.keys(statistics.byOperation || {}).length }}</div>
                <div class="stat-label">操作类型数</div>
              </el-card>
            </el-col>
            <el-col :span="6">
              <el-card class="stat-card">
                <div class="stat-value">{{ Object.keys(statistics.byUser || {}).length }}</div>
                <div class="stat-label">活跃用户数</div>
              </el-card>
            </el-col>
          </el-row>

          <el-row :gutter="20" class="stats-charts">
            <el-col :span="12">
              <el-card>
                <template #header>
                  <div class="card-header">按操作类型统计</div>
                </template>
                <div v-for="(count, operation) in statistics.byOperation" :key="operation" class="stat-item">
                  <div class="stat-item-label">{{ getOperationLabel(operation) }}</div>
                  <el-progress :percentage="getPercentage(count, statistics.total)" :format="() => count" />
                </div>
              </el-card>
            </el-col>
            <el-col :span="12">
              <el-card>
                <template #header>
                  <div class="card-header">按用户统计 (Top 10)</div>
                </template>
                <div v-for="(count, user) in statistics.byUser" :key="user" class="stat-item">
                  <div class="stat-item-label">{{ user }}</div>
                  <el-progress :percentage="getPercentage(count, statistics.total)" :format="() => count" />
                </div>
              </el-card>
            </el-col>
          </el-row>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Document, Download, DataAnalysis, View, CircleCheck, CircleClose, Search, RefreshLeft } from '@element-plus/icons-vue'
import { getAuditLogs, exportAuditLogs, searchAuditLogs, getAuditLogStatistics } from '@/api/auditLog'
import { exportExcelFile } from '@/utils/export'

// 立即输出调试信息
console.log('%c✅✅✅ [AuditLog.vue] 组件开始执行 ✅✅✅', 'color: #ff0000; font-size: 20px; font-weight: bold; background: yellow; padding: 10px;')

// 响应式数据
const loading = ref(false)
const exporting = ref(false)
const loadingStats = ref(false)
const tableData = ref([])
const detailDialogVisible = ref(false)
const showStatistics = ref(false)
const currentLog = ref(null)
const dateRange = ref(null)
const statsDateRange = ref(null)
const statistics = ref(null)

const pagination = reactive({
  pageNum: 1,
  pageSize: 10,
  total: 0
})

const queryForm = reactive({
  userId: '',
  operation: '',
  module: '',
  level: '',
  keyword: '',
  startTime: '',
  endTime: ''
})

// 获取日志列表
const fetchLogs = async () => {
  loading.value = true
  try {
    // 如果有高级搜索条件，使用 searchAuditLogs
    const hasAdvancedSearch = queryForm.level || queryForm.keyword || queryForm.startTime || queryForm.endTime

    let params = {
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize,
      ...queryForm
    }

    console.log('[AuditLog] 请求参数:', params)

    let response
    if (hasAdvancedSearch) {
      response = await searchAuditLogs(params)
    } else {
      response = await getAuditLogs(params)
    }

    console.log('%c========== [审计日志] API响应 ==========', 'color: #409eff; font-weight: bold')
    console.log('%cresponse:', 'color: #67c23a;', response)
    console.log('%cresponse类型:', 'color: #67c23a;', typeof response)
    console.log('%cresponse.data:', 'color: #67c23a;', response.data)
    console.log('%cresponse.data类型:', 'color: #67c23a;', typeof response.data)

    // 处理响应数据
    if (response && response.data) {
      const records = response.data.records || []

      console.log('%c========== [审计日志] 处理响应数据 ==========', 'color: #409eff; font-weight: bold')
      console.log('%c原始记录数:', 'color: #67c23a;', records.length)
      console.log('%c总记录数:', 'color: #67c23a;', response.data.total)
      console.log('%c第一条原始记录:', 'color: #67c23a;', records[0])

      // 字段映射：后端字段 -> 前端字段
      tableData.value = records.map(record => ({
        ...record,
        // 将后端的 resourceType 映射为前端的 module
        module: record.resourceType || record.module || '-',
        // 确保所有必需字段都存在
        username: record.username || '-',
        ipAddress: record.ipAddress || '-',
        createTime: record.createTime ? record.createTime.replace('T', ' ') : '-'
      }))

      pagination.total = response.data.total || 0

      console.log('%c处理后记录数:', 'color: #67c23a;', tableData.value.length)
      console.log('%c第一条处理后记录:', 'color: #67c23a;', tableData.value[0])
      console.log('%c✓ 数据加载成功', 'color: #67c23a;')
    } else {
      console.warn('[AuditLog] 响应数据格式异常:', response)
      tableData.value = []
      pagination.total = 0
    }
  } catch (error) {
    console.error('[AuditLog] 获取日志列表失败:', error)
    tableData.value = []
    pagination.total = 0
  } finally {
    loading.value = false
  }
}

// 获取统计数据
const fetchStatistics = async () => {
  if (!statsDateRange.value || statsDateRange.value.length !== 2) {
    ElMessage.warning('请选择时间范围')
    return
  }

  loadingStats.value = true
  try {
    const response = await getAuditLogStatistics({
      startTime: statsDateRange.value[0],
      endTime: statsDateRange.value[1]
    })

    console.log('[AuditLog] 统计数据响应:', response)

    if (response && response.data) {
      statistics.value = response.data
    } else {
      console.warn('[AuditLog] 统计数据格式异常:', response)
      statistics.value = null
    }
  } catch (error) {
    console.error('[AuditLog] 获取统计信息失败:', error)
    statistics.value = null
  } finally {
    loadingStats.value = false
  }
}

// 查询
const handleSearch = () => {
  // 处理时间范围
  if (dateRange.value && dateRange.value.length === 2) {
    queryForm.startTime = dateRange.value[0]
    queryForm.endTime = dateRange.value[1]
  } else {
    queryForm.startTime = ''
    queryForm.endTime = ''
  }

  pagination.pageNum = 1
  fetchLogs()
}

// 重置
const handleReset = () => {
  dateRange.value = null
  Object.assign(queryForm, {
    userId: '',
    operation: '',
    module: '',
    level: '',
    keyword: '',
    startTime: '',
    endTime: ''
  })
  handleSearch()
}

// 导出
const handleExport = async () => {
  exporting.value = true
  try {
    const res = await exportAuditLogs(queryForm)
    exportExcelFile(res.data, '审计日志.xlsx')
    ElMessage.success('导出成功')
  } catch (error) {
    console.error('导出失败:', error)
  } finally {
    exporting.value = false
  }
}

// 分页
const handleSizeChange = (val) => {
  pagination.pageSize = val
  fetchLogs()
}

const handleCurrentChange = (val) => {
  pagination.pageNum = val
  fetchLogs()
}

// 查看详情
const handleViewDetail = (row) => {
  currentLog.value = row
  detailDialogVisible.value = true
}

// 行点击事件
const handleRowClick = (row) => {
  handleViewDetail(row)
}

// 获取操作类型标签颜色
const getOperationType = (operation) => {
  const map = {
    'CREATE': 'success',
    'UPDATE': 'warning',
    'DELETE': 'danger',
    'QUERY': 'info',
    'LOGIN': 'success',
    'LOGOUT': 'info',
    'PERMISSION_CHANGE': 'danger',
    'CONFIG_CHANGE': 'warning',
    'BATCH': 'primary'
  }
  return map[operation] || 'info'
}

// 获取操作类型标签
const getOperationLabel = (operation) => {
  const map = {
    'CREATE': '创建',
    'UPDATE': '更新',
    'DELETE': '删除',
    'QUERY': '查询',
    'LOGIN': '登录',
    'LOGOUT': '登出',
    'PERMISSION_CHANGE': '权限变更',
    'CONFIG_CHANGE': '配置变更',
    'BATCH': '批量操作',
    'OTHER': '其他'
  }
  return map[operation] || operation
}

// 获取日志级别标签颜色
const getLevelType = (level) => {
  const map = {
    'INFO': 'info',
    'WARN': 'warning',
    'ERROR': 'danger',
    'CRITICAL': 'danger'
  }
  return map[level] || 'info'
}

// 格式化JSON
const formatJson = (jsonStr) => {
  if (!jsonStr) return '-'
  try {
    const obj = JSON.parse(jsonStr)
    return JSON.stringify(obj, null, 2)
  } catch {
    return jsonStr
  }
}

// 计算百分比
const getPercentage = (count, total) => {
  if (!total || total === 0) return 0
  return Math.round((count / total) * 100)
}

// 初始化
onMounted(() => {
  console.log('%c========== [审计日志] 组件挂载 ==========', 'color: #409eff; font-weight: bold')
  console.log('%c开始加载审计日志数据...', 'color: #67c23a;')
  fetchLogs()
})
</script>

<style scoped>
.audit-log-container {
  padding: var(--spacing-6);
  width: 100%;
  max-width: 100%;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  flex: 1;
  overflow: hidden;
  isolation: isolate;
}

/* 确保所有直接子元素不会溢出 */
.audit-log-container > * {
  width: 100%;
  box-sizing: border-box;
  max-width: 100%;
}

/* 页面标题区 - 使用设计系统变量 */
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--spacing-6);
  padding: var(--spacing-5) var(--spacing-6);
  background: var(--bg-card);
  border-radius: var(--border-radius-lg);
  box-shadow: var(--shadow-sm);
  transition: var(--transition-base);
  width: 100%;
  box-sizing: border-box;
  flex-shrink: 0;
  max-width: 100%;
}

.page-header:hover {
  box-shadow: var(--shadow-md);
}

.page-title {
  display: flex;
  align-items: center;
  gap: var(--spacing-3);
  font-size: var(--text-2xl);
  font-weight: var(--font-weight-semibold);
  color: var(--text-primary);
  font-family: var(--font-heading);
  flex-shrink: 0;
  min-width: 0;
  flex: 1;
}

.page-title .el-icon {
  color: var(--primary-500);
  font-size: 28px;
  flex-shrink: 0;
}

.page-title span {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.page-actions {
  display: flex;
  gap: var(--spacing-3);
  flex-shrink: 0;
}

/* 搜索卡片 - 统一卡片样式 */
.search-card {
  margin-bottom: var(--spacing-6);
  border: 1px solid var(--border-color-light);
  transition: var(--transition-base);
  width: 100%;
  box-sizing: border-box;
  flex-shrink: 0;
  max-width: 100%;
}

.search-card:hover {
  box-shadow: var(--shadow-card-hover);
}

:deep(.search-card .el-card__body) {
  padding: var(--spacing-5);
  width: 100%;
  box-sizing: border-box;
  max-width: 100%;
}

.search-form {
  display: flex;
  flex-wrap: wrap;
  gap: var(--spacing-4);
  align-items: flex-end;
  width: 100%;
  box-sizing: border-box;
}

.search-form .el-form-item {
  margin-bottom: var(--spacing-4);
  margin-right: 0;
  flex-shrink: 0;
}

/* 表格卡片 */
.table-card {
  margin-bottom: var(--spacing-6);
  border: 1px solid var(--border-color-light);
  transition: var(--transition-base);
  width: 100%;
  box-sizing: border-box;
  overflow: hidden;
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
  max-width: 100%;
}

.table-card:hover {
  box-shadow: var(--shadow-card-hover);
}

:deep(.table-card .el-card__body) {
  padding: var(--spacing-5);
  width: 100%;
  box-sizing: border-box;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  max-width: 100%;
}

/* 表格响应式包装器 */
:deep(.el-table) {
  width: 100%;
}

:deep(.el-table__inner-wrapper) {
  width: 100%;
  overflow-x: auto;
}

:deep(.el-table__body-wrapper) {
  overflow-x: auto;
  overflow-y: auto;
}

/* 表格容器包装 */
:deep(.table-card .el-card__body > *) {
  width: 100%;
  box-sizing: border-box;
}

/* 分页容器 */
.pagination-container {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  margin-top: var(--spacing-6);
  padding-top: var(--spacing-4);
  border-top: 1px solid var(--border-color-light);
  flex-shrink: 0;
  width: 100%;
  box-sizing: border-box;
}

/* 表格行点击效果 - 使用设计系统 */
:deep(.el-table__body tr) {
  cursor: pointer;
  transition: var(--transition-colors);
}

:deep(.el-table__body tr:hover) {
  background-color: var(--primary-50) !important;
}

:deep(.el-table__body tr:hover td) {
  color: var(--primary-700);
}

/* 表格单元格样式优化 */
:deep(.el-table th.el-table__cell) {
  background: var(--bg-secondary);
  color: var(--text-secondary);
  font-weight: var(--font-weight-semibold);
  text-transform: uppercase;
  font-size: var(--text-xs);
  letter-spacing: 0.05em;
  padding: var(--spacing-4) var(--spacing-3);
}

:deep(.el-table td.el-table__cell) {
  padding: var(--spacing-4) var(--spacing-3);
}

/* 慢查询高亮 - 使用设计系统颜色 */
.slow-execution {
  color: var(--warning-600);
  font-weight: var(--font-weight-semibold);
  display: inline-flex;
  align-items: center;
  gap: var(--spacing-1);
}

.slow-execution::before {
  content: '⚠';
  font-size: var(--text-sm);
}

/* JSON预览样式 - 优化代码显示 */
.json-preview {
  background: var(--bg-secondary);
  border: 1px solid var(--border-color-light);
  border-radius: var(--border-radius-md);
  padding: var(--spacing-4);
  margin: 0;
  max-height: 200px;
  overflow-y: auto;
  font-size: var(--text-xs);
  line-height: var(--leading-normal);
  font-family: var(--font-mono);
  color: var(--text-primary);
}

.json-preview::-webkit-scrollbar {
  width: 6px;
}

.json-preview::-webkit-scrollbar-track {
  background: var(--bg-hover);
  border-radius: var(--border-radius-sm);
}

.json-preview::-webkit-scrollbar-thumb {
  background: var(--border-color);
  border-radius: var(--border-radius-sm);
}

.json-preview::-webkit-scrollbar-thumb:hover {
  background: var(--border-hover);
}

/* 统计卡片样式 - 使用设计系统 */
.stats-summary {
  margin-bottom: var(--spacing-6);
}

.stat-card {
  text-align: center;
  padding: var(--spacing-5);
  border-radius: var(--border-radius-lg);
  border: 1px solid var(--border-color-light);
  transition: var(--transition-base);
  background: var(--gradient-card);
}

.stat-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-md);
}

.stat-value {
  font-size: var(--text-3xl);
  font-weight: var(--font-weight-bold);
  color: var(--primary-600);
  margin-bottom: var(--spacing-2);
  font-family: var(--font-heading);
  background: var(--gradient-primary);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.stat-label {
  font-size: var(--text-sm);
  color: var(--text-secondary);
  font-weight: var(--font-weight-medium);
}

.stats-charts {
  margin-top: var(--spacing-6);
}

.stats-charts .el-card {
  height: 100%;
}

.stat-item {
  margin-bottom: var(--spacing-4);
}

.stat-item-label {
  display: flex;
  justify-content: space-between;
  margin-bottom: var(--spacing-2);
  font-size: var(--text-sm);
  color: var(--text-secondary);
  font-weight: var(--font-weight-medium);
}

.card-header {
  font-weight: var(--font-weight-semibold);
  color: var(--text-primary);
  font-size: var(--text-base);
}

.stats-form {
  margin-bottom: var(--spacing-6);
  padding: var(--spacing-4);
  background: var(--bg-secondary);
  border-radius: var(--border-radius-md);
}

/* 对话框优化 */
:deep(.el-dialog) {
  border-radius: var(--border-radius-lg);
}

:deep(.el-dialog__header) {
  padding: var(--spacing-6);
  border-bottom: 1px solid var(--border-color-light);
}

:deep(.el-dialog__body) {
  padding: var(--spacing-6);
}

/* 描述列表优化 */
:deep(.el-descriptions) {
  font-size: var(--text-sm);
}

:deep(.el-descriptions__label) {
  font-weight: var(--font-weight-medium);
  color: var(--text-secondary);
  background: var(--bg-secondary);
}

:deep(.el-descriptions__content) {
  color: var(--text-primary);
}

/* 标签样式优化 */
:deep(.el-tag) {
  border-radius: var(--border-radius-full);
  padding: var(--spacing-1) var(--spacing-3);
  font-weight: var(--font-weight-medium);
  border: none;
}

:deep(.el-tag--info) {
  background: var(--info-100);
  color: var(--info-700);
}

:deep(.el-tag--success) {
  background: var(--success-100);
  color: var(--success-700);
}

:deep(.el-tag--warning) {
  background: var(--warning-100);
  color: var(--warning-700);
}

:deep(.el-tag--danger) {
  background: var(--error-100);
  color: var(--error-700);
}

:deep(.el-tag--primary) {
  background: var(--primary-100);
  color: var(--primary-700);
}

/* 进度条优化 */
:deep(.el-progress-bar__outer) {
  background: var(--bg-tertiary);
  border-radius: var(--border-radius-full);
}

:deep(.el-progress-bar__inner) {
  background: var(--gradient-primary);
  border-radius: var(--border-radius-full);
  transition: width var(--duration-normal) var(--ease-smooth);
}

/* 响应式优化 */
@media (max-width: 768px) {
  .audit-log-container {
    padding: var(--spacing-4);
    width: 100%;
    box-sizing: border-box;
  }

  .page-header {
    flex-direction: column;
    align-items: flex-start;
    gap: var(--spacing-4);
    width: 100%;
    box-sizing: border-box;
  }

  .page-actions {
    width: 100%;
    flex-direction: column;
    box-sizing: border-box;
  }

  .page-actions .el-button {
    width: 100%;
    box-sizing: border-box;
  }

  .search-card {
    width: 100%;
    box-sizing: border-box;
  }

  .table-card {
    width: 100%;
    box-sizing: border-box;
  }

  :deep(.table-card .el-card__body) {
    padding: var(--spacing-3);
    overflow-x: auto;
  }

  :deep(.el-table) {
    font-size: var(--text-xs);
  }

  .search-form {
    flex-direction: column;
  }

  .search-form .el-form-item {
    width: 100%;
  }

  .search-form .el-input,
  .search-form .el-select {
    width: 100% !important;
  }

  .stats-summary .el-col {
    margin-bottom: var(--spacing-4);
  }
}

/* 动画控制 */
@media (prefers-reduced-motion: reduce) {
  .page-header,
  .search-card,
  .table-card,
  .stat-card,
  :deep(.el-table__body tr) {
    transition: none !important;
  }
}
</style>
