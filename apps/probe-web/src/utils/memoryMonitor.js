/**
 * 内存监控工具
 *
 * 用于监控前端应用的内存使用情况，及时发现内存泄漏问题。
 *
 * 使用示例：
 * ```javascript
 * import { MemoryMonitor } from '@/utils/memoryMonitor'
 *
 * const monitor = new MemoryMonitor()
 * monitor.start(5000) // 每5秒检查一次
 *
 * // 组件卸载时停止监控
 * onUnmounted(() => {
 *   monitor.stop()
 * })
 * ```
 */

export class MemoryMonitor {
  constructor() {
    this.isMonitoring = false
    this.intervalId = null
    this.memoryHistory = []
    this.maxHistorySize = 100
    this.warningThreshold = 0.8 // 内存使用超过80%时警告
  }

  /**
   * 开始监控内存使用情况
   * @param {number} interval - 检查间隔（毫秒），默认5000ms（5秒）
   * @param {number} historySize - 保存的历史记录数量，默认100条
   */
  start(interval = 5000, historySize = 100) {
    if (this.isMonitoring) {
      console.warn('[内存监控] 监控已在运行中')
      return
    }

    // 检查浏览器是否支持内存API
    if (!performance.memory) {
      console.warn('[内存监控] 当前浏览器不支持 performance.memory API')
      console.warn('[内存监控] 请在 Chrome 或 Edge 浏览器中启用内存监控')
      console.warn('[内存监控] 启动方式: chrome --enable-precise-memory-info')
      return
    }

    this.isMonitoring = true
    this.maxHistorySize = historySize


    this.intervalId = setInterval(() => {
      this._checkMemory()
    }, interval)

    // 立即执行一次检查
    this._checkMemory()
  }

  /**
   * 停止监控
   */
  stop() {
    if (this.intervalId) {
      clearInterval(this.intervalId)
      this.intervalId = null
    }
    this.isMonitoring = false
  }

  /**
   * 获取内存使用历史
   * @returns {Array} 内存使用历史记录
   */
  getHistory() {
    return [...this.memoryHistory]
  }

  /**
   * 获取最新的内存信息
   * @returns {Object|null} 最新的内存信息
   */
  getLatestInfo() {
    return this.memoryHistory.length > 0
      ? this.memoryHistory[this.memoryHistory.length - 1]
      : null
  }

  /**
   * 清空历史记录
   */
  clearHistory() {
    this.memoryHistory = []
  }

  /**
   * 内部方法：检查内存使用情况
   * @private
   */
  _checkMemory() {
    try {
      const memory = performance.memory

      // 转换为MB单位
      const memoryInfo = {
        timestamp: Date.now(),
        usedJSHeapSize: Math.round(memory.usedJSHeapSize / 1024 / 1024),
        totalJSHeapSize: Math.round(memory.totalJSHeapSize / 1024 / 1024),
        jsHeapSizeLimit: Math.round(memory.jsHeapSizeLimit / 1024 / 1024)
      }

      // 计算内存使用率
      memoryInfo.usagePercentage = (
        memoryInfo.usedJSHeapSize / memoryInfo.jsHeapSizeLimit
      )

      // 保存到历史记录
      this.memoryHistory.push(memoryInfo)

      // 限制历史记录大小
      if (this.memoryHistory.length > this.maxHistorySize) {
        this.memoryHistory.shift()
      }

      // 输出日志
        `[内存监控] 已用: ${memoryInfo.usedJSHeapSize}MB, ` +
        `总计: ${memoryInfo.totalJSHeapSize}MB, ` +
        `限制: ${memoryInfo.jsHeapSizeLimit}MB, ` +
        `使用率: ${(memoryInfo.usagePercentage * 100).toFixed(1)}%`
      )

      // 检查是否超过警告阈值
      if (memoryInfo.usagePercentage > this.warningThreshold) {
        console.warn(
          `[内存警告] 内存使用过高! ` +
          `使用率: ${(memoryInfo.usagePercentage * 100).toFixed(1)}%, ` +
          `已用: ${memoryInfo.usedJSHeapSize}MB/${memoryInfo.jsHeapSizeLimit}MB`
        )

        // 尝试触发垃圾回收（如果浏览器支持）
        if (typeof gc === 'function') {
          gc()
        }
      }

      // 检测内存泄漏：如果内存持续增长
      this._detectMemoryLeak()

    } catch (error) {
      console.error('[内存监控] 检查内存时出错:', error)
    }
  }

  /**
   * 检测内存泄漏
   * @private
   */
  _detectMemoryLeak() {
    if (this.memoryHistory.length < 10) {
      return // 历史记录不足，无法检测
    }

    // 获取最近10次记录
    const recent10 = this.memoryHistory.slice(-10)

    // 计算内存增长趋势
    const firstUsed = recent10[0].usedJSHeapSize
    const lastUsed = recent10[recent10.length - 1].usedJSHeapSize
    const growthRate = ((lastUsed - firstUsed) / firstUsed) * 100

    // 如果内存持续增长超过20%，可能存在内存泄漏
    if (growthRate > 20) {
      console.warn(
        `[内存泄漏警告] 检测到内存可能存在泄漏! ` +
        `最近10次检查内存增长了 ${growthRate.toFixed(1)}% ` +
        `(${firstUsed}MB → ${lastUsed}MB)`
      )
    }
  }

  /**
   * 获取内存使用报告
   * @returns {Object} 内存使用报告
   */
  getReport() {
    if (this.memoryHistory.length === 0) {
      return null
    }

    const latest = this.getLatestInfo()
    const minUsed = Math.min(...this.memoryHistory.map(h => h.usedJSHeapSize))
    const maxUsed = Math.max(...this.memoryHistory.map(h => h.usedJSHeapSize))
    const avgUsed = Math.round(
      this.memoryHistory.reduce((sum, h) => sum + h.usedJSHeapSize, 0) /
      this.memoryHistory.length
    )

    return {
      current: latest,
      min: minUsed,
      max: maxUsed,
      average: avgUsed,
      growth: maxUsed - minUsed,
      sampleCount: this.memoryHistory.length
    }
  }

  /**
   * 打印内存使用报告
   */
  printReport() {
    const report = this.getReport()
    if (!report) {
      return
    }

  }
}

/**
 * 创建全局内存监控实例（单例模式）
 */
let globalMemoryMonitor = null

export const useMemoryMonitor = () => {
  if (!globalMemoryMonitor) {
    globalMemoryMonitor = new MemoryMonitor()
  }
  return globalMemoryMonitor
}

export default MemoryMonitor
