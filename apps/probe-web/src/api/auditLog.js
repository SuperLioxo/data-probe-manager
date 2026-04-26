import request from './request'

/**
 * 获取审计日志列表
 */
export function getAuditLogs(params) {
  return request({ url: '/audit-logs', method: 'get', params })
}

/**
 * 高级搜索审计日志
 */
export function searchAuditLogs(params) {
  return request({ url: '/audit-logs/search', method: 'get', params })
}

/**
 * 获取审计日志详情
 */
export function getAuditLog(id) {
  return request({ url: `/audit-logs/${id}`, method: 'get' })
}

/**
 * 获取审计日志统计信息
 */
export function getAuditLogStatistics(params) {
  return request({ url: '/audit-logs/statistics', method: 'get', params })
}

/**
 * 获取用户操作日志
 */
export function getUserAuditLogs(userId, params) {
  return request({ url: `/audit-logs/user/${userId}`, method: 'get', params })
}

/**
 * 获取业务实体操作日志
 */
export function getBusinessAuditLogs(businessType, businessId) {
  return request({ url: `/audit-logs/business/${businessType}/${businessId}`, method: 'get' })
}

/**
 * 导出审计日志
 */
export function exportAuditLogs(params) {
  return request({
    url: '/audit-logs/export',
    method: 'get',
    params,
    responseType: 'blob'
  })
}
