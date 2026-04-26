<template>
  <div class="datasource-page">
    <div class="page-header">
      <h2>统一数据源管理</h2>
      <div class="header-actions">
        <el-radio-group v-model="activeType" size="small" @change="filterSources">
          <el-radio-button value="ALL">全部</el-radio-button>
          <el-radio-button value="RELATIONAL_DB">关系数据库</el-radio-button>
          <el-radio-button value="NOSQL">NoSQL</el-radio-button>
          <el-radio-button value="FILE">文件</el-radio-button>
        </el-radio-group>
        <el-button type="primary" size="small" @click="openCreateDialog" style="margin-left: 12px;">新增数据源</el-button>
      </div>
    </div>

    <!-- 数据源类型概览 -->
    <el-row :gutter="16" class="overview-row">
      <el-col :span="4" v-for="item in typeCards" :key="item.type">
        <el-card shadow="never" class="type-card" :class="{ active: activeType === item.type }" @click="activeType = item.type; filterSources()">
          <div class="type-icon" :style="{ background: item.bg }">
            <el-icon :size="22" color="#fff"><component :is="item.icon" /></el-icon>
          </div>
          <div class="type-info">
            <div class="type-name">{{ item.name }}</div>
            <div class="type-count">{{ item.count }} 个数据源</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 数据源列表 -->
    <el-card shadow="never">
      <el-table :data="filteredSources" v-loading="loading" stripe>
        <el-table-column prop="name" label="名称" min-width="140" show-overflow-tooltip />
        <el-table-column prop="category" label="类型" width="120">
          <template #default="{ row }">
            <el-tag size="small" :type="categoryTagColor(row.category)">{{ categoryLabel(row.category) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="subType" label="子类型" width="110">
          <template #default="{ row }">
            <span>{{ subTypeLabel(row.subType) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="host" label="地址" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.host }}{{ row.port ? ':' + row.port : '' }}
          </template>
        </el-table-column>
        <el-table-column prop="database" label="数据库" min-width="120" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 'online' ? 'success' : row.status === 'offline' ? 'danger' : 'info'" effect="dark">
              {{ row.status === 'online' ? '在线' : row.status === 'offline' ? '离线' : '未知' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="updatedAt" label="最近更新" width="170">
          <template #default="{ row }">
            {{ formatTime(row.updatedAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.sourceKind === 'db'" link type="primary" size="small" @click="testSource(row)" :loading="row._testing">测试</el-button>
            <el-button link type="primary" size="small" @click="openEditDialog(row)">编辑</el-button>
            <el-popconfirm title="确定删除该数据源？" @confirm="removeSource(row)">
              <template #reference>
                <el-button link type="danger" size="small">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新增/编辑数据源对话框 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑数据源' : '新增数据源'" width="560px" destroy-on-close @closed="resetForm">
      <el-form :model="form" label-width="90px" :rules="formRules" ref="formRef">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="数据源名称" />
        </el-form-item>
        <el-form-item label="数据源类型" prop="sourceKind">
          <el-select v-model="form.sourceKind" style="width: 100%" @change="onSourceKindChange" :disabled="isEdit">
            <el-option label="数据库" value="db" />
            <el-option label="文件系统" value="file" />
            <el-option label="HTTP API" value="httpapi" />
          </el-select>
        </el-form-item>
        <template v-if="form.sourceKind === 'db'">
          <el-form-item label="数据库类型" prop="databaseType">
            <el-select v-model="form.databaseType" style="width: 100%" @change="onDbTypeChange" :disabled="isEdit">
              <el-option-group label="关系数据库">
                <el-option label="MySQL" value="MySQL" />
                <el-option label="PostgreSQL" value="PostgreSQL" />
                <el-option label="Oracle" value="Oracle" />
                <el-option label="SQL Server" value="SQL Server" />
              </el-option-group>
            </el-select>
          </el-form-item>
          <el-form-item label="主机" prop="host">
            <el-input v-model="form.host" placeholder="IP或域名" />
          </el-form-item>
          <el-form-item label="端口" prop="port">
            <el-input v-model.number="form.port" :placeholder="dbDefaultPort" />
          </el-form-item>
          <el-form-item label="数据库名" prop="database">
            <el-input v-model="form.database" placeholder="数据库名" />
          </el-form-item>
          <el-form-item label="用户名" prop="username">
            <el-input v-model="form.username" />
          </el-form-item>
          <el-form-item label="密码">
            <el-input v-model="form.password" type="password" show-password placeholder="留空则不修改" />
          </el-form-item>
        </template>
        <template v-if="form.sourceKind === 'file'">
          <el-form-item label="文件类型" prop="fileType">
            <el-select v-model="form.fileType" style="width: 100%">
              <el-option label="本地文件" value="local" />
              <el-option label="FTP/SFTP" value="ftp" />
              <el-option label="MinIO/S3" value="minio" />
            </el-select>
          </el-form-item>
          <el-form-item label="名称" prop="name">
            <el-input v-model="form.name" placeholder="探针显示名称" />
          </el-form-item>
          <el-form-item label="扫描路径">
            <el-input v-model="form.scanPath" placeholder="文件扫描路径" />
          </el-form-item>
          <el-form-item label="Agent" prop="agentCode">
            <el-input v-model="form.agentCode" placeholder="分配的 Agent 编码" />
          </el-form-item>
        </template>
        <template v-if="form.sourceKind === 'httpapi'">
          <el-form-item label="名称" prop="name">
            <el-input v-model="form.name" placeholder="数据源名称" />
          </el-form-item>
          <el-form-item label="API地址" prop="httpUrl">
            <el-input v-model="form.httpUrl" placeholder="https://api.example.com/data" />
          </el-form-item>
          <el-form-item label="请求方法">
            <el-select v-model="form.httpMethod" style="width: 100%">
              <el-option label="GET" value="GET" />
              <el-option label="POST" value="POST" />
            </el-select>
          </el-form-item>
          <el-form-item label="认证方式">
            <el-select v-model="form.httpAuthType" style="width: 100%">
              <el-option label="无认证" value="NONE" />
              <el-option label="Bearer Token" value="BEARER" />
              <el-option label="API Key" value="API_KEY" />
              <el-option label="Basic Auth" value="BASIC" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="form.httpAuthType === 'BEARER'" label="Token">
            <el-input v-model="form.httpAuthToken" placeholder="Bearer Token" />
          </el-form-item>
          <el-form-item v-if="form.httpAuthType === 'API_KEY'" label="API Key">
            <el-input v-model="form.httpAuthKey" placeholder="API Key值" />
          </el-form-item>
          <el-form-item label="数据路径">
            <el-input v-model="form.httpResponsePath" placeholder="JSONPath，如 data.items（留空自动检测）" />
          </el-form-item>
          <el-form-item label="Agent" prop="agentCode">
            <el-input v-model="form.agentCode" placeholder="分配的 Agent 编码" />
          </el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitForm" :loading="submitting">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import datasourceApi from '@/api/datasource.js'
import { createHttpApiSource, updateHttpApiSource } from '@/api/httpApiSource'

const loading = ref(false)
const submitting = ref(false)
const activeType = ref('ALL')
const dbSources = ref([])
const fileSources = ref([])
const dialogVisible = ref(false)
const isEdit = ref(false)
const editingRow = ref(null)
const formRef = ref(null)

const formRules = {
  name: [{ required: true, message: '请填写名称', trigger: 'blur' }],
  sourceKind: [{ required: true, message: '请选择数据源类型', trigger: 'change' }],
  host: [{ required: true, message: '请填写主机地址', trigger: 'blur' }],
  databaseType: [{ required: true, message: '请选择数据库类型', trigger: 'change' }],
  agentCode: [{ required: true, message: '请填写Agent编码', trigger: 'blur' }]
}

const dbPortMap = { 'MySQL': 3306, 'PostgreSQL': 5432, 'Oracle': 1521, 'SQL Server': 1433 }

const dbDefaultPort = computed(() => dbPortMap[form.databaseType] || '')

const form = reactive({
  sourceKind: 'db',
  name: '',
  databaseType: 'MySQL',
  host: '',
  port: null,
  database: '',
  username: '',
  password: '',
  fileType: 'local',
  scanPath: '',
  agentCode: '',
  httpUrl: '',
  httpMethod: 'GET',
  httpAuthType: 'NONE',
  httpAuthToken: '',
  httpAuthKey: '',
  httpResponsePath: ''
})

// 统一数据源列表（合并数据库和文件）
const allSources = computed(() => {
  const dbs = dbSources.value.map(s => ({
    id: s.id,
    name: s.name,
    category: 'RELATIONAL_DB',
    subType: s.databaseType?.toLowerCase() || '',
    host: s.databaseHost || '',
    port: s.databasePort,
    database: s.databaseName || '',
    status: s.isActive !== false ? 'online' : 'offline',
    updatedAt: s.updatedAt || s.createdAt,
    sourceKind: 'db',
    _raw: s
  }))
  const files = fileSources.value.map(s => ({
    id: s.id,
    name: s.name,
    category: 'FILE',
    subType: 'local',
    host: s.hostIp || '',
    port: s.port,
    database: '',
    status: s.status || 'unknown',
    updatedAt: s.updateTime || s.createTime,
    sourceKind: 'file',
    _raw: s
  }))
  return [...dbs, ...files]
})

const typeCards = computed(() => {
  const counts = { ALL: allSources.value.length }
  for (const s of allSources.value) {
    counts[s.category] = (counts[s.category] || 0) + 1
  }
  return [
    { type: 'ALL', name: '全部', icon: 'Menu', bg: '#909399', count: counts.ALL || 0 },
    { type: 'RELATIONAL_DB', name: '关系数据库', icon: 'Coin', bg: '#409EFF', count: counts.RELATIONAL_DB || 0 },
    { type: 'NOSQL', name: 'NoSQL', icon: 'Grid', bg: '#67C23A', count: counts.NOSQL || 0 },
    { type: 'FILE', name: '文件/协议', icon: 'FolderOpened', bg: '#E6A23C', count: counts.FILE || 0 },
  ]
})

const filteredSources = computed(() => {
  if (activeType.value === 'ALL') return allSources.value
  return allSources.value.filter(s => s.category === activeType.value)
})

onMounted(() => {
  loadSources()
})

async function loadSources() {
  loading.value = true
  try {
    const [dbRes, fileRes] = await Promise.allSettled([
      datasourceApi.getDbSources(),
      datasourceApi.getFileSources()
    ])
    if (dbRes.status === 'fulfilled' && dbRes.value?.data) {
      dbSources.value = dbRes.value.data
    } else {
      dbSources.value = []
    }
    if (fileRes.status === 'fulfilled' && fileRes.value?.data) {
      const fd = fileRes.value.data
      fileSources.value = Array.isArray(fd) ? fd : (fd.records || fd.list || [])
    } else {
      fileSources.value = []
    }
  } catch (e) {
    console.error('加载数据源失败', e)
  } finally {
    loading.value = false
  }
}

function filterSources() {
  // filteredSources is computed, just triggers reactivity
}

function openCreateDialog() {
  isEdit.value = false
  editingRow.value = null
  dialogVisible.value = true
}

function openEditDialog(row) {
  isEdit.value = true
  editingRow.value = row
  const raw = row._raw
  if (row.sourceKind === 'db') {
    form.sourceKind = 'db'
    form.name = raw.name || ''
    form.databaseType = raw.databaseType || 'MySQL'
    form.host = raw.databaseHost || ''
    form.port = raw.databasePort
    form.database = raw.databaseName || ''
    form.username = raw.username || ''
    form.password = ''
  } else {
    form.sourceKind = 'file'
    form.name = raw.name || ''
    form.scanPath = raw.config ? JSON.parse(raw.config).scanPath || '' : ''
    form.agentCode = raw.agentCode || ''
    form.fileType = 'local'
  }
  dialogVisible.value = true
}

function resetForm() {
  Object.assign(form, {
    sourceKind: 'db', name: '', databaseType: 'MySQL', host: '', port: null,
    database: '', username: '', password: '', fileType: 'local', scanPath: '', agentCode: '',
    httpUrl: '', httpMethod: 'GET', httpAuthType: 'NONE', httpAuthToken: '', httpAuthKey: '', httpResponsePath: ''
  })
  isEdit.value = false
  editingRow.value = null
}

function onSourceKindChange() {
  form.databaseType = 'MySQL'
  form.fileType = 'local'
}

function onDbTypeChange(val) {
  if (!form.port) {
    form.port = dbPortMap[val] || null
  }
}

async function submitForm() {
  if (!form.name) {
    ElMessage.warning('请填写名称')
    return
  }
  submitting.value = true
  try {
    if (form.sourceKind === 'db') {
      if (isEdit.value) {
        const data = {
          name: form.name,
          databaseType: form.databaseType,
          databaseHost: form.host,
          databasePort: form.port,
          databaseName: form.database,
          username: form.username
        }
        if (form.password) data.password = form.password
        await datasourceApi.updateDbSource(editingRow.value.id, data)
        ElMessage.success('数据源已更新')
      } else {
        await datasourceApi.createDbSource({
          name: form.name,
          databaseType: form.databaseType,
          databaseHost: form.host,
          databasePort: form.port || dbPortMap[form.databaseType],
          databaseName: form.database,
          username: form.username,
          password: form.password,
          isActive: true
        })
        ElMessage.success('数据源已创建')
      }
    } else {
      if (isEdit.value) {
        await datasourceApi.updateFileSource(editingRow.value.id, {
          name: form.name,
          config: JSON.stringify({ scanPath: form.scanPath }),
          agentCode: form.agentCode
        })
        ElMessage.success('文件数据源已更新')
      } else {
        await datasourceApi.createFileSource({
          name: form.name,
          agentCode: form.agentCode,
          config: JSON.stringify({ scanPath: form.scanPath, fileExtensions: '*', ignorePaths: '', maxDepth: 5 })
        })
        ElMessage.success('文件数据源已创建')
      }
    }
    if (form.sourceKind === 'httpapi') {
      const authConfig = {}
      if (form.httpAuthType === 'BEARER') authConfig.token = form.httpAuthToken
      else if (form.httpAuthType === 'API_KEY') { authConfig.headerName = 'X-API-Key'; authConfig.apiKey = form.httpAuthKey }
      else if (form.httpAuthType === 'BASIC') { authConfig.username = ''; authConfig.password = '' }
      const payload = {
        name: form.name, agentCode: form.agentCode,
        url: form.httpUrl, method: form.httpMethod,
        authType: form.httpAuthType,
        authConfig: Object.keys(authConfig).length ? JSON.stringify(authConfig) : null,
        responsePath: form.httpResponsePath || null
      }
      if (isEdit.value) {
        await updateHttpApiSource(editingRow.value.id, payload)
        ElMessage.success('HTTP API数据源已更新')
      } else {
        await createHttpApiSource(payload)
        ElMessage.success('HTTP API数据源已创建')
      }
    }
    dialogVisible.value = false
    loadSources()
  } catch (e) {
    console.error('保存失败', e)
  } finally {
    submitting.value = false
  }
}

async function testSource(row) {
  row._testing = true
  try {
    const raw = row._raw
    const res = await datasourceApi.testDbConnection({
      databaseType: raw.databaseType,
      databaseHost: raw.databaseHost,
      databasePort: raw.databasePort,
      databaseName: raw.databaseName,
      username: raw.username
    })
    const success = res.data?.success
    if (success) {
      ElMessage.success('连接测试成功')
      row.status = 'online'
    } else {
      ElMessage.error('连接测试失败: ' + (res.data?.message || '未知错误'))
      row.status = 'offline'
    }
  } catch (e) {
    ElMessage.error('连接测试失败')
    row.status = 'offline'
  } finally {
    row._testing = false
  }
}

async function removeSource(row) {
  try {
    if (row.sourceKind === 'db') {
      await datasourceApi.deleteDbSource(row.id)
    } else {
      await datasourceApi.deleteFileSource(row.id)
    }
    ElMessage.success('数据源已删除')
    loadSources()
  } catch (e) {
    console.error('删除失败', e)
  }
}

function formatTime(t) {
  if (!t) return '-'
  if (typeof t === 'string') return t.replace('T', ' ').substring(0, 19)
  return '-'
}

function categoryLabel(cat) {
  const map = { RELATIONAL_DB: '关系数据库', NOSQL: 'NoSQL', FILE: '文件/协议' }
  return map[cat] || cat || '-'
}

function subTypeLabel(sub) {
  const map = { mysql: 'MySQL', postgresql: 'PostgreSQL', oracle: 'Oracle', 'sql server': 'SQL Server', 'sqlserver': 'SQL Server', sqlite: 'SQLite', dm: '达梦', mongodb: 'MongoDB', redis: 'Redis', local: '本地文件', minio: 'MinIO', ftp: 'FTP' }
  return map[(sub || '').toLowerCase()] || sub || '-'
}

function categoryTagColor(cat) {
  const map = { RELATIONAL_DB: '', NOSQL: 'success', FILE: 'warning' }
  return map[cat] || 'info'
}
</script>

<style scoped>
.datasource-page { padding: 0; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.page-header h2 { margin: 0; font-size: 18px; }
.header-actions { display: flex; align-items: center; }
.overview-row { margin-bottom: 16px; }
.type-card {
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px;
  transition: border-color 0.2s;
}
.type-card.active { border-color: var(--el-color-primary); }
.type-card:hover { border-color: var(--el-color-primary-light-5); }
.type-icon {
  width: 42px;
  height: 42px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.type-name { font-size: 14px; font-weight: 500; }
.type-count { font-size: 12px; color: #909399; margin-top: 2px; }
</style>
