<template>
  <div class="database-probe-detail">
    <!-- 页面头部 -->
    <div class="page-header">
      <el-page-header @back="goBack">
        <template #content>
          <div class="header-content">
            <el-icon class="probe-icon" :class="`type-${probeInfo.type?.toLowerCase()}`">
              <component :is="getProbeIcon(probeInfo.type)" />
            </el-icon>
            <div class="probe-info">
              <h2 class="probe-name">{{ probeInfo.name }}</h2>
              <p class="probe-meta">
                <el-tag :type="getStatusType(probeInfo.status)" size="small">
                  {{ getStatusText(probeInfo.status) }}
                </el-tag>
                <span class="probe-key">{{ probeInfo.probeKey }}</span>

                <!-- 数据库实例选择下拉菜单 -->
                <el-dropdown
                  trigger="click"
                  @command="handleInstanceSwitch"
                  :disabled="loading || instances.length === 0"
                  class="database-instance-dropdown"
                >
                  <span class="database-selector">
                    <el-icon v-if="loading" class="is-loading" style="margin-right: 4px;"><Loading /></el-icon>
                    <el-icon v-else class="database-icon" style="margin-right: 4px;"><DataLine /></el-icon>
                    <span class="current-instance">
                      {{ currentInstance ? currentInstance.databaseName : (instances.length > 0 ? '选择数据库实例' : '加载中...') }}
                    </span>
                    <el-icon class="el-icon--right" style="margin-left: 4px;"><ArrowDown /></el-icon>
                  </span>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <div v-if="instances.length === 0" style="padding: 10px; color: #909399; font-size: 12px;">
                        暂无数据库实例
                      </div>
                      <el-dropdown-item
                        v-for="instance in instances"
                        :key="instance.id"
                        :command="instance"
                        :class="{ 'is-active': currentInstance && currentInstance.id === instance.id }"
                        :disabled="loading"
                      >
                        <div style="line-height: 1.5;">
                          <div style="font-weight: bold;">{{ instance.name }}</div>
                          <div style="font-size: 11px; color: var(--el-text-color-secondary);">
                            {{ instance.databaseHost }}:{{ instance.databasePort }} / {{ instance.databaseName }}
                          </div>
                        </div>
                        <el-icon v-if="currentInstance && currentInstance.id === instance.id" style="margin-left: 8px; color: var(--el-color-success);"><Check /></el-icon>
                      </el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </p>
            </div>
          </div>
        </template>
      </el-page-header>
    </div>

    <!-- 操作栏 -->
    <div class="action-bar">
      <el-space>
        <el-button
          type="primary"
          :icon="Refresh"
          @click="refreshInstances"
          :loading="loading"
          :disabled="loading"
          size="default"
        >
          {{ loading ? '正在刷新...' : '刷新实例' }}
        </el-button>
        <el-button
          type="success"
          :icon="Upload"
          @click="showImportDialog"
          :disabled="loading"
          size="default"
        >
          导入数据
        </el-button>
        <el-button
          :icon="Setting"
          @click="showConfigDialog"
          size="default"
        >
          连接配置
        </el-button>
      </el-space>

      <!-- 加载状态提示 -->
      <div v-if="loading" class="loading-tip">
        <el-icon class="is-loading"><Loading /></el-icon>
        <span>正在从服务器获取实例列表...</span>
      </div>
    </div>

    <!-- 数据库实例列表 -->
    <el-card class="instances-card" v-loading="loading" element-loading-text="正在加载实例列表...">
      <template #header>
        <div class="card-header">
          <span>
            <el-icon><DataLine /></el-icon>
            数据库实例列表
          </span>
          <el-tag>共 {{ instances.length }} 个实例</el-tag>
        </div>
      </template>

      <!-- 初始化错误提示 -->
      <el-alert
        v-if="initError"
        :title="initError"
        type="error"
        :closable="false"
        show-icon
        style="margin-bottom: 16px"
      />

      <el-alert
        v-if="instances.length > 0"
        title="提示"
        type="info"
        :closable="false"
        style="margin-bottom: 16px"
      >
        请点击下方表格中的「查看表结构」按钮来查看对应数据库的表数据
      </el-alert>

      <el-table :data="instances" stripe border>
        <el-table-column prop="name" label="实例名称" width="220" />
        <el-table-column prop="databaseName" label="数据库名" width="150">
          <template #default="{ row }">
            <el-tag type="success">{{ row.databaseName }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="databaseHost" label="主机地址" width="150" />
        <el-table-column prop="databasePort" label="端口" width="100" align="center" />
        <el-table-column prop="username" label="用户名" width="120" />
        <el-table-column label="操作" width="200" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" size="small" @click="viewTables(row)">
              查看表结构
            </el-button>
            <el-button type="primary" plain size="small" @click="testConnection(row)">
              测试连接
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!loading && instances.length === 0" description="暂无数据库实例">
        <el-button type="primary" @click="showConfigDialog">
          配置数据库连接
        </el-button>
      </el-empty>
    </el-card>

    <!-- 连接配置对话框 -->
    <el-dialog
      v-model="configDialogVisible"
      title="数据库连接配置"
      width="800px"
      :close-on-click-modal="false"
    >
      <el-alert
        title="Agent管理数据库连接"
        type="info"
        :closable="false"
        show-icon
        style="margin-bottom: 16px"
      >
        <template #default>
          <p>数据库连接配置由Agent端统一管理，请前往Agent端进行配置。</p>
          <p>配置完成后，点击"刷新实例"按钮即可看到新添加的数据库实例。</p>
        </template>
      </el-alert>

      <el-descriptions :column="1" border>
        <el-descriptions-item label="Agent地址">
          {{ agentHost }}:{{ agentPort }}
        </el-descriptions-item>
        <el-descriptions-item label="配置文件位置">
          Agent配置目录/database-config.json
        </el-descriptions-item>
        <el-descriptions-item label="支持的数据库类型">
          {{ getDatabaseTypeLabel(probeInfo.config?.databaseType) }}
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>

    <!-- 数据导入对话框 -->
    <el-dialog
      v-model="importDialogVisible"
      title="导入数据"
      width="520px"
      :close-on-click-modal="false"
      @closed="resetImportForm"
    >
      <el-form label-width="100px">
        <el-form-item label="选择文件">
          <el-upload
            ref="importUploadRef"
            drag
            :auto-upload="false"
            :limit="1"
            accept=".xlsx,.xls,.csv"
            :on-change="handleImportFileChange"
            :on-remove="handleImportFileRemove"
          >
            <el-icon class="el-icon--upload"><Upload /></el-icon>
            <div class="el-upload__text">将文件拖到此处，或<em>点击上传</em></div>
            <template #tip>
              <div style="font-size: 12px; color: #909399;">支持 .xlsx / .xls / .csv 格式</div>
            </template>
          </el-upload>
        </el-form-item>
        <el-form-item label="目标表名">
          <el-input v-model="importForm.tableName" placeholder="输入要写入的表名" clearable />
        </el-form-item>
        <el-form-item label="连接">
          <el-select v-model="importForm.connectionId" placeholder="选择数据库连接" style="width: 100%">
            <el-option
              v-for="inst in instances"
              :key="inst.id"
              :label="inst.name"
              :value="inst.id"
            >
              <span>{{ inst.name }}</span>
              <span style="color: #909399; font-size: 12px; margin-left: 8px;">{{ inst.databaseName }}</span>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item v-if="importResult">
          <el-alert :title="importResult.title" :type="importResult.type" :closable="false" show-icon />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="importDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleImport" :loading="importing" :disabled="!importFile">
          开始导入
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Refresh, Setting, DataLine, Monitor, Coin, Loading, ArrowDown, Check, Upload } from '@element-plus/icons-vue'
import request from '@/api/request'
import { databaseProbeApi } from '@/api/databaseProbe'

