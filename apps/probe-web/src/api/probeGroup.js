import request from './request'

/**
 * 获取分组树
 */
export function getGroupTree() {
  return request({ url: '/probe-groups/tree', method: 'get' })
}

/**
 * 获取分组详情
 */
export function getGroup(id) {
  return request({ url: `/probe-groups/${id}`, method: 'get' })
}

/**
 * 创建分组
 */
export function createGroup(data) {
  return request({ url: '/probe-groups', method: 'post', data })
}

/**
 * 更新分组
 */
export function updateGroup(id, data) {
  return request({ url: `/probe-groups/${id}`, method: 'put', data })
}

/**
 * 删除分组
 */
export function deleteGroup(id) {
  return request({ url: `/probe-groups/${id}`, method: 'delete' })
}

/**
 * 添加探针到分组
 */
export function addProbesToGroup(groupId, probeIds) {
  return request({ url: `/probe-groups/${groupId}/probes`, method: 'post', data: probeIds })
}

/**
 * 从分组移除探针
 */
export function removeProbeFromGroup(groupId, probeId) {
  return request({ url: `/probe-groups/${groupId}/probes/${probeId}`, method: 'delete' })
}

/**
 * 获取分组下的探针列表
 */
export function getGroupProbes(groupId) {
  return request({ url: `/probe-groups/${groupId}/probes`, method: 'get' })
}
