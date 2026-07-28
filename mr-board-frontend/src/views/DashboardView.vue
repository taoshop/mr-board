<template>
  <div class="board-page">
    <div class="filter-bar">
      <el-select v-model="filters.projectId" placeholder="项目" clearable multiple collapse-tags style="width: 200px" @change="onFilterChange">
        <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
      </el-select>
      <el-select v-model="filters.status" placeholder="状态" clearable multiple collapse-tags style="width: 200px" @change="onFilterChange">
        <el-option label="待 Review" value="pending_review" />
        <el-option label="Review 中" value="reviewing" />
        <el-option label="CI 检查中" value="ci_checking" />
        <el-option label="冲突待解决" value="conflict" />
        <el-option label="可合并" value="ready" />
        <el-option label="已合并" value="merged" />
        <el-option label="已关闭" value="closed" />
      </el-select>
      <el-input v-model="filters.author" placeholder="作者" clearable style="width: 140px" @input="onFilterChange" />
      <el-input v-model="filters.branch" placeholder="目标分支" clearable style="width: 160px" @input="onFilterChange" />
      <el-button @click="resetFilters">重置</el-button>
      <el-button type="primary" :icon="Refresh" @click="fetchBoard">刷新</el-button>
    </div>

    <!-- 骨架屏：初始加载时展示 -->
    <div v-if="initialLoading" class="skeleton-board">
      <div v-for="i in 6" :key="i" class="skeleton-column">
        <el-skeleton :rows="3" animated />
      </div>
    </div>

    <div v-else v-loading="loading" class="kanban-board">
      <div
        v-for="col in columns"
        :key="col.key"
        class="kanban-column"
        :class="[
          dragOverState?.column === col.key
            ? (dragOverState.allowed
                ? (dragOverState.conditional ? 'drag-conditional' : 'drag-allowed')
                : 'drag-forbidden')
            : '',
        ]"
        :style="{ borderTopColor: col.color }"
      >
        <div class="column-header">
          <span class="column-title" :style="{ color: col.color }">{{ col.label }}</span>
          <el-tag size="small" type="info">{{ boardData[col.key]?.length || 0 }}</el-tag>
        </div>
        <div v-if="columnStats[col.key]?.length" class="column-stats">
          <span
            v-for="s in columnStats[col.key]"
            :key="s.label"
            class="stat-item"
            :style="{ color: s.color }"
          >
            {{ s.label }}: {{ s.value }}
          </span>
        </div>
        <div
          class="column-body"
          :title="dragOverState?.column === col.key && dragOverState.reason ? dragOverState.reason : ''"
          @dragover.prevent="handleDragOver(col.key)"
          @dragleave="handleDragLeave(col.key)"
          @drop="handleDrop(col.key, $event)"
        >
          <DynamicScroller
            class="scroller"
            :items="boardData[col.key] || []"
            :min-item-size="160"
            key-field="id"
            :key="scrollerKey"
          >
            <template #default="{ item, index, active }">
              <DynamicScrollerItem
                :item="item"
                :active="active"
                :data-index="index"
              >
                <MrCard
                  :data="item"
                  :read-only="!canDrag(item)"
                  :draggable="canDrag(item)"
                  @dragstart="handleDragStart(item, $event)"
                  @view="openDetail"
                />
              </DynamicScrollerItem>
            </template>
          </DynamicScroller>
          <el-empty v-if="!boardData[col.key]?.length" description="暂无数据" :image-size="60" />
        </div>
      </div>
    </div>

    <el-drawer v-model="detailVisible" title="MR 详情" size="45%" :with-header="true">
      <div v-if="selectedMr" v-loading="detailLoading">
        <!-- 快捷操作栏 -->
        <div class="quick-actions">
          <el-button
            v-for="action in quickActions"
            :key="action.key"
            :type="action.type || ''"
            size="small"
            @click="action.handler"
          >
            {{ action.label }}
          </el-button>
        </div>
        <el-tabs v-model="detailTab">
          <!-- 基本信息 -->
          <el-tab-pane label="基本信息" name="info">
            <el-descriptions :column="1" border>
              <el-descriptions-item label="标题">{{ selectedMr.title }}</el-descriptions-item>
              <el-descriptions-item label="作者">
                <div class="desc-author">
                  <el-avatar :size="24" :src="selectedMr.authorAvatar" />
                  <span>{{ selectedMr.authorName }}</span>
                </div>
              </el-descriptions-item>
              <el-descriptions-item label="源分支">{{ selectedMr.sourceBranch }}</el-descriptions-item>
              <el-descriptions-item label="目标分支">{{ selectedMr.targetBranch }}</el-descriptions-item>
              <el-descriptions-item label="平台状态">{{ selectedMr.platformStatus }}</el-descriptions-item>
              <el-descriptions-item label="看板状态">
                <el-tag :type="tagType(selectedMr.boardStatus)">{{ statusLabel(selectedMr.boardStatus) }}</el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="CI 状态">
                <CiStatusIcon v-if="selectedMr.ciStatus" :status="selectedMr.ciStatus" :size="18" />
                <span v-else>无</span>
              </el-descriptions-item>
              <el-descriptions-item label="链接">
                <el-link :href="selectedMr.webUrl" target="_blank" type="primary">在 Git 平台打开</el-link>
              </el-descriptions-item>
            </el-descriptions>
          </el-tab-pane>

          <!-- CI Stage/Job -->
          <el-tab-pane label="CI 详情" name="ci">
            <el-empty v-if="!ciJobs.length" description="暂无 CI 记录" />
            <el-table v-else :data="ciJobs" size="small">
              <el-table-column prop="stage" label="Stage" width="120" />
              <el-table-column prop="name" label="Job" />
              <el-table-column label="状态" width="100">
                <template #default="{ row }">
                  <CiStatusIcon :status="row.status" :size="16" />
                </template>
              </el-table-column>
              <el-table-column label="日志" width="80">
                <template #default="{ row }">
                  <el-link v-if="row.logUrl" :href="row.logUrl" target="_blank" type="primary">查看</el-link>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>

          <!-- 变更文件 -->
          <el-tab-pane label="变更文件" name="changes">
            <el-empty v-if="!changes.length" description="暂无变更文件" />
            <el-table v-else :data="changes" size="small">
              <el-table-column prop="newPath" label="文件路径" />
              <el-table-column label="状态" width="100">
                <template #default="{ row }">
                  <el-tag :type="changeTagType(row.status)" size="small">{{ changeLabel(row.status) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="additions" label="新增" width="80" />
              <el-table-column prop="deletions" label="删除" width="80" />
            </el-table>
          </el-tab-pane>

          <!-- 状态时间线 -->
          <el-tab-pane label="状态时间线" name="timeline">
            <el-empty v-if="!statusHistory.length" description="暂无状态变更记录" />
            <el-timeline v-else>
              <el-timeline-item
                v-for="h in statusHistory"
                :key="h.id"
                :type="h.toStatus === 'merged' ? 'primary' : h.toStatus === 'closed' ? 'danger' : 'info'"
                :timestamp="h.createdAt"
              >
                <div>
                  <strong>{{ h.operatorName || '系统' }}</strong>
                  将状态从 <el-tag size="small">{{ statusLabel(h.fromStatus || '-') }}</el-tag>
                  改为 <el-tag size="small" :type="tagType(h.toStatus)">{{ statusLabel(h.toStatus) }}</el-tag>
                </div>
              </el-timeline-item>
            </el-timeline>
          </el-tab-pane>

          <!-- 评论 -->
          <el-tab-pane label="评论" name="comments">
            <el-empty v-if="!comments.length" description="暂无评论" />
            <div v-else class="comments-list">
              <div v-for="c in comments" :key="c.id" class="comment-item">
                <div class="comment-header">
                  <el-avatar :size="28" :src="c.authorAvatar" />
                  <span class="comment-author">{{ c.authorName }}</span>
                  <el-tag v-if="c.isSystem" size="small" type="info">系统</el-tag>
                  <span class="comment-time">{{ c.createdAt }}</span>
                </div>
                <div class="comment-body" v-html="c.body"></div>
              </div>
            </div>
          </el-tab-pane>
        </el-tabs>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, reactive, onUnmounted, computed } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, ElNotification } from 'element-plus'
