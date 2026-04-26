/**
 * 设置工具函数库
 * 提供设置的保存、加载、验证、导入导出功能
 */

const STORAGE_KEY = 'app_settings'
const SETTINGS_VERSION = '1.0.0'

/**
 * 默认设置配置
 */
export const DEFAULT_SETTINGS = {
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
    shadow: 'none'
  },
  notification: {
    desktop: true,
    alert: true,
    system: true,
    sound: true,
    alertSound: 'default',
    volume: 70,
    email: false,
    emailAddress: '',
    emailFrequency: ['immediate']
  },
  security: {
    sessionTimeout: 120,
    singleSignOn: false,
    logOperations: true,
    logRetention: 30,
    ipWhitelist: false,
    whitelistIPs: []
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
}

/**
 * 安全的localStorage操作
 */
export const safeStorage = {
  /**
   * 从localStorage读取数据
   * @param {string} key - 存储键
   * @param {*} defaultValue - 默认值
   * @returns {*} 存储的值或默认值
   */
  get(key, defaultValue = null) {
    try {
      const item = localStorage.getItem(key)
      return item ? JSON.parse(item) : defaultValue
    } catch (error) {
      console.error('localStorage读取失败:', error)
      return defaultValue
    }
  },

  /**
   * 向localStorage写入数据
   * @param {string} key - 存储键
   * @param {*} value - 要存储的值
   * @returns {boolean} 是否成功
   */
  set(key, value) {
    try {
      localStorage.setItem(key, JSON.stringify(value))
      return true
    } catch (error) {
      console.error('localStorage写入失败:', error)
      // 检查是否是QuotaExceededError
      if (error.name === 'QuotaExceededError') {
        console.warn('localStorage已满，尝试清理旧数据')
        // 可以尝试清理一些旧数据
        try {
          // 清理旧版本的设置
          const oldKeys = Object.keys(localStorage).filter(k => k.startsWith('app_settings_'))
          oldKeys.forEach(k => localStorage.removeItem(k))
          // 重试
          localStorage.setItem(key, JSON.stringify(value))
          return true
        } catch (retryError) {
          console.error('清理后仍然无法写入:', retryError)
        }
      }
      return false
    }
  },

  /**
   * 从localStorage删除数据
   * @param {string} key - 存储键
   * @returns {boolean} 是否成功
   */
  remove(key) {
    try {
      localStorage.removeItem(key)
      return true
    } catch (error) {
      console.error('localStorage删除失败:', error)
      return false
    }
  }
}

/**
 * 加载设置
 * @returns {Object} 设置对象
 */
export function loadSettings() {
  const saved = safeStorage.get(STORAGE_KEY)

  if (saved) {
    // 检查版本
    if (saved.version === SETTINGS_VERSION) {
      // 合并默认设置和保存的设置
      return mergeSettings(DEFAULT_SETTINGS, saved.settings)
    } else {
      console.warn(`设置版本不匹配: ${saved.version} (期望: ${SETTINGS_VERSION})`)
      // 版本迁移可以在这里实现
      return migrateSettings(saved, DEFAULT_SETTINGS)
    }
  }

  // 返回默认设置的深拷贝
  return JSON.parse(JSON.stringify(DEFAULT_SETTINGS))
}

/**
 * 保存设置
 * @param {Object} settings - 设置对象
 * @returns {boolean} 是否成功
 */
export function saveSettings(settings) {
  const data = {
    version: SETTINGS_VERSION,
    timestamp: Date.now(),
    settings: settings
  }
  return safeStorage.set(STORAGE_KEY, data)
}

/**
 * 验证设置
 * @param {Object} settings - 设置对象
 * @returns {Object} { valid: boolean, errors: string[] }
 */
export function validateSettings(settings) {
  const errors = []

  // 验证通用设置
  if (!settings.general) {
    errors.push('缺少通用设置配置')
  } else {
    if (settings.general.pageSize < 10 || settings.general.pageSize > 100) {
      errors.push('分页大小必须在10-100之间')
    }
    if (settings.general.refreshInterval < 0) {
      errors.push('刷新间隔不能为负数')
    }
  }

  // 验证通知设置
  if (!settings.notification) {
    errors.push('缺少通知设置配置')
  } else {
    if (settings.notification.volume < 0 || settings.notification.volume > 100) {
      errors.push('音量必须在0-100之间')
    }
    if (settings.notification.email && !settings.notification.emailAddress) {
      errors.push('启用邮件通知时必须填写邮箱地址')
    }
  }

  // 验证安全设置
  if (!settings.security) {
    errors.push('缺少安全设置配置')
  } else {
    if (settings.security.sessionTimeout < 5) {
      errors.push('会话超时时间不能少于5分钟')
    }
    if (settings.security.logRetention < 1 || settings.security.logRetention > 365) {
      errors.push('日志保留天数必须在1-365之间')
    }
  }

  // 验证系统设置
  if (!settings.system) {
    errors.push('缺少系统设置配置')
  } else {
    if (settings.system.cpuThreshold < 0 || settings.system.cpuThreshold > 100) {
      errors.push('CPU阈值必须在0-100之间')
    }
    if (settings.system.memoryThreshold < 0 || settings.system.memoryThreshold > 100) {
      errors.push('内存阈值必须在0-100之间')
    }
    if (settings.system.defaultInterval < 1 || settings.system.defaultInterval > 3600) {
      errors.push('监控间隔必须在1-3600秒之间')
    }
    if (settings.system.dataRetention < 1 || settings.system.dataRetention > 365) {
      errors.push('数据保留天数必须在1-365之间')
    }
  }

  return {
    valid: errors.length === 0,
    errors
  }
}

