/**
 * 工具函数库
 * 提供常用的辅助函数
 */

/**
 * 日期时间格式化
 */
export function formatDate(date, format = 'YYYY-MM-DD HH:mm:ss') {
  if (!date) return '-'

  const d = new Date(date)
  if (isNaN(d.getTime())) return '-'

  const year = d.getFullYear()
  const month = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  const hours = String(d.getHours()).padStart(2, '0')
  const minutes = String(d.getMinutes()).padStart(2, '0')
  const seconds = String(d.getSeconds()).padStart(2, '0')

  return format
    .replace('YYYY', year)
    .replace('MM', month)
    .replace('DD', day)
    .replace('HH', hours)
    .replace('mm', minutes)
    .replace('ss', seconds)
}

/**
 * 相对时间格式化
 */
export function formatRelativeTime(date) {
  if (!date) return '-'

  const d = new Date(date)
  const now = new Date()
  const diff = Math.floor((now - d) / 1000)

  if (diff < 60) return `${diff}秒前`
  if (diff < 3600) return `${Math.floor(diff / 60)}分钟前`
  if (diff < 86400) return `${Math.floor(diff / 3600)}小时前`
  if (diff < 2592000) return `${Math.floor(diff / 86400)}天前`
  return formatDate(date, 'YYYY-MM-DD')
}

/**
 * 文件大小格式化
 */
export function formatFileSize(bytes) {
  if (bytes === 0) return '0 B'

  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))

  return (bytes / Math.pow(k, i)).toFixed(2) + ' ' + sizes[i]
}

/**
 * 数字格式化
 */
export function formatNumber(num, decimals = 2) {
  if (num === null || num === undefined) return '-'
  return Number(num).toFixed(decimals)
}

/**
 * 网络速率格式化 (bytes/second)
 * @param {number} bytesPerSecond - 每秒字节数
 * @returns {string} 格式化后的速率，如 "1.23 MB/s"
 */
export function formatRate(bytesPerSecond) {
  if (bytesPerSecond === 0 || bytesPerSecond == null || bytesPerSecond === undefined) {
    return '0 B/s'
  }

  const k = 1024
  const sizes = ['B/s', 'KB/s', 'MB/s', 'GB/s', 'TB/s']
  const i = Math.floor(Math.log(bytesPerSecond) / Math.log(k))

  return (bytesPerSecond / Math.pow(k, i)).toFixed(2) + ' ' + sizes[i]
}

/**
 * 百分比格式化
 */
export function formatPercent(value, decimals = 1) {
  if (value === null || value === undefined) return '-'
  return `${Number(value).toFixed(decimals)}%`
}

/**
 * 颜色映射
 */
export const statusColors = {
  online: 'success',
  offline: 'info',
  error: 'danger',
  critical: 'danger',
  major: 'warning',
  minor: 'primary',
  info: 'info'
}

export const typeColors = {
  SYSTEM: 'primary',
  FILE: 'danger',
  DATABASE: 'success',
  CUSTOM: 'info'
}

/**
 * 获取状态颜色
 */
export function getStatusType(status) {
  return statusColors[status] || 'info'
}

/**
 * 获取类型颜色
 */
export function getTypeColor(type) {
  return typeColors[type] || 'info'
}

/**
 * 获取状态文本
 */
export const statusText = {
  online: '在线',
  offline: '离线',
  error: '异常',
  ONLINE: '在线',
  OFFLINE: '离线',
  ERROR: '异常'
}

export function getStatusText(status) {
  return statusText[status] || status
}

/**
 * 获取类型文本
 */
export const typeText = {
  SYSTEM: '系统监控',
  FILE: '文件监控',
  DATABASE: '数据库',
  CUSTOM: '自定义'
}

export function getTypeLabel(type) {
  return typeText[type] || type
}

/**
 * 获取告警级别文本
 */
export const severityText = {
  critical: '紧急',
  major: '重要',
  minor: '一般',
  info: '提示'
}

export function getSeverityLabel(severity) {
  return severityText[severity] || severity
}

/**
 * 获取告警级别颜色
 */
export const severityColors = {
  critical: 'danger',
  major: 'warning',
  minor: 'primary',
  info: 'success'
}

export function getSeverityType(severity) {
  return severityColors[severity] || 'info'
}

/**
 * 告警状态映射
 */
export const alertStatusText = {
  open: '未解决',
  acknowledged: '处理中',
  resolved: '已解决'
}

export function getAlertStatusLabel(status) {
  return alertStatusText[status] || status
}

