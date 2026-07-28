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
      <!-- ====== 头部：CI 状态 + 状态标签 + MR# ====== -->
      <div class="card-header">
        <div class="status-wrap">
          <span
            v-if="data.ciStatus"
            class="ci-dot"
            :class="'ci-' + data.ciStatus"
            :title="ciTooltip"
          >
            <el-icon v-if="data.ciStatus === 'running'" class="is-loading"><Loading /></el-icon>
            <el-icon v-else-if="data.ciStatus === 'pending'"><Minus /></el-icon>
            <el-icon v-else-if="data.ciStatus === 'failed'"><CircleClose /></el-icon>
            <el-icon v-else-if="data.ciStatus === 'success'"><CircleCheck /></el-icon>
            <el-icon v-else><Minus /></el-icon>
          </span>
          <el-tag size="small" :type="tagType" effect="light">{{ statusLabel }}</el-tag>
        </div>
        <span class="mr-id">#{{ data.platformMrId }}</span>
      </div>

      <!-- ====== Reviewer 状态行 ====== -->
      <div v-if="reviewers.length" class="card-section reviewer-line">
        <div class="reviewer-avatars">
          <div
            v-for="r in reviewers"
            :key="r"
            class="reviewer-chip"
            :class="'chip-' + approvalClass"
            :title="r + ' — ' + approvalLabel"
          >
            <el-avatar :size="22">{{ r.charAt(0).toUpperCase() }}</el-avatar>
            <span class="reviewer-name">{{ r }}</span>
            <span class="reviewer-status-icon">
              <el-icon v-if="data.approvalStatus === 'approved'"><CircleCheck /></el-icon>
              <el-icon v-else-if="data.approvalStatus === 'changes_requested'"><CircleClose /></el-icon>
              <el-icon v-else><Timer /></el-icon>
            </span>
          </div>
        </div>
        <!-- 未指派 Reviewer 提示（pending_review 列） -->
        <span v-if="status === 'pending_review' && !reviewers.length" class="no-reviewer-hint">
          <el-icon><Warning /></el-icon> 未指派 Reviewer
        </span>
      </div>

      <!-- ====== Reviewer 未指派提示（无 reviewer 时） ====== -->
      <div v-if="!reviewers.length && status !== 'merged' && status !== 'closed'" class="card-section reviewer-empty">
        <span class="no-reviewer-hint">
          <el-icon><Warning /></el-icon> 未指派 Reviewer
        </span>
      </div>

      <!-- ====== 伪就绪提示 ====== -->
      <div v-if="pseudoReadyText" class="card-section pseudo-ready">
        <el-icon><Warning /></el-icon>
        <span>{{ pseudoReadyText }}</span>
      </div>

      <!-- ====== 标题 ====== -->
      <div class="card-section title-section">
        <div class="title" :title="data.title">{{ data.title }}</div>
      </div>

      <!-- ====== 阻塞原因标签（conflict 列） ====== -->
      <div v-if="status === 'conflict'" class="card-section">
        <div class="conflict-reasons">
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
      </div>

      <!-- ====== CI 摘要 ====== -->
      <div v-if="ciSummaryText" class="card-section">
        <div class="ci-summary" :class="'ci-summary-' + data.ciStatus">
          <el-icon v-if="data.ciStatus === 'running'" class="is-loading"><Loading /></el-icon>
          <el-icon v-else-if="data.ciStatus === 'pending'"><Minus /></el-icon>
          <el-icon v-else-if="data.ciStatus === 'failed'"><CircleClose /></el-icon>
          <el-icon v-else-if="data.ciStatus === 'success'"><CircleCheck /></el-icon>
          <span>{{ ciSummaryText }}</span>
        </div>
      </div>

      <!-- ====== 作者 + 分支 ====== -->
      <div class="card-section meta-row">
        <div class="author-info">
          <el-avatar :size="16" :src="data.authorAvatar" />
          <span class="author-name">{{ data.authorName }}</span>
        </div>
        <div class="branch-info" :title="data.sourceBranch + ' → ' + data.targetBranch">
          <el-icon><Link /></el-icon>
          <span class="branch-name">{{ data.sourceBranch }}</span>
          <el-icon><ArrowRight /></el-icon>
          <span class="branch-name">{{ data.targetBranch }}</span>
        </div>
      </div>

      <!-- ====== 底栏：统计 + 操作 ====== -->
      <div class="card-footer">
        <div class="stats">
          <span v-if="data.commentsCount" class="stat" title="评论数">
            <el-icon><ChatLineRound /></el-icon>{{ data.commentsCount }}
          </span>
          <span v-if="data.changesCount" class="stat" title="变更文件数">
            <el-icon><Document /></el-icon>{{ data.changesCount }}
          </span>
        </div>
        <el-button v-if="!readOnly" link size="small" class="view-btn" @click.stop="$emit('view', data)">
          查看详情
        </el-button>
      </div>
    </el-card>
  </el-tooltip>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { Link, ArrowRight, ChatLineRound, Document, Loading, Minus, CircleClose, Warning, Lock, CircleCheck, Timer } from '@element-plus/icons-vue'
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

const approvalClass = computed(() => {
  const s = props.data.approvalStatus || 'pending'
  if (s === 'approved') return 'approved'
  if (s === 'changes_requested') return 'changes-requested'
  return 'pending'
})

const approvalLabel = computed(() => {
  const map: Record<string, string> = {
    approved: '已通过',
    changes_requested: '需修改',
    reviewing: '评审中',
    pending: '待评审',
  }
  return map[props.data.approvalStatus || ''] || '待评审'
})

const ciTooltip = computed(() => {
  const map: Record<string, string> = {
    running: 'CI 运行中',
    pending: 'CI 等待中',
    failed: 'CI 失败',
    success: 'CI 通过',
    unknown: 'CI 状态未知',
  }
  return map[props.data.ciStatus || ''] || ''
})

