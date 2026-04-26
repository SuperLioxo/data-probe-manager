<template>
  <div class="page-header">
    <div class="page-header-left">
      <h1 class="page-title">{{ title }}</h1>
      <span v-if="subtitle" class="page-subtitle">{{ subtitle }}</span>
    </div>
    <div class="page-header-right">
      <slot name="actions" />
    </div>
  </div>
  <div v-if="$slots.filters" class="page-filters">
    <slot name="filters" />
  </div>
</template>

<script setup>
defineProps({
  title: { type: String, required: true },
  subtitle: String
})
</script>

<style scoped>
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
  gap: 16px;
}

.page-header-left {
  display: flex;
  align-items: baseline;
  gap: 12px;
}

.page-title {
  font-size: 20px;
  font-weight: 700;
  color: #1f2937;
  margin: 0;
  letter-spacing: -0.01em;
}

.page-subtitle {
  font-size: 13px;
  color: #9ca3af;
}

.page-header-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.page-filters {
  margin-bottom: 16px;
}
</style>
