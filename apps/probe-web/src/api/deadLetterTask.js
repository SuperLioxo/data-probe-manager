import request from './request'

export function getDeadLetterTasks(params) {
  return request({ url: '/dead-letter-tasks', method: 'get', params })
}

export function retryDeadLetterTask(id) {
  return request({ url: `/dead-letter-tasks/${id}/retry`, method: 'post' })
}

export function deleteDeadLetterTask(id) {
  return request({ url: `/dead-letter-tasks/${id}`, method: 'delete' })
}

export function purgeDeadLetterTasks() {
  return request({ url: '/dead-letter-tasks/purge', method: 'delete' })
}

export function getDeadLetterStatistics() {
  return request({ url: '/dead-letter-tasks/statistics', method: 'get' })
}