const route = useRoute()
const router = useRouter()

const probeKey = route.params.probeKey || route.query.probeKey
const loading = ref(false)
const instances = ref([])
const configDialogVisible = ref(false)

// 数据导入
const importDialogVisible = ref(false)
const importFile = ref(null)
const importing = ref(false)
const importResult = ref(null)
const importUploadRef = ref(null)
const importForm = reactive({
  tableName: '',
  connectionId: null
})

// 当前选中的数据库实例
const currentInstance = ref(null)

// 页面加载状态
const pageLoading = ref(true)
const initError = ref('')

// 探针信息
const probeInfo = reactive({
  name: '',
  probeKey: '',
  type: '',
  status: '',
  config: {}
})

const agentHost = ref('localhost')
const agentPort = ref(58081)

// 获取探针图标
const getProbeIcon = (type) => {
  return type === 'DATABASE' ? Coin : Monitor
}

// 获取状态类型
const getStatusType = (status) => {
  const statusMap = {
    'online': 'success',
    'offline': 'danger',
    'error': 'warning'
  }
  return statusMap[status] || 'info'
}

// 获取状态文本
const getStatusText = (status) => {
  const statusMap = {
    'online': '在线',
    'offline': '离线',
    'error': '异常'
  }
  return statusMap[status] || '未知'
}

