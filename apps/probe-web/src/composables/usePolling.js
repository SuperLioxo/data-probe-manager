import { ref, onMounted, onUnmounted } from 'vue'

export function usePolling(fetchFn, intervalMs = 30000, options = {}) {
  const data = ref(null)
  const loading = ref(false)
  const error = ref(null)
  let timer = null

  const refresh = async () => {
    loading.value = true
    error.value = null
    try {
      data.value = await fetchFn()
    } catch (e) {
      error.value = e
    } finally {
      loading.value = false
    }
  }

  const start = () => {
    if (options.immediate !== false) refresh()
    timer = setInterval(refresh, intervalMs)
  }

  const stop = () => {
    if (timer) { clearInterval(timer); timer = null }
  }

  onMounted(start)
  onUnmounted(stop)

  return { data, loading, error, start, stop, refresh }
}
