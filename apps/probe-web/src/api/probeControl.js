import request from './request'

/**
 * 获取探针状态
 */
export function getProbeStatus(probeKey) {
  return request({
    url: `/probe-control/${probeKey}/status`,
    method: 'get'
  })
}

/**
 * 停止探针
 */
export function stopProbe(probeKey) {
  return request({
    url: `/probe-control/${probeKey}/stop`,
    method: 'post'
  })
}

/**
 * 启动探针
 */
export function startProbe(probeKey) {
  return request({
    url: `/probe-control/${probeKey}/start`,
    method: 'post'
  })
}

/**
 * 重启探针
 */
export function restartProbe(probeKey) {
  return request({
    url: `/probe-control/${probeKey}/restart`,
    method: 'post'
  })
}

/**
 * 更新探针配置
 */
export function updateProbeConfig(probeKey, config) {
  return request({
    url: `/probe-control/${probeKey}/config`,
    method: 'post',
    data: config
  })
}
