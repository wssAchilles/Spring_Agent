/**
 * 工业级聊天视口与流式滚动调度器
 * 1. 无冲突 Scroll Lock 状态机：用户上滑翻阅历史时锁定视口，防止被新 Token 强制拉回；
 * 2. 悬浮单色毛玻璃未读提醒与一键平滑贴底；
 * 3. 基于 requestAnimationFrame (RAF) 16.6ms 帧预算的双缓冲吐字调度器。
 */
import { ref, onMounted, onUnmounted, nextTick } from 'vue'

export function useChatScrollController(containerRef, options = {}) {
  const { lockThreshold = 80, resumeThreshold = 20 } = options

  const isLocked = ref(false)              // 用户是否上滑锁定了视口
  const hasNewMessageBelow = ref(false)    // 是否在底部有未读新内容浮标
  const unreadCount = ref(0)               // 锁定期间累加的新 Token / 片段数

  let isAutoScrolling = false

  /**
   * 原生滚动监听（使用 passive 保证滚动流畅度）
   */
  const handleScroll = () => {
    if (!containerRef.value || isAutoScrolling) return

    const { scrollTop, scrollHeight, clientHeight } = containerRef.value
    const distanceFromBottom = scrollHeight - scrollTop - clientHeight

    if (distanceFromBottom > lockThreshold) {
      if (!isLocked.value) {
        isLocked.value = true
      }
    } else if (distanceFromBottom <= resumeThreshold) {
      if (isLocked.value) {
        isLocked.value = false
        hasNewMessageBelow.value = false
        unreadCount.value = 0
      }
    }
  }

  /**
   * 数据增量更新时触发的智能贴底
   */
  const scrollOnUpdate = async () => {
    await nextTick()
    if (!containerRef.value) return

    if (!isLocked.value) {
      isAutoScrolling = true
      containerRef.value.scrollTop = containerRef.value.scrollHeight
      requestAnimationFrame(() => {
        isAutoScrolling = false
      })
    } else {
      // 锁定状态下不强拉视口，累加未读并提醒用户
      hasNewMessageBelow.value = true
      unreadCount.value += 1
    }
  }

  /**
   * 点击悬浮毛玻璃提示，一键平滑贴底
   */
  const scrollToBottomSmooth = () => {
    if (!containerRef.value) return
    isLocked.value = false
    hasNewMessageBelow.value = false
    unreadCount.value = 0

    containerRef.value.scrollTo({
      top: containerRef.value.scrollHeight,
      behavior: 'smooth'
    })
  }

  onMounted(() => {
    if (containerRef.value) {
      containerRef.value.addEventListener('scroll', handleScroll, { passive: true })
    }
  })

  onUnmounted(() => {
    if (containerRef.value) {
      containerRef.value.removeEventListener('scroll', handleScroll)
    }
  })

  return {
    isLocked,
    hasNewMessageBelow,
    unreadCount,
    scrollOnUpdate,
    scrollToBottomSmooth
  }
}

/**
 * 60fps 弹性缓冲队列与自适应吐字调度器
 */
export function createTokenScheduler(onFrameUpdate) {
  let buffer = ''
  let displayedLength = 0
  let isRunning = false
  let rafId = null

  const tick = () => {
    if (!isRunning) return

    const totalLength = buffer.length
    if (displayedLength < totalLength) {
      const backlog = totalLength - displayedLength
      // 自适应计算本帧吐字量 (1 ~ 15 字符，消除网络 Burst 顿挫)
      const step = Math.max(1, Math.min(15, Math.floor(backlog / 6)))
      displayedLength = Math.min(totalLength, displayedLength + step)

      if (typeof onFrameUpdate === 'function') {
        onFrameUpdate(buffer.substring(0, displayedLength), displayedLength >= totalLength)
      }
    }

    rafId = requestAnimationFrame(tick)
  }

  return {
    append(chunk) {
      buffer += chunk
      if (!isRunning) {
        isRunning = true
        rafId = requestAnimationFrame(tick)
      }
    },
    flush() {
      displayedLength = buffer.length
      if (typeof onFrameUpdate === 'function') {
        onFrameUpdate(buffer, true)
      }
      isRunning = false
      if (rafId) {
        cancelAnimationFrame(rafId)
        rafId = null
      }
    },
    reset() {
      buffer = ''
      displayedLength = 0
      isRunning = false
      if (rafId) {
        cancelAnimationFrame(rafId)
        rafId = null
      }
    }
  }
}
