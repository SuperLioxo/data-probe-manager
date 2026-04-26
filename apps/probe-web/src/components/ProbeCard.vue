<template>
  <article
    class="probe-card glass-card"
    :class="[
      `probe-card-${probe.status}`,
      { 'probe-card-selected': isSelected }
    ]"
    :aria-label="`${probe.name} 探针卡片，状态：${getStatusText(probe.status)}`"
    :aria-selected="isSelected"
    role="button"
    tabindex="0"
    @click="handleClick"
    @keydown.enter="handleClick"
    @keydown.space.prevent="handleClick"
  >
    <el-card
      shadow="hover"
      class="probe-card-inner"
    >
      <template #header>
        <div class="card-header">
          <div class="probe-info">
            <el-icon class="probe-icon" :class="`type-${probe.type?.toLowerCase()}`" aria-hidden="true">
              <Monitor v-if="probe.type === 'SYSTEM'" />
              <Setting v-else />
            </el-icon>
            <div class="probe-names">
              <h3 class="probe-name">{{ probe.name }}</h3>
              <p class="probe-key">{{ probe.probeKey }}</p>
            </div>
          </div>
          <el-tag
            :type="getStatusType(probe.status)"
            effect="plain"
            size="small"
            class="status-tag"
            :aria-label="'探针状态：' + getStatusText(probe.status)"
          >
            <el-icon class="status-icon" aria-hidden="true">
              <SuccessFilled v-if="probe.status === 'online'" />
              <CircleClose v-else-if="probe.status === 'offline'" />
              <WarningFilled v-else />
            </el-icon>
            {{ getStatusText(probe.status) }}
          </el-tag>
        </div>
      </template>

      <div class="card-content">
        <!-- 指标展示 -->
        <div class="metrics-grid" role="list" aria-label="探针性能指标">
          <div class="metric-item" role="listitem">
            <div class="metric-label">CPU</div>
            <div class="metric-value">
              <el-progress
                :percentage="probe.cpuUsage || 0"
                :status="getProgressStatus(probe.cpuUsage || 0)"
                :show-text="false"
                :stroke-width="8"
                :aria-label="'CPU使用率 ' + (probe.cpuUsage || 0).toFixed(1) + '百分比'"
              />
              <span class="metric-number" :aria-label="'CPU使用率'">{{ (probe.cpuUsage || 0).toFixed(1) }}%</span>
            </div>
          </div>

          <div class="metric-item" role="listitem">
            <div class="metric-label">内存</div>
            <div class="metric-value">
              <span class="metric-number">{{ formatBytes(probe.memoryUsage || 0) }}</span>
            </div>
          </div>

          <div class="metric-item" role="listitem">
            <div class="metric-label">磁盘</div>
            <div class="metric-value">
              <el-progress
                :percentage="probe.diskUsage || 50"
                :status="getProgressStatus(probe.diskUsage || 50)"
                :show-text="false"
                :stroke-width="8"
                :aria-label="'磁盘使用率 ' + (probe.diskUsage || 50) + '百分比'"
              />
              <span class="metric-number" :aria-label="'磁盘使用率'">{{ probe.diskUsage || 50 }}%</span>
            </div>
          </div>

          <div class="metric-item" role="listitem">
            <div class="metric-label">网络</div>
            <div class="metric-value">
              <div class="network-info">
                <span aria-label="网络上传">↑ {{ formatBytes(probe.networkOut || 0) }}</span>
                <span aria-label="网络下载">↓ {{ formatBytes(probe.networkIn || 0) }}</span>
              </div>
            </div>
          </div>
        </div>

        <!-- 附加信息 -->
        <div class="card-footer">
          <div class="footer-item">
            <el-icon aria-hidden="true"><Location /></el-icon>
            <span>{{ probe.hostIp }}:{{ probe.port }}</span>
          </div>
          <div class="footer-item">
            <el-icon aria-hidden="true"><Clock /></el-icon>
            <time :datetime="probe.lastHeartbeat">{{ formatTime(probe.lastHeartbeat) }}</time>
          </div>
        </div>
      </div>

      <!-- 操作按钮 -->
      <template #footer>
        <div class="card-actions" role="group" aria-label="探针操作">
          <el-button-group>
            <el-button
              size="small"
              type="primary"
              link
              @click.stop="handleView"
              :aria-label="'查看 ' + probe.name + ' 详情'"
              class="touch-target"
            >
              <el-icon><View /></el-icon>
              详情
            </el-button>
            <el-button
              size="small"
              type="primary"
              link
              @click.stop="handleEdit"
              :aria-label="'编辑 ' + probe.name"
              class="touch-target"
            >
              <el-icon><Edit /></el-icon>
              编辑
            </el-button>
            <el-button
              size="small"
              type="success"
              link
              @click.stop="handleMonitor"
              v-if="probe.status === 'online'"
              :aria-label="'监控 ' + probe.name"
              class="touch-target"
            >
              <el-icon><DataAnalysis /></el-icon>
              监控
            </el-button>
            <el-button
              size="small"
              type="danger"
              link
              @click.stop="handleDelete"
              :aria-label="'删除 ' + probe.name"
              class="touch-target"
            >
              <el-icon><Delete /></el-icon>
              删除
            </el-button>
          </el-button-group>
        </div>
      </template>
    </el-card>
  </article>