/**
 * 导出设置为JSON文件
 * @param {Object} settings - 设置对象
 * @param {string} filename - 文件名（可选）
 */
export function exportSettings(settings, filename = null) {
  const data = {
    version: SETTINGS_VERSION,
    exportTime: new Date().toISOString(),
    hostname: window.location.hostname,
    settings: settings
  }

  const blob = new Blob([JSON.stringify(data, null, 2)], {
    type: 'application/json;charset=utf-8'
  })

  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename || `probe-settings-${Date.now()}.json`
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
}

/**
 * 从JSON文件导入设置
 * @param {File} file - JSON文件对象
 * @returns {Promise<Object>} 解析后的设置对象
 */
export function importSettings(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()

    reader.onload = (e) => {
      try {
        const data = JSON.parse(e.target.result)

        // 验证文件格式
        if (!data.version || !data.settings) {
          throw new Error('文件格式不正确：缺少版本或设置数据')
        }

        // 版本兼容性检查
        if (data.version !== SETTINGS_VERSION) {
          console.warn(`导入的设置版本 ${data.version} 与当前版本 ${SETTINGS_VERSION} 不匹配`)
          // 这里可以实现版本迁移逻辑
        }

        // 验证导入的设置
        const validation = validateSettings(data.settings)
        if (!validation.valid) {
          throw new Error('设置验证失败：' + validation.errors.join('; '))
        }

        resolve(data)
      } catch (error) {
        reject(error)
      }
    }

    reader.onerror = () => {
      reject(new Error('文件读取失败'))
    }

    reader.readAsText(file)
  })
}

/**
 * 合并设置（用于版本升级）
 * @param {Object} defaults - 默认设置
 * @param {Object} saved - 保存的设置
 * @returns {Object} 合并后的设置
 */
function mergeSettings(defaults, saved) {
  const result = JSON.parse(JSON.stringify(defaults))

  Object.keys(saved).forEach(category => {
    if (result[category]) {
      // 合并每个分类的设置
      Object.assign(result[category], saved[category])
    } else {
      // 新增的分类
      result[category] = saved[category]
    }
  })

  return result
}

/**
 * 迁移旧版本设置到新版本
 * @param {Object} oldData - 旧版本设置数据
 * @param {Object} defaults - 默认设置
 * @returns {Object} 迁移后的设置
 */
function migrateSettings(oldData, defaults) {

  // 这里可以实现具体的版本迁移逻辑
  // 例如：从 0.9.0 迁移到 1.0.0

  // 目前简单合并
  const result = JSON.parse(JSON.stringify(defaults))
  if (oldData.settings) {
    return mergeSettings(result, oldData.settings)
  }

  return result
}

/**
 * 重置设置为默认值
 * @param {string} category - 要重置的分类（可选，不传则重置所有）
 * @returns {Object} 重置后的设置
 */
export function resetSettings(category = null) {
  if (category) {
    // 重置单个分类
    const current = loadSettings()
    current[category] = JSON.parse(JSON.stringify(DEFAULT_SETTINGS[category]))
    return current
  } else {
    // 重置所有设置
    return JSON.parse(JSON.stringify(DEFAULT_SETTINGS))
  }
}

/**
 * 检查localStorage是否可用
 * @returns {boolean} 是否可用
 */
export function isStorageAvailable() {
  try {
    const test = '__storage_test__'
    localStorage.setItem(test, test)
    localStorage.removeItem(test)
    return true
  } catch (e) {
    return false
  }
}

/**
 * 获取设置存储大小（近似值）
 * @returns {number} 存储大小（字节）
 */
export function getStorageSize() {
  try {
    const data = localStorage.getItem(STORAGE_KEY)
    return data ? new Blob([data]).size : 0
  } catch (e) {
    return 0
  }
}

/**
 * 清除所有设置数据
 * @returns {boolean} 是否成功
 */
export function clearSettings() {
  return safeStorage.remove(STORAGE_KEY)
}
