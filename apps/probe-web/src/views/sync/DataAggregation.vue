<template>
  <div class="data-aggregation-page">
    <div class="page-header">
      <h2>数据汇聚</h2>
      <el-button @click="refreshAll" :loading="loading">
        <el-icon><Refresh /></el-icon> 刷新
      </el-button>
    </div>

    <!-- 同步概览统计 -->
    <el-row :gutter="16" class="stats-row">
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="stat-value">{{ stats.totalSources ?? '-' }}</div>
          <div class="stat-label">数据源</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="stat-value" style="color: #67c23a">{{ stats.syncedTables ?? '-' }}</div>
          <div class="stat-label">已同步表</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="stat-value" style="color: #e6a23c">{{ stats.pendingTables ?? '-' }}</div>
          <div class="stat-label">待同步表</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="stat-value" style="color: #f56c6c">{{ stats.failedTables ?? '-' }}</div>
          <div class="stat-label">同步失败</div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <!-- 左侧：数据源列表 -->
      <el-col :span="8">
        <el-card shadow="never" class="source-list-card">
          <template #header>
            <span>数据源</span>
          </template>
          <div v-loading="sourcesLoading">
            <div
              v-for="source in datasources"
              :key="source.source_id || source.id"
              class="source-item"
              :class="{ active: selectedSource === source }"
              @click="selectSource(source)"
            >
              <div class="source-info">
                <el-icon class="source-icon" :style="{ color: dbTypeColor(source.database_type || source.databaseType) }">
                  <Coin />
                </el-icon>
                <div>
                  <div class="source-name">{{ source.source_name || source.name }}</div>
                  <div class="source-meta">
                    {{ source.database_type || source.databaseType }} · {{ source.host || source.databaseHost }}:{{ source.port || source.databasePort }}
                  </div>
                </div>
              </div>
            </div>
            <el-empty v-if="!sourcesLoading && datasources.length === 0" description="暂无数据源" />
          </div>
        </el-card>
      </el-col>

      <!-- 右侧：表列表 + 数据浏览 -->
      <el-col :span="16">
        <el-card shadow="never" v-if="!selectedSource">
          <el-empty description="请从左侧选择一个数据源" />
        </el-card>

        <template v-else>
          <!-- 表列表 -->
          <el-card shadow="never" class="table-list-card">
            <template #header>
              <div class="card-header-row">
                <span>{{ selectedSource.source_name || selectedSource.name }} - 表列表</span>
                <el-input
                  v-model="tableSearch"
                  placeholder="搜索表名"
                  clearable
                  style="width: 200px"
                  size="small"
                  @clear="loadTables"
                  @keyup.enter="loadTables"
                />
              </div>
            </template>
            <el-table
              :data="tables"
              v-loading="tablesLoading"
              stripe
              size="small"
              @row-click="selectTable"
              highlight-current-row
              style="cursor: pointer"
            >
              <el-table-column prop="tableName" label="表名" min-width="160" show-overflow-tooltip>
                <template #default="{ row }">
                  {{ row.table_name || row.tableName }}
                </template>
              </el-table-column>
              <el-table-column prop="databaseName" label="数据库" width="120" show-overflow-tooltip>
                <template #default="{ row }">
                  {{ row.database_name || row.databaseName || row.schema_name || '-' }}
                </template>
              </el-table-column>
              <el-table-column label="行数" width="100" align="right">
                <template #default="{ row }">
                  {{ (row.row_count ?? row.rowCount) != null ? Number(row.row_count ?? row.rowCount).toLocaleString() : '-' }}
                </template>
              </el-table-column>
              <el-table-column label="列数" width="80" align="right">
                <template #default="{ row }">
                  {{ (row.column_count ?? row.columnCount) != null ? row.column_count ?? row.columnCount : '-' }}
                </template>
              </el-table-column>
              <el-table-column label="更新时间" width="170">
                <template #default="{ row }">
                  {{ row.updated_at || row.synced_at || row.updateTime || '-' }}
                </template>
              </el-table-column>
            </el-table>
            <div class="pagination">
              <el-pagination
                v-model:current-page="tablePageNum"
                v-model:page-size="tablePageSize"
                :total="tableTotal"
                :page-sizes="[10, 15, 30, 50]"
                layout="total, sizes, prev, pager, next, jumper"
                size="small"
                @size-change="loadTables"
                @current-change="loadTables"
              />
            </div>
          </el-card>

          <!-- 表数据浏览 -->
          <el-card shadow="never" class="data-card" v-if="selectedTable">
            <template #header>
              <div class="card-header-row">
                <span>{{ selectedTable.table_name || selectedTable.tableName }} - 数据浏览</span>
                <el-pagination
                  v-model:current-page="dataPageNum"
                  v-model:page-size="dataPageSize"
                  :total="dataTotal"
                  :page-sizes="[10, 20, 50]"
                  layout="total, sizes, prev, pager, next, jumper"
                  size="small"
                  @size-change="loadTableData"
                  @current-change="loadTableData"
                />
              </div>
            </template>
            <el-table
              :data="tableData"
              v-loading="dataLoading"
              stripe
              size="small"
              max-height="400"
            >
              <el-table-column
                v-for="col in tableColumns"
                :key="col"
                :prop="col"
                :label="col"
                min-width="140"
                show-overflow-tooltip
              />
            </el-table>
          </el-card>
        </template>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
