<template>
  <div class="file-upload-page">
    <div class="page-header">
      <div class="header-left">
        <h2>数据文件上传</h2>
        <span class="subtitle">上传和管理数据文件</span>
      </div>
      <div class="header-right">
        <el-button :icon="Refresh" @click="loadData">刷新</el-button>
      </div>
    </div>

    <!-- 上传区域 -->
    <el-card shadow="never" class="upload-card">
      <template #header>
        <span>文件上传</span>
      </template>
      <el-upload
        ref="uploadRef"
        class="upload-area"
        drag
        multiple
        :action="''"
        :auto-upload="false"
        :on-change="handleFileChange"
        :on-remove="handleFileRemove"
        accept=".csv,.xlsx,.xls,.json,.xml,.txt,.log,.sql,.parquet,.avro,.orc"
      >
        <el-icon class="el-icon--upload"><Upload /></el-icon>
        <div class="el-upload__text">将文件拖到此处，或<em>点击上传</em></div>
        <template #tip>
          <div class="el-upload__tip">支持 CSV、Excel、JSON、XML、SQL、日志等文件</div>
        </template>
      </el-upload>
      <div class="upload-actions">
        <el-select v-model="uploadProbeKey" placeholder="关联探针（可选）" clearable style="width: 240px; margin-right: 12px">
          <el-option v-for="p in probes" :key="p.probeKey" :label="p.name" :value="p.probeKey" />
        </el-select>
        <el-button type="primary" :icon="Upload" @click="handleUpload" :loading="uploading" :disabled="selectedFiles.length === 0">
          开始上传 ({{ selectedFiles.length }})
        </el-button>
      </div>
    </el-card>

    <!-- 统计 -->
    <div class="stats-row">
      <el-card shadow="never" class="stat-card">
        <div class="stat-value">{{ fileStats.totalFiles || 0 }}</div>
        <div class="stat-label">已上传文件</div>
      </el-card>
      <el-card shadow="never" class="stat-card">
        <div class="stat-value">{{ fileStats.totalSizeReadable || '0 B' }}</div>
        <div class="stat-label">总大小</div>
      </el-card>
      <el-card shadow="never" class="stat-card">
        <div class="stat-value">{{ (fileStats.fileTypes || []).length }}</div>
        <div class="stat-label">文件类型</div>
      </el-card>
      <el-card shadow="never" class="stat-card">
        <div class="stat-value">{{ aggregationStats.fileCount || 0 }}</div>
        <div class="stat-label">已注册汇聚</div>
      </el-card>
      <el-card shadow="never" class="stat-card">
        <div class="stat-value">{{ auditCount }}</div>
        <div class="stat-label">审计记录</div>
      </el-card>
    </div>

    <!-- 文件列表 -->
    <el-card shadow="never">
      <template #header>
        <div class="table-header">
          <span>已上传文件</span>
          <el-input v-model="searchName" placeholder="搜索文件名" clearable style="width: 200px" @clear="loadFileList" @keyup.enter="loadFileList" />
        </div>
      </template>

      <el-table :data="fileList" v-loading="loading" stripe>
        <el-table-column prop="fileName" label="文件名" min-width="200" show-overflow-tooltip />
        <el-table-column prop="fileSize" label="大小" width="120">
          <template #default="{ row }">{{ formatSize(row.fileSize) }}</template>
        </el-table-column>
        <el-table-column prop="fileExtension" label="类型" width="90">
          <template #default="{ row }">
            <el-tag size="small" type="info">{{ row.fileExtension || '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="probeKey" label="关联探针" width="200" show-overflow-tooltip />
        <el-table-column prop="aggregationTable" label="汇聚表" width="150">
          <template #default="{ row }">
            <el-tag v-if="row.aggregationTable" size="small" type="success">{{ row.aggregationTable }}</el-tag>
            <span v-else style="color: #909399; font-size: 12px">未解析</span>
          </template>
        </el-table-column>
        <el-table-column prop="auditLogged" label="审计" width="70" align="center">
          <template #default="{ row }">
            <el-icon v-if="row.auditLogged" color="#67c23a"><Check /></el-icon>
            <el-icon v-else color="#909399"><Close /></el-icon>
          </template>
        </el-table-column>
        <el-table-column prop="fileMd5" label="MD5" width="280" show-overflow-tooltip />
        <el-table-column prop="createTime" label="上传时间" width="180">
          <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-popconfirm title="确定删除此文件？" @confirm="handleDelete(row)">
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
          @size-change="loadFileList" @current-change="loadFileList" />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { Upload, Refresh, Check, Close } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { uploadFiles, getFileList, deleteFile, getFileStatistics } from '@/api/fileUpload'
import { probeApi } from '@/api/probe'
import { aggregationApi } from '@/api/aggregation'
import request from '@/api/request'
import dayjs from 'dayjs'

const probes = ref([])
const uploadProbeKey = ref('')
const selectedFiles = ref([])
const uploading = ref(false)
const searchName = ref('')
const fileList = ref([])
const fileStats = ref({})
const aggregationStats = ref({})
const auditCount = ref(0)
const loading = ref(false)
const pageNum = ref(1)
const pageSize = ref(20)
const total = ref(0)

const handleFileChange = (file, files) => { selectedFiles.value = files }
const handleFileRemove = (file, files) => { selectedFiles.value = files }

const handleUpload = async () => {
  if (selectedFiles.value.length === 0) return
  uploading.value = true
  try {
    const formData = new FormData()
    selectedFiles.value.forEach(f => formData.append('files', f.raw))
    if (uploadProbeKey.value) formData.append('probeKey', uploadProbeKey.value)
    const res = await uploadFiles(formData)
    if (res.code === 200) {
      ElMessage.success(`成功上传 ${selectedFiles.value.length} 个文件`)
      selectedFiles.value = []
      loadData()
    }
  } catch (e) {
    ElMessage.error('上传失败')
  }
  uploading.value = false
}

const handleDelete = async (row) => {
  await deleteFile(row.id)
  ElMessage.success('文件已删除')
  loadData()
}

const formatSize = (bytes) => {
  if (!bytes) return '-'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1048576) return (bytes / 1024).toFixed(1) + ' KB'
  if (bytes < 1073741824) return (bytes / 1048576).toFixed(1) + ' MB'
  return (bytes / 1073741824).toFixed(1) + ' GB'
}

const formatTime = (t) => t ? dayjs(t).format('YYYY-MM-DD HH:mm:ss') : '-'

const loadStats = async () => {
  const [fileRes, aggRes] = await Promise.allSettled([
    getFileStatistics(),
    aggregationApi.getStats()
  ])
  if (fileRes.status === 'fulfilled' && fileRes.value?.code === 200) fileStats.value = fileRes.value.data || {}
  if (aggRes.status === 'fulfilled' && aggRes.value?.data) aggregationStats.value = aggRes.value.data
  try {
    const auditRes = await request({ url: '/audit-logs', method: 'get', params: { pageNum: 1, pageSize: 1 } })
    if (auditRes?.data?.total !== undefined) auditCount.value = auditRes.data.total
  } catch {}
}

const loadFileList = async () => {
  loading.value = true
  const res = await getFileList({ fileName: searchName.value || undefined, pageNum: pageNum.value, pageSize: pageSize.value })
  if (res.code === 200 && res.data) {
    fileList.value = res.data.records || []
    total.value = res.data.total || 0
  }
  loading.value = false
}

const loadProbes = async () => {
  const res = await probeApi.getList({ pageNum: 1, pageSize: 100 })
  if (res.code === 200) probes.value = res.data?.records || []
}

const loadData = () => { loadStats(); loadFileList() }

onMounted(() => { loadProbes(); loadData() })
</script>

<style scoped>
.file-upload-page { padding: 20px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.page-header h2 { margin: 0; font-size: 20px; }
.page-header .subtitle { color: #909399; font-size: 13px; margin-left: 12px; }
.header-right { display: flex; gap: 12px; }
.upload-card { margin-bottom: 20px; }
.upload-area { margin-bottom: 12px; }
.upload-actions { display: flex; align-items: center; }
.stats-row { display: grid; grid-template-columns: repeat(5, 1fr); gap: 16px; margin-bottom: 20px; }
.stat-card { text-align: center; }
.stat-card :deep(.el-card__body) { padding: 16px 20px; }
.stat-value { font-size: 28px; font-weight: 600; color: #409eff; }
.stat-label { font-size: 13px; color: #909399; margin-top: 4px; }
.table-header { display: flex; justify-content: space-between; align-items: center; }
.pagination { display: flex; justify-content: flex-end; margin-top: 16px; }
</style>
