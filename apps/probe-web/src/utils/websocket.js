/**
 * WebSocket客户端工具类
 * 用于与探针服务端WebSocket通信
 */

export class ProbeWebSocket {
  constructor(url, probeKey, options = {}) {
    this.url = url
    this.probeKey = probeKey
    this.reconnectDelay = options.reconnectDelay || 3000
    this.maxReconnectAttempts = options.maxReconnectAttempts || 10
    this.listeners = new Map()
    this.ws = null
    this.reconnectAttempts = 0
    this.manualClose = false
  }

  /**
   * 连接WebSocket
   */
  connect() {
    if (this.ws?.readyState === WebSocket.OPEN) {
      return
    }

    try {
      // 构建WebSocket URL，添加probe_key参数
      const url = new URL(this.url)
      url.searchParams.append('probe_key', this.probeKey)

      this.ws = new WebSocket(url.toString())

      this.ws.onopen = () => {
        this.reconnectAttempts = 0
        this.emit('connected', { probeKey: this.probeKey, timestamp: Date.now() })
      }

      this.ws.onmessage = (event) => {
        try {
          const message = JSON.parse(event.data)
          this.handleMessage(message)
        } catch (error) {
          console.error('[WebSocket] 消息解析失败:', error, event.data)
          this.emit('error', { error, rawData: event.data })
        }
      }

      this.ws.onerror = (error) => {
        console.error('[WebSocket] 错误:', error)
        this.emit('error', { error })
      }

      this.ws.onclose = (event) => {
        if (!this.manualClose) {
          this.reconnect()
        }
      }
    } catch (error) {
      console.error('[WebSocket] 连接失败:', error)
      this.reconnect()
    }
  }

  /**
   * 重连
   */
  reconnect() {
    if (this.reconnectAttempts >= this.maxReconnectAttempts) {
      console.error('[WebSocket] 达到最大重连次数，停止重连')
      this.emit('maxReconnectReached')
      return
    }

    this.reconnectAttempts++

    setTimeout(() => {
      this.connect()
    }, this.reconnectDelay)
  }

  /**
   * 监听事件
   */
  on(type, callback) {
    if (!this.listeners.has(type)) {
      this.listeners.set(type, [])
    }
    this.listeners.get(type).push(callback)
  }

  /**
   * 取消监听事件
   */
  off(type, callback) {
    if (!this.listeners.has(type)) return
    const callbacks = this.listeners.get(type)
    const index = callbacks.indexOf(callback)
    if (index > -1) {
      callbacks.splice(index, 1)
    }
  }

  /**
   * 触发事件
   */
  emit(type, data) {
    const callbacks = this.listeners.get(type) || []
    callbacks.forEach(cb => {
      try {
        cb(data)
      } catch (error) {
        console.error(`[WebSocket] 事件处理器错误 [${type}]:`, error)
      }
    })
  }

  /**
   * 处理不同类型的消息
   */
  handleMessage(message) {
    // 处理不同类型的消息
    switch (message.type) {
      case 'CONNECTED':
        this.emit('connected', message)
        break
      case 'DATABASE_METADATA_UPDATE':
        this.emit('databaseMetadata', message.payload || message)
        break
      case 'NETWORK_PING_RESULT':
        this.emit('networkPing', message.payload || message)
        break
      case 'FILE_SCAN_REPORT':
        this.emit('fileScan', message.payload || message)
        break
      case 'HEARTBEAT':
        this.emit('heartbeat', message)
        break
      case 'ERROR':
        this.emit('serverError', message)
        break
      case 'ACK':
        this.emit('ack', message)
        break
      default:
        // 处理加密消息或其他自定义消息
        if (message.cmd) {
          this.emit(message.cmd.toLowerCase(), message)
        } else {
          this.emit('unknown', message)
        }
    }
  }

  /**
   * 发送消息
   */
  send(data) {
    if (this.ws?.readyState === WebSocket.OPEN) {
      try {
        const message = typeof data === 'string' ? data : JSON.stringify(data)
        this.ws.send(message)
        return true
      } catch (error) {
        console.error('[WebSocket] 发送消息失败:', error)
        return false
      }
    } else {
      console.warn('[WebSocket] 未连接，无法发送消息')
      return false
    }
  }

  /**
   * 发送命令
   */
  sendCommand(cmd, payload = {}) {
    const message = {
      type: 'COMMAND',
      cmd,
      probeKey: this.probeKey,
      timestamp: Date.now(),
      ...payload
    }
    return this.send(message)
  }

  /**
   * 关闭连接
   */
  close() {
    this.manualClose = true
    this.ws?.close()
  }

  /**
   * 获取连接状态
   */
  getReadyState() {
    return this.ws?.readyState ?? WebSocket.CLOSED
  }

  /**
   * 是否已连接
   */
  isConnected() {
    return this.ws?.readyState === WebSocket.OPEN
  }
}

// WebSocket状态常量
ProbeWebSocket.CONNECTING = WebSocket.CONNECTING
ProbeWebSocket.OPEN = WebSocket.OPEN
ProbeWebSocket.CLOSING = WebSocket.CLOSING
ProbeWebSocket.CLOSED = WebSocket.CLOSED

export default ProbeWebSocket
