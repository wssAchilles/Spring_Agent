/**
 * 全局通用防抖搜索 Composable
 * 遵循 Doherty 400ms 阈值理论，默认采用 300ms 防抖节奏，
 * 避免用户高频击键引发重复重排和后端高并发流量风暴。
 */
import { ref, watch, onBeforeUnmount } from 'vue'

export function useDebounceSearch(initialValue = '', onSearchCallback = null, delay = 300) {
  const searchQuery = ref(initialValue)
  const debouncedQuery = ref(initialValue)
  const isDebouncing = ref(false)
  let timer = null

  const triggerNow = (val) => {
    if (timer) {
      clearTimeout(timer)
      timer = null
    }
    const currentVal = val !== undefined ? val : searchQuery.value
    debouncedQuery.value = currentVal
    isDebouncing.value = false
    if (typeof onSearchCallback === 'function') {
      onSearchCallback(currentVal)
    }
  }

  const resetSearch = () => {
    if (timer) {
      clearTimeout(timer)
      timer = null
    }
    searchQuery.value = ''
    debouncedQuery.value = ''
    isDebouncing.value = false
    if (typeof onSearchCallback === 'function') {
      onSearchCallback('')
    }
  }

  watch(searchQuery, (newVal) => {
    isDebouncing.value = true
    if (timer) {
      clearTimeout(timer)
    }
    timer = setTimeout(() => {
      debouncedQuery.value = newVal
      isDebouncing.value = false
      if (typeof onSearchCallback === 'function') {
        onSearchCallback(newVal)
      }
    }, delay)
  })

  onBeforeUnmount(() => {
    if (timer) {
      clearTimeout(timer)
      timer = null
    }
  })

  return {
    searchQuery,
    debouncedQuery,
    isDebouncing,
    triggerNow,
    resetSearch
  }
}
