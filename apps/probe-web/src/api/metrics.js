import request from './request'

// 查询探针指标数据
export function getProbeMetrics(probeId, params) {
  return request({
    url: `/metrics/probe/${probeId}`,
    method: 'get',
    params,
    timeout: 30000, // 增加超时时间到30秒
    showError: false // 不显示错误消息，由调用方处理
  })
}

// 获取探针最新指标数据
export function getLatestMetrics(probeId) {
  return request({
    url: `/metrics/probe/${probeId}/latest`,
    method: 'get',
    timeout: 30000, // 增加超时时间到30秒
    showError: false // 不显示错误消息，由调用方处理
  })
}

// 获取探针指标摘要（专用接口）
// 返回前端显示所需的核心指标：CPU使用率、内存使用率等
export function getProbeMetricsSummary(probeId) {
  return request({
    url: `/metrics/probe/${probeId}/summary`,
    method: 'get',
    timeout: 30000, // 增加超时时间到30秒
    showError: false // 不显示错误消息，由调用方处理
  })
}

// 上报监控数据
export function reportMetric(data) {
  return request({
    url: '/metrics',
    method: 'post',
    data
  })
}

// 批量上报监控数据
export function batchReportMetrics(data) {
  return request({
    url: '/metrics/batch',
    method: 'post',
    data
  })
}

// 导出默认对象
export default {
  getProbeMetrics,
  getLatestMetrics,
  getProbeMetricsSummary,
  reportMetric,
  batchReportMetrics
}
