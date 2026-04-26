import request from './request.js'

/**
 * 数据库探针API
 * Phase 4: CRUD 走统一 /api/probes，元数据查询走 /api/database-metadata
 */
export const databaseProbeApi = {
  create: (data) => {
    return request({
      url: '/probes',
      method: 'post',
      data: { ...data, type: 'DATABASE' }
    })
  },

  update: (id, data) => {
    return request({
      url: `/probes/${id}`,
      method: 'put',
      data
    })
  },

  delete: (id) => {
    return request({
      url: `/probes/${id}`,
      method: 'delete'
    })
  },

  getById: (id) => {
    return request({
      url: `/probes/${id}`,
      method: 'get'
    })
  },

  getByProbeKey: (probeKey) => {
    return request({
      url: `/database-metadata/${probeKey}/info`,
      method: 'get'
    })
  },

  getPage: (params) => {
    return request({
      url: '/probes',
      method: 'get',
      params: { ...params, type: 'DATABASE' }
    })
  },

  testConnection: (data) => {
    return request({
      url: '/probes/test-connection',
      method: 'post',
      data
    })
  },

  getTables: (probeKey, params) => {
    return request({
      url: `/database-metadata/${probeKey}/tables`,
      method: 'get',
      params
    })
  },

  switchInstance: (probeKey, instanceId) => {
    return request({
      url: `/database-metadata/${probeKey}/switch-instance`,
      method: 'post',
      params: { instanceId }
    })
  },

  getMetadata: (probeKey, instanceId) => {
    return request({
      url: `/database-metadata/${probeKey}/metadata`,
      method: 'get',
      params: instanceId ? { instanceId } : undefined
    })
  },

  triggerCollection: (probeKey) => {
    return request({
      url: `/probes/${probeKey}/collect`,
      method: 'post'
    })
  },

  switchConnection: (probeKey, connectionId) => {
    return request({
      url: `/database-metadata/${probeKey}/switch-connection`,
      method: 'post',
      params: { connectionId }
    })
  },

  getInstances: (probeKey) => {
    return request({
      url: `/probes/${probeKey}/instances`,
      method: 'get',
      showError: false
    })
  },

  saveSelectedInstance: (probeKey, connectionId) => {
    return request({
      url: `/probes/${probeKey}/selected-instance`,
      method: 'post',
      params: { connectionId }
    })
  },

  testInstanceConnection: (probeKey, instanceId) => {
    return request({
      url: `/database-metadata/${probeKey}/instances/${instanceId}/test`,
      method: 'post'
    })
  },

  getTableData: (probeKey, tableName, params) => {
    return request({
      url: `/database-metadata/${probeKey}/tables/${tableName}/data`,
      method: 'get',
      params,
      timeout: 30000
    })
  },

  importData: (probeKey, formData) => {
    return request({
      url: `/database-metadata/${probeKey}/import`,
      method: 'post',
      data: formData,
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 120000
    })
  }
}

export const getMetadata = databaseProbeApi.getMetadata
export const getTables = databaseProbeApi.getTables
export const testConnection = databaseProbeApi.testConnection
export const triggerCollection = databaseProbeApi.triggerCollection
export const create = databaseProbeApi.create
export const update = databaseProbeApi.update
export const remove = databaseProbeApi.delete
export const getById = databaseProbeApi.getById
export const getPage = databaseProbeApi.getPage
export const getInstances = databaseProbeApi.getInstances
export const testInstanceConnection = databaseProbeApi.testInstanceConnection
