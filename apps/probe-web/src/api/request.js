import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'

const request = axios.create({
  baseURL: '/api',
  timeout: 10000
})

// 标记是否正在刷新 token，防止并发刷新
let isRefreshing = false
// 失败请求队列
let failedQueue = []

// 标记是否正在处理登录过期，防止重复弹窗
let isHandlingLoginExpired = false

// 请求拦截器
request.interceptors.request.use(
  config => {
    const token = localStorage.getItem('token')
    if (token) {
      config.headers['Authorization'] = `Bearer ${token}`
    }

    // 仅在开发环境打印请求日志，且不记录敏感字段
    if (import.meta.env.DEV) {
      console.log('%c========== [API请求] ==========', 'color: #927511; font-weight: bold')
      console.log('%c请求方法:', 'color: #67c23a;', config.method?.toUpperCase())
      console.log('%c请求URL:', 'color: #67c23a;', config.url)
      console.log('%c请求参数:', 'color: #67c23a;', config.params || '无')
      // 不打印请求体（可能含密码等敏感数据）
      console.log('==========================', 'font-weight: bold')
    }

    return config
  },
  error => {
    return Promise.reject(error)
  }
)

// 响应拦截器
request.interceptors.response.use(
  response => {
    // blob 响应直接返回（文件下载等）
    if (response.config.responseType === 'blob') {
      return response
    }

    const res = response.data

    // 仅在开发环境打印响应日志
    if (import.meta.env.DEV) {
      console.log('%c========== [API响应] ==========', 'color: #198754; font-weight: bold')
      console.log('%c响应URL:', 'color: #67c23a;', response.config?.url || '未知')
      console.log('%c响应code:', 'color: #67c23a;', res.code)
      console.log('==========================', 'font-weight: bold')
    }

    if (res.code === 200) {
      return res
    } else {
      // 检查是否配置了自动显示错误消息（默认显示）
      if (response.config.showError !== false) {
        ElMessage.error(res.message || '请求失败')
      }
      return Promise.reject(new Error(res.message || '请求失败'))
    }
  },
  async error => {
    console.error('[API错误]', error)

    const originalRequest = error.config

    // 处理401未授权错误 - 尝试刷新 token
    if (error.response && error.response.status === 401 && !originalRequest._retry) {
      // 防止重复处理
      if (isHandlingLoginExpired) {
        return Promise.reject(new Error('正在处理登录过期'))
      }

      const refreshToken = localStorage.getItem('refreshToken')

      // 如果没有 refresh token，直接跳转登录页
      if (!refreshToken) {
        return handleLoginExpired()
      }

      // 如果正在刷新，将请求加入队列
      if (isRefreshing) {
        return new Promise((resolve, reject) => {
          failedQueue.push({ resolve, reject })
        }).then(token => {
          originalRequest.headers['Authorization'] = `Bearer ${token}`
          return request(originalRequest)
        }).catch(err => {
          return Promise.reject(err)
        })
      }

      // 标记正在刷新
      isRefreshing = true
      originalRequest._retry = true

      try {
        // 尝试刷新 token
        const response = await axios.post('/api/auth/refresh', {
          refreshToken: refreshToken
        })

        if (response.data.code === 200) {
          const { accessToken, refreshToken: newRefreshToken } = response.data.data

          // 更新本地存储的 token
          localStorage.setItem('token', accessToken)
          localStorage.setItem('refreshToken', newRefreshToken)

          // 处理队列中的请求
          failedQueue.forEach(prom => prom.resolve(accessToken))
          failedQueue = []

          // 重试原始请求
          originalRequest.headers['Authorization'] = `Bearer ${accessToken}`
          return request(originalRequest)
        } else {
          // 刷新失败，清除队列并跳转登录页
          failedQueue.forEach(prom => prom.reject(new Error('刷新 token 失败')))
          failedQueue = []
          return handleLoginExpired()
        }
      } catch (refreshError) {
        console.error('[刷新 token 失败]', refreshError)
        // 刷新失败，清除队列并跳转登录页
        failedQueue.forEach(prom => prom.reject(refreshError))
        failedQueue = []
        return handleLoginExpired()
      } finally {
        isRefreshing = false
      }
    }

    // 处理网络错误
    if (!error.response) {
      if (originalRequest.showError !== false) {
        ElMessage.error('网络连接失败，请检查网络设置')
      }
      return Promise.reject(new Error('网络连接失败'))
    }

    // 处理其他错误
    const message = error.response?.data?.message || error.message || '网络错误'
    if (originalRequest.showError !== false) {
      ElMessage.error(message)
    }
    return Promise.reject(error)
  }
)

// 处理登录过期
function handleLoginExpired() {
  if (isHandlingLoginExpired) {
    return Promise.reject(new Error('正在处理登录过期'))
  }

  isHandlingLoginExpired = true

  console.warn('[认证失败] 登录已过期，清除本地数据')

  // 清除本地存储的登录信息
  localStorage.removeItem('token')
  localStorage.removeItem('refreshToken')
  localStorage.removeItem('userInfo')
  localStorage.removeItem('isLogin')
  localStorage.removeItem('username')

  // 显示提示消息
  ElMessage.warning({
    message: '登录已过期，请重新登录',
    duration: 2000,
    onClose: () => {
      // 跳转到登录页
      router.push('/login').finally(() => {
        isHandlingLoginExpired = false
      })
    }
  })

  return Promise.reject(new Error('登录已过期'))
}

export default request
