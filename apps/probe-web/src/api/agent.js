import request from './request'

export default {
  /**
   * 获取所有Agent列表
   */
  getAgents: () => {
    return request({
      url: '/agents',
      method: 'get'
    })
  },

  /**
   * 获取Agent状态
   */
  getAgentStatus: (agentCode) => {
    return request({
      url: `/agents/${agentCode}/status`,
      method: 'get'
    })
  },

  /**
   * 获取Agent操作指引
   */
  getAgentGuide: (agentCode) => {
    return request({
      url: `/agents/${agentCode}/guide`,
      method: 'get'
    })
  },

  /**
   * Agent注册（Agent启动时自动调用）
   */
  registerAgent: (agentCode, data) => {
    return request({
      url: `/agents/${agentCode}/register`,
      method: 'post',
      data: data
    })
  },

  /**
   * 获取Agent下的探针列表
   */
  getAgentProbes: (agentCode) => {
    return request({
      url: `/agents/${agentCode}/probes`,
      method: 'get'
    })
  },

  /**
   * 启动Agent
   */
  startAgent: (agentCode) => {
    return request({
      url: `/agent-control/${agentCode}/start`,
      method: 'post',
      timeout: 60000
    })
  },

  /**
   * 停止Agent
   */
  stopAgent: (agentCode) => {
    return request({
      url: `/agent-control/${agentCode}/stop`,
      method: 'post',
      timeout: 60000
    })
  },

  /**
   * 重启Agent
   */
  restartAgent: (agentCode) => {
    return request({
      url: `/agent-control/${agentCode}/restart`,
      method: 'post',
      timeout: 60000
    })
  }
}