// 获取数据库类型标签
const getDatabaseTypeLabel = (dbType) => {
  const typeMap = {
    'mysql': 'MySQL',
    'postgresql': 'PostgreSQL',
    'oracle': 'Oracle',
    'sqlserver': 'SQL Server',
    'mongodb': 'MongoDB',
    'redis': 'Redis'
  }
  return typeMap[dbType] || dbType
}

// 格式化时间
const formatTime = (timestamp) => {
  if (!timestamp) return '-'
  const date = new Date(timestamp)
  return date.toLocaleString('zh-CN')
}

// 返回上一页
const goBack = () => {
  router.back()
}

// 获取探针详情
const fetchProbeInfo = async () => {
  try {
    console.log('%c========== [DatabaseProbeDetail] 开始获取探针详情 ==========', 'color: #409eff; font-weight: bold')
    console.log('%cprobeKey:', 'color: #67c23a; font-weight: bold', probeKey)

    const response = await request({
      url: `/probes/${probeKey}`,
      method: 'get'
    })

    console.log('%c探针详情API响应:', 'color: #e6a23c;', response)

    if (response.code === 200 && response.data) {
      Object.assign(probeInfo, response.data)
      console.log('%c✓ 探针详情获取成功:', 'color: #67c23a; font-weight: bold', probeInfo)

      ElMessage({
        message: `✓ 探针信息加载成功：${probeInfo.name}`,
        type: 'success',
        duration: 2000
      })
    } else {
      console.error('%c✗ 探针详情API返回错误:', 'color: #f56c6c; font-weight: bold', response.message)
    }
  } catch (error) {
    console.error('%c✗ 获取探针详情异常:', 'color: #f56c6c; font-weight: bold', error)
  }
  console.log('%c============================================================', 'color: #909399')
}

// 防止重复调用的标志位
let isFetchingInstances = false

