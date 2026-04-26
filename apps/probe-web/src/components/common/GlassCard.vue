<template>
  <div class="glass-card-root" :class="{ hoverable }">
    <div v-if="title" class="glass-card-header">
      <span class="glass-card-title">{{ title }}</span>
      <slot name="actions" />
    </div>
    <slot />
  </div>
</template>

<script setup>
defineProps({
  title: String,
  hoverable: { type: Boolean, default: false }
})
</script>

<style scoped>
.glass-card-root {
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 20px;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}
.glass-card-root.hoverable:hover {
  border-color: #d1d5db;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}
.glass-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.glass-card-title {
  font-size: 14px;
  font-weight: 600;
  color: #1f2937;
}
</style>