import { getColumns, getBoard, getProjects, updateMrStatus, getMrDetail, getMrChanges, getMrComments, rerunMrCi, assignMrReviewer, remindMrReviewers, reopenMr } from '@/api/board'
import type { CiJob, StatusHistory, ChangeItem, CommentItem } from '@/api/board'
import { useUserStore } from '@/stores/user'
import MrCard from '@/components/MrCard.vue'
import CiStatusIcon from '@/components/CiStatusIcon.vue'
import { getStatusLabel, getStatusTagType } from '@/constants/boardStatus'

interface Column {
  key: string
  label: string
  color: string
}

interface Mr {
  id: number
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
  platformStatus?: string
  webUrl?: string
  reviewers?: string
  approvalStatus?: string
}

interface ProjectOption {
  id: number
  name: string
}

const userStore = useUserStore()
const loading = ref(false)
const initialLoading = ref(true)
const columns = ref<Column[]>([])
const boardData = ref<Record<string, Mr[]>>({})
const projects = ref<ProjectOption[]>([])
const filters = reactive({
  projectId: [] as number[],
  status: [] as string[],
  author: '',
  branch: '',
})

const detailVisible = ref(false)
const detailTab = ref('info')
const detailLoading = ref(false)
const selectedMr = ref<Mr | null>(null)
const ciJobs = ref<CiJob[]>([])
const changes = ref<ChangeItem[]>([])
const statusHistory = ref<StatusHistory[]>([])
const comments = ref<CommentItem[]>([])
let debounceTimer: ReturnType<typeof setTimeout> | null = null
let autoRefreshTimer: ReturnType<typeof setInterval> | null = null
const AUTO_REFRESH_INTERVAL = 60_000

