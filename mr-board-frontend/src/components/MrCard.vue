<template>
  <el-tooltip
    :disabled="!showConflictTooltip"
    content="已批准但不可合并：可能是分支保护规则或需要变基"
    placement="top"
  >
    <el-card
      class="mr-card"
      :class="['status-' + status, { 'is-conflict': data.hasConflict, 'is-readonly': readOnly }]"
      shadow="hover"
      :body-style="{ padding: '10px 12px' }"
      :draggable="draggable"
      @dragstart="$emit('dragstart', $event)"
      @mousedown="onMouseDown"
      @click="handleCardClick"
    >
      <div class="card-header">
        <div class="status-wrap">
          <!-- CI 状态小圆点（头部标识） -->
          <span
            v-if="data.ciStatus === 'running' || data.ciStatus === 'pending' || data.ciStatus === 'failed'"
            class="ci-dot"
            :class="'ci-' + data.ciStatus"
            :title="ciTooltip"
          >
            <el-icon v-if="data.ciStatus === 'running'" class="is-loading"><Loading /></el-icon>
            <el-icon v-else-if="data.ciStatus === 'pending'"><Minus /></el-icon>
            <el-icon v-else-if="data.ciStatus === 'failed'"><CircleClose /></el-icon>
          </span>
          <el-tag size="small" :type="tagType" effect="light">{{ statusLabel }}</el-tag>
        </div>
        <span class="mr-id">#{{ data.platformMrId }}</span>
      </div>
      <div class="title" :title="data.title">{{ data.title }}</div>
      <div class="conflict-reasons" v-if="status === 'conflict'">
        <el-tag v-if="data.hasConflict" size="small" type="danger" effect="dark">
          <el-icon><Warning /></el-icon> 代码冲突
        </el-tag>
        <el-tag v-else-if="data.ciStatus === 'failed'" size="small" type="danger" effect="dark">
          <el-icon><CircleClose /></el-icon> CI 失败
        </el-tag>
        <el-tag v-else-if="data.mergeable === false" size="small" type="warning" effect="dark">
          <el-icon><Lock /></el-icon> 需变基/保护规则
        </el-tag>
      </div>
      <div class="meta">
        <div class="author">
          <el-avatar :size="18" :src="data.authorAvatar" />
          <span class="name">{{ data.authorName }}</span>
          <span v-if="reviewers.length" class="reviewers">
            <el-icon><User /></el-icon>
            <span class="reviewer-names">{{ reviewers.join(', ') }}</span>
          </span>
        </div>
        <div class="branch" :title="data.sourceBranch + ' → ' + data.targetBranch">
          <el-icon><Link /></el-icon>
          <span class="branch-name">{{ data.sourceBranch }}</span>
          <el-icon><ArrowRight /></el-icon>
          <span class="branch-name">{{ data.targetBranch }}</span>
        </div>
      </div>
      <div class="footer">
        <div class="stats">
          <span v-if="data.commentsCount" class="stat">
            <el-icon><ChatLineRound /></el-icon>{{ data.commentsCount }}
          </span>
          <span v-if="data.changesCount" class="stat">
            <el-icon><Document /></el-icon>{{ data.changesCount }}
          </span>
        </div>
        <div class="ci" v-if="data.ciStatus">
          <CiStatusIcon :status="data.ciStatus" :size="14" />
        </div>
      </div>
      <div class="actions" v-if="!readOnly">
        <el-button link size="small" @click.stop="$emit('view', data)">查看详情</el-button>
      </div>
    </el-card>
  </el-tooltip>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { Link, ArrowRight, ChatLineRound, Document, User, Loading, Minus, CircleClose, Warning, Lock } from '@element-plus/icons-vue'
import CiStatusIcon from './CiStatusIcon.vue'
import { getStatusLabel, getStatusTagType } from '@/constants/boardStatus'

interface MrData {
  id?: number
  platformMrId: number
  title: string
  authorName: string
  authorAvatar?: string
  sourceBranch: string
  targetBranch: string
  boardStatus: string
  ciStatus?: string
  commentsCount?: number
  changesCount?: number
  hasConflict?: boolean
  mergeable?: boolean
  webUrl?: string
  reviewers?: string
  approvalStatus?: string
}

const props = defineProps<{
  data: MrData
  readOnly?: boolean
  draggable?: boolean
}>()

const emit = defineEmits(['view', 'dragstart'])

const status = computed(() => props.data.boardStatus || 'pending_review')
const statusLabel = computed(() => getStatusLabel(status.value))
const tagType = computed(() => getStatusTagType(status.value))

