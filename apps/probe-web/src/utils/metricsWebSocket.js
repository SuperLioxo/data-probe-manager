/**
 * Metrics WebSocket Manager
 * 管理监控指标的实时WebSocket推送
 */

class MetricsWebSocketManager {
  constructor() {
    this.ws = null
    this.reconnectAttempts = 0
    this.maxReconnectAttempts = 5
    this.reconnectInterval = 2000
    this.listeners = new Map()
    this.isConnected = false
    this.isReady = false // 标记连接是否就绪（可以发送消息）
    this.heartbeatInterval = null
    this.connectTime = null // 记录连接时间
    this._isReconnecting = false // 重连锁，防止并发重连
    this._reconnectTimer = null
    this._token = null
  }

  /**
   * 连接WebSocket
   * @param {string} token - JWT token
   */
  connect(token) {
    if (this.ws && this.ws.readyState === WebSocket.OPEN) {
      console.log('WebSocket already connected')
      return
    }

    this._token = token
    const wsUrl = `ws://localhost:8081/ws/metrics?token=${token}`
    console.log('Connecting to WebSocket:', wsUrl.replace(/token=[^&]+/, 'token=****'))

    try {
      this.ws = new WebSocket(wsUrl)

      this.ws.onopen = () => {
        console.log('[WebSocket] 连接已建立')
        this.isConnected = true
        this.connectTime = Date.now()
        this.reconnectAttempts = 0

        // 延迟2秒后再标记为就绪并启动心跳，确保连接完全稳定
        setTimeout(() => {
          if (this.isConnected) {
            this.isReady = true
            console.log('[WebSocket] 连接就绪，可以发送消息')
            this.startHeartbeat()  // 只在连接就绪后启动心跳
            this.notifyListeners('connected', { status: 'connected' })
          }
        }, 2000)  // 增加到2秒，确保连接完全稳定
      }

      this.ws.onmessage = (event) => {
        // 过滤非文本消息和空消息
        if (!event.data || typeof event.data !== 'string') {
          return
        }

        try {
          const data = JSON.parse(event.data)
          console.log('[WebSocket] 收到消息:', data.type, data)

          switch (data.type) {
            case 'METRICS_UPDATE':
              this.notifyListeners('metrics', data.payload)
              break
            case 'PROBE_STATUS':
              this.notifyListeners('status', data.payload)
              break
            case 'ALERT':
              this.notifyListeners('alert', data.payload)
              break
            case 'PONG':
              console.debug('Received PONG from server')
              break
            case 'SUBSCRIBE_ACK':
              // 订阅确认
              console.log('Subscription acknowledged:', data.payload)
              this.notifyListeners('subscribed', data.payload)
              break
            case 'UNSUBSCRIBE_ACK':
              // 取消订阅确认
              console.log('Unsubscription acknowledged:', data.payload)
              this.notifyListeners('unsubscribed', data.payload)
              break
            case 'ERROR':
              // 错误消息
              console.error('WebSocket error from server:', data.payload)
              this.notifyListeners('error', data.payload)
              break
            default:
              console.warn('Unknown message type:', data.type)
          }
        } catch (error) {
          console.error('Failed to parse WebSocket message:', error, 'raw:', event.data)
        }
      }

      this.ws.onerror = (error) => {
        console.error('[WebSocket] 错误:', error)
        this.isConnected = false
        this.isReady = false
        this.connectTime = null
      }

      this.ws.onclose = () => {
        console.log('[WebSocket] 连接已关闭')
        this.isConnected = false
        this.isReady = false
        this.connectTime = null
        this.stopHeartbeat()
        this.notifyListeners('disconnected', { status: 'disconnected' })

        // 自动重连（加锁防止并发）
        if (this.reconnectAttempts < this.maxReconnectAttempts && !this._isReconnecting) {
          this._isReconnecting = true
          this.reconnectAttempts++
          const delay = this.reconnectInterval * Math.pow(2, this.reconnectAttempts - 1)
          console.log(`Reconnecting in ${delay}ms... (attempt ${this.reconnectAttempts}/${this.maxReconnectAttempts})`)

          this._reconnectTimer = setTimeout(() => {
            this._isReconnecting = false
            this.connect(this._token)
          }, delay)
        } else if (this.reconnectAttempts >= this.maxReconnectAttempts) {
          console.error('Max reconnect attempts reached')
          this.notifyListeners('error', { message: 'Connection failed' })
        }
      }
    } catch (error) {
      console.error('Failed to create WebSocket connection:', error)
      this.notifyListeners('error', { message: error.message })
    }
  }

