import request from './request'

export function pushAgentConfig(data) {
  return request({ url: '/agent-config/push', method: 'post', data })
}

export function pushConfigToAll(data) {
  return request({ url: '/agent-config/push-all', method: 'post', data })
}