// 获取数据库实例列表
const fetchInstances = async (showFeedback = false) => {
  // 防止重复调用
  if (isFetchingInstances) {
    console.warn('%c⚠️ fetchInstances 已经在执行中，跳过重复调用', 'color: #e6a23c;')
    return
  }

  loading.value = true
  initError.value = ''
  isFetchingInstances = true

  try {
    console.log('%c========== [DatabaseProbeDetail] 开始获取数据库实例 ==========', 'color: #409eff; font-weight: bold')
    console.log('%cprobeKey:', 'color: #67c23a; font-weight: bold', probeKey)
    console.log('%cshowFeedback:', 'color: #e6a23c;', showFeedback)

    if (showFeedback) {
      console.log('%c✓ 显示反馈消息：已启用', 'color: #67c23a;')
    }

    const response = await request({
      url: `/probes/${probeKey}/instances`,
      method: 'get',
      showError: false
    })

    console.log('%cAPI响应状态:', 'color: #e6a23c;', response.code)
    console.log('%cAPI响应数据:', 'color: #e6a23c;', response.data)
    console.log('%c完整响应:', 'color: #e6a23c;', response)

    if (response.code === 200 && response.data) {
      const oldCount = instances.value.length
      instances.value = response.data.instances || []
      const newCount = instances.value.length

      console.log('%c✓ 获取成功: 实例数量从', 'color: #67c23a; font-weight: bold', oldCount, '更新为', newCount)
      console.log('%c实例列表:', 'color: #67c23a;', instances.value)

      // ✅ 优先从后端恢复用户上次的选择（currentConnectionId）
      const savedConnectionId = response.data.currentConnectionId
      let restoredInstance = null

      if (savedConnectionId) {
        // 从后端返回的 currentConnectionId 匹配实例
        restoredInstance = instances.value.find(inst => inst.id === savedConnectionId)
        if (restoredInstance) {
          console.log('%c✓ 从后端恢复用户选择的实例:', 'color: #67c23a; font-weight: bold', restoredInstance.name)
        }
      }

      // 后端没有记录时，回退到 localStorage
      if (!restoredInstance) {
        restoredInstance = restoreSelectedInstance()
      }

      if (restoredInstance) {
        // 成功恢复用户上次的选择
        currentInstance.value = restoredInstance
        console.log('%c✓ 已恢复用户上次选择的实例:', 'color: #67c23a; font-weight: bold', restoredInstance.name)
      } else if (!currentInstance.value && instances.value.length > 0) {
        // 如果没有保存的选择且当前没有实例，自动选择第一个
        // 优先选择test_db_2（产品库存系统）
        const testDb2Instance = instances.value.find(inst =>
          inst.databaseName === 'test_db_2' || inst.name.includes('产品库存')
        )
        currentInstance.value = testDb2Instance || instances.value[0]
        console.log('%c✓ 自动设置实例为当前实例:', 'color: #67c23a; font-weight: bold', currentInstance.value.name)
        console.log('%c实例ID:', 'color: #67c23a;', currentInstance.value.id)
        console.log('%c数据库名:', 'color: #67c23a;', currentInstance.value.databaseName)

        // 保存自动选择的实例
        saveSelectedInstance(currentInstance.value)
      } else if (currentInstance.value) {
        console.log('%c当前已存在实例:', 'color: #e6a23c;', currentInstance.value.name)
      }

      if (showFeedback) {
        ElMessage({
          message: `✓ 刷新成功！当前共有 ${newCount} 个数据库实例`,
          type: 'success',
          duration: 3000,
          showClose: true
        })
        console.log('%c✓ 已显示成功消息给用户', 'color: #67c23a; font-weight: bold')
      }
    } else {
      console.error('%c✗ API返回错误:', 'color: #f56c6c; font-weight: bold', response.message)
      const errorMsg = response.message || '获取数据库实例失败'
      initError.value = errorMsg

      // 不在这里显示错误消息，让catch块统一处理
      throw new Error(errorMsg)
    }
  } catch (error) {
    console.error('%c✗ 获取数据库实例异常:', 'color: #f56c6c; font-weight: bold', error)
    console.error('%c错误详情:', 'color: #f56c6c;', error.message)
    console.error('%c错误堆栈:', 'color: #f56c6c;', error.stack)

    const errorMsg = '获取数据库实例失败：' + (error.message || '网络异常')
    initError.value = errorMsg

    if (showFeedback) {
      ElMessage({
        message: '✗ ' + errorMsg,
        type: 'error',
        duration: 5000,
        showClose: true
      })
      console.log('%c✗ 已显示异常消息给用户', 'color: #f56c6c; font-weight: bold')
    }
  } finally {
    loading.value = false
    pageLoading.value = false
    isFetchingInstances = false // 清除标志位，允许下次调用
    console.log('%c=========================================================', 'color: #909399')
  }
}

// 刷新实例列表
const refreshInstances = async () => {
  console.log('%c========== [DatabaseProbeDetail] 用户点击刷新按钮 ==========', 'color: #409eff; font-weight: bold')
  console.log('%c当前时间:', 'color: #e6a23c;', new Date().toLocaleString())
  console.log('%c当前实例数量:', 'color: #e6a23c;', instances.value.length)

  ElMessage({
    message: '开始刷新数据库实例列表...',
    type: 'info',
    duration: 1500
  })
  console.log('%c✓ 已显示开始刷新消息', 'color: #67c23a;')

  await fetchInstances(true)

  console.log('%c=========================================================', 'color: #909399')
}

