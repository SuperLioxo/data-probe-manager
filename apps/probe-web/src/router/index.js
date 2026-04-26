import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
    meta: { title: '登录' }
  },
  { path: '/', redirect: '/dashboard' },

  // ===== Dashboard =====
  {
    path: '/dashboard',
    name: 'Dashboard',
    component: () => import('@/views/dashboard/DashboardView.vue'),
    meta: { title: '首页概览', module: 'dashboard' }
  },

  // ===== Collection =====
  {
    path: '/collection/probes',
    name: 'ProbeList',
    component: () => import('@/views/collection/ProbeList.vue'),
    meta: { title: '数据源管理', module: 'collection' }
  },
  {
    path: '/collection/agents',
    name: 'AgentManager',
    component: () => import('@/views/collection/AgentManager.vue'),
    meta: { title: 'Agent管理', module: 'collection' }
  },
  {
    path: '/collection/groups',
    name: 'ProbeGroupManage',
    component: () => import('@/views/collection/ProbeGroupManage.vue'),
    meta: { title: '数据源分组', module: 'collection' }
  },
{
    path: '/collection/database/:probeKey',
    name: 'DatabaseTablesView',
    component: () => import('@/views/collection/DatabaseTablesView.vue'),
    meta: { title: '数据库表', module: 'collection' }
  },
  {
    path: '/collection/database-probe/:probeKey',
    name: 'DatabaseProbeDetail',
    component: () => import('@/views/collection/DatabaseProbeDetail.vue'),
    meta: { title: '探针详情', module: 'collection' }
  },

  // ===== Sync =====
  {
    path: '/sync/tasks',
    name: 'SyncTask',
    component: () => import('@/views/sync/SyncTask.vue'),
    meta: { title: '同步任务', module: 'sync' }
  },
  {
    path: '/sync/dead-letter',
    name: 'DeadLetterTask',
    component: () => import('@/views/sync/DeadLetterTask.vue'),
    meta: { title: '失败数据', module: 'sync' }
  },
  {
    path: '/sync/aggregation',
    name: 'DataAggregation',
    component: () => import('@/views/sync/DataAggregation.vue'),
    meta: { title: '数据汇聚', module: 'sync' }
  },

  // ===== Quality =====
  {
    path: '/quality/rules',
    name: 'QualityRule',
    component: () => import('@/views/quality/QualityRule.vue'),
    meta: { title: '质量规则', module: 'quality' }
  },
  {
    path: '/quality/changes',
    name: 'ChangeDetection',
    component: () => import('@/views/quality/ChangeDetection.vue'),
    meta: { title: '变更检测', module: 'quality' }
  },
  {
    path: '/quality/alerts',
    name: 'ChangeAlert',
    component: () => import('@/views/quality/ChangeAlert.vue'),
    meta: { title: '告警记录', module: 'quality' }
  },
  {
    path: '/quality/datasource-alerts',
    name: 'DataSourceAlert',
    component: () => import('@/views/quality/DataSourceAlert.vue'),
    meta: { title: '数据源告警', module: 'quality' }
  },

  // ===== Monitoring =====
  {
    path: '/monitoring/realtime',
    name: 'MonitorDashboard',
    component: () => import('@/views/monitoring/MonitorDashboard.vue'),
    meta: { title: '实时监控', module: 'monitoring' }
  },
  {
    path: '/monitoring/statistics',
    name: 'DataStatistics',
    component: () => import('@/views/monitoring/DataStatistics.vue'),
    meta: { title: '数据统计', module: 'monitoring' }
  },

  // ===== System =====
  {
    path: '/system/agent-upgrade',
    name: 'AgentUpgrade',
    component: () => import('@/views/system/AgentUpgrade.vue'),
    meta: { title: 'Agent升级', module: 'system' }
  },
  {
    path: '/system/agent-logs',
    name: 'AgentLog',
    component: () => import('@/views/system/AgentLog.vue'),
    meta: { title: 'Agent日志', module: 'system' }
  },
  {
    path: '/system/audit-logs',
    name: 'AuditLog',
    component: () => import('@/views/system/AuditLog.vue'),
    meta: { title: '审计日志', module: 'system' }
  },
  {
    path: '/system/settings',
    name: 'Settings',
    component: () => import('@/views/system/Settings.vue'),
    meta: { title: '系统设置', module: 'system' }
  },
  {
    path: '/system/data-manager',
    name: 'DataManager',
    component: () => import('@/views/system/DataManager.vue'),
    meta: { title: '数据管理', module: 'system' }
  },
  {
    path: '/system/file-upload',
    name: 'FileUpload',
    component: () => import('@/views/system/FileUpload.vue'),
    meta: { title: '文件上传', module: 'system' }
  },
  {
    path: '/system/datasource',
    name: 'DataSourceManage',
    component: () => import('@/views/system/DataSourceManage.vue'),
    meta: { title: '数据源管理', module: 'system' }
  },

  // ===== Backward-compatible redirects =====
  { path: '/probes', redirect: '/collection/probes' },
  { path: '/agents', redirect: '/collection/agents' },
  { path: '/probe-groups', redirect: '/collection/groups' },
  { path: '/database/:probeKey', redirect: to => `/collection/database/${to.params.probeKey}` },
  { path: '/database-probe/:probeKey', redirect: to => `/collection/database-probe/${to.params.probeKey}` },
  { path: '/sync-tasks', redirect: '/sync/tasks' },
  { path: '/dead-letter-tasks', redirect: '/sync/dead-letter' },
  { path: '/quality-rules', redirect: '/quality/rules' },
  { path: '/change-detection', redirect: '/quality/changes' },
  { path: '/change-alerts', redirect: '/quality/alerts' },
  { path: '/monitor', redirect: '/monitoring/realtime' },
  { path: '/statistics', redirect: '/monitoring/statistics' },
  { path: '/dashboard-enhanced', redirect: '/dashboard' },
  { path: '/agent-upgrade', redirect: '/system/agent-upgrade' },
  { path: '/agent-logs', redirect: '/system/agent-logs' },
  { path: '/audit-logs', redirect: '/system/audit-logs' },
  { path: '/settings', redirect: '/system/settings' },
  { path: '/data-manager', redirect: '/system/data-manager' },
  { path: '/file-upload', redirect: '/system/file-upload' },
  { path: '/datasource', redirect: '/system/datasource' },
  { path: '/probe-control', redirect: '/collection/probes' },

  { path: '/404', name: 'NotFound', component: () => import('@/views/NotFound.vue') },
  { path: '/:pathMatch(.*)*', redirect: '/404' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  const isLogin = localStorage.getItem('isLogin')
  const token = localStorage.getItem('token')
  const isLoggedIn = isLogin === 'true' && token

  if (to.path !== '/login' && !isLoggedIn) {
    next('/login')
  } else if (to.path === '/login' && isLoggedIn) {
    next('/dashboard')
  } else {
    next()
  }
})

router.onError((error) => {
  console.error('[Router Error]', error)
})

export default router
