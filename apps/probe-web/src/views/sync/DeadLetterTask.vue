<template>
  <div class="app-container">
    <el-card>
      <template #header>
        <div style="display:flex;justify-content:space-between;align-items:center">
          <span>死信任务</span>
          <el-button type="danger" size="small" @click="handlePurge">清理已耗尽</el-button>
        </div>
      </template>

      <el-tabs v-model="filters.status" @tab-change="loadData">
        <el-tab-pane label="全部" name="" />
        <el-tab-pane label="待重试" name="PENDING" />
        <el-tab-pane label="重试中" name="RETRYING" />
        <el-tab-pane label="已耗尽" name="EXHAUSTED" />
        <el-tab-pane label="已解决" name="RESOLVED" />
      </el-tabs>

      <el-table :data="tableData" border stripe v-loading="loading">
        <el-table-column prop="taskName" label="任务名称" width="160" show-overflow-tooltip />
        <el-table-column prop="sourceProbeKey" label="源探针" width="120" />
        <el-table-column prop="failureReason" label="失败原因" show-overflow-tooltip />
        <el-table-column prop="retryCount" label="重试次数" width="80" align="center" />
        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status==='PENDING'||row.status==='RETRYING'"
              type="primary" size="small" @click="handleRetry(row)">重试</el-button>
            <el-button type="danger" size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination style="margin-top:16px;justify-content:flex-end"
        v-model:current-page="page" v-model:page-size="pageSize"
        :total="total" layout="total, prev, pager, next"
        @current-change="loadData" />
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getDeadLetterTasks, retryDeadLetterTask, deleteDeadLetterTask, purgeDeadLetterTasks } from '../../api/deadLetterTask'

const filters = ref({ status: '' })
const tableData = ref([])
const loading = ref(false)
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)

const statusTag = (s) => ({ PENDING: 'warning', RETRYING: '', EXHAUSTED: 'danger', RESOLVED: 'success' }[s] || 'info')

async function loadData() {
  loading.value = true
  try {
    const { data } = await getDeadLetterTasks({
      status: filters.value.status || undefined,
      pageNum: page.value,
      pageSize: pageSize.value
    })
    if (data?.records) {
      tableData.value = data.records
      total.value = data.total || 0
    }
  } finally {
    loading.value = false
  }
}

async function handleRetry(row) {
  await retryDeadLetterTask(row.id)
  ElMessage.success('已触发重试')
  loadData()
}

async function handleDelete(row) {
  await ElMessageBox.confirm('确定删除此死信任务？', '确认')
  await deleteDeadLetterTask(row.id)
  ElMessage.success('已删除')
  loadData()
}

async function handlePurge() {
  await purgeDeadLetterTasks()
  ElMessage.success('清理完成')
  loadData()
}

onMounted(loadData)
</script>
