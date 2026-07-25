<template>
  <div class="board-page">
    <div class="filter-bar">
      <el-select v-model="filters.projectId" placeholder="项目" clearable multiple collapse-tags @change="onFilterChange" style="width: 200px">
        <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
      </el-select>
      <el-select v-model="filters.status" placeholder="状态" clearable multiple collapse-tags @change="onFilterChange" style="width: 200px">
        <el-option label="开发中" value="open" />
        <el-option label="测试中" value="testing" />
        <el-option label="可合并" value="ready" />
        <el-option label="冲突" value="conflict" />
        <el-option label="已合并" value="merged" />
        <el-option label="已关闭" value="closed" />
        <el-option label="构建失败" value="failed" />
      </el-select>
      <el-input v-model="filters.author" placeholder="作者" clearable @input="onFilterChange" style="width: 140px" />
      <el-input v-model="filters.branch" placeholder="目标分支" clearable @input="onFilterChange" style="width: 160px" />
      <el-button @click="resetFilters">重置</el-button>
      <el-button type="primary" :icon="Refresh" @click="fetchBoard">刷新</el-button>
    </div>

    <!-- 骨架屏：初始加载时展示 -->
    <div v-if="initialLoading" class="skeleton-board">
      <div v-for="i in 7" :key="i" class="skeleton-column">
        <el-skeleton :rows="3" animated />
      </div>
    </div>

    <div v-else class="kanban-board" v-loading="loading">
      <div
        v-for="col in columns"
        :key="col.key"
        class="kanban-column"
        :class="{ 'drag-over': dragOverColumn === col.key }"
        :style="{ borderTopColor: col.color }"
      >
        <div class="column-header">
          <span class="column-title" :style="{ color: col.color }">{{ col.label }}</span>
          <el-tag size="small" type="info">{{ boardData[col.key]?.length || 0 }}</el-tag>
        </div>
        <div
          class="column-body"
          @dragover.prevent="handleDragOver(col.key)"
          @dragleave="handleDragLeave(col.key)"
          @drop="handleDrop(col.key, $event)"
        >
          <RecycleScroller
            class="scroller"
            :items="boardData[col.key] || []"
            :item-size="120"
            key-field="id"
            v-slot="{ item }"
          >
            <MrCard
              :data="item"
              :readOnly="!canDrag(item)"
              :draggable="canDrag(item)"
              @dragstart="handleDragStart(item, $event)"
              @view="openDetail"
            />
          </RecycleScroller>
          <el-empty v-if="!boardData[col.key]?.length" description="暂无数据" :image-size="60" />
        </div>
      </div>
    </div>

    <el-drawer v-model="detailVisible" title="MR 详情" size="45%" :with-header="true">
      <div v-if="selectedMr" v-loading="detailLoading">
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
import { getColumns, getBoard, getProjects, updateMrStatus, getMrDetail, getMrChanges, getMrComments } from '@/api/board'
import type { CiJob, StatusHistory, ChangeItem, CommentItem } from '@/api/board'
import { useUserStore } from '@/stores/user'
import MrCard from '@/components/MrCard.vue'
import CiStatusIcon from '@/components/CiStatusIcon.vue'

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
  platformStatus?: string
  webUrl?: string
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
const dragOverColumn = ref<string | null>(null)

const currentUser = computed(() => userStore.userInfo)
const userRoles = computed(() => currentUser.value?.roles || [])
const isAdminOrTechlead = computed(() =>
  userRoles.value.includes('admin') || userRoles.value.includes('techlead')
)
const isDeveloper = computed(() => userRoles.value.includes('developer'))
const isReviewer = computed(() => userRoles.value.includes('reviewer'))

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
  dragOverColumn.value = columnKey
}

function handleDragLeave(columnKey: string) {
  if (dragOverColumn.value === columnKey) {
    dragOverColumn.value = null
  }
}

async function handleDrop(targetStatus: string, event: DragEvent) {
  event.preventDefault()
  dragOverColumn.value = null
  const mr = draggingItem.value
  draggingItem.value = null
  if (!mr) return
  if (mr.boardStatus === targetStatus) return

  const dropError = canDrop(mr, targetStatus)
  if (dropError) {
    ElMessage.warning(dropError)
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
    ElMessage.success('状态更新成功')
  } catch (e: any) {
    // 回滚
    const currentList = boardData.value[targetStatus] || []
    const idx = currentList.findIndex((m) => m.id === mr.id)
    if (idx > -1) {
      currentList.splice(idx, 1)
    }
    mr.boardStatus = oldStatus
    sourceList.splice(sourceIdx > -1 ? sourceIdx : 0, 0, mr)
    ElMessage.error(e.message || '状态更新失败')
  }
}

function statusLabel(status: string): string {
  const map: Record<string, string> = {
    open: '开发中',
    testing: '测试中',
    ready: '可合并',
    conflict: '冲突',
    merged: '已合并',
    closed: '已关闭',
    failed: '构建失败',
  }
  return map[status] || status
}

function tagType(status: string): string {
  const map: Record<string, string> = {
    open: 'info',
    testing: 'warning',
    ready: 'success',
    conflict: 'danger',
    merged: 'primary',
    closed: 'info',
    failed: 'danger',
  }
  return map[status] || 'info'
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

      &.drag-over {
        box-shadow: 0 0 0 2px #409eff;
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
