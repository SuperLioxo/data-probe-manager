<template>
  <div class="status-bar" :class="{ 'status-error': !systemStatus.healthy }">
    <div class="status-left">
      <el-icon
        class="status-icon"
        :class="systemStatus.healthy ? 'status-online' : 'status-offline'"
      >
        <CircleFilled v-if="systemStatus.healthy" />
        <CircleClose v-else />
      </el-icon>
      <span class="status-text">{{ systemStatus.message }}</span>
    </div>
    <div class="status-right">
      <span class="status-item">
        <el-icon><Monitor /></el-icon>
        探针: {{ systemStatus.totalProbes }}
      </span>
      <span class="status-item status-success">
        <el-icon><SuccessFilled /></el-icon>
        在线: {{ systemStatus.onlineProbes }}
      </span>
      <span class="status-item">
        <el-icon><Clock /></el-icon>
        {{ currentTime }}
      </span>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted } from 'vue'
import { probeApi } from '@/api/probe'

const systemStatus = reactive({
  healthy: true,
  message: '系统运行正常',
  totalProbes: 0,
  onlineProbes: 0,
  offlineProbes: 0
})

const currentTime = ref('')
let timeInterval = null
let statusInterval = null

// 更新时间
const updateTime = () => {
  const now = new Date()
  currentTime.value = now.toLocaleString('zh-CN', {
    hour12: false,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  })
}

// 获取系统状态
const fetchSystemStatus = async () => {
  try {
    // 获取探针统计
    const probeRes = await probeApi.getList({ pageNum: 1, pageSize: 100 })
    if (probeRes.code === 200) {
      const probes = probeRes.data.records || []
      systemStatus.totalProbes = probes.length
      systemStatus.onlineProbes = probes.filter(p => p.status === 'online').length
      systemStatus.offlineProbes = probes.filter(p => p.status === 'offline').length
    }

    // 判断系统健康状态
    const offlineRatio = systemStatus.offlineProbes / (systemStatus.totalProbes || 1)
    if (offlineRatio > 0.5) {
      systemStatus.healthy = false
      systemStatus.message = '系统异常: 请检查离线探针'
    } else if (offlineRatio > 0) {
      systemStatus.healthy = true
      systemStatus.message = '系统运行中: 存在离线探针'
    } else {
      systemStatus.healthy = true
      systemStatus.message = '系统运行正常'
    }
  } catch (error) {
    console.error('获取系统状态失败:', error)
    systemStatus.healthy = false
    systemStatus.message = '连接异常: 无法获取系统状态'
  }
}

onMounted(() => {
  updateTime()
  fetchSystemStatus()

  // 每秒更新时间
  timeInterval = setInterval(updateTime, 1000)

  // 每30秒更新系统状态
  statusInterval = setInterval(fetchSystemStatus, 30000)
})

onUnmounted(() => {
  if (timeInterval) clearInterval(timeInterval)
  if (statusInterval) clearInterval(statusInterval)
})
</script>

<style scoped lang="scss">
.status-bar {
  height: 40px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  font-size: 13px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  position: relative;
  z-index: 100;

  &.status-error {
    background: linear-gradient(135deg, #f56c6c 0%, #e6a23c 100%);
  }

  .status-left {
    display: flex;
    align-items: center;
    gap: 8px;

    .status-icon {
      font-size: 12px;

      &.status-online {
        color: #67c23a;
        animation: pulse 2s ease-in-out infinite;
      }

      &.status-offline {
        color: #f56c6c;
        animation: blink 1s ease-in-out infinite;
      }
    }

    .status-text {
      font-weight: 500;
    }
  }

  .status-right {
    display: flex;
    align-items: center;
    gap: 20px;

    .status-item {
      display: flex;
      align-items: center;
      gap: 4px;
      padding: 4px 12px;
      background: rgba(255, 255, 255, 0.15);
      border-radius: 12px;
      transition: all 0.3s;

      .el-icon {
        font-size: 14px;
      }

      &:hover {
        background: rgba(255, 255, 255, 0.25);
        transform: translateY(-1px);
      }

      &.status-success {
        background: rgba(103, 194, 58, 0.2);
      }

      &.status-warning {
        background: rgba(230, 162, 60, 0.2);
        animation: shake 0.5s ease-in-out;
      }
    }
  }
}

@keyframes pulse {
  0%, 100% {
    opacity: 1;
  }
  50% {
    opacity: 0.5;
  }
}

@keyframes blink {
  0%, 100% {
    opacity: 1;
  }
  50% {
    opacity: 0.3;
  }
}

@keyframes shake {
  0%, 100% {
    transform: translateX(0);
  }
  25% {
    transform: translateX(-2px);
  }
  75% {
    transform: translateX(2px);
  }
}
</style>
