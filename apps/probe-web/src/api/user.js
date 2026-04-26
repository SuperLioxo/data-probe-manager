import request from './request'

/**
 * 用户认证API
 */

// 登录
export function login(data) {
  return request({
    url: '/auth/login',
    method: 'post',
    data
  })
}

// 登出
export function logout() {
  return request({
    url: '/auth/logout',
    method: 'post'
  })
}

// 获取当前用户信息
export function getCurrentUser() {
  return request({
    url: '/auth/user-info',
    method: 'get'
  })
}

// 更新用户信息
export function updateUserInfo(data) {
  return request({
    url: '/user/info',
    method: 'put',
    data
  })
}

// 修改密码
export function changePassword(data) {
  return request({
    url: '/user/password',
    method: 'put',
    data
  })
}

// 上传头像
export function uploadAvatar(file) {
  const formData = new FormData()
  formData.append('file', file)
  return request({
    url: '/user/avatar',
    method: 'post',
    data: formData,
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  })
}

// 获取登录历史
export function getLoginHistory(params) {
  return request({
    url: '/user/login-history',
    method: 'get',
    params
  })
}

/**
 * 系统设置API
 */

// 获取用户设置
export function getUserSettings() {
  return request({
    url: '/settings',
    method: 'get'
  })
}

// 更新用户设置
export function updateUserSettings(data) {
  return request({
    url: '/settings',
    method: 'put',
    data
  })
}

// 重置用户设置
export function resetUserSettings() {
  return request({
    url: '/settings/reset',
    method: 'post'
  })
}

/**
 * 系统信息API
 */

// 获取系统信息
export function getSystemInfo() {
  return request({
    url: '/system/info',
    method: 'get'
  })
}

// 检查系统更新
export function checkUpdate() {
  return request({
    url: '/system/check-update',
    method: 'get'
  })
}

// 导出系统信息
export function exportSystemInfo() {
  return request({
    url: '/system/export-info',
    method: 'get',
    responseType: 'blob'
  })
}

// 获取系统日志
export function getSystemLogs(params) {
  return request({
    url: '/system/logs',
    method: 'get',
    params
  })
}

/**
 * 导出API对象
 */
const userApi = {
  login,
  logout,
  getCurrentUser,
  updateUserInfo,
  changePassword,
  uploadAvatar,
  getLoginHistory
}

const settingsApi = {
  getUserSettings,
  updateUserSettings,
  resetUserSettings
}

const systemApi = {
  getSystemInfo,
  checkUpdate,
  exportSystemInfo,
  getSystemLogs
}

export { userApi, settingsApi, systemApi }
export default userApi