export const alertStatusColors = {
  open: 'danger',
  acknowledged: 'warning',
  resolved: 'success'
}

export function getAlertStatusType(status) {
  return alertStatusColors[status] || 'info'
}

/**
 * 进度条状态
 */
export function getProgressStatus(percentage) {
  if (percentage >= 90) return 'exception'
  if (percentage >= 70) return 'warning'
  return 'success'
}

/**
 * 下载文件
 */
export function downloadFile(blob, filename) {
  const url = window.URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  window.URL.revokeObjectURL(url)
}

/**
 * 导出数据为CSV
 */
export function exportToCSV(data, filename = 'export.csv') {
  if (!data || data.length === 0) {
    console.warn('没有数据可导出')
    return
  }

  const headers = Object.keys(data[0])
  const csvContent = [
    headers.join(','),
    ...data.map(row => headers.map(header => `"${row[header] || ''}"`).join(','))
  ].join('\n')

  const blob = new Blob(['\ufeff' + csvContent], { type: 'text/csv;charset=utf-8;' })
  downloadFile(blob, filename)
}

/**
 * 导出数据为JSON
 */
export function exportToJSON(data, filename = 'export.json') {
  if (!data) {
    console.warn('没有数据可导出')
    return
  }

  const json = JSON.stringify(data, null, 2)
  const blob = new Blob([json], { type: 'application/json' })
  downloadFile(blob, filename)
}

/**
 * 复制到剪贴板
 */
export async function copyToClipboard(text) {
  try {
    await navigator.clipboard.writeText(text)
    return true
  } catch (error) {
    // 降级方案
    const textarea = document.createElement('textarea')
    textarea.value = text
    textarea.style.position = 'fixed'
    textarea.style.opacity = '0'
    document.body.appendChild(textarea)
    textarea.select()
    try {
      document.execCommand('copy')
      return true
    } catch (err) {
      console.error('复制失败:', err)
      return false
    } finally {
      document.body.removeChild(textarea)
    }
  }
}

/**
 * 生成随机ID
 */
export function generateId() {
  return Date.now().toString(36) + Math.random().toString(36).substr(2)
}

/**
 * 深度克隆对象
 */
export function deepClone(obj) {
  if (obj === null || typeof obj !== 'object') return obj
  if (obj instanceof Date) return new Date(obj.getTime())
  if (obj instanceof Array) return obj.map(item => deepClone(item))

  const cloned = {}
  for (const key in obj) {
    if (obj.hasOwnProperty(key)) {
      cloned[key] = deepClone(obj[key])
    }
  }
  return cloned
}

/**
 * 防抖函数
 */
export function debounce(func, wait) {
  let timeout
  return function executedFunction(...args) {
    const later = () => {
      clearTimeout(timeout)
      func(...args)
    }
    clearTimeout(timeout)
    timeout = setTimeout(later, wait)
  }
}

/**
 * 节流函数
 */
export function throttle(func, limit) {
  let inThrottle
  return function(...args) {
    if (!inThrottle) {
      func.apply(this, args)
      inThrottle = true
      setTimeout(() => inThrottle = false, limit)
    }
  }
}

/**
 * 获取查询参数
 */
export function getQueryParams() {
  const params = new URLSearchParams(window.location.search)
  return Object.fromEntries(params.entries())
}

/**
 * 设置查询参数
 */
export function setQueryParams(params) {
  const url = new URL(window.location.href)
  Object.entries(params).forEach(([key, value]) => {
    url.searchParams.set(key, value)
  })
  window.history.replaceState({}, '', url.toString())
}

/**
 * 定时器管理
 */
export class Timer {
  constructor(callback, interval) {
    this.callback = callback
    this.interval = interval
    this.timerId = null
  }

  start() {
    if (this.timerId) return
    this.timerId = setInterval(this.callback, this.interval)
  }

  stop() {
    if (this.timerId) {
      clearInterval(this.timerId)
      this.timerId = null
    }
  }

  restart() {
    this.stop()
    this.start()
  }
}

/**
 * 消息提示工具
 */
export const Message = {
  success: (message, duration = 3000) => {
    // 使用 Element Plus 的 ElMessage
  },

  error: (message, duration = 3000) => {
    console.error('[Error]', message)
  },

  warning: (message, duration = 3000) => {
    console.warn('[Warning]', message)
  },

  info: (message, duration = 3000) => {
    console.info('[Info]', message)
  }
}

/**
 * 本地存储工具
 */
