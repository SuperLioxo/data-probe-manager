<template>
  <el-table
    v-bind="$attrs"
    :data="data"
    border
    stripe
    class="light-table"
  >
    <slot />
    <template #empty>
      <div class="table-empty">
        <el-icon :size="40" color="#d1d5db"><Document /></el-icon>
        <p>暂无数据</p>
      </div>
    </template>
  </el-table>
  <div v-if="total > pageSize" class="table-pagination">
    <el-pagination
      v-model:current-page="currentPage"
      v-model:page-size="pageSize"
      :total="total"
      layout="total, prev, pager, next"
      background
      @current-change="$emit('page-change', currentPage)"
    />
  </div>
</template>

<script setup>
import { ref } from 'vue'

const props = defineProps({
  data: { type: Array, default: () => [] },
  total: { type: Number, default: 0 },
  pageSize: { type: Number, default: 20 }
})

defineEmits(['page-change'])

const currentPage = ref(1)
</script>

<style scoped>
.table-empty {
  padding: 40px 0;
  text-align: center;
  color: #9ca3af;
}

.table-empty p {
  margin-top: 8px;
  font-size: 13px;
}

.table-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
