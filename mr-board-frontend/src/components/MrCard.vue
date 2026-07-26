<template>
  <el-card
    class="mr-card"
    :class="['status-' + status, { 'is-conflict': data.hasConflict, 'is-readonly': readOnly }]"
    shadow="hover"
    :body-style="{ padding: '12px' }"
  >
    <div class="card-header">
      <el-tag size="small" :type="tagType" effect="light">{{ statusLabel }}</el-tag>
      <span class="mr-id">#{{ data.platformMrId }}</span>
    </div>
    <div class="title" :title="data.title">{{ data.title }}</div>
    <div class="meta">
      <div class="author">
        <el-avatar :size="20" :src="data.authorAvatar" />
        <span class="name">{{ data.authorName }}</span>
      </div>
      <div class="branch" :title="data.sourceBranch + ' → ' + data.targetBranch">
        <el-icon><Link /></el-icon>
        <span>{{ data.sourceBranch }}</span>
        <el-icon><ArrowRight /></el-icon>
        <span>{{ data.targetBranch }}</span>
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
        <CiStatusIcon :status="data.ciStatus" :size="16" />
      </div>
    </div>
    <div class="actions" v-if="!readOnly">
      <el-button link size="small" @click="$emit('view', data)">查看</el-button>
    </div>
  </el-card>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Link, ArrowRight, ChatLineRound, Document } from '@element-plus/icons-vue'
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
  webUrl?: string
}

const props = defineProps<{
  data: MrData
  readOnly?: boolean
}>()

defineEmits(['view'])

const status = computed(() => props.data.boardStatus || 'pending_review')

const statusLabel = computed(() => getStatusLabel(status.value))

const tagType = computed(() => getStatusTagType(status.value))
</script>

<style scoped lang="scss">
.mr-card {
  margin-bottom: 10px;
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
  &.status-ci_checking { border-left-color: #409eff; }
  &.status-conflict { border-left-color: #f56c6c; }
  &.status-ready { border-left-color: #67c23a; }
  &.status-merged { border-left-color: #409eff; }
  &.status-closed { border-left-color: #909399; }

  .card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 8px;

    .mr-id {
      font-size: 12px;
      color: #909399;
    }
  }

  .title {
    font-size: 14px;
    font-weight: 500;
    color: #303133;
    margin-bottom: 10px;
    overflow: hidden;
    text-overflow: ellipsis;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
  }

  .meta {
    margin-bottom: 8px;

    .author {
      display: flex;
      align-items: center;
      gap: 6px;
      margin-bottom: 6px;

      .name {
        font-size: 12px;
        color: #606266;
      }
    }

    .branch {
      display: flex;
      align-items: center;
      gap: 4px;
      font-size: 12px;
      color: #909399;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
  }

  .footer {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 6px;

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
