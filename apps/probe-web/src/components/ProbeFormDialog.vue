<template>
  <el-dialog
    v-model="visible"
    :title="isEdit ? '编辑探针' : '创建探针'"
    width="900px"
    :close-on-click-modal="false"
    destroy-on-close
    @close="handleClose"
    class="compact-dialog"
    :class="{ 'has-type-selector': !isEdit }"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px" class="dialog-form">
      <!-- 探针类型选择（仅新增时可选） -->
      <template v-if="!isEdit">
        <div class="form-section">
          <div class="section-label">选择探针类型</div>
          <div class="probe-type-selector">
            <el-radio-group v-model="form.type" class="probe-type-options">
              <!-- 系统监控 -->
              <div
                class="probe-type-card"
                :class="{ 'is-selected': form.type === 'SYSTEM' }"
                role="radio"
                :aria-checked="form.type === 'SYSTEM'"
                tabindex="0"
                @click="selectType('SYSTEM')"
                @keydown.enter="selectType('SYSTEM')"
                @keydown.space.prevent="selectType('SYSTEM')"
              >
                <div class="card-icon">
                  <el-icon :size="20"><Monitor /></el-icon>
                </div>
                <div class="card-content">
                  <h3 class="card-title">系统监控</h3>
                  <p class="card-description">CPU · 内存 · 磁盘 · 网络</p>
                </div>
              </div>

              <!-- 文件监控 -->
              <div
                class="probe-type-card"
                :class="{ 'is-selected': form.type === 'FILE' }"
                role="radio"
                :aria-checked="form.type === 'FILE'"
                tabindex="0"
                @click="selectType('FILE')"
                @keydown.enter="selectType('FILE')"
                @keydown.space.prevent="selectType('FILE')"
              >
                <div class="card-icon">
                  <el-icon :size="20"><Document /></el-icon>
                </div>
                <div class="card-content">
                  <h3 class="card-title">文件监控</h3>
                  <p class="card-description">文件变化 · 内容分析 · 路径过滤</p>
                </div>
              </div>

              <!-- 数据库监控 -->
              <div
                class="probe-type-card"
                :class="{ 'is-selected': form.type === 'DATABASE' }"
                role="radio"
                :aria-checked="form.type === 'DATABASE'"
                tabindex="0"
                @click="selectType('DATABASE')"
                @keydown.enter="selectType('DATABASE')"
                @keydown.space.prevent="selectType('DATABASE')"
              >
                <div class="card-icon">
                  <el-icon :size="20"><DataLine /></el-icon>
                </div>
                <div class="card-content">
                  <h3 class="card-title">数据库监控</h3>
                  <p class="card-description">连接状态 · 查询性能 · 表空间</p>
                </div>
              </div>
            </el-radio-group>
          </div>
        </div>
      </template>

      <!-- 基础信息 -->
      <div class="form-section">
        <div class="section-label">基础信息</div>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="探针名称" prop="name">
              <el-input v-model="form.name" placeholder="请输入探针名称" clearable />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="探针标识">
              <el-input
                v-model="form.probeKey"
                placeholder="自动生成"
                clearable
              >
                <template #append>
                  <el-button @click="generateProbeKey" :icon="MagicStick">生成</el-button>
                </template>
              </el-input>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="主机IP">
              <el-input
                v-model="form.hostIp"
                placeholder="默认127.0.0.1"
                clearable
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="端口" prop="port">
              <el-input-number v-model="form.port" :min="1" :max="65535" placeholder="默认58081" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
      </div>

      <!-- 监控配置（所有探针类型通用） -->
      <div class="form-section">
        <div class="section-label">监控配置</div>

        <!-- 文件探针专属配置 -->
        <template v-if="form.type === 'FILE'">
          <el-row :gutter="16">
            <el-col :span="12">
              <el-form-item label="扫描路径" prop="scanPath">
                <el-input
                  v-model="form.scanPath"
                  placeholder="如：/var/log"
                  clearable
                />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="文件扩展名">
                <el-input
                  v-model="form.fileExtensions"
                  placeholder=".log,.txt"
                  clearable
                />
              </el-form-item>
            </el-col>
          </el-row>

          <el-row :gutter="16">
            <el-col :span="12">
              <el-form-item label="忽略路径">
                <el-input
                  v-model="form.ignorePaths"
                  placeholder="/tmp,/cache"
                  clearable
                />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="最大深度">
                <el-input-number v-model="form.maxDepth" :min="1" :max="20" style="width: 100%" />
              </el-form-item>
            </el-col>
          </el-row>
        </template>

        <!-- 数据库探针专属配置 -->
        <template v-else-if="form.type === 'DATABASE'">
          <DatabaseProbeForm
            ref="databaseFormRef"
            v-model="databaseConfig"
          />
        </template>

        <!-- 通用采集配置（所有类型） -->
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item
              :label="form.type === 'FILE' ? '扫描间隔(秒)' : '采集间隔(秒)'"
              :prop="form.type === 'FILE' ? undefined : 'collectInterval'"
            >
              <el-input-number
                v-if="form.type === 'FILE'"
                v-model="form.scanInterval"
                :min="10"
                :max="3600"
                style="width: 100%"
              />
              <el-input-number
                v-else
                v-model="form.collectInterval"
                :min="10"
                :max="3600"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="描述信息">
              <el-input
                v-model="form.description"
                type="textarea"
                :rows="1"
                placeholder="选填"
              />
            </el-form-item>
          </el-col>
        </el-row>
      </div>
    </el-form>

    <template #footer>
      <div class="dialog-footer">
        <el-button @click="handleClose">取消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitting">
          <el-icon v-if="!submitting"><Check /></el-icon>
          {{ isEdit ? '保存' : '创建' }}
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import {
  MagicStick,
  Check,
  Monitor,
  Document,
  DataLine
} from '@element-plus/icons-vue'
import { create, update } from '@/api/probe'
import { create as createFileProbe, update as updateFileProbe } from '@/api/fileProbe'
import { create as createDatabaseProbe } from '@/api/databaseProbe'
import DatabaseProbeForm from './DatabaseProbeForm.vue'

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  },
  probe: {
    type: Object,
    default: () => null
  }
})