// 拖拽状态
const draggingItem = ref<Mr | null>(null)
interface DragOverState {
  column: string
  allowed: boolean
  conditional?: boolean
  reason?: string
}
const dragOverState = ref<DragOverState | null>(null)

const currentUser = computed(() => userStore.userInfo)
const userRoles = computed(() => currentUser.value?.roles || [])
const isAdminOrTechlead = computed(() =>
  userRoles.value.includes('admin') || userRoles.value.includes('techlead')
)
const isDeveloper = computed(() => userRoles.value.includes('developer'))
const isReviewer = computed(() => userRoles.value.includes('reviewer'))

const columnStats = computed(() => {
  const stats: Record<
    string,
    { label: string; value: number; color?: string }[]
  > = {}
  for (const col of columns.value) {
    const list = boardData.value[col.key] || []
    const items: { label: string; value: number; color?: string }[] = []

    switch (col.key) {
      case 'pending_review': {
        const noReviewer = list.filter((m) => !m.reviewers).length
        if (noReviewer)
          items.push({ label: '未指派', value: noReviewer, color: '#909399' })
        break
      }
      case 'reviewing': {
        const approved = list.filter(
          (m) => m.approvalStatus === 'approved'
        ).length
        const changesReq = list.filter(
          (m) => m.approvalStatus === 'changes_requested'
        ).length
        const pending = list.filter(
          (m) =>
            !m.approvalStatus ||
            m.approvalStatus === 'pending' ||
            m.approvalStatus === 'reviewing'
        ).length
        if (approved)
          items.push({ label: '已通过', value: approved, color: '#67c23a' })
        if (changesReq)
          items.push({ label: '需修改', value: changesReq, color: '#f56c6c' })
        if (pending)
          items.push({ label: '待评审', value: pending, color: '#909399' })
        break
      }
      case 'ci_checking': {
        const ciRunning = list.filter((m) => m.ciStatus === 'running' || m.ciStatus === 'pending').length
        const ciFailed = list.filter((m) => m.ciStatus === 'failed').length
        if (ciRunning)
          items.push({ label: '运行中', value: ciRunning, color: '#e6a23c' })
        if (ciFailed)
          items.push({ label: 'CI失败', value: ciFailed, color: '#f56c6c' })
        break
      }
      case 'conflict': {
        const conflict = list.filter((m) => m.hasConflict).length
        const notMergeable = list.filter(
          (m) =>
            m.mergeable === false &&
            !m.hasConflict
        ).length
        if (conflict)
          items.push({ label: '冲突', value: conflict, color: '#f56c6c' })
        if (notMergeable)
          items.push({ label: '需变基', value: notMergeable, color: '#e6a23c' })
        break
      }
      case 'ready':
        items.push({ label: '可合并', value: list.length, color: '#67c23a' })
        break
      case 'merged':
      case 'closed':
        items.push({ label: '总计', value: list.length, color: col.color })
        break
    }
    stats[col.key] = items
  }
  return stats
})

