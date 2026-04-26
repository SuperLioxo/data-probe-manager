<template>
  <div class="app-container">
    <el-row :gutter="16">
      <el-col :span="12">
        <el-card>
          <template #header>
            <div style="display:flex;justify-content:space-between;align-items:center">
              <span>版本管理</span>
              <el-upload :show-file-list="false" :before-upload="handleUpload" accept=".jar">
                <el-button type="primary" size="small">上传新版本</el-button>
              </el-upload>
            </div>
          </template>

          <el-table :data="versions" border stripe v-loading="loading">
            <el-table-column prop="version" label="版本号" width="120" />
            <el-table-column prop="fileSize" label="文件大小" width="100">
              <template #default="{ row }">{{ formatSize(row.fileSize) }}</template>
            </el-table-column>
            <el-table-column prop="checksum" label="SHA-256" show-overflow-tooltip />
            <el-table-column prop="releaseNotes" label="发布说明" show-overflow-tooltip />
            <el-table-column prop="uploadedBy" label="上传者" width="80" />
            <el-table-column prop="createTime" label="上传时间" width="170" />
            <el-table-column label="操作" width="80" fixed="right">
              <template #default="{ row }">
                <el-button type="danger" size="small" @click="handleDelete(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>

      <el-col :span="12">
        <el-card>
          <template #header>
            <div style="display:flex;justify-content:space-between;align-items:center">
              <span>升级操作</span>
              <el-button size="small" @click="loadAgents">刷新Agent列表</el-button>
            </div>
          </template>

          <el-form :model="upgradeForm" label-width="80px" style="margin-bottom:16px">
            <el-form-item label="目标版本">
              <el-select v-model="upgradeForm.targetVersion" placeholder="选择版本">
                <el-option v-for="v in versions" :key="v.id" :label="v.version" :value="v.version" />
              </el-select>
            </el-form-item>
          </el-form>

          <el-table ref="agentTable" :data="agents" border stripe @selection-change="handleSelectionChange">
            <el-table-column type="selection" width="50" />
            <el-table-column prop="agentCode" label="Agent编码" width="140" />
            <el-table-column prop="status" label="状态" width="80">
              <template #default="{ row }">
                <el-tag :type="row.status === 'ONLINE' ? 'success' : 'danger'" size="small">{{ row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="ip" label="IP地址" width="130" />
            <el-table-column label="操作" width="80">
              <template #default="{ row }">
                <el-button type="primary" size="small" @click="handleUpgradeOne(row)" :disabled="!upgradeForm.targetVersion">升级</el-button>
              </template>
            </el-table-column>
          </el-table>

          <div style="margin-top:12px;text-align:right">
            <el-button type="primary" @click="handleBatchUpgrade" :disabled="!upgradeForm.targetVersion || selectedAgents.length === 0">
              批量升级 ({{ selectedAgents.length }})
            </el-button>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listVersions, deleteVersion, uploadVersion, triggerUpgrade, triggerBatchUpgrade } from '../../api/agentUpgrade'
import request from '../../api/request'

const versions = ref([])
const agents = ref([])
const selectedAgents = ref([])
const loading = ref(false)
const upgradeForm = ref({ targetVersion: '' })

function formatSize(bytes) {
  if (!bytes) return '-'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1048576) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / 1048576).toFixed(1) + ' MB'
}

async function loadData() {
  loading.value = true
  try {
    const res = await listVersions()
    versions.value = res.data || []
  } finally {
    loading.value = false
  }
}

async function loadAgents() {
  try {
    const res = await request({ url: '/agents', method: 'get' })
    agents.value = res.data || []
  } catch { agents.value = [] }
}

function handleSelectionChange(rows) {
  selectedAgents.value = rows
}

async function handleUpload(file) {
  const { value: version } = await ElMessageBox.prompt('请输入版本号', '上传新版本', { inputPattern: /\S+/, inputErrorMessage: '版本号不能为空' })
  const formData = new FormData()
  formData.append('file', file)
  formData.append('version', version)
  try {
    await uploadVersion(formData)
    ElMessage.success('上传成功')
    loadData()
  } catch (e) {
    ElMessage.error('上传失败: ' + (e.message || '未知错误'))
  }
  return false
}

async function handleDelete(row) {
  await ElMessageBox.confirm(`确定删除版本 ${row.version}?`, '确认')
  try {
    await deleteVersion(row.id)
    ElMessage.success('删除成功')
    loadData()
  } catch (e) {
    ElMessage.error('删除失败')
  }
}

async function handleUpgradeOne(row) {
  await ElMessageBox.confirm(`确定将 ${row.agentCode} 升级到 ${upgradeForm.value.targetVersion}?`, '确认')
  try {
    await triggerUpgrade(row.agentCode, upgradeForm.value.targetVersion)
    ElMessage.success('升级命令已发送')
  } catch (e) {
    ElMessage.error('升级失败: ' + (e.message || '未知错误'))
  }
}

async function handleBatchUpgrade() {
  const codes = selectedAgents.value.map(a => a.agentCode)
  await ElMessageBox.confirm(`确定将 ${codes.length} 个Agent升级到 ${upgradeForm.value.targetVersion}?`, '确认')
  try {
    await triggerBatchUpgrade(codes, upgradeForm.value.targetVersion)
    ElMessage.success('批量升级命令已发送')
  } catch (e) {
    ElMessage.error('批量升级失败')
  }
}

onMounted(() => {
  loadData()
  loadAgents()
})
</script>