const emit = defineEmits(['update:modelValue', 'success'])

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const isEdit = computed(() => !!(props.probe?.id))

const formRef = ref(null)
const databaseFormRef = ref(null)
const submitting = ref(false)

// 表单数据
const form = reactive({
  id: undefined,
  name: '',
  probeKey: '',
  type: 'SYSTEM',
  hostIp: '',
  port: 58081,
  collectInterval: 60,
  description: '',
  // 文件探针专属字段
  scanPath: '',
  fileExtensions: '',
  ignorePaths: '',
  scanInterval: 300,
  maxDepth: 10
})

// 数据库探针配置
const databaseConfig = ref({})

// 探针类型选择方法
const selectType = (type) => {
  form.type = type
}

// 表单验证规则
const rules = {
  type: [
    { required: true, message: '请选择探针类型', trigger: 'change' }
  ],
  name: [
    { required: true, message: '请输入探针名称', trigger: 'blur' },
    { min: 2, max: 100, message: '探针名称长度在2-100个字符之间', trigger: 'blur' }
  ],
  port: [
    { required: true, message: '请输入端口号', trigger: 'blur' },
    {
      type: 'number',
      min: 1,
      max: 65535,
      message: '端口号必须在1-65535之间',
      trigger: 'blur'
    }
  ],
  collectInterval: [
    { required: true, message: '请选择采集间隔', trigger: 'change' },
    {
      type: 'number',
      min: 1,
      max: 3600,
      message: '采集间隔必须在1-3600秒之间',
      trigger: 'change'
    }
  ],
  scanPath: [
    { required: true, message: '请输入扫描路径', trigger: 'blur' }
  ]
}

// 监听对话框打开，初始化表单数据
watch(() => props.modelValue, (newValue) => {
  if (newValue) {
    if (isEdit.value && props.probe) {
      // 编辑模式：加载探针数据
      Object.assign(form, {
        id: props.probe.id,
        name: props.probe.name || '',
        probeKey: props.probe.probeKey || '',
        type: props.probe.type || 'SYSTEM',
        hostIp: props.probe.hostIp || '',
        port: props.probe.port || 58081,
        collectInterval: props.probe.collectInterval || 60,
        description: props.probe.description || '',
        // 文件探针专属字段
        scanPath: props.probe.scanPath || '',
        fileExtensions: props.probe.fileExtensions || '',
        ignorePaths: props.probe.ignorePaths || '',
        scanInterval: props.probe.scanInterval || 300,
        maxDepth: props.probe.maxDepth || 10
      })
    } else {
      // 新增模式：重置表单并自动生成probeKey
      resetForm()
      generateProbeKey()
    }
  }
})

// 监听类型变化，自动重新生成 probeKey
watch(() => form.type, () => {
  if (!isEdit.value) {
    generateProbeKey()
  }
})

