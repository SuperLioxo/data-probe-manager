import request from './request'

// 分页查询变化日志
export function getChangeLogs(params) {
  return request({ url: '/change-detection/logs', method: 'get', params })
}

// 获取变化统计
export function getChangeStatistics(probeKey) {
  return request({ url: '/change-detection/statistics', method: 'get', params: { probeKey } })
}

// 获取最近的变化记录
export function getRecentChanges(probeKey, limit) {
  return request({ url: '/change-detection/recent', method: 'get', params: { probeKey, limit: limit || 50 } })
}

// 获取表的快照历史
export function getSnapshots(probeKey, tableName, limit) {
  return request({ url: '/change-detection/snapshots', method: 'get', params: { probeKey, tableName, limit: limit || 10 } })
}

// 手动触发变化检测
export function triggerDetection(probeKey) {
  return request({ url: `/change-detection/detect/${probeKey}`, method: 'post' })
}
