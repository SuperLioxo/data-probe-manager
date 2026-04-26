import request from './request'

export function getDataSourceAlertConfigs(params) {
  return request({ url: '/datasource-alerts/configs', method: 'get', params })
}

export function createDataSourceAlertConfig(data) {
  return request({ url: '/datasource-alerts/configs', method: 'post', data })
}

export function updateDataSourceAlertConfig(id, data) {
  return request({ url: `/datasource-alerts/configs/${id}`, method: 'put', data })
}

export function deleteDataSourceAlertConfig(id) {
  return request({ url: `/datasource-alerts/configs/${id}`, method: 'delete' })
}

export function getDataSourceAlertRecords(params) {
  return request({ url: '/datasource-alerts/records', method: 'get', params })
}

export function getDataSourceAlertStatistics() {
  return request({ url: '/datasource-alerts/statistics', method: 'get' })
}
