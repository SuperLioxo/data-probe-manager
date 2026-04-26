<template>
  <div class="stat-card" :class="color">
    <div class="stat-card-icon">
      <el-icon :size="20"><component :is="icon" /></el-icon>
    </div>
    <div class="stat-card-body">
      <div class="stat-card-value">{{ displayValue }}</div>
      <div class="stat-card-label">{{ label }}</div>
    </div>
    <div v-if="trend" class="stat-card-trend" :class="trend.direction">
      <el-icon :size="12"><Top v-if="trend.direction === 'up'" /><Bottom v-else /></el-icon>
      <span>{{ trend.value }}</span>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
const props = defineProps({
  label: String,
  value: [Number, String],
  icon: { type: String, default: 'Coin' },
  color: { type: String, default: 'blue' },
  trend: Object
})

const displayValue = computed(() => {
  if (typeof props.value === 'number') {
    return props.value >= 1000 ? (props.value / 1000).toFixed(1) + 'k' : String(props.value)
  }
  return props.value
})
</script>

<style scoped>
.stat-card {
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 20px;
  display: flex;
  align-items: flex-start;
  gap: 14px;
  position: relative;
  overflow: hidden;
  transition: all 0.15s ease;
  position: relative;
}
.stat-card:hover {
  border-color: #d1d5db;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}
.stat-card::before {
  content: '';
  position: absolute;
  top: 0; left: 0;
  width: 100%;
  height: 2px;
}
.stat-card.blue::before   { background: #3B82F6; }
.stat-card.green::before  { background: #10B981; }
.stat-card.amber::before  { background: #F59E0B; }
.stat-card.red::before    { background: #EF4444; }
.stat-card.violet::before { background: #8B5CF6; }

.stat-card-icon {
  width: 40px;
  height: 40px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.stat-card.blue .stat-card-icon   { background: rgba(59, 130, 246, 0.1); color: #3B82F6; }
.stat-card.green .stat-card-icon  { background: rgba(16, 185, 129, 0.1); color: #10B981; }
.stat-card.amber .stat-card-icon  { background: rgba(245, 158, 11, 0.1); color: #F59E0B; }
.stat-card.red .stat-card-icon    { background: rgba(239, 68, 68, 0.1); color: #EF4444; }
.stat-card.violet .stat-card-icon { background: rgba(139, 92, 246, 0.1); color: #8B5CF6; }

.stat-card-body { flex: 1; min-width: 0; }

.stat-card-value {
  font-family: 'Fira Code', monospace;
  font-size: 28px;
  font-weight: 600;
  line-height: 1.2;
  color: #1f2937;
  letter-spacing: -0.02em;
}

.stat-card-label {
  margin-top: 4px;
  font-size: 11px;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  color: #9ca3af;
}

.stat-card-trend {
  position: absolute;
  top: 20px;
  right: 20px;
  font-size: 12px;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 2px;
}
.stat-card-trend.up   { color: #10B981; }
.stat-card-trend.down { color: #EF4444; }
</style>
