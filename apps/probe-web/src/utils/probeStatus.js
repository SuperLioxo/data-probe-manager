/**
 * 探针状态工具类
 * 统一管理探针状态检查逻辑
 */

/**
 * 检查探针是否在线
 * @param {Object} probe - 探针对象
 * @returns {boolean}
 */
export function isProbeOnline(probe) {
  if (!probe || !probe.status) return false
  return probe.status.toLowerCase() === 'online'
}

/**
 * 检查探针是否离线
 * @param {Object} probe - 探针对象
 * @returns {boolean}
 */
export function isProbeOffline(probe) {
  return !isProbeOnline(probe)
}

/**
 * 验证探针在线状态，如果离线则显示错误提示
 * @param {Object} probe - 探针对象
 * @param {string} operation - 操作名称
 * @returns {boolean} - 返回true表示在线，false表示离线
 */
export function validateProbeOnline(probe, operation = '操作') {
  if (isProbeOffline(probe)) {
    ElMessage.error(`探针已离线，无法${operation}`)
    return false
  }
  return true
}

/**
 * 获取探针状态文本
 * @param {string} status - 状态值
 * @returns {string}
 */
export function getStatusText(status) {
  const statusMap = {
    'online': '在线',
    'offline': '离线',
    'error': '异常',
    'maintenance': '维护中',
    'disabled': '已禁用'
  }
  return statusMap[status?.toLowerCase()] || '未知'
}

/**
 * 获取探针状态类型
 * @param {string} status - 状态值
 * @returns {string} - Element Plus的标签类型
 */
export function getStatusType(status) {
  const typeMap = {
    'online': 'success',
    'offline': 'info',
    'error': 'danger',
    'maintenance': 'warning',
    'disabled': 'info'
  }
  return typeMap[status?.toLowerCase()] || 'info'
}
