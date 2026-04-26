import request from './request.js'

/**
 * 统一数据源管理 API
 * 数据库类型走 /api/database-connections
 * 文件类型走 /api/probes?type=FILE
 */
export const datasourceApi = {
  // ===== 数据库数据源 =====
  getDbSources: () => {
    return request({ url: '/database-connections', method: 'get' })
  },
  createDbSource: (data) => {
    return request({ url: '/database-connections', method: 'post', data })
  },
  updateDbSource: (id, data) => {
    return request({ url: `/database-connections/${id}`, method: 'put', data })
  },
  deleteDbSource: (id) => {
    return request({ url: `/database-connections/${id}`, method: 'delete' })
  },
  testDbConnection: (data) => {
    return request({ url: '/database-connections/test', method: 'post', data })
  },
  testDbConnectionById: (id) => {
    return request({ url: `/database-connections/${id}/test`, method: 'post' })
  },

  // ===== 文件数据源 =====
  getFileSources: () => {
    return request({ url: '/probes', method: 'get', params: { type: 'FILE', pageSize: 100 } })
  },
  createFileSource: (data) => {
    return request({ url: '/probes', method: 'post', data: { ...data, type: 'FILE' } })
  },
  updateFileSource: (id, data) => {
    return request({ url: `/probes/${id}`, method: 'put', data })
  },
  deleteFileSource: (id) => {
    return request({ url: `/probes/${id}`, method: 'delete' })
  }
}

export default datasourceApi