// 显示配置对话框
// 数据导入功能
const showImportDialog = () => {
  if (instances.value.length === 0) {
    ElMessage.warning('请先配置数据库连接')
    return
  }
  importDialogVisible.value = true
}

const handleImportFileChange = (uploadFile) => {
  importFile.value = uploadFile.raw
}

const handleImportFileRemove = () => {
  importFile.value = null
}

const handleImport = async () => {
  if (!importFile.value) {
    ElMessage.warning('请选择要导入的文件')
    return
  }
  if (!importForm.tableName.trim()) {
    ElMessage.warning('请输入目标表名')
    return
  }
  if (!importForm.connectionId) {
    ElMessage.warning('请选择数据库连接')
    return
  }

  importing.value = true
  importResult.value = null
  try {
    const formData = new FormData()
    formData.append('file', importFile.value)
    formData.append('tableName', importForm.tableName.trim())
    formData.append('connectionId', importForm.connectionId)

    const res = await databaseProbeApi.importData(probeKey, formData)
    if (res.code === 200) {
      const data = res.data
      ElMessage.success(`导入成功：${data.rows} 行数据写入表 ${data.tableName}`)
      importResult.value = { title: `成功导入 ${data.rows} 行到表 "${data.tableName}"`, type: 'success' }
    } else {
      importResult.value = { title: res.message || '导入失败', type: 'error' }
    }
  } catch (e) {
    importResult.value = { title: '导入失败：' + (e.message || '网络错误'), type: 'error' }
    ElMessage.error('数据导入失败')
  } finally {
    importing.value = false
  }
}

const resetImportForm = () => {
  importFile.value = null
  importResult.value = null
  importForm.tableName = ''
  importForm.connectionId = null
  importUploadRef.value?.clearFiles()
}

const showConfigDialog = () => {
  configDialogVisible.value = true
}

// 切换数据库实例
const handleInstanceSwitch = async (instance) => {
  console.log('%c========== [DatabaseProbeDetail] 切换数据库实例 ==========', 'color: #409eff; font-weight: bold')
  console.log('%c选择的实例:', 'color: #67c23a;', instance)

  if (!instance || !instance.id) {
    console.warn('%c✗ 无效的实例', 'color: #f56c6c; font-weight: bold')
    return
  }

  // 如果点击的是当前实例，不做任何操作
  if (currentInstance.value && currentInstance.value.id === instance.id) {
    console.log('%c当前已是此实例，无需切换', 'color: #e6a23c;')
    return
  }

  loading.value = true

  try {
    // 更新当前实例
    currentInstance.value = instance

    // ✅ 持久化保存用户选择（localStorage + 后端）
    saveSelectedInstance(instance)

    // 异步保存到后端
    try {
      await databaseProbeApi.saveSelectedInstance(probeKey, instance.id)
      console.log('%c✓ 已保存实例选择到后端:', 'color: #67c23a;', instance.id)
    } catch (err) {
      console.warn('%c⚠ 保存实例选择到后端失败（不影响使用）:', 'color: #e6a23c;', err.message)
    }

    console.log('%c✓ 已切换到实例:', 'color: #67c23a; font-weight: bold', instance.name)
    console.log('%c实例详情:', 'color: #67c23a;', {
      host: instance.databaseHost,
      port: instance.databasePort,
      databaseName: instance.databaseName
    })

    ElMessage({
      message: `✓ 已切换到数据库：${instance.name}`,
      type: 'success',
      duration: 2000
    })

    // 自动触发采集该数据库的元数据
    console.log('%c📤 自动触发数据库采集...', 'color: #e6a23c; font-weight: bold')
    await handleTriggerCollection()

  } catch (error) {
    console.error('%c✗ 切换实例失败:', 'color: #f56c6c; font-weight: bold', error)
    console.error('切换实例失败:', error)
  } finally {
    loading.value = false
    console.log('%c=========================================================', 'color: #909399')
  }
}

