<template>
  <div class="realtime-chart-container">
    <div class="chart-header">
      <div class="chart-title">
        <el-icon><TrendCharts /></el-icon>
        <span>{{ title }}</span>
      </div>
      <div class="chart-controls">
        <el-radio-group v-model="timeRange" size="small" @change="handleTimeRangeChange">
          <el-radio-button value="1m">1分钟</el-radio-button>
          <el-radio-button value="5m">5分钟</el-radio-button>
          <el-radio-button value="15m">15分钟</el-radio-button>
        </el-radio-group>
        <el-button size="small" :icon="isPaused ? VideoPlay : VideoPause" @click="togglePause">
          {{ isPaused ? '继续' : '暂停' }}
        </el-button>
      </div>
    </div>
    <div ref="chartRef" class="chart-content" :style="{ height: height }"></div>
    <div class="chart-legend">
      <div
        v-for="item in legendItems"
        :key="item.name"
        class="legend-item"
      >
        <span class="legend-color" :style="{ background: item.color }"></span>
        <span class="legend-label">{{ item.name }}</span>
        <span class="legend-value">{{ item.value }}</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, watch, nextTick } from 'vue'
import * as echarts from 'echarts'

const props = defineProps({
  title: {
    type: String,
    default: '实时监控'
  },
  height: {
    type: String,
    default: '350px'
  },
  probeId: {
    type: Number,
    default: null
  },
  metric: {
    type: String,
    default: 'cpu'
  },
  realtimeValue: {
    type: Number,
    default: undefined
  }
})

const emit = defineEmits(['data-update'])

const chartRef = ref()
const timeRange = ref('5m')
const isPaused = ref(false)
let chart = null
let updateTimer = null

const legendItems = ref([
  { name: '当前值', value: '-', color: '#409eff' },
  { name: '平均值', value: '-', color: '#67c23a' },
  { name: '最大值', value: '-', color: '#f56c6c' }
])

// 初始化图表
const initChart = () => {
  if (!chartRef.value) return

  chart = echarts.init(chartRef.value)

  const option = {
    tooltip: {
      trigger: 'axis',
      formatter: (params) => {
        let result = params[0].axisValueLabel + '<br/>'
        params.forEach(item => {
          result += `${item.marker} ${item.seriesName}: ${item.value.toFixed(2)}%<br/>`
        })
        return result
      }
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '3%',
      top: '3%',
      containLabel: true
    },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: [],
      axisLine: {
        lineStyle: { color: '#e8e8e8' }
      },
      axisLabel: {
        color: '#909399',
        fontSize: 11
      }
    },
    yAxis: {
      type: 'value',
      name: `${props.metric.toUpperCase()}(%)`,
      nameTextStyle: {
        color: '#909399',
        fontSize: 12
      },
      axisLine: {
        lineStyle: { color: '#e8e8e8' }
      },
      axisLabel: {
        color: '#909399',
        formatter: '{value}%'
      },
      splitLine: {
        lineStyle: { color: '#f0f0f0', type: 'dashed' }
      }
    },
    series: [
      {
        name: props.metric.toUpperCase(),
        type: 'line',
        smooth: true,
        symbol: 'none',
        sampling: 'lttb',
        lineStyle: {
          width: 2,
          color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [
            { offset: 0, color: '#409eff' },
            { offset: 1, color: '#67c23a' }
          ])
        },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(64, 158, 255, 0.3)' },
            { offset: 1, color: 'rgba(64, 158, 255, 0.05)' }
          ])
        },
        data: []
      }
    ],
    animation: true,
    animationDuration: 300
  }

  chart.setOption(option)

  // 响应式调整
  window.addEventListener('resize', handleResize)
}

const handleResize = () => {
  if (chart) {
    chart.resize()
  }
}

// 模拟实时数据更新
const updateData = () => {
  if (isPaused.value || !chart) return

  const option = chart.getOption()
  const xAxisData = option.xAxis[0].data
  const seriesData = option.series[0].data

  // 生成新的数据点
  const now = new Date()
  const timeLabel = now.toLocaleTimeString('zh-CN', { hour12: false })

  // 使用父组件传入的真实值，如果没有则使用上一个值或默认值
  let newValue
  if (props.realtimeValue !== undefined) {
    newValue = props.realtimeValue
  } else if (seriesData.length > 0) {
    // 没有新数据时，保持上一个值
    newValue = seriesData[seriesData.length - 1]
  } else {
    // 初始默认值
    newValue = 0
  }

  // 保持数据点数量
  const maxPoints = timeRange.value === '1m' ? 60 : timeRange.value === '5m' ? 300 : 900

  if (xAxisData.length >= maxPoints) {
    xAxisData.shift()
    seriesData.shift()
  }

  xAxisData.push(timeLabel)
  seriesData.push(newValue)

  // 更新图表
  chart.setOption({
    xAxis: { data: xAxisData },
    series: [{ data: seriesData }]
  })

  // 更新图例统计
  const current = seriesData[seriesData.length - 1]
  const avg = seriesData.reduce((a, b) => a + b, 0) / seriesData.length
  const max = Math.max(...seriesData)

  legendItems.value = [
    { name: '当前值', value: current.toFixed(1) + '%', color: '#409eff' },
    { name: '平均值', value: avg.toFixed(1) + '%', color: '#67c23a' },
    { name: '最大值', value: max.toFixed(1) + '%', color: '#f56c6c' }
  ]

  // 触发数据更新事件
  emit('data-update', {
    metric: props.metric,
    current: newValue,
    avg,
    max
  })
}

const handleTimeRangeChange = () => {
  // 清空并重新开始
  if (chart) {
    chart.setOption({
      xAxis: { data: [] },
      series: [{ data: [] }]
    })
  }
}

const togglePause = () => {
  isPaused.value = !isPaused
}

onMounted(() => {
  nextTick(() => {
    initChart()
    // 每秒更新一次数据
    updateTimer = setInterval(updateData, 1000)
  })
})

onUnmounted(() => {
  if (updateTimer) {
    clearInterval(updateTimer)
  }
  if (chart) {
    chart.dispose()
    chart = null
  }
  window.removeEventListener('resize', handleResize)
})

watch(() => props.metric, () => {
  if (chart) {
    chart.setOption({
      yAxis: {
        name: `${props.metric.toUpperCase()}(%)`
      },
      series: [{
        name: props.metric.toUpperCase()
      }]
    })
  }
})
</script>

<style scoped lang="scss">
.realtime-chart-container {
  background: #fff;
  border-radius: 8px;
  padding: 20px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);

  .chart-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 16px;

    .chart-title {
      display: flex;
      align-items: center;
      font-size: 16px;
      font-weight: 600;
      color: #303133;

      .el-icon {
        font-size: 20px;
        margin-right: 8px;
        color: #409eff;
      }
    }

    .chart-controls {
      display: flex;
      gap: 12px;
      align-items: center;
    }
  }

  .chart-content {
    margin-bottom: 16px;
  }

  .chart-legend {
    display: flex;
    gap: 24px;
    padding-top: 12px;
    border-top: 1px solid #ebeef5;

    .legend-item {
      display: flex;
      align-items: center;
      gap: 8px;

      .legend-color {
        width: 12px;
        height: 12px;
        border-radius: 2px;
      }

      .legend-label {
        font-size: 13px;
        color: #606266;
      }

      .legend-value {
        font-size: 14px;
        font-weight: 600;
        color: #303133;
        font-family: 'Courier New', monospace;
      }
    }
  }
}
</style>
