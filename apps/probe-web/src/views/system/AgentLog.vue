<template>
  <div class="app-container">
    <el-card>
      <template #header>
        <div style="display:flex;justify-content:space-between;align-items:center">
          <span>Agent 日志</span>
          <el-button type="success" size="small" @click="handleDownload">导出 CSV</el-button>
        </div>
      </template>

      <el-form :inline="true" :model="filters" size="small">
        <el-form-item label="Agent">
          <el-input v-model="filters.agentCode" placeholder="Agent 编码" clearable />
        </el-form-item>
        <el-form-item label="级别">
          <el-select v-model="filters.level" placeholder="全部" clearable>
            <el-option label="ERROR" value="ERROR" />
            <el-option label="WARN" value="WARN" />
            <el-option label="INFO" value="INFO" />
            <el-option label="DEBUG" value="DEBUG" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadData">查询</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="tableData" border stripe v-loading="loading">
        <el-table-column prop="timestamp" label="时间" width="170" />
        <el-table-column prop="agentCode" label="Agent" width="120" />
        <el-table-column prop="level" label="级别" width="80">
          <template #default="{ row }">
            <el-tag :type="levelTag(row.level)" size="small">{{ row.level }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="logger" label="Logger" width="200" show-overflow-tooltip />
        <el-table-column prop="message" label="消息" show-overflow-tooltip />
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
import { getAgentLogs, downloadAgentLogs } from '../../api/agentLog'

const filters = ref({ agentCode: '', level: '' })
const tableData = ref([])
const loading = ref(false)
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)

const levelTag = (level) => ({ ERROR: 'danger', WARN: 'warning', INFO: 'info', DEBUG: '' }[level] || '')

async function loadData() {
  loading.value = true
  try {
    const { data } = await getAgentLogs({
      agentCode: filters.value.agentCode || undefined,
      level: filters.value.level || undefined,
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

async function handleDownload() {
  try {
    const res = await downloadAgentLogs(filters.value.agentCode || undefined)
    const url = window.URL.createObjectURL(new Blob([res.data || res]))
    const link = document.createElement('a')
    link.href = url
    link.download = 'agent-logs.csv'
    link.click()
    window.URL.revokeObjectURL(url)
  } catch { /* ignore */ }
}

onMounted(loadData)
</script>
