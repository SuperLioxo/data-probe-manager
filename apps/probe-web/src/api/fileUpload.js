import request from './request'

export function uploadFile(formData) {
  return request({ url: '/files/upload', method: 'post', data: formData, headers: { 'Content-Type': 'multipart/form-data' }, timeout: 120000 })
}

export function uploadFiles(formData) {
  return request({ url: '/files/upload/batch', method: 'post', data: formData, headers: { 'Content-Type': 'multipart/form-data' }, timeout: 120000 })
}

export function getFileList(params) {
  return request({ url: '/files', method: 'get', params })
}

export function deleteFile(id) {
  return request({ url: `/files/${id}`, method: 'delete' })
}

export function getFileStatistics(probeKey) {
  return request({ url: '/files/statistics', method: 'get', params: { probeKey } })
}
