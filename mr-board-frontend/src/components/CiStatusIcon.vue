<template>
  <el-tooltip :content="statusText" placement="top">
    <el-icon :size="size" :class="['ci-icon', 'ci-' + normalizedStatus]">
      <CircleCheck v-if="normalizedStatus === 'success'" />
      <CircleClose v-else-if="normalizedStatus === 'failed'" />
      <Loading v-else-if="normalizedStatus === 'running' || normalizedStatus === 'pending'" />
      <Minus v-else />
    </el-icon>
  </el-tooltip>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { CircleCheck, CircleClose, Loading, Minus } from '@element-plus/icons-vue'

const props = withDefaults(defineProps<{
  status?: string
  size?: number
}>(), {
  status: '',
  size: 16,
})

const normalizedStatus = computed(() => {
  const s = (props.status || '').toLowerCase().trim()
  if (['success', 'passed'].includes(s)) return 'success'
  if (['failed', 'failure', 'error'].includes(s)) return 'failed'
  if (['running', 'in_progress', 'inprogress'].includes(s)) return 'running'
  if (['pending', 'waiting', 'queued', 'created'].includes(s)) return 'pending'
  return 'none'
})

const statusText = computed(() => {
  const map: Record<string, string> = {
    success: 'CI 成功',
    failed: 'CI 失败',
    running: 'CI 进行中',
    pending: 'CI 等待中',
    none: '无 CI',
  }
  return map[normalizedStatus.value] || '未知'
})
</script>

<style scoped lang="scss">
.ci-icon {
  vertical-align: middle;

  &.ci-success { color: #67c23a; }
  &.ci-failed  { color: #f56c6c; }
  &.ci-running { color: #e6a23c; }
  &.ci-pending { color: #909399; }
  &.ci-none    { color: #c0c4cc; }
}
</style>
