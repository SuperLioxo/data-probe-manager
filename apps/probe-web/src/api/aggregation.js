import request from './request.js'

/**
 * 汇聚数据 API
 * 前端仅从此接口读取汇聚后的数据
 */
export const aggregationApi = {
  getDatasources: () => {
    return request({ url: '/aggregation/datasources', method: 'get' })
  },
  getTables: (sourceId) => {
    return request({ url: '/aggregation/tables', method: 'get', params: sourceId ? { sourceId } : {} })
  },
  getTableData: (sourceId, tableName, pageNum = 1, pageSize = 20) => {
    return request({ url: `/aggregation/tables/${sourceId}/${tableName}/data`, method: 'get', params: { pageNum, pageSize } })
  },
  getStats: () => {
    return request({ url: '/aggregation/stats', method: 'get' })
  }
}

export default aggregationApi