/** 筛选变化时刷新 DynamicScroller 的 key，递增确保强制重新渲染 */
const scrollerVersion = ref(0)

/** 筛选变化时刷新 DynamicScroller 的 key */
const scrollerKey = computed(() =>
  `board-${filters.author}-${filters.branch}-${filters.projectId.join(',')}-${filters.status.join(',')}-${scrollerVersion.value}`
)

function canDrag(mr: Mr): boolean {
  if (isReviewer.value) return false
  if (isAdminOrTechlead.value) return true
  if (isDeveloper.value) {
    return mr.authorName === currentUser.value?.username
  }
  return true
}

function canDrop(mr: Mr, targetStatus: string): string | null {
  if (mr.hasConflict && (targetStatus === 'ready' || targetStatus === 'merged')) {
    return '当前MR存在冲突，无法拖入该列'
  }
  if ((targetStatus === 'merged' || targetStatus === 'closed') && !isAdminOrTechlead.value) {
    return '无权限将MR设为已合并/已关闭'
  }
  return null
}

async function fetchColumns() {
  const res = await getColumns()
  if (res.code === 200) {
    columns.value = res.data
  }
}

async function fetchProjects() {
  const res = await getProjects()
  if (res.code === 200) {
    projects.value = res.data
  }
}

let previousMyMrStatus = new Map<number, string>()

function extractMyMrs(data: Record<string, Mr[]>): Map<number, string> {
  const map = new Map<number, string>()
  const myName = currentUser.value?.username
  if (!myName) return map
  for (const list of Object.values(data)) {
    for (const mr of list) {
      if (mr.authorName === myName) {
        map.set(mr.id, mr.boardStatus)
      }
    }
  }
  return map
}

function checkStatusChange(newData: Record<string, Mr[]>) {
  const currentMyMrs = extractMyMrs(newData)
  for (const [id, newStatus] of currentMyMrs) {
    const oldStatus = previousMyMrStatus.get(id)
    if (oldStatus && oldStatus !== newStatus) {
      const mr = Object.values(newData).flat().find((m) => m.id === id)
      ElNotification({
        title: 'MR 状态变更',
        message: `MR #${mr?.platformMrId || id} 状态变为「${statusLabel(newStatus)}」`,
        type: 'info',
        duration: 5000,
      })
    }
  }
  previousMyMrStatus = currentMyMrs
}

async function fetchBoard() {
  loading.value = true
  try {
    const params: Record<string, any> = {}
    if (filters.projectId.length) params.projectId = filters.projectId.join(',')
    if (filters.status.length) params.status = filters.status.join(',')
    if (filters.author) params.author = filters.author
    if (filters.branch) params.branch = filters.branch
    const res = await getBoard(params)
    if (res.code === 200) {
      const newData = res.data
      checkStatusChange(newData)
      boardData.value = newData
      // 递增版本号，强制 DynamicScroller 重新渲染
      scrollerVersion.value++
    }
  } catch (e) {
    ElMessage.error('获取看板数据失败')
  } finally {
    loading.value = false
    initialLoading.value = false
  }
}

function onFilterChange() {
  if (debounceTimer) clearTimeout(debounceTimer)
  debounceTimer = setTimeout(() => {
    fetchBoard()
  }, 400)
}

function resetFilters() {
  filters.projectId = []
  filters.status = []
  filters.author = ''
  filters.branch = ''
  fetchBoard()
}

async function openDetail(mr: Mr) {
  selectedMr.value = mr
  detailVisible.value = true
  detailTab.value = 'info'
  detailLoading.value = true
  ciJobs.value = []
  changes.value = []
  statusHistory.value = []
  comments.value = []
  try {
    const [detailRes, changesRes, commentsRes] = await Promise.all([
      getMrDetail(mr.id),
      getMrChanges(mr.id),
      getMrComments(mr.id),
    ])
    if (detailRes.code === 200) {
      const data = detailRes.data
      ciJobs.value = data.ciJobs || []
      statusHistory.value = data.statusHistory || []
    }
    if (changesRes.code === 200) {
      changes.value = changesRes.data || []
    }
    if (commentsRes.code === 200) {
      comments.value = commentsRes.data || []
    }
  } catch (e) {
    ElMessage.error('加载详情失败')
  } finally {
    detailLoading.value = false
  }
}