// ✅ 保存选中的数据库实例到 localStorage
const saveSelectedInstance = (instance) => {
  try {
    const storageKey = `db_probe_selected_instance_${probeKey}`
    const dataToSave = {
      instanceId: instance.id,
      databaseName: instance.databaseName,
      name: instance.name,
      databaseHost: instance.databaseHost,
      databasePort: instance.databasePort,
      timestamp: Date.now()
    }
    localStorage.setItem(storageKey, JSON.stringify(dataToSave))
    console.log('%c✓ 已保存数据库实例选择到 localStorage:', 'color: #67c23a;', storageKey, dataToSave)
  } catch (error) {
    console.warn('%c✗ 保存实例选择失败:', 'color: #f56c6c;', error)
  }
}

// ✅ 从 localStorage 恢复选中的数据库实例
const restoreSelectedInstance = () => {
  try {
    const storageKey = `db_probe_selected_instance_${probeKey}`
    const savedData = localStorage.getItem(storageKey)

    if (!savedData) {
      console.log('%c localStorage 中没有保存的实例选择', 'color: #909399;')
      return null
    }

    const savedInstance = JSON.parse(savedData)
    console.log('%c从 localStorage 读取到保存的实例:', 'color: #e6a23c;', savedInstance)

    // 在实例列表中查找匹配的实例
    const matchedInstance = instances.value.find(inst =>
      inst.id === savedInstance.instanceId ||
      inst.databaseName === savedInstance.databaseName
    )

    if (matchedInstance) {
      console.log('%c✓ 找到匹配的实例，恢复选择:', 'color: #67c23a; font-weight: bold', matchedInstance.name)
      return matchedInstance
    } else {
      console.warn('%c✗ 未找到匹配的实例，可能已被删除', 'color: #f56c6c;')
      // 清除过期的保存数据
      localStorage.removeItem(storageKey)
      return null
    }
  } catch (error) {
    console.error('%c✗ 恢复实例选择失败:', 'color: #f56c6c;', error)
    return null
  }
}

// 查看表结构
const viewTables = (instance) => {
  console.log('========== [DatabaseProbeDetail] 查看表结构 ==========')
  console.log('实例信息:', instance)
  console.log('即将跳转到表结构页面')

  router.push({
    path: `/database/${probeKey}`,
    query: {
      instanceId: instance.databaseName,
      instanceName: instance.name
    }
  })

  console.log('======================================================')
}

// 触发数据库元数据采集
const handleTriggerCollection = async () => {
  console.log('========== [DatabaseProbeDetail] 开始触发数据库采集 ==========')
  console.log('probeKey:', probeKey)

  const loadingMsg = ElMessage({
    message: '📤 正在触发数据库采集...',
    type: 'info',
    duration: 0,
    showClose: false
  })

  try {
    const response = await request({
      url: `/probes/${probeKey}/collect`,
      method: 'post',
      data: {}
    })

    console.log('%cAPI响应:', 'color: #e6a23c;', response)

    if (response.code === 200 || response.success) {
      loadingMsg.close()

      ElMessage({
        message: '✓ 采集命令已发送，请等待Agent完成采集...',
        type: 'success',
        duration: 3000
      })

      console.log('%c✓ 采集命令发送成功', 'color: #67c23a; font-weight: bold')

      // 等待2秒后刷新实例列表
      setTimeout(async () => {
        console.log('%c🔄 刷新实例列表...', 'color: #e6a23c;')
        await refreshInstances()
      }, 2000)
    } else {
      loadingMsg.close()
      ElMessage.error(response.message || '触发采集失败')
    }
  } catch (error) {
    loadingMsg.close()
    console.error('%c✗ 触发采集异常:', 'color: #f56c6c; font-weight: bold', error)
    ElMessage.error('触发采集失败：' + (error.message || '网络异常'))
  }

  console.log('======================================================')
}

