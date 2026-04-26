import request from './request'

export function getAgentLogs(params) {
  return request({ url: '/agent-logs', method: 'get', params })
}

export function downloadAgentLogs(agentCode) {
  return request({
    url: '/agent-logs/download',
    method: 'get',
    params: { agentCode },
    responseType: 'blob'
  })
}