function handleDragStart(mr: Mr, event: DragEvent) {
  draggingItem.value = mr
  event.dataTransfer?.setData('text/plain', String(mr.id))
  event.dataTransfer!.effectAllowed = 'move'
}

function handleDragOver(columnKey: string) {
  const mr = draggingItem.value
  if (!mr) return
  if (dragOverState.value?.column === columnKey) return

  const error = canDrop(mr, columnKey)
  if (error) {
    dragOverState.value = { column: columnKey, allowed: false, reason: error }
  } else if (columnKey === 'ready') {
    dragOverState.value = { column: columnKey, allowed: true, conditional: true, reason: '需 CI 通过 + Review 通过 + 无冲突方可合并' }
  } else if (columnKey === 'merged') {
    dragOverState.value = { column: columnKey, allowed: true, conditional: true, reason: '此操作将同步到 Git 平台，请二次确认' }
  } else {
    dragOverState.value = { column: columnKey, allowed: true }
  }
}

function handleDragLeave(columnKey: string) {
  if (dragOverState.value?.column === columnKey) {
    dragOverState.value = null
  }
}

async function handleDrop(targetStatus: string, event: DragEvent) {
  event.preventDefault()
  dragOverState.value = null
  const mr = draggingItem.value
  draggingItem.value = null
  if (!mr) return
  if (mr.boardStatus === targetStatus) return

  const dropError = canDrop(mr, targetStatus)
  if (dropError) {
    ElMessage.warning({ message: dropError, duration: 5000 })
    return
  }

  // merged / closed 二次确认
  if (targetStatus === 'merged' || targetStatus === 'closed') {
    try {
      await ElMessageBox.confirm(
        `确定要将 MR #${mr.platformMrId} 标记为「${statusLabel(targetStatus)}」吗？此操作将同步到Git平台。`,
        '二次确认',
        { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }
      )
    } catch {
      return
    }
  }

  const oldStatus = mr.boardStatus
  const sourceList = boardData.value[oldStatus] || []
  const targetList = boardData.value[targetStatus] || []

  // 乐观更新
  const sourceIdx = sourceList.findIndex((m) => m.id === mr.id)
  if (sourceIdx > -1) {
    sourceList.splice(sourceIdx, 1)
  }
  mr.boardStatus = targetStatus
  targetList.unshift(mr)

  try {
    const res = await updateMrStatus(mr.id, targetStatus)
    if (res.code !== 200) {
      throw new Error(res.data.msg || '状态更新失败')
    }
    ElMessage.success({ message: '状态更新成功', duration: 5000 })
  } catch (e: any) {
    // 回滚（错误提示由 request 拦截器统一显示）
    const currentList = boardData.value[targetStatus] || []
    const idx = currentList.findIndex((m) => m.id === mr.id)
    if (idx > -1) {
      currentList.splice(idx, 1)
    }
    mr.boardStatus = oldStatus
    sourceList.splice(sourceIdx > -1 ? sourceIdx : 0, 0, mr)
  }
}

function statusLabel(status: string): string {
  return getStatusLabel(status)
}

function tagType(status: string): string {
  return getStatusTagType(status)
}

function changeLabel(status: string): string {
  const map: Record<string, string> = {
    added: '新增',
    modified: '修改',
    deleted: '删除',
    renamed: '重命名',
  }
  return map[status] || status
}

function changeTagType(status: string): string {
  const map: Record<string, string> = {
    added: 'success',
    modified: 'warning',
    deleted: 'danger',
    renamed: 'info',
  }
  return map[status] || 'info'
}

interface QuickAction {
  key: string
  label: string
  type?: '' | 'primary' | 'success' | 'warning' | 'danger'
  visible: boolean
  handler: () => void
}