</template>

<script setup>
import { useRouter } from 'vue-router'

const props = defineProps({
  probe: {
    type: Object,
    required: true
  },
  isSelected: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['click', 'view', 'edit', 'delete', 'monitor'])

const router = useRouter()

const getStatusType = (status) => {
  const typeMap = {
    online: 'success',
    offline: 'info',
    error: 'danger',
    ONLINE: 'success',
    OFFLINE: 'info',
    ERROR: 'danger'
  }
  return typeMap[status] || 'info'
}

const getStatusText = (status) => {
  const textMap = {
    online: '在线',
    offline: '离线',
    error: '异常',
    ONLINE: '在线',
    OFFLINE: '离线',
    ERROR: '异常'
  }
  return textMap[status] || status
}

const getProgressStatus = (percentage) => {
  if (percentage >= 90) return 'exception'
  if (percentage >= 70) return 'warning'
  return 'success'
}

const formatBytes = (bytes) => {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return (bytes / Math.pow(k, i)).toFixed(1) + ' ' + sizes[i]
}

const formatTime = (time) => {
  if (!time) return '-'
  const date = new Date(time)
  const now = new Date()
  const diff = Math.floor((now - date) / 1000)

  if (diff < 60) return `${diff}秒前`
  if (diff < 3600) return `${Math.floor(diff / 60)}分钟前`
  if (diff < 86400) return `${Math.floor(diff / 3600)}小时前`
  return date.toLocaleString('zh-CN')
}

const handleClick = () => {
  emit('click', props.probe)
}

const handleView = () => {
  emit('view', props.probe)
}

const handleEdit = () => {
  emit('edit', props.probe)
}

const handleDelete = () => {
  emit('delete', props.probe)
}

const handleMonitor = () => {
  emit('monitor', props.probe)
  router.push({ path: '/monitor', query: { probeId: props.probe.id } })
}
</script>

<style scoped lang="scss">
.probe-card {
  cursor: pointer;
  transition: all 0.2s ease;
  border-radius: 8px;
  margin-bottom: 16px;
  position: relative;

  &:hover {
    .probe-card-inner {
      box-shadow: var(--shadow-lg);
      border-color: var(--primary-500);
    }
  }

  &:active {
    transform: scale(0.98);
  }

  &:focus-visible {
    outline: 2px solid var(--primary-500);
    outline-offset: 2px;
    border-radius: 8px;
  }

  &.probe-card-selected {
    .probe-card-inner {
      border-color: var(--primary-500);
      box-shadow: 0 0 0 2px rgba(59, 130, 246, 0.2);
    }
  }

  &.probe-card-online {
    .probe-card-inner {
      border-left: 3px solid var(--el-color-success);
    }
  }

  &.probe-card-offline {
    .probe-card-inner {
      border-left: 3px solid var(--el-color-info);
      opacity: 0.8;
    }
  }

  &.probe-card-error {
    .probe-card-inner {
      border-left: 3px solid var(--el-color-danger);
      animation: errorPulse 2s ease-in-out infinite;
    }
  }
}

.probe-card-inner {
  transition: all 0.2s ease;
  height: 100%;

  :deep(.el-card__header) {
    padding: 12px 16px;
    background: var(--gradient-header);
  }

  :deep(.el-card__body) {
    padding: 16px;
  }

  :deep(.el-card__footer) {
    padding: 12px 16px;
    background: var(--bg-secondary);
  }
}

@keyframes errorPulse {
  0%, 100% {
    box-shadow: 0 0 0 0 rgba(245, 108, 108, 0.4);
  }
  50% {
    box-shadow: 0 0 0 8px rgba(245, 108, 108, 0);
  }
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;

  .probe-info {
    display: flex;
    align-items: center;
    gap: 12px;
    flex: 1;
    min-width: 0;

    .probe-icon {
      font-size: 28px;
      flex-shrink: 0;

      &.type-system {
        color: var(--probe-type-system);
      }

      &.type-application {
        color: var(--probe-type-application);
      }

      &.type-network {
        color: var(--probe-type-network);
      }

      &.type-custom {
        color: var(--probe-type-custom);
      }
    }

    .probe-names {
      flex: 1;
      min-width: 0;

      .probe-name {
        font-size: 15px;
        font-weight: 600;
        color: var(--text-primary);
        margin-bottom: 4px;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }

      .probe-key {
        font-size: 12px;
        color: var(--text-tertiary);
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }
    }
  }

  .status-tag {
    flex-shrink: 0;

    .status-icon {
      margin-right: 4px;
    }
  }
}

.card-content {
  .metrics-grid {
    display: grid;
    grid-template-columns: repeat(2, 1fr);
    gap: 12px;
    margin-bottom: 12px;

    @media (max-width: 480px) {
      grid-template-columns: 1fr;
    }

    .metric-item {
      .metric-label {
        font-size: 12px;
        color: var(--text-tertiary);
        margin-bottom: 6px;
      }

      .metric-value {
        display: flex;
        align-items: center;
        gap: 8px;

        :deep(.el-progress) {
          flex: 1;
          min-width: 0;
        }

        .metric-number {
          font-size: 13px;
          font-weight: 600;
          color: var(--text-primary);
          min-width: 45px;
          text-align: right;
          flex-shrink: 0;
        }

        .network-info {
          display: flex;
          flex-direction: column;
          gap: 2px;
          font-size: 11px;
          color: var(--text-secondary);
        }
      }
    }
  }

  .card-footer {
    display: flex;
    justify-content: space-between;
    padding-top: 12px;
    border-top: 1px solid var(--border-color);
    gap: 8px;

    @media (max-width: 480px) {
      flex-direction: column;
      gap: 4px;
    }

    .footer-item {
      display: flex;
      align-items: center;
      gap: 4px;
      font-size: 12px;
      color: var(--text-tertiary);
      min-width: 0;

      .el-icon {
        font-size: 14px;
        flex-shrink: 0;
      }

      span, time {
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }
    }
  }
}

.card-actions {
  display: flex;
  justify-content: center;

  :deep(.el-button-group) {
    display: flex;
    flex-wrap: wrap;
    gap: 4px;

    .el-button {
      min-width: 44px;
      min-height: 44px;

      @media (max-width: 480px) {
        padding: 4px 8px;
        font-size: 12px;

        .el-icon {
          margin-right: 2px;
        }
      }
    }
  }
}
</style>
