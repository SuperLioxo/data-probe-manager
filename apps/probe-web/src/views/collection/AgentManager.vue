<template>
  <div class="agent-manager">
    <el-card class="header-card">
      <div class="header-content">
        <div class="title-section">
          <h2>Agent后端管理</h2>
          <p class="description">管理和监控探针代理程序的运行状态</p>
        </div>
        <div class="stats-section">
          <div class="stat-item">
            <div class="stat-label">总Agent数</div>
            <div class="stat-value">{{ agents.length }}</div>
          </div>
          <div class="stat-item">
            <div class="stat-label">在线</div>
            <div class="stat-value online">{{ onlineCount }}</div>
          </div>
          <div class="stat-item">
            <div class="stat-label">离线</div>
            <div class="stat-value offline">{{ offlineCount }}</div>
          </div>
        </div>
      </div>
    </el-card>

    <el-card class="main-card">
      <template #header>
        <div class="card-header">
          <span>Agent列表</span>
          <el-button
            type="primary"
            :icon="Refresh"
            @click="loadAgents"
            :loading="loading"
          >
            刷新
          </el-button>
        </div>
      </template>

      <el-table
        :data="agents"
        v-loading="loading"
        stripe
        style="width: 100%"
      >
        <el-table-column prop="agentCode" label="Agent代码" width="150" />
        <el-table-column prop="agentName" label="Agent名称" width="200" />
        <el-table-column prop="hostIp" label="主机地址" width="150">
          <template #default="{ row }">
            {{ row.hostIp }}:{{ row.port }}
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="row.status === 'online' ? 'success' : 'danger'">
              {{ row.status === 'online' ? '在线' : '离线' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="version" label="版本" width="100" />
        <el-table-column prop="lastHeartbeat" label="最后心跳" width="180">
          <template #default="{ row }">
            {{ formatTime(row.lastHeartbeat) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="300" fixed="right">
          <template #default="{ row }">
            <el-button-group>
              <el-button
                type="primary"
                size="small"
                :icon="VideoPlay"
                @click="handleStart(row)"
                :disabled="row.status === 'online'"
              >
                启动
              </el-button>
              <el-button
                type="warning"
                size="small"
                :icon="VideoPause"
                @click="handleStop(row)"
                :disabled="row.status === 'offline'"
              >
                停止
              </el-button>
              <el-button
                type="info"
                size="small"
                :icon="RefreshRight"
                @click="handleRestart(row)"
                :disabled="row.status === 'offline'"
              >
                重启
              </el-button>
            </el-button-group>
            <el-button
              type="info"
              size="small"
              :icon="Document"
              @click="handleViewGuide(row)"
              style="margin-left: 10px"
            >
              指引
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 操作指引对话框 -->
    <el-dialog
      v-model="guideDialogVisible"
      :title="`Agent操作指引 - ${currentAgent?.agentCode}`"
      width="800px"
      destroy-on-close
    >
      <div v-if="currentAgent" class="guide-content">
        <el-alert
          :title="currentAgent.online ? 'Agent当前在线' : 'Agent当前离线'"
          :type="currentAgent.online ? 'success' : 'warning'"
          :closable="false"
          style="margin-bottom: 20px"
        >
          {{ currentAgent.online ? 'Agent程序正在运行中' : 'Agent程序未运行' }}
        </el-alert>

        <el-descriptions :column="2" border style="margin-bottom: 20px">
          <el-descriptions-item label="Agent代码">
            {{ currentAgent.agentCode }}
          </el-descriptions-item>
          <el-descriptions-item label="主机地址">
            {{ currentAgent.hostIp }}:{{ currentAgent.port }}
          </el-descriptions-item>
          <el-descriptions-item label="版本">
            {{ currentAgent.version }}
          </el-descriptions-item>
          <el-descriptions-item label="最后心跳">
            {{ formatTime(currentAgent.lastHeartbeat) }}
          </el-descriptions-item>
        </el-descriptions>

        <el-divider content-position="left">操作命令</el-divider>

        <div class="command-section">
          <div class="command-item">
            <div class="command-title">启动Agent</div>
            <el-input
              :model-value="currentAgent.commands?.start"
              type="textarea"
              :rows="3"
              readonly
              placeholder="启动命令"
            />
            <el-button
              type="primary"
              size="small"
              @click="copyCommand('start')"
              style="margin-top: 5px"
            >
              复制命令
            </el-button>
          </div>

          <div class="command-item">
            <div class="command-title">停止Agent</div>
            <el-input
              :model-value="currentAgent.commands?.stop"
              type="textarea"
              :rows="2"
              readonly
              placeholder="停止命令"
            />
            <el-button
              type="warning"
              size="small"
              @click="copyCommand('stop')"
              style="margin-top: 5px"
            >
              复制命令
            </el-button>
          </div>

          <div class="command-item">
            <div class="command-title">重启Agent</div>
            <el-input
              :model-value="currentAgent.commands?.restart"
              type="textarea"
              :rows="4"
              readonly
              placeholder="重启命令"
            />
            <el-button
              type="info"
              size="small"
              @click="copyCommand('restart')"
              style="margin-top: 5px"
            >
              复制命令
            </el-button>
          </div>

          <div class="command-item">
            <div class="command-title">检查Agent状态</div>
            <el-input
              :model-value="currentAgent.commands?.checkStatus"
              readonly
              placeholder="检查命令"
            />
          </div>

          <div class="command-item">
            <div class="command-title">查看Agent日志</div>
            <el-input
              :model-value="currentAgent.commands?.viewLogs"
              readonly
              placeholder="日志命令"
            />
          </div>
        </div>

        <el-divider content-position="left">说明</el-divider>

        <el-alert
          title="重要提示"
          type="info"
          :closable="false"
        >
          <p>1. Agent是独立的进程，需要在Agent所在服务器上执行命令</p>
          <p>2. 启动Agent前，请确保已配置好环境变量（PROBE_KEY等）</p>
          <p>3. 建议使用 nohup 或 systemd 管理Agent进程</p>
          <p>4. Agent启动后会自动注册到Admin系统</p>
        </el-alert>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, VideoPlay, VideoPause, RefreshRight, Document } from '@element-plus/icons-vue'
import agentApi from '@/api/agent'

// 数据
const agents = ref([])
const loading = ref(false)
const guideDialogVisible = ref(false)
const currentAgent = ref(null)

// 定时器引用
let refreshTimer = null

// 统计
const onlineCount = computed(() => agents.value.filter(a => a.status === 'online').length)
const offlineCount = computed(() => agents.value.filter(a => a.status === 'offline').length)

// 加载Agent列表
const loadAgents = async () => {
  loading.value = true
  try {
    const response = await agentApi.getAgents()
    if (response.code === 200) {
      agents.value = response.data || []
    } else {
      ElMessage.error(response.message || '加载Agent列表失败')
    }
  } catch (error) {
    console.error('[Agent管理] 加载失败:', error)
    ElMessage.error('加载Agent列表失败: ' + error.message)
  } finally {
    loading.value = false
  }
}

// 启动Agent
const handleStart = async (agent) => {
  if (agent.status === 'online') {
    ElMessage.info('Agent已在线，无需启动')
    return
  }

  try {
    await ElMessageBox.confirm(
      `确认启动Agent程序【${agent.agentCode}】吗？`,
      '启动Agent',
      {
        confirmButtonText: '确认启动',
        cancelButtonText: '取消',
        type: 'info'
      }
    )

    loading.value = true
    const response = await agentApi.startAgent(agent.agentCode)

    if (response.code === 200) {
      ElMessage.success(response.data?.message || 'Agent启动命令已发送')
      // 延迟刷新列表，等待Agent状态更新
      setTimeout(() => {
        loadAgents()
      }, 3000)
    } else {
      ElMessage.error(response.message || 'Agent启动失败')
    }
  } catch (error) {
    if (error !== 'cancel') {
      console.error('[Agent管理] 启动失败:', error)
      // 只显示一条错误消息
      const errorMsg = error.response?.data?.message || error.message || '未知错误'
      ElMessage.error('Agent启动失败: ' + errorMsg)
    }
  } finally {
    loading.value = false
  }
}

// 停止Agent
const handleStop = async (agent) => {
  if (agent.status === 'offline') {
    ElMessage.info('Agent已离线，无需停止')
    return
  }

  try {
    await ElMessageBox.confirm(
      `确认停止Agent程序【${agent.agentCode}】吗？`,
      '停止Agent',
      {
        confirmButtonText: '确认停止',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )

    loading.value = true
    const response = await agentApi.stopAgent(agent.agentCode)

    if (response.code === 200) {
      ElMessage.success(response.data?.message || 'Agent停止命令已发送')
      // 延迟刷新列表，等待Agent状态更新
      setTimeout(() => {
        loadAgents()
      }, 3000)
    } else {
      ElMessage.error(response.message || 'Agent停止失败')
    }
  } catch (error) {
    if (error !== 'cancel') {
      console.error('[Agent管理] 停止失败:', error)
      const errorMsg = error.response?.data?.message || error.message || '未知错误'
      ElMessage.error('Agent停止失败: ' + errorMsg)
    }
  } finally {
    loading.value = false
  }
}

// 重启Agent
const handleRestart = async (agent) => {
  try {
    await ElMessageBox.confirm(
      `确认重启Agent程序【${agent.agentCode}】吗？`,
      '重启Agent',
      {
        confirmButtonText: '确认重启',
        cancelButtonText: '取消',
        type: 'info'
      }
    )

    loading.value = true
    const response = await agentApi.restartAgent(agent.agentCode)

    if (response.code === 200) {
      ElMessage.success(response.data?.message || 'Agent重启命令已发送')
      // 延迟刷新列表，等待Agent状态更新
      setTimeout(() => {
        loadAgents()
      }, 5000) // 重启需要更长时间
    } else {
      ElMessage.error(response.message || 'Agent重启失败')
    }
  } catch (error) {
    if (error !== 'cancel') {
      console.error('[Agent管理] 重启失败:', error)
      const errorMsg = error.response?.data?.message || error.message || '未知错误'
      ElMessage.error('Agent重启失败: ' + errorMsg)
    }
  } finally {
    loading.value = false
  }
}

// 查看指引
const handleViewGuide = (agent) => {
  showAgentGuide(agent)
}

// 显示Agent指引
const showAgentGuide = async (agent) => {
  loading.value = true
  try {
    const response = await agentApi.getAgentGuide(agent.agentCode)
    if (response.code === 200) {
      currentAgent.value = {
        ...agent,
        ...response.data,
        online: response.data.online
      }
      guideDialogVisible.value = true
    } else {
      ElMessage.error(response.message || '获取操作指引失败')
    }
  } catch (error) {
    console.error('[Agent管理] 获取指引失败:', error)
    const errorMsg = error.response?.data?.message || error.message || '未知错误'
    ElMessage.error('获取操作指引失败: ' + errorMsg)
  } finally {
    loading.value = false
  }
}

// 复制命令
const copyCommand = (commandKey) => {
  const command = currentAgent.value.commands[commandKey]
  if (!command) {
    ElMessage.warning('命令不存在')
    return
  }

  navigator.clipboard.writeText(command).then(() => {
    ElMessage.success('命令已复制到剪贴板')
  }).catch(() => {
    ElMessage.error('复制失败，请手动复制')
  })
}

// 格式化时间
const formatTime = (time) => {
  if (!time) return '-'
  const date = new Date(time)
  return date.toLocaleString('zh-CN')
}

// 页面加载时获取Agent列表
onMounted(() => {
  loadAgents()
  // 定期刷新（每30秒）
  refreshTimer = setInterval(() => {
    loadAgents()
  }, 30000)
})

// 页面卸载时清理定时器
onUnmounted(() => {
  if (refreshTimer) {
    clearInterval(refreshTimer)
    refreshTimer = null
  }
})
</script>

<style scoped lang="scss">
.agent-manager {
  padding: 20px;

  .header-card {
    margin-bottom: 20px;

    .header-content {
      display: flex;
      justify-content: space-between;
      align-items: center;

      .title-section {
        h2 {
          margin: 0 0 8px 0;
          font-size: 24px;
          color: #303133;
        }

        .description {
          margin: 0;
          color: #909399;
          font-size: 14px;
        }
      }

      .stats-section {
        display: flex;
        gap: 30px;

        .stat-item {
          text-align: center;

          .stat-label {
            font-size: 14px;
            color: #909399;
            margin-bottom: 8px;
          }

          .stat-value {
            font-size: 28px;
            font-weight: bold;
            color: #303133;

            &.online {
              color: #67c23a;
            }

            &.offline {
              color: #f56c6c;
            }
          }
        }
      }
    }
  }

  .main-card {
    .card-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
    }
  }

  .guide-content {
    .command-section {
      .command-item {
        margin-bottom: 20px;

        &:last-child {
          margin-bottom: 0;
        }

        .command-title {
          font-weight: bold;
          margin-bottom: 8px;
          color: #303133;
        }
      }
    }
  }
}
</style>
