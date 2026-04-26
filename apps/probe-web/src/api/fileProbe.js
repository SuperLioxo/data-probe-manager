import request from './request'
import axios from 'axios'

/**
 * 文件探针管理API
 * 使用 /api/file-probes 端点（file_probe 表）
 */

// 分页查询文件探针
export function getPage(params) {
  return request({
    url: '/file-probes',
    method: 'get',
    params
  })
}

// 获取探针详情
export function getById(id) {
  return request({
    url: `/file-probes/${id}`,
    method: 'get'
  })
}

// 获取探针详情（别名）
export function getDetail(id) {
  return getById(id)
}

// 根据probeKey获取探针
export function getByProbeKey(probeKey) {
  return request({
    url: `/file-probes`,
    method: 'get',
    params: { probeKey }
  })
}

// 创建文件探针
export function create(data) {
  return request({
    url: '/file-probes',
    method: 'post',
    data: { ...data, type: 'FILE' }
  })
}

// 更新文件探针
export function update(id, data) {
  return request({
    url: `/file-probes/${id}`,
    method: 'put',
    data
  })
}

// 删除文件探针
export function deleteProbe(id) {
  return request({
    url: `/file-probes/${id}`,
    method: 'delete'
  })
}

// 触发扫描
export function triggerScan(id) {
  return request({
    url: `/file-probes/${id}/scan`,
    method: 'post'
  })
}

// 获取文件列表
export function getFiles(id, params) {
  return request({
    url: `/file-probes/${id}/files`,
    method: 'get',
    params
  })
}

// 下载文件
export function downloadFile(fileId) {
  const token = localStorage.getItem('token')
  return axios({
    baseURL: '/api',
    url: `/file-metadata/${fileId}/download`,
    method: 'get',
    responseType: 'blob',
    headers: token ? { Authorization: `Bearer ${token}` } : {}
  }).then(response => response.data)
}

// 删除文件
export function deleteFile(fileId) {
  return request({
    url: `/file-metadata/${fileId}`,
    method: 'delete'
  })
}

// 上传文件到探针扫描目录
export function uploadToProbe(id, formData) {
  return request({
    url: `/probes/${id}/upload`,
    method: 'post',
    data: formData,
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 60000
  })
}
