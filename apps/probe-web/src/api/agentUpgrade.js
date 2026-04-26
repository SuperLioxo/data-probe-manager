import request from './request'

export function uploadVersion(formData) {
  return request({ url: '/agent-upgrade/upload', method: 'post', data: formData, headers: { 'Content-Type': 'multipart/form-data' } })
}

export function listVersions() {
  return request({ url: '/agent-upgrade/versions', method: 'get' })
}

export function deleteVersion(id) {
  return request({ url: `/agent-upgrade/versions/${id}`, method: 'delete' })
}

export function triggerUpgrade(agentCode, targetVersion) {
  return request({ url: '/agent-upgrade/trigger', method: 'post', data: { agentCode, targetVersion } })
}

export function triggerBatchUpgrade(agentCodes, targetVersion) {
  return request({ url: '/agent-upgrade/trigger-batch', method: 'post', data: { agentCodes, targetVersion } })
}

export function getUpgradeStatus() {
  return request({ url: '/agent-upgrade/status', method: 'get' })
}
