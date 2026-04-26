import request from './request'

export function getSyncTasks(params) {
  return request({ url: '/sync-tasks', method: 'get', params })
}

export function getSyncTask(id) {
  return request({ url: `/sync-tasks/${id}`, method: 'get' })
}

export function createSyncTask(data) {
  return request({ url: '/sync-tasks', method: 'post', data })
}

export function updateSyncTask(id, data) {
  return request({ url: `/sync-tasks/${id}`, method: 'put', data })
}

export function deleteSyncTask(id) {
  return request({ url: `/sync-tasks/${id}`, method: 'delete' })
}

export function toggleSyncTask(id, enabled) {
  return request({ url: `/sync-tasks/${id}/toggle`, method: 'put', params: { enabled } })
}

export function triggerSync(id) {
  return request({ url: `/sync-tasks/${id}/trigger`, method: 'post' })
}

export function getSyncLogs(params) {
  return request({ url: `/sync-tasks/${params.taskId}/logs`, method: 'get', params })
}

export function getSyncStatistics() {
  return request({ url: '/sync-tasks/statistics', method: 'get' })
}