/**
 * 数据汇聚页面
 *
 * 页面布局：左右分栏
 * - 左侧：数据源列表（从 aggregation.data_source_registry 加载）
 * - 右侧上：选中数据源的表列表（从 aggregation.table_metadata 加载，支持搜索和分页）
 * - 右侧下：选中表的数据浏览（动态连接原始数据源查询实际数据）
 *
 * 数据流：
 * 1. 页面加载 → 并行加载统计信息 + 数据源列表
 * 2. 点击数据源 → 加载该数据源下的表列表（带分页）
 * 3. 点击表行 → 动态连接数据源查询表数据（后端根据 database_type 构建 JDBC 连接）
 */
import { ref, onMounted } from 'vue'
import { Refresh, Coin } from '@element-plus/icons-vue'
import { aggregationApi } from '@/api/aggregation'

// ========== 加载状态 ==========
const loading = ref(false)         // 全局刷新按钮
const sourcesLoading = ref(false)  // 数据源列表加载中
const tablesLoading = ref(false)   // 表列表加载中
const dataLoading = ref(false)     // 表数据加载中

// ========== 统计数据 ==========
const stats = ref({})

// ========== 数据源选择状态 ==========
const datasources = ref([])
const selectedSource = ref(null)

// ========== 表列表状态 ==========
const tables = ref([])
const selectedTable = ref(null)
const tableSearch = ref('')
const tablePageNum = ref(1)
const tablePageSize = ref(15)  // 默认每页15条，适配屏幕高度
const tableTotal = ref(0)

// ========== 表数据浏览状态 ==========
const tableData = ref([])       // 当前页数据行
const tableColumns = ref([])    // 列名列表（动态从返回数据中提取）
const dataPageNum = ref(1)
const dataPageSize = ref(20)
const dataTotal = ref(0)

// 根据数据库类型返回对应品牌颜色（用于数据源图标）
function dbTypeColor(type) {
  const colors = { mysql: '#4479A1', postgresql: '#336791', oracle: '#F80000', sqlserver: '#CC2927' }
  return colors[(type || '').toLowerCase()] || '#909399'
}

// 字节数格式化为人类可读字符串
function formatSize(bytes) {
  if (bytes == null) return '-'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1048576) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / 1048576).toFixed(1) + ' MB'
}

// 刷新全部数据（统计 + 数据源列表）
function refreshAll() {
  loading.value = true
  Promise.all([loadStats(), loadDatasources()]).finally(() => {
    loading.value = false
  })
}

// 加载汇聚统计（数据源数、已同步表数、不合格记录数）
async function loadStats() {
  try {
    const res = await aggregationApi.getStats()
    const d = res.data?.data || res.data || {}
    stats.value = {
      totalSources: d.dataSourceCount ?? 0,
      syncedTables: d.tableCount ?? 0,
      pendingTables: 0,
      failedTables: d.badRecordCount ?? 0
    }
  } catch (e) {
    console.warn('加载统计失败', e)
  }
}

