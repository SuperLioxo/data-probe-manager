/**
 * 全局状态管理 Store
 *
 * 使用 Vue 3 Composition API + reactive 实现轻量级状态管理（未使用 Vuex/Pinia）。
 * 所有组件通过 useStore() 获取同一个 reactive 对象，实现跨组件状态共享。
 *
 * 状态结构：
 * - state.user       当前登录用户信息（id、角色、权限列表）
 * - state.settings   系统设置（外观、通知、安全、系统参数），持久化到 localStorage
 * - state.statistics 首页仪表盘统计数据（探针在线/离线数量）
 * - state.notificationQueue 通知队列
 *
 * 持久化策略：
 * 用户信息和设置通过 saveSettings() 写入 localStorage，页面刷新后通过 loadSettings() 恢复。
 * 角色（roles）和权限（permissions）额外存储为独立的 localStorage 键，供路由守卫使用。
 */

import { reactive, computed } from 'vue'
import { probeApi } from '@/api/probe'

// 全局状态
const state = reactive({
  // 用户信息
  user: {
    id: null,
    username: '',
    realName: '',
    email: '',
    avatar: '',
    department: 'dev',
    position: '',
    roles: JSON.parse(localStorage.getItem('roles') || '[]'),
    permissions: JSON.parse(localStorage.getItem('permissions') || '[]')
  },

  // 系统设置
  settings: {
    general: {
      language: 'zh-CN',
      timezone: 'GMT+8',
      dateFormat: 'YYYY-MM-DD',
      timeFormat: '24h',
      pageSize: 20,
      refreshInterval: 30
    },
    appearance: {
      theme: 'light',
      primaryColor: '#409eff',
      sidebarWidth: 'medium',
      animation: true,
      compact: false,
      shadow: 'light'
    },
    notification: {
      desktop: true,
      alert: true,
      system: true,
      sound: true,
      alertSound: 'default',
      volume: 70,
      email: false,
      emailAddress: 'admin@example.com',
      emailFrequency: ['immediate']
    },
    security: {
      sessionTimeout: 120,
      singleSignOn: false,
      logOperations: true,
      logRetention: 30,
      ipWhitelist: false,
      whitelistIPs: ['192.168.1.0/24', '10.0.0.0/8']
    },
    system: {
      defaultInterval: 60,
      dataRetention: 30,
      cpuThreshold: 80,
      memoryThreshold: 85,
      alertSilence: 0,
      enableCache: true,
      cacheTime: 5,
      maxConnections: 100
    }
  },

  // 实时统计数据
  statistics: {
    totalProbes: 0,
    onlineProbes: 0,
    offlineProbes: 0,
    errorProbes: 0,
    systemHealth: true
  },

  // 未读消息
  unreadMessages: 0,

  // 通知队列
  notificationQueue: [],

  // 加载状态
  loading: {
    probes: false,
    alerts: false,
    statistics: false
  }
})

// getters
const getters = {
  // 是否登录
  isLoggedIn: computed(() => !!state.user.id),

  // 用户角色
  isAdmin: computed(() => state.user.roles.includes('ROLE_ADMIN')),

  // 主题模式
  isDarkTheme: computed(() => state.settings.appearance.theme === 'dark'),

  // 当前语言
  currentLanguage: computed(() => state.settings.general.language),

  // 刷新间隔(毫秒)
  refreshInterval: computed(() => state.settings.general.refreshInterval * 1000),

  // 系统健康状态
  systemHealthy: computed(() => {
    const { totalProbes, onlineProbes } = state.statistics
    if (totalProbes === 0) return true
    const offlineRatio = (totalProbes - onlineProbes) / totalProbes
    return offlineRatio < 0.5
  })
}

// actions
const actions = {
  // 设置用户信息
  setUser(user) {
    Object.assign(state.user, user)
    this.saveSettings()
  },

  // 更新设置
  updateSettings(category, settings) {
    if (category && settings) {
      Object.assign(state.settings[category], settings)
    }
    this.saveSettings()
  },

  // 保存设置到本地存储
  saveSettings() {
    try {
      localStorage.setItem('app_settings', JSON.stringify({
        user: state.user,
        settings: state.settings
      }))
    } catch (error) {
      console.error('保存设置失败:', error)
    }
  },

  // 加载设置
  loadSettings() {
    try {
      const saved = localStorage.getItem('app_settings')
      if (saved) {
        const data = JSON.parse(saved)
        Object.assign(state.user, data.user || {})
        Object.assign(state.settings, data.settings || {})
      }
    } catch (error) {
      console.error('加载设置失败:', error)
    }
  },

  // 更新统计数据
  updateStatistics(data) {
    Object.assign(state.statistics, data)
  },

  // 刷新统计数据
  async refreshStatistics() {
    try {
      state.loading.statistics = true

      const probesRes = await probeApi.getList({ pageNum: 1, pageSize: 100 })

      if (probesRes.code === 200) {
        const probes = probesRes.data.records || []
        state.statistics.totalProbes = probes.length
        state.statistics.onlineProbes = probes.filter(p => p.status === 'online').length
        state.statistics.offlineProbes = probes.filter(p => p.status === 'offline').length
      }

      state.statistics.systemHealth = getters.systemHealthy.value
    } catch (error) {
      console.error('刷新统计失败:', error)
    } finally {
      state.loading.statistics = false
    }
  },

  // 添加通知
  addNotification(notification) {
    state.notificationQueue.push({
      id: Date.now(),
      timestamp: new Date().toISOString(),
      read: false,
      ...notification
    })
    state.unreadMessages++
  },

  // 标记通知已读
  markNotificationRead(id) {
    const notification = state.notificationQueue.find(n => n.id === id)
    if (notification && !notification.read) {
      notification.read = true
      state.unreadMessages--
    }
  },

  // 清空通知
  clearNotifications() {
    state.notificationQueue = []
    state.unreadMessages = 0
  },

  // 登出
  logout() {
    // 清空用户信息
    Object.assign(state.user, {
      id: null,
      username: '',
      roles: [],
      permissions: []
    })

    // 清空本地存储
    localStorage.removeItem('app_settings')
    localStorage.removeItem('token')

    // 重定向到登录页
    window.location.href = '/login'
  }
}

// 初始化
actions.loadSettings()

// 导出store
export function useStore() {
  return {
    state,
    getters,
    ...actions
  }
}

// 默认导出
export default {
  state,
  getters,
  ...actions
}