const ciSummaryText = computed(() => {
  const map: Record<string, string> = {
    running: 'CI 运行中',
    pending: 'CI 等待中',
    failed: 'CI 失败，请检查日志',
    success: 'CI 通过',
  }
  return map[props.data.ciStatus || ''] || ''
})

const pseudoReadyText = computed(() => {
  if (status.value !== 'ready') return ''
  const map: Record<string, string> = {
    running: 'CI 仍在运行，暂不可合并',
    pending: 'CI 等待中，暂不可合并',
    failed: 'CI 未通过，暂不可合并',
  }
  return map[props.data.ciStatus || ''] || ''
})

const showConflictTooltip = computed(() => {
  return status.value === 'conflict'
    && props.data.approvalStatus === 'approved'
    && props.data.mergeable === false
})

// click / drag 冲突检测
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

  /* ── 通用区块间距 ── */
  .card-section {
    margin-bottom: 6px;

    &:last-child {
      margin-bottom: 0;
    }
  }

  /* ── 头部 ── */
  .card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 8px;

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

      &.ci-running { color: #e6a23c; }
      &.ci-pending { color: #909399; }
      &.ci-failed  { color: #f56c6c; }
      &.ci-success { color: #67c23a; }
      &.ci-unknown { color: #c0c4cc; }
    }

    .mr-id {
      font-size: 12px;
      color: #909399;
      font-weight: 600;
    }
  }

  /* ── Reviewer 行 ── */
  .reviewer-line {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: 4px;

    .reviewer-avatars {
      display: flex;
      gap: 6px;
      flex-wrap: wrap;
    }

    .reviewer-chip {
      display: inline-flex;
      align-items: center;
      gap: 3px;
      padding: 1px 6px 1px 2px;
      border-radius: 12px;
      border: 1px solid #e4e7ed;
      background: #fafafa;

      .el-avatar {
        font-size: 10px;
        background: #e4e7ed;
        color: #606266;
      }

      .reviewer-name {
        font-size: 11px;
        color: #606266;
        max-width: 60px;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }

      .reviewer-status-icon {
        font-size: 11px;
        display: inline-flex;
        align-items: center;
      }

      &.chip-approved {
        border-color: #b7eb8f;
        background: #f6ffed;
        .reviewer-status-icon { color: #52c41a; }
      }

      &.chip-changes-requested {
        border-color: #ffa39e;
        background: #fff2f0;
        .reviewer-status-icon { color: #ff4d4f; }
      }

      &.chip-pending {
        border-color: #d9d9d9;
        background: #fafafa;
        .reviewer-status-icon { color: #bfbfbf; }
      }
    }
  }

  .reviewer-empty {
    display: flex;
    align-items: center;
  }

  .no-reviewer-hint {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    font-size: 11px;
    color: #909399;
    padding: 2px 8px;
    background: #f5f7fa;
    border-radius: 4px;

    .el-icon {
      font-size: 12px;
      color: #e6a23c;
    }
  }

  /* ── 标题 ── */
  .title-section {
    margin-bottom: 8px;
  }

  .title {
    font-size: 13px;
    font-weight: 500;
    color: #303133;
    overflow: hidden;
    text-overflow: ellipsis;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    line-height: 1.45;
  }

  /* ── 伪就绪提示 ── */
  .pseudo-ready {
    display: flex;
    align-items: center;
    gap: 6px;
    padding: 4px 8px;
    background: #fdf6ec;
    border-radius: 4px;
    font-size: 12px;
    color: #e6a23c;

    .el-icon {
      font-size: 14px;
    }
  }

  /* ── 阻塞原因标签（conflict 列） ── */
  .conflict-reasons {
    display: flex;
    gap: 6px;
    flex-wrap: wrap;
    padding: 6px 8px;
    background: #fdf2f2;
    border-radius: 4px;

    .el-tag {
      display: inline-flex;
      align-items: center;
      gap: 4px;
    }
  }

  /* ── CI 摘要 ── */
  .ci-summary {
    display: flex;
    align-items: center;
    gap: 6px;
    padding: 4px 8px;
    background: #f5f7fa;
    border-radius: 4px;
    font-size: 11px;
    color: #606266;

    .el-icon {
      font-size: 12px;
    }

    &.ci-summary-running {
      background: #fff7e6;
      color: #d46b08;
    }

    &.ci-summary-failed {
      background: #fff2f0;
      color: #cf1322;
    }

    &.ci-summary-success {
      background: #f6ffed;
      color: #389e0d;
    }
  }

  /* ── 作者 + 分支 ── */
  .meta-row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
    padding: 4px 0;
    border-top: 1px solid #f0f0f0;

    .author-info {
      display: flex;
      align-items: center;
      gap: 4px;
      flex-shrink: 0;

      .author-name {
        font-size: 11px;
        color: #606266;
        font-weight: 500;
      }
    }

    .branch-info {
      display: flex;
      align-items: center;
      gap: 3px;
      font-size: 11px;
      color: #909399;
      overflow: hidden;
      min-width: 0;

      .el-icon {
        font-size: 11px;
        flex-shrink: 0;
      }

      .branch-name {
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
        max-width: 70px;
      }
    }
  }

  /* ── 底栏 ── */
  .card-footer {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding-top: 4px;
    border-top: 1px solid #f0f0f0;

    .stats {
      display: flex;
      gap: 12px;

      .stat {
        display: flex;
        align-items: center;
        gap: 3px;
        font-size: 11px;
        color: #bfbfbf;

        .el-icon {
          font-size: 12px;
        }
      }
    }

    .view-btn {
      font-size: 11px;
    }
  }
}
</style>