const reviewers = computed(() => {
  if (!props.data.reviewers) return []
  return props.data.reviewers.split(',').filter(Boolean)
})

const ciTooltip = computed(() => {
  const map: Record<string, string> = {
    running: 'CI 运行中',
    pending: 'CI 等待中',
    failed: 'CI 失败',
  }
  return map[props.data.ciStatus || ''] || ''
})

const showConflictTooltip = computed(() => {
  return status.value === 'conflict'
    && props.data.approvalStatus === 'approved'
    && props.data.mergeable === false
})

// click / drag 冲突检测：mousedown 与 click 时鼠标位移超过阈值则认为是拖拽，不触发点击
const DRAG_THRESHOLD = 5
const mouseDownPos = ref<{ x: number; y: number } | null>(null)

function onMouseDown(e: MouseEvent) {
  mouseDownPos.value = { x: e.clientX, y: e.clientY }
}

function handleCardClick(e: MouseEvent) {
  if (mouseDownPos.value) {
    const dx = Math.abs(e.clientX - mouseDownPos.value.x)
    const dy = Math.abs(e.clientY - mouseDownPos.value.y)
    if (dx > DRAG_THRESHOLD || dy > DRAG_THRESHOLD) {
      // 拖拽过程中，阻止 click 打开详情
      return
    }
  }
  emit('view', props.data)
}
</script>

<style scoped lang="scss">
.mr-card {
  margin-bottom: 8px;
  cursor: grab;
  border-left: 4px solid transparent;
  transition: transform 0.2s;
  position: relative;

  &:hover {
    transform: translateY(-2px);
  }

  &.is-conflict {
    border: 1px solid #f56c6c;
    border-left: 4px solid #f56c6c;

    &::before {
      content: '';
      position: absolute;
      top: 0;
      left: 0;
      right: 0;
      height: 4px;
      background: #f56c6c;
      border-radius: 4px 4px 0 0;
    }
  }

  &.is-readonly {
    opacity: 0.7;
    cursor: not-allowed;
  }

  &.status-pending_review { border-left-color: #909399; }
  &.status-reviewing { border-left-color: #e6a23c; }
  &.status-conflict { border-left-color: #f56c6c; }
  &.status-ready { border-left-color: #67c23a; }
  &.status-merged { border-left-color: #409eff; }
  &.status-closed { border-left-color: #909399; }

  .card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 6px;

    .status-wrap {
      display: flex;
      align-items: center;
      gap: 6px;
    }

    .ci-dot {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 14px;
      height: 14px;
      font-size: 12px;

      &.ci-running {
        color: #e6a23c;
      }

      &.ci-pending {
        color: #909399;
      }

      &.ci-failed {
        color: #f56c6c;
      }
    }

    .mr-id {
      font-size: 12px;
      color: #909399;
    }
  }

  .title {
    font-size: 13px;
    font-weight: 500;
    color: #303133;
    margin-bottom: 8px;
    overflow: hidden;
    text-overflow: ellipsis;
    display: -webkit-box;
    -webkit-line-clamp: 3;
    -webkit-box-orient: vertical;
    line-height: 1.4;
  }

  .conflict-reasons {
    display: flex;
    gap: 6px;
    flex-wrap: wrap;
    margin-bottom: 8px;
    padding: 6px 8px;
    background: #fdf2f2;
    border-radius: 4px;

    .el-tag {
      display: inline-flex;
      align-items: center;
      gap: 4px;
    }
  }

  .meta {
    margin-bottom: 6px;

    .author {
      display: flex;
      align-items: center;
      gap: 6px;
      margin-bottom: 4px;

      .name {
        font-size: 12px;
        color: #606266;
      }

      .reviewers {
        display: flex;
        align-items: center;
        gap: 4px;
        font-size: 11px;
        color: #409eff;
        margin-left: auto;
        background: #f0f9ff;
        padding: 1px 6px;
        border-radius: 4px;
        max-width: 120px;

        .reviewer-names {
          overflow: hidden;
          text-overflow: ellipsis;
          white-space: nowrap;
        }
      }
    }

    .branch {
      display: flex;
      align-items: center;
      gap: 4px;
      font-size: 11px;
      color: #909399;
      overflow: hidden;

      .branch-name {
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
        max-width: 80px;
      }
    }
  }

  .footer {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 4px;

    .stats {
      display: flex;
      gap: 10px;

      .stat {
        display: flex;
        align-items: center;
        gap: 4px;
        font-size: 12px;
        color: #909399;
      }
    }
  }

  .actions {
    display: flex;
    justify-content: flex-end;
  }
}
</style>
