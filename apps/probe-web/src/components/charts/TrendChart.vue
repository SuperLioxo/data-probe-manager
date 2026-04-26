<template>
  <div ref="chartRef" :style="{ height: height + 'px', width: '100%' }"></div>
</template>

<script setup>
import { ref, watch, onMounted } from 'vue'
import { useECharts } from '../../composables/useECharts'

const props = defineProps({
  option: { type: Object, required: true },
  height: { type: Number, default: 300 }
})

const chartRef = ref(null)
const { setOption } = useECharts(chartRef)

watch(() => props.option, (newOpt) => {
  if (newOpt) setOption(newOpt, true)
}, { deep: true })

onMounted(() => {
  if (props.option) setOption(props.option)
})
</script>