const quickActions = computed(() => {
  const mr = selectedMr.value
  if (!mr) return []

  const actions: QuickAction[] = []

  actions.push({
    key: 'merge',
    label: '一键合并',
    type: 'success',
    visible: mr.boardStatus === 'ready' && isAdminOrTechlead.value,
    handler: () => quickMerge(mr),
  })

  actions.push({
    key: 'close',
    label: '关闭 MR',
    type: 'danger',
    visible:
      mr.boardStatus !== 'merged' &&
      mr.boardStatus !== 'closed' &&
      isAdminOrTechlead.value,
    handler: () => quickClose(mr),
  })

  actions.push({
    key: 'rerun-ci',
    label: '重跑 CI',
    type: 'primary',
    visible: mr.boardStatus !== 'merged' && mr.boardStatus !== 'closed',
    handler: () => quickRerunCi(mr),
  })

  actions.push({
    key: 'assign-reviewer',
    label: '指派 Reviewer',
    type: 'primary',
    visible: mr.boardStatus === 'pending_review',
    handler: () => quickAssignReviewer(mr),
  })

  actions.push({
    key: 'remind-reviewer',
    label: '提醒 Reviewer',
    type: 'primary',
    visible: mr.boardStatus === 'reviewing',
    handler: () => quickRemindReviewer(mr),
  })

  actions.push({
    key: 'open-platform',
    label: '在平台打开',
    visible: !!mr.webUrl,
    handler: () => openInPlatform(mr),
  })

  actions.push({
    key: 'reopen',
    label: '重新打开',
    visible: mr.boardStatus === 'closed' && isAdminOrTechlead.value,
    handler: () => quickReopen(mr),
  })

  return actions.filter((a) => a.visible)
})

async function quickMerge(mr: Mr) {
  try {
    await ElMessageBox.confirm(
      `确定要将 MR #${mr.platformMrId} 合并到 ${mr.targetBranch} 吗？`,
      '一键合并',
      { confirmButtonText: '合并', cancelButtonText: '取消', type: 'success' }
    )
    const res = await updateMrStatus(mr.id, 'merged')
    if (res.code === 200) {
      ElMessage.success('合并成功')
      detailVisible.value = false
      fetchBoard()
    }
  } catch {
    /* 取消 */
  }
}

async function quickClose(mr: Mr) {
  try {
    await ElMessageBox.confirm(
      `确定要关闭 MR #${mr.platformMrId} 吗？`,
      '关闭 MR',
      { confirmButtonText: '关闭', cancelButtonText: '取消', type: 'warning' }
    )
    const res = await updateMrStatus(mr.id, 'closed')
    if (res.code === 200) {
      ElMessage.success('关闭成功')
      detailVisible.value = false
      fetchBoard()
    }
  } catch {
    /* 取消 */
  }
}

function openInPlatform(mr: Mr) {
  if (mr.webUrl) window.open(mr.webUrl, '_blank')
}

function quickRerunCi(mr: Mr) {
  ElMessageBox.confirm(`确定要重跑 MR #${mr.platformMrId} 的 CI 吗？`, '重跑 CI', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'info',
  }).then(async () => {
    try {
      const res = await rerunMrCi(mr.id)
      if (res.code === 200) {
        ElMessage.success('CI 重跑指令已发送')
      } else {
        throw new Error(res.msg || '重跑 CI 失败')
      }
    } catch (e: any) {
      ElMessage.error(e.message || '重跑 CI 失败')
    }
  }).catch(() => {})
}

function quickAssignReviewer(mr: Mr) {
  ElMessageBox.prompt('请输入要指派的 Reviewer 用户名（多个用逗号分隔）', '指派 Reviewer', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    inputPattern: /\S+/,
    inputErrorMessage: '用户名不能为空',
  }).then(async ({ value }) => {
    const reviewers = value.split(',').map((s) => s.trim()).filter(Boolean)
    if (reviewers.length === 0) {
      ElMessage.warning('请输入有效的用户名')
      return
    }
    try {
      const res = await assignMrReviewer(mr.id, reviewers)
      if (res.code === 200) {
        ElMessage.success('Reviewer 指派成功')
        fetchBoard()
      } else {
        throw new Error(res.msg || '指派失败')
      }
    } catch (e: any) {
      ElMessage.error(e.message || '指派 Reviewer 失败')
    }
  }).catch(() => {})
}

function quickRemindReviewer(mr: Mr) {
  ElMessageBox.confirm(`确定要提醒 MR #${mr.platformMrId} 的 Reviewer 吗？`, '提醒 Reviewer', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'info',
  }).then(async () => {
    try {
      const res = await remindMrReviewers(mr.id)
      if (res.code === 200) {
        ElMessage.success('提醒已发送')
      } else {
        throw new Error(res.msg || '提醒失败')
      }
    } catch (e: any) {
      ElMessage.error(e.message || '提醒 Reviewer 失败')
    }
  }).catch(() => {})
}