// 监听类型变化，当切换到DATABASE时初始化配置
watch(() => form.type, (newType, oldType) => {
  if (!isEdit.value && newType === 'DATABASE' && oldType !== 'DATABASE') {
    databaseConfig.value = {
      databaseType: 'PostgreSQL',
      databaseHost: 'localhost',
      databasePort: 5432,
      databaseName: '',
      username: '',
      password: '',
      schemas: [],
      collectInterval: 60
    }
  }
})

// 自动生成探针Key
const generateProbeKey = () => {
  const timestamp = Date.now().toString(36).substring(0, 6)
  const random = Math.random().toString(36).substring(2, 5)

  let suffix = ''
  if (form.type === 'SYSTEM') {
    suffix = 'system'
  } else if (form.type === 'FILE') {
    suffix = 'file'
  } else if (form.type === 'DATABASE') {
    suffix = 'database'
  } else {
    suffix = 'custom'
  }

  form.probeKey = `AGENT-${suffix}-${timestamp}-${random}`
}

// 重置表单
const resetForm = () => {
  Object.assign(form, {
    id: undefined,
    name: '',
    probeKey: '',
    type: 'SYSTEM',
    hostIp: '',
    port: 58081,
    collectInterval: 60,
    description: '',
    scanPath: '',
    fileExtensions: '',
    ignorePaths: '',
    scanInterval: 300,
    maxDepth: 10
  })
  databaseConfig.value = {}
}

// 提交表单
const handleSubmit = async () => {
  try {
    await formRef.value.validate()

    // 如果 probeKey 为空，自动生成
    if (!form.probeKey || form.probeKey.trim() === '') {
      generateProbeKey()
    }

    // 如果是数据库探针，还需要验证数据库配置表单
    if (form.type === 'DATABASE') {
      await databaseFormRef.value?.validate()
    }

    submitting.value = true

    // 处理 hostIp：如果为空，使用默认值 127.0.0.1
    const finalHostIp = form.hostIp?.trim() || '127.0.0.1'

    let result
    if (form.type === 'FILE') {
      // 文件探针
      const fileData = {
        ...form,
        hostIp: finalHostIp
      }
      if (isEdit.value) {
        result = await updateFileProbe(form.id, fileData)
      } else {
        result = await createFileProbe(fileData)
      }
    } else if (form.type === 'DATABASE') {
      // 数据库探针 - 简化配置，只传递数据库类型
      const dbData = {
        name: form.name,
        type: form.type,
        probeKey: form.probeKey,
        hostIp: finalHostIp,
        port: form.port || 58081,
        collectInterval: form.collectInterval || 60,
        config: JSON.stringify({
          databaseType: databaseConfig.value.databaseType,
          description: '数据库连接配置由Agent管理'
        })
      }
      if (isEdit.value) {
        const updateData = {
          id: form.id,
          name: form.name,
          probeKey: form.probeKey,
          type: form.type,
          hostIp: finalHostIp,
          port: form.port,
          collectInterval: form.collectInterval,
          config: dbData.config
        }
        result = await update(form.id, updateData)
      } else {
        result = await create(dbData)
      }
    } else {
      // 系统探针
      const data = {
        ...form,
        hostIp: finalHostIp
      }
      if (isEdit.value) {
        const updateData = {
          id: form.id,
          name: form.name,
          probeKey: form.probeKey,
          type: form.type,
          hostIp: finalHostIp,
          port: form.port,
          collectInterval: form.collectInterval
        }
        result = await update(form.id, updateData)
      } else {
        result = await create(data)
      }
    }

    if (result.code === 200) {
      ElMessage.success(isEdit.value ? '更新成功' : '创建成功')
      emit('success')
      handleClose()
    } else {
      ElMessage.error(result?.message || '操作失败')
    }
  } catch (error) {
    console.error('提交失败:', error)
  } finally {
    submitting.value = false
  }
}

// 关闭对话框
const handleClose = () => {
  resetForm()
  visible.value = false
}
</script>

<style scoped lang="scss">
.compact-dialog {
  :deep(.el-dialog__body) {
    padding-top: 12px !important;
    padding-bottom: 12px !important;
    max-height: 520px;
    overflow-y: auto;

    // 自定义滚动条样式
    &::-webkit-scrollbar {
      width: 6px;
    }

    &::-webkit-scrollbar-track {
      background: #f1f1f1;
      border-radius: 3px;
    }

    &::-webkit-scrollbar-thumb {
      background: #c1c1c1;
      border-radius: 3px;

      &:hover {
        background: #a8a8a8;
      }
    }
  }

  &.has-type-selector {
    :deep(.el-dialog__body) {
      max-height: 580px;
    }
  }

  :deep(.el-dialog__header) {
    padding: 16px 20px 12px;
  }

  :deep(.el-dialog__footer) {
    padding: 8px 20px 16px;
  }
}