export const storage = {
  get(key, defaultValue = null) {
    try {
      const item = localStorage.getItem(key)
      return item ? JSON.parse(item) : defaultValue
    } catch {
      return defaultValue
    }
  },

  set(key, value) {
    try {
      localStorage.setItem(key, JSON.stringify(value))
    } catch (error) {
      console.error('保存到本地存储失败:', error)
    }
  },

  remove(key) {
    localStorage.removeItem(key)
  },

  clear() {
    localStorage.clear()
  }
}

/**
 * sessionStorage工具
 */
export const session = {
  get(key, defaultValue = null) {
    try {
      const item = sessionStorage.getItem(key)
      return item ? JSON.parse(item) : defaultValue
    } catch {
      return defaultValue
    }
  },

  set(key, value) {
    try {
      sessionStorage.setItem(key, JSON.stringify(value))
    } catch (error) {
      console.error('保存到sessionStorage失败:', error)
    }
  },

  remove(key) {
    sessionStorage.removeItem(key)
  },

  clear() {
    sessionStorage.clear()
  }
}

/**
 * 验证工具
 */
export const validator = {
  // 邮箱验证
  email: (email) => {
    const re = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
    return re.test(email)
  },

  // 手机号验证
  phone: (phone) => {
    const re = /^1[3-9]\d{9}$/
    return re.test(phone)
  },

  // IP地址验证
  ip: (ip) => {
    const re = /^(?:\d{1,3}\.){3}\d{1,3}$/
    return re.test(ip)
  },

  // URL验证
  url: (url) => {
    try {
      new URL(url)
      return true
    } catch {
      return false
    }
  },

  // 密码强度验证
  passwordStrength: (password) => {
    let strength = 0
    if (password.length >= 6) strength++
    if (password.length >= 10) strength++
    if (/[A-Z]/.test(password) && /[a-z]/.test(password)) strength++
    if (/\d/.test(password)) strength++
    if (/[^A-Za-z0-9]/.test(password)) strength++

    if (strength <= 2) return 'weak'
    if (strength <= 3) return 'medium'
    return 'strong'
  }
}

/**
 * 数组工具
 */
export const array = {
  // 数组去重
  unique: (arr, key) => {
    if (!key) {
      return [...new Set(arr)]
    }
    const seen = new Set()
    return arr.filter(item => {
      const k = item[key]
      if (!seen.has(k)) {
        seen.add(k)
        return true
      }
      return false
    })
  },

  // 数组分组
  groupBy: (arr, key) => {
    return arr.reduce((result, item) => {
      const group = item[key]
      if (!result[group]) {
        result[group] = []
      }
      result[group].push(item)
      return result
    }, {})
  },

  // 数组排序
  sortBy: (arr, key, order = 'asc') => {
    return [...arr].sort((a, b) => {
      const aVal = a[key]
      const bVal = b[key]
      if (order === 'asc') {
        return aVal > bVal ? 1 : aVal < bVal ? -1 : 0
      } else {
        return aVal < bVal ? 1 : aVal > bVal ? -1 : 0
      }
    })
  }
}

/**
 * 对象工具
 */
export const object = {
  // 获取对象的值数组
  values: (obj) => Object.keys(obj).map(key => obj[key]),

  // 获取对象的键数组
  keys: (obj) => Object.keys(obj),

  // 获取对象的键值对数组
  entries: (obj) => Object.entries(obj),

  // 合并对象
  merge: (...objs) => Object.assign({}, ...objs),

  // 对象映射
  map: (obj, fn) => {
    return Object.keys(obj).reduce((result, key) => {
      result[key] = fn(obj[key], key, obj)
      return result
    }, {})
  },

  // 过滤对象
  filter: (obj, fn) => {
    return Object.keys(obj).reduce((result, key) => {
      if (fn(obj[key], key, obj)) {
        result[key] = obj[key]
      }
      return result
    }, {})
  }
}

// 默认导出所有工具
export default {
  formatDate,
  formatRelativeTime,
  formatFileSize,
  formatNumber,
  formatPercent,
  getStatusType,
  getTypeColor,
  getStatusText,
  getTypeLabel,
  getSeverityLabel,
  getSeverityType,
  getAlertStatusLabel,
  getAlertStatusType,
  getProgressStatus,
  downloadFile,
  exportToCSV,
  exportToJSON,
  copyToClipboard,
  generateId,
  deepClone,
  debounce,
  throttle,
  getQueryParams,
  setQueryParams,
  Timer,
  Message,
  storage,
  session,
  validator,
  array,
  object
}