// 加载数据源列表（从汇聚库读取所有已注册的数据源）
async function loadDatasources() {
  sourcesLoading.value = true
  try {
    const res = await aggregationApi.getDatasources()
    const data = res.data?.data || res.data || []
    datasources.value = Array.isArray(data) ? data : []
  } catch (e) {
    console.warn('加载数据源失败', e)
    datasources.value = []
  } finally {
    sourcesLoading.value = false
  }
}

// 选中数据源：重置表选择状态，加载该数据源的表列表
async function selectSource(source) {
  selectedSource.value = source
  selectedTable.value = null
  tableData.value = []
  tableColumns.value = []
  tablePageNum.value = 1
  await loadTables()
}

// 加载表列表（服务端分页 + 搜索）
// 后端返回 { records: [...], total: N }，total 用于分页组件显示总页数
async function loadTables() {
  if (!selectedSource.value) return
  tablesLoading.value = true
  try {
    const sourceId = selectedSource.value.source_id || selectedSource.value.id || selectedSource.value.name
    const res = await aggregationApi.getTables(sourceId, tableSearch.value, tablePageNum.value, tablePageSize.value)
    const data = res.data?.data || res.data || {}
    const records = data.records || (Array.isArray(data) ? data : [])
    tables.value = records
    tableTotal.value = data.total ?? (Array.isArray(data) ? data.length : 0)
  } catch (e) {
    console.warn('加载表列表失败', e)
    tables.value = []
  } finally {
    tablesLoading.value = false
  }
}

// 选中表行：加载该表的实际数据
async function selectTable(row) {
  selectedTable.value = row
  dataPageNum.value = 1
  await loadTableData()
}

// 加载表数据（后端动态连接原始数据源执行 SELECT 查询）
// 列名从返回数据的第一行 keys 自动提取，无需额外的列信息接口
async function loadTableData() {
  if (!selectedSource.value || !selectedTable.value) return
  dataLoading.value = true
  try {
    const sourceId = selectedSource.value.source_id || selectedSource.value.id
    const tableName = selectedTable.value.table_name || selectedTable.value.tableName
    const res = await aggregationApi.getTableData(sourceId, tableName, dataPageNum.value, dataPageSize.value)
    const data = res.data?.data || res.data || {}
    const rows = data.records || data.rows || data.list || []
    const cols = data.columns || (rows.length > 0 ? Object.keys(rows[0]) : [])
    tableData.value = rows
    tableColumns.value = cols
    dataTotal.value = data.total || rows.length
  } catch (e) {
    console.warn('加载表数据失败', e)
    tableData.value = []
    tableColumns.value = []
  } finally {
    dataLoading.value = false
  }
}

// 页面挂载时加载初始数据
onMounted(() => {
  refreshAll()
})
</script>

<style scoped>
.data-aggregation-page {
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
  font-size: 18px;
}

.stats-row {
  margin-bottom: 20px;
}

.stat-card {
  text-align: center;
}

.stat-value {
  font-size: 28px;
  font-weight: 600;
  line-height: 1.2;
}

.stat-label {
  font-size: 13px;
  color: #909399;
  margin-top: 4px;
}

.source-list-card {
  min-height: 500px;
}

.source-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 12px;
  border-radius: 6px;
  cursor: pointer;
  transition: background 0.2s;
}

.source-item:hover {
  background: #f5f7fa;
}

.source-item.active {
  background: #ecf5ff;
}

.source-info {
  display: flex;
  align-items: center;
  gap: 10px;
}

.source-icon {
  font-size: 22px;
}

.source-name {
  font-size: 14px;
  font-weight: 500;
}

.source-meta {
  font-size: 12px;
  color: #909399;
}

.table-list-card {
  margin-bottom: 16px;
}

.card-header-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.data-card {
  margin-bottom: 16px;
}

.pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}
</style>
