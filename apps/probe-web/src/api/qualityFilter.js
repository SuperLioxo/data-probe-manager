import request from './request.js'

/**
 * 质量过滤 - 不合格记录 API
 */
export const qualityFilterApi = {
  getBadRecords: (params) => {
    return request({ url: '/quality-rules/bad-records', method: 'get', params })
  },
  exportBadRecords: (params) => {
    return request({ url: '/quality-rules/bad-records/export', method: 'get', params, responseType: 'blob' })
  }
}

export default qualityFilterApi
