<template>
  <aside class="sidebar" :class="{ collapsed: isCollapsed }">
    <button class="sidebar-toggle" @click="toggleCollapse" :aria-label="isCollapsed ? '展开' : '折叠'">
      <el-icon><Fold v-if="!isCollapsed" /><Expand v-else /></el-icon>
    </button>

    <nav class="sidebar-nav">
      <div v-for="group in menuGroups" :key="group.key" class="nav-group">
        <div v-if="!isCollapsed" class="nav-group-title">{{ group.label }}</div>
        <div v-else class="nav-group-divider"></div>
        <router-link
          v-for="item in group.items" :key="item.path"
          :to="item.path"
          class="nav-item"
          :class="{ active: isActive(item.path) }"
        >
          <el-icon :size="16"><component :is="item.icon" /></el-icon>
          <span v-if="!isCollapsed" class="nav-label">{{ item.label }}</span>
        </router-link>
      </div>
    </nav>
  </aside>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const isCollapsed = ref(false)

const toggleCollapse = () => { isCollapsed.value = !isCollapsed.value }

const isActive = (path) => {
  const current = route.path
  return current === path || (path !== '/' && current.startsWith(path))
}

const menuGroups = computed(() => [
  {
    key: 'dashboard',
    label: '概览',
    items: [
      { path: '/dashboard', icon: 'Odometer', label: '首页概览' }
    ]
  },
  {
    key: 'collection',
    label: '采集管理',
    items: [
      { path: '/collection/probes', icon: 'Coin', label: '数据源' },
      { path: '/collection/agents', icon: 'Cpu', label: 'Agent' },
      { path: '/collection/groups', icon: 'FolderOpened', label: '分组管理' }
    ]
  },
  {
    key: 'sync',
    label: '同步管理',
    items: [
      { path: '/sync/tasks', icon: 'Refresh', label: '同步任务' },
      { path: '/sync/dead-letter', icon: 'WarningFilled', label: '失败数据' },
      { path: '/sync/aggregation', icon: 'Coin', label: '数据汇聚' }
    ]
  },
  {
    key: 'quality',
    label: '质量与变更',
    items: [
      { path: '/quality/rules', icon: 'Filter', label: '质量规则' },
      { path: '/quality/changes', icon: 'DataLine', label: '变更检测' },
      { path: '/quality/alerts', icon: 'Bell', label: '告警记录' },
      { path: '/quality/datasource-alerts', icon: 'Warning', label: '数据源告警' },
    ]
  },
  {
    key: 'monitoring',
    label: '监控中心',
    items: [
      { path: '/monitoring/realtime', icon: 'Monitor', label: '实时监控' },
      { path: '/monitoring/statistics', icon: 'DataAnalysis', label: '数据统计' }
    ]
  },
  {
    key: 'system',
    label: '系统管理',
    items: [
      { path: '/system/agent-upgrade', icon: 'Upload', label: 'Agent升级' },
      { path: '/system/agent-logs', icon: 'Tickets', label: 'Agent日志' },
      { path: '/system/audit-logs', icon: 'Document', label: '审计日志' },
      { path: '/system/settings', icon: 'Setting', label: '系统设置' }
    ]
  }
])
</script>

<style scoped>
.sidebar {
  width: 240px;
  min-width: 240px;
  height: 100%;
  background: #fff;
  border-right: 1px solid #e5e7eb;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  transition: width 0.25s ease, min-width 0.25s ease;
  position: relative;
  flex-shrink: 0;
}

.sidebar.collapsed {
  width: 64px;
  min-width: 64px;
}

.sidebar-toggle {
  width: 100%;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  background: none;
  color: #9ca3af;
  cursor: pointer;
  transition: all 0.15s ease;
  flex-shrink: 0;
}

.sidebar-toggle:hover {
  color: #374151;
  background: #f3f4f6;
}

.sidebar-nav {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 0 8px 16px;
}

.nav-group {
  margin-bottom: 4px;
}

.nav-group-title {
  padding: 8px 12px 4px;
  font-size: 10px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.1em;
  color: #9ca3af;
  white-space: nowrap;
}

.nav-group-divider {
  height: 1px;
  background: #e5e7eb;
  margin: 8px 12px;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  border-radius: 6px;
  color: #6b7280;
  text-decoration: none;
  font-size: 13px;
  font-weight: 500;
  transition: all 0.15s ease;
  white-space: nowrap;
  position: relative;
}

.nav-item:hover {
  color: #1f2937;
  background: #f3f4f6;
}

.nav-item.active {
  color: #3B82F6;
  background: rgba(59, 130, 246, 0.06);
}

.nav-item.active::before {
  content: '';
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 3px;
  height: 18px;
  border-radius: 0 2px 2px 0;
  background: #3B82F6;
}

.collapsed .nav-item {
  justify-content: center;
  padding: 10px;
}

.nav-label { flex: 1; }
</style>
