<template>
  <el-dialog
    v-model="visible"
    title="JSON配置导入探针"
    width="800px"
    :close-on-click-modal="false"
    @close="handleClose"
  >
    <div class="import-container">
      <!-- 操作说明 -->
      <el-alert
        title="导入说明"
        type="info"
        :closable="false"
        style="margin-bottom: 16px"
      >
        <template #default>
          <ul class="import-tips">
            <li>支持批量导入多个探针配置</li>
            <li>探针标识（probeKey）必须唯一</li>
            <li>重复的探针标识将自动跳过</li>
            <li>可点击"下载模板"获取示例JSON</li>
          </ul>
        </template>
      </el-alert>

      <!-- 操作按钮 -->
      <div class="action-buttons">
        <el-button @click="downloadTemplate" :icon="Download">
          下载JSON模板
        </el-button>
        <el-button @click="loadFromFile" :icon="Upload">
          从文件导入
        </el-button>
        <input
          ref="fileInputRef"
          type="file"
          accept=".json"
          style="display: none"
          @change="handleFileChange"
        />
      </div>

      <!-- JSON编辑器 -->
      <div class="json-editor">
        <div class="editor-header">
          <span>JSON配置</span>
          <el-tag v-if="isValid" type="success">格式正确</el-tag>
          <el-tag v-else-if="jsonContent" type="danger">格式错误</el-tag>
        </div>
        <el-input
          v-model="jsonContent"
          type="textarea"
          :rows="15"
          placeholder="请输入或粘贴JSON配置..."
          @input="validateJson"
        />
        <div v-if="errorMessage" class="error-message">
          <el-icon><Warning /></el-icon>
          {{ errorMessage }}
        </div>
      </div>

      <!-- 导入预览 -->
      <div v-if="previewData && previewData.probes" class="import-preview">
        <div class="preview-header">
          <span>导入预览</span>
          <el-tag type="info">共 {{ previewData.probes.length }} 个探针</el-tag>
        </div>
        <el-table :data="previewData.probes" max-height="300" stripe>
          <el-table-column prop="name" label="探针名称" width="150" />
          <el-table-column prop="probeKey" label="探针标识" width="180" />
          <el-table-column prop="type" label="类型" width="100">
            <template #default="{ row }">
              <el-tag size="small">{{ row.type }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="hostIp" label="主机IP" width="120" />
          <el-table-column prop="collectInterval" label="采集间隔" width="100">
            <template #default="{ row }">
              {{ row.collectInterval }}秒
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>

    <template #footer>
      <div class="dialog-footer">
        <el-button @click="handleClose">取消</el-button>
        <el-button
          type="primary"
          @click="handleImport"
          :loading="importing"
          :disabled="!isValid || !previewData"
        >
          <el-icon><Upload /></el-icon>
          导入探针
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { Download, Upload, Warning } from '@element-plus/icons-vue'
import { importJson } from '@/api/probe'

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['update:modelValue', 'success'])

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const fileInputRef = ref(null)
const jsonContent = ref('')
const previewData = ref(null)
const errorMessage = ref('')
const importing = ref(false)
const isValid = ref(false)

// JSON模板
const jsonTemplate = {
  "probes": [
    {
      "probeKey": "system-server-01",
      "name": "服务器01-系统监控",
      "type": "SYSTEM",
      "hostIp": "192.168.1.10",
      "port": 9999,
      "collectInterval": 60,
      "description": "系统监控探针"
    },
    {
      "probeKey": "file-server-01",
      "name": "文件服务器01",
      "type": "FILE",
      "hostIp": "192.168.1.20",
      "port": 58081,
      "collectInterval": 30,
      "description": "文件扫描探针"
    }
  ]
}

