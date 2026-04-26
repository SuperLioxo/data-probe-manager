import { ref, onMounted, onUnmounted, shallowRef, watch } from 'vue'
import * as echarts from 'echarts'

export function useECharts(chartRef, options = {}) {
  const chartInstance = shallowRef(null)

  const init = () => {
    if (!chartRef.value || chartInstance.value) return
    chartInstance.value = echarts.init(chartRef.value)
    if (options.autoResize !== false) {
      window.addEventListener('resize', handleResize)
    }
  }

  const setOption = (option, notMerge = false) => {
    if (!chartInstance.value) init()
    chartInstance.value?.setOption(option, notMerge)
  }

  const handleResize = () => {
    chartInstance.value?.resize()
  }

  const dispose = () => {
    window.removeEventListener('resize', handleResize)
    chartInstance.value?.dispose()
    chartInstance.value = null
  }

  onMounted(() => {
    if (chartRef.value) init()
  })

  onUnmounted(dispose)

  return { chartInstance, setOption, resize: handleResize, dispose }
}
