import request from './request.js'

/**
 * 探针管理API
 */
export const probeApi = {
  /**
   * 获取探针列表
   */
  getList: (params) => {
    return request({
      url: '/probes',
      method: 'get',
      params: {
        ...params,
        _t: Date.now() // 添加时间戳绕过缓存
      }
    })
  },

  /**
   * 获取探针详情
   */
  getById: (id) => {
    return request({
      url: `/probes/${id}`,
      method: 'get'
    })
  },

  /**
   * 创建探针
   */
  create: (data) => {
    return request({
      url: '/probes',
      method: 'post',
      data
    })
  },

  /**
   * 更新探针
   */
  update: (id, data) => {
    return request({
      url: `/probes/${id}`,
      method: 'put',
      data
    })
  },

  /**
   * 删除探针
   */
  delete: (id) => {
    return request({
      url: `/probes/${id}`,
      method: 'delete'
    })
  },

  /**
   * 更新心跳
   */
  heartbeat: (probeKey) => {
    return request({
      url: `/probes/heartbeat/${probeKey}`,
      method: 'post'
    })
  },

  /**
   * 导出探针列表
   */
  export: (params) => {
    return request({
      url: '/probes/export',
      method: 'get',
      params,
      responseType: 'blob'
    })
  },

  /**
   * 批量创建探针
   */
  batchCreate: (data) => {
    return request({
      url: '/probes/batch',
      method: 'post',
      data
    })
  },

  /**
   * JSON导入探针
   */
  importJson: (jsonString) => {
    return request({
      url: '/probes/import/json',
      method: 'post',
      data: jsonString,
      headers: {
        'Content-Type': 'application/json'
      }
    })
  },

  /**
   * 导出JSON配置
   */
  exportJson: (params) => {
    return request({
      url: '/probes/export/json',
      method: 'get',
      params,
      responseType: 'blob'
    })
  },

  /**
   * 启动探针
   */
  startProbe: (probeKey) => {
    return request({
      url: `/probe-control/${probeKey}/start`,
      method: 'post',
      timeout: 60000
    })
  },

  /**
   * 停止探针
   */
  stopProbe: (probeKey) => {
    return request({
      url: `/probe-control/${probeKey}/stop`,
      method: 'post',
      timeout: 60000
    })
  },

  /**
   * 重启探针
   */
  restartProbe: (probeKey) => {
    return request({
      url: `/probe-control/${probeKey}/restart`,
      method: 'post',
      timeout: 60000
    })
  },

  /**
   * 检查Agent在线状态
   * @param {string} agentCode - Agent编码
   */
  getAgentStatus: (agentCode) => {
    return request({
      url: `/agent-control/${agentCode}/status`,
      method: 'get'
    })
  },

  /**
   * 启动Agent
   * @param {string} agentCode - Agent编码
   */
  startAgent: (agentCode) => {
    return request({
      url: `/agent-control/${agentCode}/start`,
      method: 'post'
    })
  },

  /**
   * 停止Agent
   * @param {string} agentCode - Agent编码
   */
  stopAgent: (agentCode) => {
    return request({
      url: `/agent-control/${agentCode}/stop`,
      method: 'post'
    })
  },

  /**
   * 重启Agent
   * @param {string} agentCode - Agent编码
   */
  restartAgent: (agentCode) => {
    return request({
      url: `/agent-control/${agentCode}/restart`,
      method: 'post'
    })
  },

  /**
   * 获取支持的数据库类型列表
   */
  getDatabaseTypes: () => {
    return request({
      url: '/probes/database-types',
      method: 'get'
    })
  },

  /**
   * 获取在线探针列表
   */
  getOnlineProbes: () => {
    return request({
      url: '/probes/online',
      method: 'get'
    })
  }
}

// 导出常用方法的快捷方式
export const getList = probeApi.getList
export const getById = probeApi.getById
export const create = probeApi.create
export const update = probeApi.update
export const deleteProbe = probeApi.delete
export const importJson = probeApi.importJson
export const exportJson = probeApi.exportJson
export const getOnlineProbes = probeApi.getOnlineProbes
