<template>
  <div class="database-probe-form">
    <el-form :model="form" :rules="rules" ref="formRef" label-width="120px">
      <!-- 数据库类型 -->
      <el-form-item label="数据库类型" prop="databaseType">
        <el-select
          v-model="form.databaseType"
          placeholder="请选择数据库类型"
          @change="onDatabaseTypeChange"
          :loading="loadingDatabaseTypes"
        >
          <el-option
            v-for="dbType in databaseTypes"
            :key="dbType.type"
            :label="dbType.label"
            :value="dbType.type"
          />
        </el-select>
      </el-form-item>
    </el-form>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { probeApi } from '@/api/probe'

const props = defineProps({
  modelValue: {
    type: Object,
    default: () => ({})
  }
})

const emit = defineEmits(['update:modelValue'])

const formRef = ref()
const loadingDatabaseTypes = ref(false)
const databaseTypes = ref([])

// 表单数据
const form = reactive({
  databaseType: '',
  databaseHost: '',
  databasePort: null,
  databaseName: '',
  username: '',
  password: '',
  schemas: [],
  collectInterval: 60
})

// 初始化表单数据
if (props.modelValue && Object.keys(props.modelValue).length > 0) {
  Object.assign(form, props.modelValue)
}

// 获取数据库类型列表
const fetchDatabaseTypes = async () => {
  try {
    loadingDatabaseTypes.value = true
    const response = await probeApi.getDatabaseTypes()
    if (response.code === 200 && response.data) {
      databaseTypes.value = response.data
      console.log('已加载的数据库类型:', databaseTypes.value)
    } else {
      console.error('获取数据库类型失败:', response.message)
      // 使用fallback列表，不显示错误消息（request.js已经显示了）
      databaseTypes.value = [
        { type: 'mysql', label: 'MySQL', defaultPort: 3306 },
        { type: 'postgresql', label: 'PostgreSQL', defaultPort: 5432 },
        { type: 'oracle', label: 'Oracle', defaultPort: 1521 },
        { type: 'sqlserver', label: 'SQL Server', defaultPort: 1433 }
      ]
    }
  } catch (error) {
    console.error('获取数据库类型异常:', error)
    ElMessage.error('获取数据库类型失败，已使用默认列表')
    // 使用fallback列表
    databaseTypes.value = [
      { type: 'mysql', label: 'MySQL', defaultPort: 3306 },
      { type: 'postgresql', label: 'PostgreSQL', defaultPort: 5432 },
      { type: 'oracle', label: 'Oracle', defaultPort: 1521 },
      { type: 'sqlserver', label: 'SQL Server', defaultPort: 1433 }
    ]
  } finally {
    loadingDatabaseTypes.value = false
  }
}

// 组件挂载时获取数据库类型
onMounted(() => {
  fetchDatabaseTypes()
})

// 监听外部数据变化
watch(() => props.modelValue, (newValue) => {
  if (newValue && Object.keys(newValue).length > 0) {
    Object.assign(form, newValue)
  }
}, { immediate: true })

// 监听内部数据变化，通知父组件
watch(form, (newValue) => {
  emit('update:modelValue', { ...newValue })
}, { deep: true })

// 数据库类型改变时的处理
const onDatabaseTypeChange = (type) => {
  const selectedType = databaseTypes.value.find(db => db.type === type)
  if (selectedType && selectedType.defaultPort) {
    form.databasePort = selectedType.defaultPort
  }
}

// 表单验证规则
const rules = {
  databaseType: [
    { required: true, message: '请选择数据库类型', trigger: 'change' }
  ]
}

// 暴露验证方法供父组件调用
const validate = () => {
  return formRef.value.validate()
}

defineExpose({
  validate
})
</script>

<style scoped lang="scss">
.database-probe-form {
  :deep(.el-form-item) {
    margin-bottom: 10px;
    display: flex;
    align-items: center;
    min-height: 42px;
  }

  :deep(.el-form-item__label) {
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

  :deep(.el-select) {
    width: 100%;

    .el-input__wrapper {
      height: 32px;
    }

    .el-input__inner {
      height: 32px;
      font-size: 13px;
      line-height: 24px;
    }
  }

  :deep(.el-input__inner) {
    height: 32px;
    font-size: 13px;
    line-height: 24px;
  }

  :deep(.el-input__wrapper) {
    height: 32px;
    box-sizing: border-box;
  }
}
</style>
