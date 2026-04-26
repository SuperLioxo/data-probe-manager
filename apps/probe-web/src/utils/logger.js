/**
 * 日志工具类
 * 根据环境变量控制日志输出
 */
const isDevelopment = import.meta.env.DEV

export const logger = {
  log: (...args) => {
    if (isDevelopment) {
    }
  },
  warn: (...args) => {
    if (isDevelopment) {
      console.warn('[WARN]', ...args)
    }
  },
  error: (...args) => {
    // 错误日志始终输出
    console.error('[ERROR]', ...args)
  },
  info: (...args) => {
    if (isDevelopment) {
      console.info('[INFO]', ...args)
    }
  }
}
