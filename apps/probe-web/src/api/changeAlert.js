import request from './request'

export function getChangeAlertConfigs(params) {
  return request({ url: '/change-alerts/configs', method: 'get', params })
}

export function createChangeAlertConfig(data) {
  return request({ url: '/change-alerts/configs', method: 'post', data })
}

export function updateChangeAlertConfig(id, data) {
  return request({ url: `/change-alerts/configs/${id}`, method: 'put', data })
}

export function deleteChangeAlertConfig(id) {
  return request({ url: `/change-alerts/configs/${id}`, method: 'delete' })
}

export function getChangeAlertRecords(params) {
  return request({ url: '/change-alerts/records', method: 'get', params })
}

export function getChangeAlertStatistics() {
  return request({ url: '/change-alerts/statistics', method: 'get' })
}