// 下载模板
const downloadTemplate = () => {
  const templateStr = JSON.stringify(jsonTemplate, null, 2)
  const blob = new Blob([templateStr], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = 'probe-import-template.json'
  a.click()
  URL.revokeObjectURL(url)
  ElMessage.success('模板下载成功')
}

// 从文件导入
const loadFromFile = () => {
  fileInputRef.value?.click()
}

// 处理文件选择
const handleFileChange = (event) => {
  const file = event.target.files[0]
  if (!file) return

  const reader = new FileReader()
  reader.onload = (e) => {
    jsonContent.value = e.target.result
    validateJson()
  }
  reader.onerror = () => {
    ElMessage.error('文件读取失败')
  }
  reader.readAsText(file)

  // 清空input以允许重复选择同一文件
  event.target.value = ''
}

// 验证JSON格式
const validateJson = () => {
  errorMessage.value = ''
  isValid.value = false
  previewData.value = null

  if (!jsonContent.value.trim()) {
    return
  }

  try {
    const data = JSON.parse(jsonContent.value)

    if (!data.probes || !Array.isArray(data.probes)) {
      errorMessage.value = 'JSON格式错误：缺少 probes 数组'
      return
    }

    if (data.probes.length === 0) {
      errorMessage.value = '探针列表为空'
      return
    }

    // 验证每个探针的基本字段
    const errors = []
    data.probes.forEach((probe, index) => {
      if (!probe.probeKey) {
        errors.push(`第 ${index + 1} 个探针缺少 probeKey`)
      }
      if (!probe.name) {
        errors.push(`第 ${index + 1} 个探针缺少 name`)
      }
      // type 默认为 SYSTEM
      if (!probe.type) {
        probe.type = 'SYSTEM'
      }
      if (!probe.hostIp) {
        errors.push(`第 ${index + 1} 个探针缺少 hostIp`)
      }
    })

    if (errors.length > 0) {
      errorMessage.value = errors.join('；')
      return
    }

    // 验证通过
    isValid.value = true
    previewData.value = data

  } catch (error) {
    errorMessage.value = 'JSON格式错误：' + error.message
  }
}

// 导入探针
const handleImport = async () => {
  if (!isValid.value || !previewData.value) {
    ElMessage.warning('请先输入有效的JSON配置')
    return
  }

  try {
    importing.value = true

    const result = await importJson(jsonContent.value)

    ElMessage.success(
      `导入成功！总数：${result.data.total}，成功：${result.data.success}，失败：${result.data.failed}`
    )

    emit('success')
    handleClose()

  } catch (error) {
    ElMessage.error('导入失败：' + (error.message || '未知错误'))
  } finally {
    importing.value = false
  }
}

// 关闭对话框
const handleClose = () => {
  jsonContent.value = ''
  previewData.value = null
  errorMessage.value = ''
  isValid.value = false
  visible.value = false
}
</script>

<style scoped lang="scss">
.import-container {
  .import-tips {
    margin: 0;
    padding-left: 20px;
    li {
      margin: 4px 0;
    }
  }

  .action-buttons {
    margin-bottom: 16px;
    display: flex;
    gap: 12px;
  }

  .json-editor {
    border: 1px solid #dcdfe6;
    border-radius: 4px;
    overflow: hidden;

    .editor-header {
      padding: 8px 12px;
      background: #f5f7fa;
      border-bottom: 1px solid #dcdfe6;
      display: flex;
      justify-content: space-between;
      align-items: center;
      font-weight: 500;
    }

    .error-message {
      padding: 8px 12px;
      background: #fef0f0;
      border-top: 1px solid #fde2e2;
      color: #f56c6c;
      display: flex;
      align-items: center;
      gap: 6px;
      font-size: 13px;
    }
  }

  .import-preview {
    margin-top: 16px;
    border: 1px solid #dcdfe6;
    border-radius: 4px;
    overflow: hidden;

    .preview-header {
      padding: 8px 12px;
      background: #f5f7fa;
      border-bottom: 1px solid #dcdfe6;
      display: flex;
      justify-content: space-between;
      align-items: center;
      font-weight: 500;
    }
  }
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}
</style>