function quickReopen(mr: Mr) {
  ElMessageBox.confirm(`确定要重新打开 MR #${mr.platformMrId} 吗？`, '重新打开 MR', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  }).then(async () => {
    try {
      const res = await reopenMr(mr.id)
      if (res.code === 200) {
        ElMessage.success('MR 已重新打开')
        detailVisible.value = false
        fetchBoard()
      } else {
        throw new Error(res.msg || '重新打开失败')
      }
    } catch (e: any) {
      ElMessage.error(e.message || '重新打开 MR 失败')
    }
  }).catch(() => {})
}

function startAutoRefresh() {
  stopAutoRefresh()
  autoRefreshTimer = setInterval(() => {
    fetchBoard()
  }, AUTO_REFRESH_INTERVAL)
}

function stopAutoRefresh() {
  if (autoRefreshTimer) {
    clearInterval(autoRefreshTimer)
    autoRefreshTimer = null
  }
}

onMounted(() => {
  fetchColumns()
  fetchProjects()
  fetchBoard()
  startAutoRefresh()
})

onUnmounted(() => {
  stopAutoRefresh()
  if (debounceTimer) clearTimeout(debounceTimer)
})
</script>

<style scoped lang="scss">
.board-page {
  .filter-bar {
    display: flex;
    gap: 12px;
    margin-bottom: 16px;
    flex-wrap: wrap;
  }

  .skeleton-board {
    display: flex;
    gap: 12px;
    overflow-x: auto;
    padding-bottom: 8px;

    .skeleton-column {
      flex: 0 0 300px;
      background: #fff;
      border-radius: 6px;
      border-top: 4px solid #e4e7ed;
      padding: 16px;
      max-height: calc(100vh - 200px);
    }
  }

  .kanban-board {
    display: flex;
    gap: 12px;
    overflow-x: auto;
    padding-bottom: 8px;

    .kanban-column {
      flex: 0 0 300px;
      background: #fff;
      border-radius: 6px;
      border-top: 4px solid;
      display: flex;
      flex-direction: column;
      max-height: calc(100vh - 200px);
      transition: box-shadow 0.2s;

      @media screen and (max-width: 1600px) {
        flex: 0 0 260px;
      }

      @media screen and (max-width: 1440px) {
        flex: 0 0 240px;
      }

      @media screen and (max-width: 1280px) {
        flex: 0 0 220px;
      }

      &.drag-allowed {
        box-shadow: 0 0 0 2px #67c23a;
      }

      &.drag-conditional {
        box-shadow: 0 0 0 2px #e6a23c;
      }

      &.drag-forbidden {
        box-shadow: 0 0 0 2px #f56c6c;
        opacity: 0.85;
      }

      .column-header {
        padding: 12px 16px;
        border-bottom: 1px solid #ebeef5;
        display: flex;
        justify-content: space-between;
        align-items: center;

        .column-title {
          font-weight: 600;
          font-size: 14px;
        }
      }

      .column-stats {
        padding: 6px 16px;
        border-bottom: 1px solid #ebeef5;
        display: flex;
        gap: 10px;
        flex-wrap: wrap;

        .stat-item {
          font-size: 11px;
          font-weight: 500;
        }
      }

      .column-body {
        flex: 1;
        overflow-y: auto;
        padding: 12px;

        .scroller {
          height: 100%;
        }
      }
    }
  }

  .quick-actions {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    margin-bottom: 16px;
    padding-bottom: 12px;
    border-bottom: 1px solid #ebeef5;
  }

  .desc-author {
    display: flex;
    align-items: center;
    gap: 8px;
  }
}

.comments-list {
  .comment-item {
    padding: 12px 0;
    border-bottom: 1px solid #ebeef5;

    &:last-child {
      border-bottom: none;
    }

    .comment-header {
      display: flex;
      align-items: center;
      gap: 8px;
      margin-bottom: 8px;

      .comment-author {
        font-weight: 600;
        font-size: 13px;
      }

      .comment-time {
        font-size: 12px;
        color: #909399;
        margin-left: auto;
      }
    }

    .comment-body {
      font-size: 13px;
      line-height: 1.6;
      color: #303133;
      margin-left: 36px;
      word-break: break-word;
    }
  }
}
</style>
