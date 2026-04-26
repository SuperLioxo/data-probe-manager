<template>
  <header class="topnav">
    <div class="topnav-left">
      <router-link to="/dashboard" class="topnav-brand">
        <el-icon :size="22" color="#3B82F6"><Monitor /></el-icon>
        <span class="brand-text">DataProbe</span>
      </router-link>
      <nav class="topnav-modules">
        <router-link
          v-for="m in modules" :key="m.path"
          :to="m.path"
          class="module-link"
          :class="{ active: activeModule === m.key }"
        >{{ m.label }}</router-link>
      </nav>
    </div>
    <div class="topnav-right">
      <div class="system-status">
        <span class="status-dot status-dot--online"></span>
        <span class="status-label">System OK</span>
      </div>
      <el-tooltip content="刷新" placement="bottom">
        <button class="topnav-icon-btn" @click="handleRefresh" aria-label="刷新">
          <el-icon><Refresh /></el-icon>
        </button>
      </el-tooltip>
      <el-tooltip content="全屏" placement="bottom">
        <button class="topnav-icon-btn" @click="toggleFullscreen" aria-label="全屏">
          <el-icon><FullScreen /></el-icon>
        </button>
      </el-tooltip>
      <el-dropdown trigger="click" @command="handleCommand">
        <button class="topnav-user">
          <el-avatar :size="28" class="user-avatar"><el-icon><User /></el-icon></el-avatar>
          <span class="user-name">管理员</span>
          <el-icon class="dropdown-caret"><CaretBottom /></el-icon>
        </button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="logout"><el-icon><SwitchButton /></el-icon>退出登录</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </header>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

const route = useRoute()
const router = useRouter()

const modules = [
  { key: 'dashboard', path: '/dashboard', label: '概览' },
  { key: 'collection', path: '/collection/probes', label: '采集' },
  { key: 'sync', path: '/sync/tasks', label: '同步' },
  { key: 'quality', path: '/quality/rules', label: '质量' },
  { key: 'monitoring', path: '/monitoring/realtime', label: '监控' },
  { key: 'system', path: '/system/agent-upgrade', label: '系统' }
]

const activeModule = computed(() => {
  const path = route.path
  if (path.startsWith('/dashboard')) return 'dashboard'
  if (path.startsWith('/collection') || path.startsWith('/probes') || path.startsWith('/agents') || path.startsWith('/probe-groups') || path.startsWith('/datasource')) return 'collection'
  if (path.startsWith('/sync') || path.startsWith('/dead-letter')) return 'sync'
  if (path.startsWith('/quality') || path.startsWith('/change-') || path.startsWith('/alert-')) return 'quality'
  if (path.startsWith('/monitoring') || path.startsWith('/monitor') || path.startsWith('/statistics')) return 'monitoring'
  if (path.startsWith('/system') || path.startsWith('/audit') || path.startsWith('/settings') || path.startsWith('/agent-')) return 'system'
  return ''
})

const handleRefresh = () => location.reload()

const toggleFullscreen = () => {
  if (!document.fullscreenElement) {
    document.documentElement.requestFullscreen()
  } else {
    document.exitFullscreen()
  }
}

const handleCommand = (cmd) => {
  if (cmd === 'logout') {
    localStorage.removeItem('isLogin')
    localStorage.removeItem('token')
    ElMessage.success('已退出登录')
    router.push('/login')
  }
}
</script>

<style scoped>
.topnav {
  height: 60px;
  background: #fff;
  border-bottom: 1px solid #e5e7eb;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 300;
  flex-shrink: 0;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
}

.topnav-left {
  display: flex;
  align-items: center;
  gap: 32px;
}

.topnav-brand {
  display: flex;
  align-items: center;
  gap: 8px;
  text-decoration: none;
}

.brand-text {
  font-size: 16px;
  font-weight: 700;
  color: #1f2937;
  letter-spacing: -0.01em;
}

.topnav-modules {
  display: flex;
  gap: 4px;
}

.module-link {
  padding: 6px 14px;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 500;
  color: #6b7280;
  text-decoration: none;
  transition: all 0.15s ease;
}

.module-link:hover {
  color: #1f2937;
  background: #f3f4f6;
}

.module-link.active {
  color: #3B82F6;
  background: rgba(59, 130, 246, 0.08);
}

.topnav-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.system-status {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 12px;
  border-radius: 6px;
  background: #f0fdf4;
  font-size: 12px;
}

.status-dot--online {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #10B981;
}

.status-label {
  color: #059669;
  font-weight: 500;
}

.topnav-icon-btn {
  width: 32px;
  height: 32px;
  border-radius: 6px;
  border: none;
  background: none;
  color: #6b7280;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.15s ease;
}

.topnav-icon-btn:hover {
  color: #1f2937;
  background: #f3f4f6;
}

.topnav-user {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 8px;
  border-radius: 6px;
  border: none;
  background: none;
  cursor: pointer;
  transition: background 0.15s ease;
}

.topnav-user:hover { background: #f3f4f6; }

.user-avatar { background: linear-gradient(135deg, #3B82F6, #8B5CF6); }

.user-name { font-size: 13px; color: #6b7280; }

.dropdown-caret { font-size: 10px; color: #9ca3af; }

@media (max-width: 768px) {
  .topnav-modules { display: none; }
  .brand-text { display: none; }
  .system-status .status-label { display: none; }
  .user-name { display: none; }
}
</style>