// 测试连接
const testConnection = async (instance) => {
  console.log('========== [DatabaseProbeDetail] 开始测试数据库连接 ==========')
  console.log('实例信息:', instance)

  const loadingInstance = ElMessage({
    message: '正在测试连接...',
    type: 'info',
    duration: 0
  })

  try {
    const response = await databaseProbeApi.testInstanceConnection(probeKey, instance.databaseName)

    console.log('测试连接响应:', response)

    loadingInstance.close()

    if (response.code === 200) {
      console.log('✓ 连接测试成功')
      ElMessage.success(`连接测试成功：${instance.name}`)
    } else {
      console.error('✗ 连接测试失败:', response.message)
      ElMessage.error(response.message || '连接测试失败')
    }
  } catch (error) {
    console.error('✗ 测试连接异常:', error)
    loadingInstance.close()
    ElMessage.error('测试连接失败：' + (error.message || '网络异常'))
  }
  console.log('=============================================================')
}

// 初始化
onMounted(async () => {
  console.log('%c========== [DatabaseProbeDetail] 页面初始化 ==========', 'color: #409eff; font-weight: bold')
  console.log('%c当前路由参数:', 'color: #e6a23c;', route.params)
  console.log('%c查询参数:', 'color: #e6a23c;', route.query)
  console.log('%c探针Key:', 'color: #67c23a; font-weight: bold', probeKey)

  // 显示初始化消息
  ElMessage({
    message: '正在加载数据库探针详情...',
    type: 'info',
    duration: 2000
  })
  console.log('%c✓ 已显示初始化加载消息', 'color: #67c23a;')

  await fetchProbeInfo()
  await fetchInstances(true)

  // ✅ 自动刷新数据：如果探针在线，自动触发一次数据采集
  if (probeInfo.status === 'online') {
    console.log('%c探针在线，自动触发数据采集...', 'color: #e6a23c;')

    // 等待1秒让UI先渲染探针信息
    await new Promise(resolve => setTimeout(resolve, 1000))

    // 自动刷新数据库详情（触发元数据采集）
    await handleTriggerCollection()

    console.log('%c✓ 自动数据采集完成', 'color: #67c23a;')
  } else {
    console.log('%c探针离线，跳过自动数据采集', 'color: #909399;')
  }

  console.log('%c=======================================================', 'color: #909399')
})
</script>

<style scoped lang="scss">
.database-probe-detail {
  padding: 20px;

  .page-header {
    margin-bottom: 20px;

    .header-content {
      display: flex;
      align-items: center;
      gap: 16px;

      .probe-icon {
        font-size: 32px;

        &.type-database {
          color: #67c23a;
        }
      }

      .probe-info {
        flex: 1;

        .probe-name {
          margin: 0 0 8px 0;
          font-size: 20px;
          font-weight: 600;
        }

        .probe-meta {
          display: flex;
          align-items: center;
          gap: 12px;
          margin: 0;
          font-size: 14px;

          .probe-key {
            color: #909399;
          }

          .database-instance-dropdown {
            .database-selector {
              padding: 6px 12px;
              border-radius: 6px;
              background-color: var(--el-fill-color-light);
              border: 1px solid var(--el-border-color);
              transition: all 0.2s;
              cursor: pointer;
              display: inline-flex;
              align-items: center;

              &:hover {
                background-color: var(--el-fill-color);
                border-color: var(--el-color-primary);
              }

              .database-icon {
                color: var(--el-color-primary);
              }

              .current-instance {
                color: var(--el-color-primary);
                font-weight: 600;
                font-size: 14px;
              }

              .is-loading {
                animation: rotating 2s linear infinite;
              }
            }
          }

          .database-type {
            color: #409eff;
            font-weight: 500;
          }
        }
      }
    }
  }

  .action-bar {
    margin-bottom: 20px;
    display: flex;
    align-items: center;
    gap: 16px;

    .loading-tip {
      display: flex;
      align-items: center;
      gap: 8px;
      color: #409eff;
      font-size: 14px;
      animation: fadeIn 0.3s ease-in-out;

      .el-icon {
        font-size: 16px;
      }
    }
  }

  .instances-card {
    .card-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
    }
  }
}

// 下拉菜单样式
:deep(.el-dropdown-menu__item) {
  &.is-active {
    background-color: var(--el-color-primary-light-9);
    color: var(--el-color-primary);
  }

  &.is-active .el-icon {
    color: var(--el-color-primary);
  }
}

@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(-10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes rotating {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}
</style>