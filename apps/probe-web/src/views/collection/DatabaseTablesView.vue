<template>
  <div class="database-tables-view">
    <div class="header">
      <div class="header-left">
        <el-button @click="handleGoBack" :icon="ArrowLeft" circle size="small" title="返回" />
        <h3>
          <el-icon><List /></el-icon>
          数据库表列表
        </h3>
      </div>
      <div class="header-info">
        <span v-if="instanceName" class="database-name">
          {{ instanceName }}
        </span>
        <span v-else-if="databaseName" class="database-name">
          {{ databaseName }}
        </span>
        <el-tag :type="statusType">{{ statusText }}</el-tag>
      </div>
    </div>

    <!-- 筛选和搜索 -->
    <div class="filter-bar">
      <el-input
        v-model="searchText"
        placeholder="搜索表名..."
        clearable
        style="width: 300px; margin-right: 10px"
        @input="handleSearch"
      >
        <template #prefix>
          <el-icon><Search /></el-icon>
        </template>
      </el-input>

      <el-button @click="handleRefresh" :loading="refreshing">
        <el-icon><Refresh /></el-icon>
        刷新
      </el-button>

      <el-button
        type="primary"
        @click="handleTriggerCollection"
        :disabled="!isOnline"
      >
        <el-icon><Collection /></el-icon>
        采集元数据
      </el-button>
    </div>

    <!-- 表格 -->
    <el-table
      v-loading="loading"
      :data="tables"
      stripe
      style="width: 100%"
      :default-sort="{ prop: 'tableName', order: 'ascending' }"
    >
      <el-table-column prop="tableName" label="表名" sortable width="200">
        <template #default="{ row }">
          <el-link
            type="primary"
            @click="handleViewData(row)"
            style="cursor: pointer; font-weight: 500;"
          >
            {{ row.tableName }}
          </el-link>
        </template>
      </el-table-column>

      <el-table-column label="记录数" sortable width="120">
        <template #default="{ row }">
          <span>{{ formatNumber(row.rowCount) }}</span>
        </template>
      </el-table-column>

      <el-table-column label="数据大小" sortable width="120">
        <template #default="{ row }">
          <span>{{ formatBytes(row.dataSize) }}</span>
        </template>
      </el-table-column>

      <el-table-column label="索引大小" sortable width="120">
        <template #default="{ row }">
          <span>{{ formatBytes(row.indexSize) }}</span>
        </template>
      </el-table-column>

      <el-table-column label="总大小" sortable width="120">
        <template #default="{ row }">
          <span>{{ formatBytes(row.totalSize) }}</span>
        </template>
      </el-table-column>

      <el-table-column prop="engine" label="引擎" width="100" />

      <el-table-column prop="createTime" label="创建时间" width="160">
        <template #default="{ row }">
          {{ formatDateTime(row.createTime) }}
        </template>
      </el-table-column>

      <el-table-column prop="updateTime" label="更新时间" width="160">
        <template #default="{ row }">
          {{ formatDateTime(row.updateTime) }}
        </template>
      </el-table-column>

      <el-table-column label="操作" width="150" fixed="right">
        <template #default="{ row }">
          <el-button
            type="primary"
            size="small"
            @click="handleViewColumns(row)"
          >
            查看字段
          </el-button>
          <el-button
            type="info"
            size="small"
            @click="handleViewData(row)"
          >
            查看数据
          </el-button>
        </template>
      </el-table-column>

      <template #empty>
        <el-empty
          :description="isOnline ? '暂无表数据，点击「采集元数据」开始采集' : '探针离线，无法获取数据'"
        >
          <el-button
            v-if="isOnline"
            type="primary"
            @click="handleTriggerCollection"
          >
            采集元数据
          </el-button>
        </el-empty>
      </template>
    </el-table>

    <!-- 分页 -->
    <div class="pagination-wrapper">
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :page-sizes="[10, 20, 50, 100]"
        :total="total"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="handleSizeChange"
        @current-change="handlePageChange"
      />
    </div>

    <!-- 字段列对话框 -->
    <el-dialog
      v-model="columnsDialogVisible"
      :title="`表字段 - ${currentTable?.tableName || ''}`"
      width="80%"
      destroy-on-close
    >
      <el-table
        v-loading="columnsLoading"
        :data="columns"
        stripe
        max-height="500px"
      >
        <el-table-column prop="columnName" label="字段名" width="150" />

        <el-table-column prop="dataType" label="数据类型" width="150" />

        <el-table-column prop="columnType" label="类型详情" width="200" />

        <el-table-column label="允许NULL" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.isNullable ? 'success' : 'danger'" size="small">
              {{ row.isNullable ? 'YES' : 'NO' }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="keyType" label="键类型" width="100" />

        <el-table-column prop="defaultValue" label="默认值" width="120" />

        <el-table-column prop="extra" label="额外信息" width="120" />

        <el-table-column prop="comment" label="注释" show-overflow-tooltip />
      </el-table>

      <template #empty>
        <el-empty description="暂无字段信息" />
      </template>
    </el-dialog>

    <!-- 表数据对话框 -->
    <el-dialog
      v-model="tableDataDialogVisible"
      :title="`表数据 - ${currentTable?.tableName || ''}`"
      width="90%"
      destroy-on-close
      top="5vh"
    >
      <div class="table-data-container">
        <!-- 数据信息 -->
        <div class="table-data-info" v-if="tableDataInfo">
          <el-tag type="info">总行数: {{ formatNumber(tableDataInfo.total) }}</el-tag>
          <el-tag type="success">当前页: {{ tableDataInfo.pageNum }}</el-tag>
          <el-tag>每页: {{ tableDataInfo.pageSize }}</el-tag>
          <el-tag type="warning">列数: {{ tableDataColumns.length }}</el-tag>
          <el-tag>显示行数: {{ tableDataRows.length }}</el-tag>
        </div>

        <!-- 数据完整性提示 -->
        <div class="data-completeness-info" v-if="tableDataInfo && tableDataRows.length > 0">
          <div class="info-item">
            <span class="label">数据完整性:</span>
            <span>{{ checkDataCompleteness() }}</span>
          </div>
          <div class="info-item" v-if="tableDataInfo.total > tableDataPageSize">
            <span class="label">提示:</span>
            <span>总数据超过一页，请使用分页查看全部数据</span>
          </div>
        </div>

        <!-- 错误提示 -->
        <el-alert
          v-if="tableDataError"
          :title="tableDataError"
          type="error"
          :closable="false"
          show-icon
          style="margin-bottom: 16px"
        />

        <!-- 表数据 -->
        <el-table
          v-loading="tableDataLoading"
          :data="tableDataRows"
          stripe
          border
          max-height="60vh"
          :default-sort="{ prop: 'id', order: 'ascending' }"
          :row-style="{ fontSize: '13px' }"
        >
          <el-table-column
            v-for="column in tableDataColumns"
            :key="column.name"
            :prop="column.name"
            :label="column.name"
            :min-width="getColumnWidth(column.type)"
            :width="getColumnFixedWidth(column.type)"
            show-overflow-tooltip
          >
            <template #default="{ row }">
              <div class="table-cell-content" :title="getCellTooltip(row[column.name])">
                {{ formatCellValue(row[column.name]) }}
              </div>
            </template>
          </el-table-column>
        </el-table>

        <!-- 分页 -->
        <div class="table-data-pagination" v-if="tableDataInfo && tableDataInfo.total > 0">
          <el-pagination
            v-model:current-page="tableDataPageNum"
            v-model:page-size="tableDataPageSize"
            :page-sizes="[10, 20, 50, 100]"
            :total="tableDataInfo.total"
            layout="total, sizes, prev, pager, next, jumper"
            @size-change="handleTableDataSizeChange"
            @current-change="handleTableDataPageChange"
          />
        </div>

        <!-- 空状态 -->
        <el-empty v-if="!tableDataLoading && tableDataRows.length === 0 && !tableDataError" description="暂无数据" />
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { List, Refresh, Collection, Search, ArrowLeft } from '@element-plus/icons-vue'
import { databaseProbeApi } from '@/api/databaseProbe'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()
const probeKey = route.params.probeKey

// 监听路由参数变化
watch(() => route.query, (newQuery) => {
  console.log('%c========== [DatabaseTablesView] 路由参数变化 ==========', 'color: #409eff; font-weight: bold')
  console.log('%c新的路由参数:', 'color: #67c23a;', newQuery)

  if (newQuery.instanceId || newQuery.databaseName) {
    const newInstanceId = newQuery.instanceId || newQuery.databaseName
    if (newInstanceId !== instanceId.value) {
      console.log('%c✓ instanceId 变化:', 'color: #67c23a;', instanceId.value, '->', newInstanceId)
      instanceId.value = newInstanceId
      // 重新加载数据
      loadTables()
    }
  }

  if (newQuery.instanceName && newQuery.instanceName !== instanceName.value) {
    console.log('%c✓ instanceName 变化:', 'color: #67c23a;', instanceName.value, '->', newQuery.instanceName)
    instanceName.value = newQuery.instanceName
  }

  console.log('%c=======================================================', 'color: #909399')
}, { immediate: false }) // 不立即执行，避免与 onMounted 重复

const props = defineProps({
  databaseType: {
    type: String,
    default: 'PostgreSQL'
  }
})

const emit = defineEmits(['refresh'])

// 状态
const loading = ref(false)
const refreshing = ref(false)
const tables = ref([])
const columns = ref([])
const columnsLoading = ref(false)
const columnsDialogVisible = ref(false)
const currentTable = ref(null)
const searchText = ref('')

// 表数据状态
const tableDataDialogVisible = ref(false)
const tableDataLoading = ref(false)
const tableDataColumns = ref([])
const tableDataRows = ref([])
const tableDataInfo = ref(null)
const tableDataError = ref('')
const tableDataPageNum = ref(1)
const tableDataPageSize = ref(20)

// 分页
const currentPage = ref(1)
const pageSize = ref(20)
const total = ref(0)

// 数据库信息 - 从路由查询参数获取
const instanceId = ref(route.query.instanceId || route.query.databaseName || '')
const instanceName = ref(route.query.instanceName || '')
const databaseName = ref('')  // 数据库名称
const status = ref('online')  // 默认在线，允许用户触发采集

// ✅ 从 localStorage 恢复数据库实例选择
const restoreDatabaseInstance = () => {
  try {
    const storageKey = `db_probe_selected_instance_${probeKey}`
    const savedData = localStorage.getItem(storageKey)

    if (savedData) {
      const savedInstance = JSON.parse(savedData)
      console.log('%c✓ 从 localStorage 读取到保存的数据库实例:', 'color: #67c23a;', savedInstance)

      // 如果路由参数中没有 instanceId，使用保存的值
      if (!instanceId.value) {
        if (savedInstance.databaseName) {
          instanceId.value = savedInstance.databaseName
          console.log('%c✓ 使用保存的 databaseName:', 'color: #67c23a;', savedInstance.databaseName)
        } else if (savedInstance.instanceId) {
          instanceId.value = savedInstance.instanceId
          console.log('%c✓ 使用保存的 instanceId:', 'color: #67c23a;', savedInstance.instanceId)
        }
      }

      // 如果路由参数中没有 instanceName，使用保存的值
      if (!instanceName.value && savedInstance.name) {
        instanceName.value = savedInstance.name
        console.log('%c✓ 使用保存的实例名称:', 'color: #67c23a;', savedInstance.name)
      }

      return savedInstance
    } else {
      console.log('%c⚠️ localStorage 中没有保存的数据库实例', 'color: #909399;')
      return null
    }
  } catch (error) {
    console.error('%c✗ 读取数据库实例失败:', 'color: #f56c6c;', error)
    return null
  }
}

// 计算属性
const isOnline = computed(() => status.value === 'online')

const statusType = computed(() => {
  switch (status.value) {
    case 'online': return 'success'
    case 'offline': return 'info'
    case 'error': return 'danger'
    default: return ''
  }
})

const statusText = computed(() => {
  switch (status.value) {
    case 'online': return '在线'
    case 'offline': return '离线'
    case 'error': return '错误'
    default: return '未知'
  }
})

// 格式化函数
const formatNumber = (num) => {
  if (num == null) return '-'
  return num.toLocaleString()
}

const formatBytes = (bytes) => {
  if (bytes == null || bytes === 0) return '-'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  let size = bytes
  let unitIndex = 0
  while (size >= 1024 && unitIndex < units.length - 1) {
    size /= 1024
    unitIndex++
  }
  return `${size.toFixed(2)} ${units[unitIndex]}`
}

const formatDateTime = (dateTime) => {
  if (!dateTime) return '-'
  return new Date(dateTime).toLocaleString('zh-CN')
}

// 加载表数据
const loadTables = async () => {
  try {
    console.log('%c========== [DatabaseTablesView] 开始加载表数据 ==========', 'color: #409eff; font-weight: bold')
    console.log('%cprobeKey:', 'color: #67c23a; font-weight: bold', probeKey)
    console.log('%c分页信息:', 'color: #e6a23c;', {
      pageNum: currentPage.value,
      pageSize: pageSize.value,
      search: searchText.value
    })

    loading.value = true

    // 构建请求参数
    const params = {
      pageNum: currentPage.value,
      pageSize: pageSize.value,
      search: searchText.value || undefined
    }

    // 如果有 instanceId，添加为参数
    if (instanceId.value) {
      params.instanceId = instanceId.value
    }

    const response = await databaseProbeApi.getTables(probeKey, params)

    console.log('%cAPI响应:', 'color: #e6a23c;', response)

    if (response.code === 200 || response.success) {
      // 从分页响应中提取数据
      let tableData = []
      let totalCount = 0

      // 分页格式：{ records: [], total: 0, size: 20, current: 1, pages: 0 }
      if (Array.isArray(response.data.records)) {
        tableData = response.data.records
        totalCount = response.data.total || 0
      }
      // 直接数组格式
      else if (Array.isArray(response.data)) {
        tableData = response.data
        totalCount = tableData.length
      }
      // 嵌套格式
      else if (Array.isArray(response.data.list)) {
        tableData = response.data.list
        totalCount = response.data.total || tableData.length
      }
      // 其他可能的字段名
      else if (Array.isArray(response.data.tables)) {
        tableData = response.data.tables
        totalCount = response.data.total || tableData.length
      } else if (Array.isArray(response.data.items)) {
        tableData = response.data.items
        totalCount = response.data.total || tableData.length
      }

      tables.value = tableData
      total.value = totalCount

      console.log('%c✓ 加载成功: 表数量 =', 'color: #67c23a; font-weight: bold', totalCount)
      console.log('%c表数据:', 'color: #67c23a;', tableData)


      // 尝试获取数据库元数据
      loadMetadata()

      ElMessage({
        message: `✓ 加载成功！共 ${totalCount} 个表`,
        type: 'success',
        duration: 2000
      })
    } else {
      console.error('%c✗ API返回错误:', 'color: #f56c6c; font-weight: bold', response.message)
      ElMessage.error(response.message || '加载表列表失败')
    }
  } catch (error) {
    console.error('%c✗ 加载表数据异常:', 'color: #f56c6c; font-weight: bold', error)
    ElMessage.error('加载表列表失败：' + (error.message || '网络异常'))
    tables.value = []
    total.value = 0
  } finally {
    loading.value = false
    console.log('%c=========================================================', 'color: #909399')
  }
}

// 加载元数据
const loadMetadata = async () => {
  try {
    console.log('%c========== [DatabaseTablesView] 加载元数据 ==========', 'color: #409eff; font-weight: bold')
    console.log('%cprobeKey:', 'color: #67c23a; font-weight: bold', probeKey)
    console.log('%cinstanceId:', 'color: #e6a23c;', instanceId.value)

    // 传递 instanceId 参数以获取指定数据库实例的元数据
    const response = await databaseProbeApi.getMetadata(probeKey, instanceId.value)

    console.log('%c元数据API响应:', 'color: #e6a23c;', response)

    if (response.code === 200 || response.success) {
      const metadata = response.data
      if (metadata) {
        // 更新数据库名称
        if (metadata.databaseName) {
          databaseName.value = metadata.databaseName
          console.log('%c✓ 更新数据库名称:', 'color: #67c23a;', databaseName.value)
        }
        // 更新状态（如果元数据中包含状态信息）
        if (metadata.status) {
          status.value = metadata.status
          console.log('%c✓ 更新状态:', 'color: #67c23a;', status.value)
        }
      }
    }
  } catch (error) {
    console.warn('%c✗ 加载元数据失败:', 'color: #f56c6c; font-weight: bold', error)
  }
  console.log('%c=========================================================', 'color: #909399')
}

// 加载字段信息
const loadColumns = async (tableName) => {
  try {
    columnsLoading.value = true
    // TODO: 实现获取表字段的API
    // const response = await databaseProbeApi.getTableColumns(probeKey, tableName)
    // columns.value = response.data || []

    // 临时模拟数据
    columns.value = []

    columnsDialogVisible.value = true
  } catch (error) {
    console.error('加载字段信息失败:', error)
    ElMessage.error('加载字段信息失败')
  } finally {
    columnsLoading.value = false
  }
}

// 事件处理
const handleSearch = () => {
  currentPage.value = 1
  loadTables()
}

const handleRefresh = async () => {
  console.log('%c========== [DatabaseTablesView] 用户点击刷新按钮 ==========', 'color: #409eff; font-weight: bold')
  console.log('%c当前时间:', 'color: #e6a23c;', new Date().toLocaleString())

  ElMessage({
    message: '开始刷新表列表...',
    type: 'info',
    duration: 1500
  })

  refreshing.value = true
  currentPage.value = 1
  await loadTables()
  refreshing.value = false

  console.log('%c=========================================================', 'color: #909399')
}

const handleTriggerCollection = async () => {
  console.log('%c========== [DatabaseTablesView] 用户点击采集元数据 ==========', 'color: #409eff; font-weight: bold')
  console.log('%cprobeKey:', 'color: #67c23a; font-weight: bold', probeKey)

  const loadingMsg = ElMessage({
    message: '正在提交采集任务...',
    type: 'info',
    duration: 0
  })

  try {
    const response = await databaseProbeApi.triggerCollection(probeKey)

    console.log('%c采集任务响应:', 'color: #e6a23c;', response)

    loadingMsg.close()

    if (response.code === 200 || response.success) {
      console.log('%c✓ 采集任务已提交', 'color: #67c23a; font-weight: bold')

      ElMessage({
        message: '✓ 元数据采集任务已提交，2秒后自动刷新...',
        type: 'success',
        duration: 2000
      })

      // 延迟刷新
      setTimeout(() => {
        console.log('%c开始延迟刷新...', 'color: #e6a23c;')
        loadTables()
      }, 2000)
    } else {
      console.error('%c✗ 采集任务提交失败:', 'color: #f56c6c; font-weight: bold', response.message)
      ElMessage.error(response.message || '触发采集失败')
    }
  } catch (error) {
    console.error('%c✗ 触发采集异常:', 'color: #f56c6c; font-weight: bold', error)
    loadingMsg.close()
    ElMessage.error('触发采集失败：' + (error.message || '网络异常'))
  }

  console.log('%c==========================================================', 'color: #909399')
}

// 处理数据库切换选择
const handleViewColumns = (row) => {
  currentTable.value = row
  loadColumns(row.tableName)
}

const handleViewData = async (row) => {
  console.log('%c========== [DatabaseTablesView] 查看表数据 ==========', 'color: #409eff; font-weight: bold')
  console.log('%c表信息:', 'color: #67c23a;', row)

  currentTable.value = row
  tableDataDialogVisible.value = true
  tableDataPageNum.value = 1
  tableDataPageSize.value = 20

  await loadTableData(row.tableName)

  console.log('%c=========================================================', 'color: #909399')
}

// 加载表数据
const loadTableData = async (tableName) => {
  try {
    console.log('%c---------- [loadTableData] 开始加载表数据 ----------', 'color: #409eff')
    console.log('%cprobeKey:', 'color: #67c23a;', probeKey)
    console.log('%ctableName:', 'color: #67c23a;', tableName)
    console.log('%cinstanceId.value:', 'color: #e6a23c;', instanceId.value)
    console.log('%c分页:', 'color: #e6a23c;', { pageNum: tableDataPageNum.value, pageSize: tableDataPageSize.value })

    tableDataLoading.value = true
    tableDataError.value = ''

    // 构建请求参数
    const params = {
      pageNum: tableDataPageNum.value,
      pageSize: tableDataPageSize.value
    }

    // ⭐ 关键修复：传递 databaseName 参数（统一probeKey架构必须指定数据库实例）
    if (instanceId.value) {
      params.databaseName = instanceId.value
      console.log('%c✓ 添加 databaseName 参数:', 'color: #67c23a;', instanceId.value)
    } else {
      console.error('%c✗ 警告: instanceId.value 为空，未添加 databaseName 参数！', 'color: #f56c6c; font-weight: bold')
      console.error('%c✗ 这将导致查询错误的数据库或查询失败！', 'color: #f56c6c;')
    }

    console.log('%c最终请求参数:', 'color: #67c23a;', params)

    const response = await databaseProbeApi.getTableData(probeKey, tableName, params)

    console.log('%cAPI响应:', 'color: #e6a23c;', response)

    if (response.data) {
      // 处理列信息
      if (response.data.columns && Array.isArray(response.data.columns)) {
        tableDataColumns.value = response.data.columns
        console.log('%c✓ 列信息加载成功，数量:', 'color: #67c23a;', response.data.columns.length)
        console.log('%c列详情:', 'color: #e6a23c;', response.data.columns.map(col => `${col.name} (${col.type})`).join(', '))
      } else {
        tableDataColumns.value = []
        console.warn('%c⚠️ 未获取到列信息', 'color: #e6a23c;')
      }

      // 处理行数据
      if (response.data.rows && Array.isArray(response.data.rows)) {
        tableDataRows.value = response.data.rows
        console.log('%c✓ 行数据加载成功，数量:', 'color: #67c23a;', response.data.rows.length)

        // 显示前几行数据的详细信息
        if (response.data.rows.length > 0) {
          console.log('%c第一行数据示例:', 'color: #e6a23c;', response.data.rows[0])
          if (response.data.rows.length > 1) {
            console.log('%c最后一行数据示例:', 'color: #e6a23c;', response.data.rows[response.data.rows.length - 1])
          }
        }
      } else {
        tableDataRows.value = []
        console.warn('%c⚠️ 未获取到行数据', 'color: #e6a23c;')
      }

      // 处理分页信息
      if (response.data.total !== undefined) {
        tableDataInfo.value = {
          total: response.data.total,
          pageNum: response.data.pageNum || tableDataPageNum.value,
          pageSize: response.data.pageSize || tableDataPageSize.value
        }
        console.log('%c✓ 总行数:', 'color: #67c23a;', response.data.total)
        console.log('%c✓ 分页信息:', 'color: #e6a23c;', tableDataInfo.value)
      }

      // 处理错误
      if (response.data.error) {
        tableDataError.value = response.data.error
        console.warn('%c⚠️ API返回错误:', 'color: #e6a23c;', response.data.error)
        if (response.data.message) {
          ElMessage.warning(response.data.message)
        }
      } else {
        console.log('%c✓ 表数据加载完成', 'color: #67c23a; font-weight: bold')
        console.log('%c数据摘要:', 'color: #67c23a;', {
          列数: tableDataColumns.value.length,
          行数: tableDataRows.value.length,
          总数: tableDataInfo.value?.total || '未知',
          当前页: tableDataInfo.value?.pageNum || 1
        })
      }
    } else {
      tableDataError.value = '未获取到数据'
      console.error('%c✗ API响应数据为空', 'color: #f56c6c')
    }

  } catch (error) {
    console.error('%c✗ 加载表数据异常:', 'color: #f56c6c; font-weight: bold', error)
    tableDataError.value = error.message || '加载失败'
    ElMessage.error('加载表数据失败：' + (error.message || '网络异常'))
  } finally {
    tableDataLoading.value = false
    console.log('%c----------------------------------------------------', 'color: #909399')
  }
}

// 表数据分页处理
const handleTableDataPageChange = (page) => {
  console.log('%c表数据分页变化:', 'color: #409eff', page)
  tableDataPageNum.value = page
  loadTableData(currentTable.value.tableName)
}

const handleTableDataSizeChange = (size) => {
  console.log('%c表数据每页大小变化:', 'color: #409eff', size)
  tableDataPageSize.value = size
  tableDataPageNum.value = 1
  loadTableData(currentTable.value.tableName)
}

// 格式化单元格值
const formatCellValue = (value) => {
  if (value === null || value === undefined) {
    return '<NULL>'
  }
  if (typeof value === 'object') {
    try {
      const jsonStr = JSON.stringify(value, null, 2)
      // 如果JSON字符串太长，返回截断版本
      if (jsonStr.length > 100) {
        return jsonStr.substring(0, 100) + '...'
      }
      return jsonStr
    } catch (e) {
      return '[复杂对象]'
    }
  }
  if (typeof value === 'boolean') {
    return value ? 'true' : 'false'
  }
  return String(value)
}

// 获取单元格tooltip内容
const getCellTooltip = (value) => {
  if (value === null || value === undefined) {
    return 'NULL'
  }
  if (typeof value === 'object') {
    try {
      return JSON.stringify(value, null, 2)
    } catch (e) {
      return String(value)
    }
  }
  return String(value)
}

// 根据列类型获取最小列宽
const getColumnWidth = (type) => {
  if (!type) return 120

  const typeLower = type.toLowerCase()
  if (typeLower.includes('text') || typeLower.includes('varchar')) {
    return 200
  } else if (typeLower.includes('int') || typeLower.includes('decimal') || typeLower.includes('numeric')) {
    return 120
  } else if (typeLower.includes('date') || typeLower.includes('time')) {
    return 180
  } else if (typeLower.includes('bool')) {
    return 80
  }
  return 150
}

// 获取固定列宽（用于避免内容被过度截断）
const getColumnFixedWidth = (type) => {
  if (!type) return undefined // 使用默认宽度

  const typeLower = type.toLowerCase()
  // 对于较长的数据类型，不设置固定宽度，让列自适应
  if (typeLower.includes('text') || typeLower.includes('varchar') || typeLower.includes('json')) {
    return undefined // 使用min-width，允许内容撑开
  }
  // 对于数值类型，设置固定宽度以对齐
  if (typeLower.includes('int') || typeLower.includes('decimal') || typeLower.includes('numeric')) {
    return 150
  }
  return undefined
}

// 检查数据完整性
const checkDataCompleteness = () => {
  if (!tableDataRows.value || tableDataRows.value.length === 0) {
    return '无数据'
  }

  // 检查是否有NULL值
  let nullCount = 0
  let totalCells = 0

  tableDataRows.value.forEach(row => {
    tableDataColumns.value.forEach(column => {
      totalCells++
      const value = row[column.name]
      if (value === null || value === undefined) {
        nullCount++
      }
    })
  })

  const completeness = ((1 - nullCount / totalCells) * 100).toFixed(1)

  if (nullCount === 0) {
    return `完整 (100% - ${totalCells} 个单元格)`
  } else {
    return `${completeness}% (${nullCount}/${totalCells} 个单元格为空)`
  }
}

const handleSizeChange = (val) => {
  pageSize.value = val
  currentPage.value = 1
  loadTables()
}

const handlePageChange = (val) => {
  currentPage.value = val
  loadTables()
}


// 返回上一页
const handleGoBack = () => {
  router.back()
}

// 初始化
onMounted(async () => {
  console.log('%c========== [DatabaseTablesView] 页面初始化 ==========', 'color: #409eff; font-weight: bold')
  console.log('%cprobeKey:', 'color: #67c23a; font-weight: bold', probeKey)
  console.log('%c默认数据库类型:', 'color: #e6a23c;', props.databaseType)
  console.log('%c路由查询参数:', 'color: #e6a23c;', route.query)
  console.log('%cinstanceId (route.query.instanceId):', 'color: #e6a23c;', route.query.instanceId)
  console.log('%cinstanceName (route.query.instanceName):', 'color: #e6a23c;', route.query.instanceName)

  // ✅ 从 localStorage 恢复数据库实例选择
  restoreDatabaseInstance()

  // 如果 localStorage 未恢复到 instanceId，尝试从后端获取
  if (!instanceId.value) {
    try {
      const resp = await databaseProbeApi.getInstances(probeKey)
      if (resp.code === 200 && resp.data) {
        const savedConnectionId = resp.data.currentConnectionId
        if (savedConnectionId) {
          const instList = resp.data.instances || []
          const saved = instList.find(inst => inst.id === savedConnectionId)
          if (saved) {
            instanceId.value = saved.databaseName
            instanceName.value = saved.name
            console.log('%c✓ 从后端恢复数据库实例:', 'color: #67c23a; font-weight: bold', saved.databaseName)
          }
        }
      }
    } catch (err) {
      console.warn('%c⚠ 从后端恢复实例失败:', 'color: #e6a23c;', err.message)
    }
  }

  console.log('%c恢复后的 instanceId.value:', 'color: #67c23a;', instanceId.value)
  console.log('%c恢复后的 instanceName.value:', 'color: #67c23a;', instanceName.value)

  // 检查是否成功获取到 instanceId
  if (!instanceId.value) {
    console.error('%c✗ 警告: instanceId 为空，可能导致数据查询失败！', 'color: #f56c6c; font-weight: bold')
    console.error('%c✗ 请检查路由参数是否正确传递: /database/' + probeKey + '?instanceId=xxx', 'color: #f56c6c;')
    ElMessage.warning('⚠️ 未指定数据库实例，请从数据库探针详情页访问')
  } else {
    console.log('%c✓ instanceId 设置成功:', 'color: #67c23a; font-weight: bold', instanceId.value)
  }

  ElMessage({
    message: '正在加载数据库表列表...',
    type: 'info',
    duration: 2000
  })

  loadTables()

  console.log('%c=======================================================', 'color: #909399')
})

// 暴露刷新方法供父组件调用
defineExpose({
  refresh: loadTables
})
</script>

<style scoped lang="scss">
.database-tables-view {
    padding: 20px;

    .header {
      display: flex;
      justify-content: space-between;
      align-items: center;
    margin-bottom: 20px;
    padding-bottom: 15px;
    border-bottom: 1px solid #EBEEF5;

    .header-left {
      display: flex;
      align-items: center;
      gap: 12px;

      h3 {
        margin: 0;
        font-size: 18px;
        font-weight: 500;
        color: #303133;
        display: flex;
        align-items: center;
        gap: 8px;
      }
    }

    .header-info {
      display: flex;
      align-items: center;
      gap: 10px;

      .database-name {
        font-size: 14px;
        color: #606266;
        font-weight: 500;
      }
    }
  }

  .filter-bar {
    display: flex;
    align-items: center;
    margin-bottom: 15px;
    padding: 15px;
    background-color: #F5F7FA;
    border-radius: 4px;
    gap: 10px;
  }

  .pagination-wrapper {
    display: flex;
    justify-content: center;
    margin-top: 20px;
  }

  // 表数据对话框样式
  .table-data-container {
    .table-data-info {
      display: flex;
      gap: 10px;
      margin-bottom: 16px;
      padding: 12px;
      background-color: var(--el-fill-color-light);
      border-radius: 6px;
    }

    .table-data-pagination {
      display: flex;
      justify-content: center;
      margin-top: 20px;
      padding-top: 16px;
      border-top: 1px solid var(--el-border-color-lighter);
    }

    :deep(.el-table) {
      font-size: 13px;

      .el-table__header th {
        background-color: var(--el-fill-color-light);
        font-weight: 600;
        padding: 12px 8px;
      }

      .el-table__body td {
        font-family: 'Courier New', Courier, monospace;
        font-size: 12px;
        padding: 8px;
        line-height: 1.5;
      }

      // 改善单元格内容显示
      .table-cell-content {
        word-break: break-word;
        white-space: pre-wrap;
        line-height: 1.6;
        max-width: 100%;
        overflow: hidden;
        text-overflow: ellipsis;
      }

      // 改善空值显示
      .cell:has(.table-cell-content) {
        color: #606266;
      }
    }

    // 添加数据完整性提示
    .data-completeness-info {
      margin-top: 12px;
      padding: 10px;
      background-color: #f0f9ff;
      border-left: 3px solid #409eff;
      border-radius: 4px;
      font-size: 12px;
      color: #606266;

      .info-item {
        margin: 4px 0;
        display: flex;
        align-items: center;
        gap: 8px;

        .label {
          font-weight: 600;
          color: #303133;
        }
      }
    }
  }
}
</style>
