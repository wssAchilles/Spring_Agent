/*
 * Copyright © 2026 Qiantong Technology Co., Ltd.
 * qKnow Knowledge Platform
 *
 * License:
 * Released under the Apache License, Version 2.0.
 *
 * 许可协议：
 * 本项目基于 Apache License 2.0 开源协议发布，
 * 允许在遵守协议的前提下进行商用、修改和分发。
 */

/**
 * 前端请求生命周期管理器 (CancelManager)
 * 采用 W3C DOM AbortController 标准与 Map 结构，实现成对生命周期管理：
 * 1. 自动取消重复未完成的相同请求，防止多次提交与竞争乱序；
 * 2. 在请求完成或失败时从 Map 彻底移除，切断 GC 闭包引用链，消除长时间运行下的内存泄漏；
 * 3. 页面路由切换时支持一键批量取消所有挂起的异步网络请求。
 */
class CancelManager {
  constructor() {
    /** @type {Map<string, AbortController>} */
    this.pendingMap = new Map()
  }

  /**
   * 根据请求方法、URL、参数生成唯一键
   * @param {Object} config Axios 请求配置
   * @returns {string} 唯一请求指纹
   */
  generateReqKey(config) {
    if (!config) return ''
    const method = (config.method || 'get').toLowerCase()
    const url = config.url || ''
    
    // 安全序列化，避免循环引用或特殊对象导致异常
    let paramsStr = ''
    try {
      if (config.params) {
        paramsStr = typeof config.params === 'string' ? config.params : JSON.stringify(config.params)
      }
    } catch (e) {
      paramsStr = String(config.params)
    }

    let dataStr = ''
    try {
      if (config.data && !(config.data instanceof FormData) && !(config.data instanceof Blob)) {
        dataStr = typeof config.data === 'string' ? config.data : JSON.stringify(config.data)
      }
    } catch (e) {
      dataStr = String(config.data)
    }

    return `${method}&${url}&${paramsStr}&${dataStr}`
  }

  /**
   * 添加请求到挂起池并绑定 AbortController
   * @param {Object} config Axios 请求配置
   */
  addPending(config) {
    if (!config) return
    const reqKey = this.generateReqKey(config)
    
    // 如果已有相同的未完成请求，先取消旧请求（后发起者胜出）
    if (this.pendingMap.has(reqKey)) {
      const oldController = this.pendingMap.get(reqKey)
      try {
        oldController.abort('Duplicate request canceled')
      } catch (err) {
        // 忽略中止异常
      }
      this.pendingMap.delete(reqKey)
    }

    // 创建新的 AbortController 并绑定至 config.signal
    const controller = new AbortController()
    config.signal = controller.signal
    this.pendingMap.set(reqKey, controller)
  }

  /**
   * 从挂起池中移除请求（成对释放闭包引用，防止内存泄漏）
   * @param {Object} config Axios 请求配置
   */
  removePending(config) {
    if (!config) return
    const reqKey = this.generateReqKey(config)
    if (this.pendingMap.has(reqKey)) {
      this.pendingMap.delete(reqKey)
    }
  }

  /**
   * 路由切换时一键取消所有挂起的异步网络请求
   */
  clearAllPending() {
    for (const [key, controller] of this.pendingMap.entries()) {
      try {
        controller.abort('Route change: Request canceled')
      } catch (err) {
        // 忽略单个取消异常
      }
    }
    this.pendingMap.clear()
  }

  /**
   * 获取当前挂起请求数量（用于调试与健康监控）
   * @returns {number}
   */
  getPendingCount() {
    return this.pendingMap.size
  }
}

export const cancelManager = new CancelManager()
export default cancelManager