.form-section {
  margin-bottom: 16px;
  min-height: 80px;

  &:last-child {
    margin-bottom: 0;
  }
}

.section-label {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 12px;
  height: 20px;
  line-height: 20px;
  padding-left: 2px;
  position: relative;

  &::before {
    content: '';
    position: absolute;
    left: -8px;
    top: 50%;
    transform: translateY(-50%);
    width: 3px;
    height: 14px;
    background: linear-gradient(135deg, #409eff 0%, #66b1ff 100%);
    border-radius: 2px;
  }
}

.probe-type-selector {
  margin-bottom: 4px;
}

.probe-type-options {
  display: flex;
  gap: 12px;
  width: 100%;
}

.probe-type-card {
  flex: 1;
  background: #fff;
  border: 1.5px solid #e4e7ed;
  border-radius: 8px;
  padding: 12px;
  cursor: pointer;
  transition: all 0.2s ease;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  height: 88px;
  position: relative;

  &:hover {
    border-color: #409eff;
    box-shadow: 0 2px 8px rgba(64, 158, 255, 0.12);
    transform: translateY(-1px);
  }

  &.is-selected {
    border-color: #409eff;
    background: linear-gradient(135deg, #ecf5ff 0%, #f0f9ff 100%);
    box-shadow: 0 2px 12px rgba(64, 158, 255, 0.18);

    .card-icon {
      color: #409eff;
      background: #fff;
      box-shadow: 0 2px 8px rgba(64, 158, 255, 0.15);
    }

    .card-title {
      color: #409eff;
    }
  }

  &:focus {
    outline: 2px solid #409eff;
    outline-offset: 2px;
  }
}

.card-icon {
  background: #f5f7fa;
  border-radius: 50%;
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #606266;
  margin-bottom: 8px;
  flex-shrink: 0;
  transition: all 0.2s ease;
}

.card-content {
  width: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  flex: 1;
}

.card-title {
  margin: 0 0 4px 0;
  font-size: 13px;
  font-weight: 600;
  color: #303133;
  line-height: 18px;
  height: 18px;
  display: flex;
  align-items: center;
}

.card-description {
  margin: 0;
  font-size: 11px;
  color: #909399;
  line-height: 16px;
  height: 16px;
  display: flex;
  align-items: center;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

:deep(.el-form-item) {
  margin-bottom: 10px;
  display: flex;
  align-items: center;
  min-height: 42px;
}

:deep(.el-form-item__label) {
  padding-bottom: 0;
  font-size: 13px;
  font-weight: 500;
  color: #606266;
  line-height: 32px;
  height: 32px;
  display: flex;
  align-items: center;
}

:deep(.el-form-item__content) {
  display: flex;
  align-items: center;
  line-height: 32px;
  min-height: 32px;
}

// 统一栅格列高度
:deep(.el-col) {
  min-height: 42px;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

// 统一行高度
:deep(.el-row) {
  margin-bottom: 0;

  &:not(:last-child) {
    margin-bottom: 0;
  }
}

:deep(.el-input-number) {
  width: 100%;
  height: 32px;

  .el-input__inner {
    height: 32px;
  }
}

:deep(.el-textarea__inner) {
  min-height: 32px !important;
  padding: 4px 8px !important;
  font-size: 13px;
  line-height: 24px;
}

:deep(.el-input__inner) {
  height: 32px;
  font-size: 13px;
  line-height: 24px;
}

:deep(.el-input__wrapper) {
  padding: 0 8px;
  height: 32px;
  box-sizing: border-box;
}

:deep(.el-select) {
  .el-input__wrapper {
    height: 32px;
  }
}

:deep(.el-button) {
  height: 32px;
  padding: 0 16px;
  font-size: 13px;
}

// Improved input focus states
:deep(.el-input:focus),
:deep(.el-input-number:focus),
:deep(.el-textarea:focus) {
  .el-input__inner,
  .el-textarea__inner {
    border-color: #409eff;
  }
}

// 响应式设计
@media (max-width: 768px) {
  .probe-type-options {
    flex-direction: column;
    gap: 10px;
  }

  .probe-type-card {
    flex-direction: row;
    text-align: left;
    height: 72px;
    padding: 10px;
    justify-content: flex-start;

    .card-icon {
      margin-bottom: 0;
      margin-right: 10px;
      margin-left: 0;
    }

    .card-content {
      align-items: flex-start;
      justify-content: center;
    }

    .card-title {
      font-size: 12px;
      margin-bottom: 2px;
    }

    .card-description {
      font-size: 11px;
    }
  }

  :deep(.el-col) {
    min-height: auto;
  }
}
</style>