  /**
   * 断开WebSocket连接
   */
  disconnect() {
    // 取消待执行的重连定时器
    if (this._reconnectTimer) {
      clearTimeout(this._reconnectTimer)
      this._reconnectTimer = null
    }
    this._isReconnecting = false
    this.reconnectAttempts = this.maxReconnectAttempts // 阻止后续自动重连

    if (this.ws) {
      this.stopHeartbeat()
      this.ws.onclose = null // 防止触发自动重连
      this.ws.close()
      this.ws = null
      this.isConnected = false
      this.isReady = false
      this.connectTime = null
      console.log('WebSocket disconnected')
    }
  }

  /**
   * 发送消息
   * @param {Object} message - 消息对象
   */
  send(message) {
    if (!this.isReady) {
      console.warn('[WebSocket] 连接未就绪，跳过发送消息:', message)
      return
    }

    if (this.ws && this.ws.readyState === WebSocket.OPEN) {
      try {
        // 验证消息格式
        if (!message || typeof message !== 'object') {
          console.error('[WebSocket] 无效的消息格式:', message)
          return
        }

        if (!message.type || typeof message.type !== 'string') {
          console.error('[WebSocket] 消息缺少type字段或type无效:', message)
          return
        }

        // 构造符合后端格式的消息：type + payload + timestamp
        const fullMessage = {
          type: message.type,
          payload: message.payload !== undefined ? message.payload : null,
          timestamp: Date.now()
        }

        const messageStr = JSON.stringify(fullMessage)
        console.log('[WebSocket] 发送消息:', messageStr)
        this.ws.send(messageStr)
      } catch (error) {
        console.error('[WebSocket] 发送消息失败:', error, message)
      }
    } else {
      console.warn('[WebSocket] 未连接，无法发送消息:', message)
    }
  }

  /**
   * 订阅事件
   * @param {string} event - 事件名称
   * @param {Function} callback - 回调函数
   */
  on(event, callback) {
    if (!this.listeners.has(event)) {
      this.listeners.set(event, [])
    }
    this.listeners.get(event).push(callback)
  }

  /**
   * 取消订阅
   * @param {string} event - 事件名称
   * @param {Function} callback - 回调函数
   */
  off(event, callback) {
    if (this.listeners.has(event)) {
      const callbacks = this.listeners.get(event)
      const index = callbacks.indexOf(callback)
      if (index > -1) {
        callbacks.splice(index, 1)
      }
    }
  }

  /**
   * 通知所有监听器
   * @param {string} event - 事件名称
   * @param {Object} data - 数据
   */
  notifyListeners(event, data) {
    if (this.listeners.has(event)) {
      this.listeners.get(event).forEach(callback => {
        try {
          callback(data)
        } catch (error) {
          console.error(`Error in ${event} listener:`, error)
        }
      })
    }
  }

  /**
   * 启动心跳
   */
  startHeartbeat() {
    this.heartbeatInterval = setInterval(() => {
      if (this.isConnected && this.isReady) {
        this.send({ type: 'PING' })
      }
    }, 30000) // 每30秒发送一次心跳
  }

  /**
   * 停止心跳
   */
  stopHeartbeat() {
    if (this.heartbeatInterval) {
      clearInterval(this.heartbeatInterval)
      this.heartbeatInterval = null
    }
  }

  /**
   * 订阅探针指标
   * @param {Array<number>} probeIds - 探针ID数组
   */
  subscribeProbes(probeIds) {
    if (!probeIds || probeIds.length === 0) {
      console.warn('[WebSocket] 没有探针ID，跳过订阅')
      return
    }

    // 验证所有ID都是数字
    const validIds = probeIds.filter(id => typeof id === 'number' && !isNaN(id))
    if (validIds.length === 0) {
      console.warn('[WebSocket] 没有有效的探针ID，跳过订阅')
      return
    }

    this.send({
      type: 'SUBSCRIBE',
      payload: { probeIds: validIds }
    })
  }

  /**
   * 取消订阅探针指标
   * @param {Array<number>} probeIds - 探针ID数组
   */
  unsubscribeProbes(probeIds) {
    this.send({
      type: 'UNSUBSCRIBE',
      payload: { probeIds }
    })
  }

  /**
   * 获取连接状态
   * @returns {boolean}
   */
  getConnectionState() {
    return this.isConnected
  }
}

// 导出单例
export const metricsWebSocket = new MetricsWebSocketManager()
export default metricsWebSocket
