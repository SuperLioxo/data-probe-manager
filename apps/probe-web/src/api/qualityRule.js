import request from './request'

export function getQualityRules(params) {
  return request({ url: '/quality-rules', method: 'get', params })
}

export function getQualityRule(id) {
  return request({ url: `/quality-rules/${id}`, method: 'get' })
}

export function createQualityRule(data) {
  return request({ url: '/quality-rules', method: 'post', data })
}

export function updateQualityRule(id, data) {
  return request({ url: `/quality-rules/${id}`, method: 'put', data })
}

export function deleteQualityRule(id) {
  return request({ url: `/quality-rules/${id}`, method: 'delete' })
}

export function checkQualityRule(id) {
  return request({ url: `/quality-rules/${id}/check`, method: 'post' })
}

export function getQualityStatistics(probeKey) {
  return request({ url: '/quality-rules/statistics', method: 'get', params: { probeKey } })
}

export function getQualityReports(params) {
  return request({ url: '/quality-rules/reports', method: 'get', params })
}
