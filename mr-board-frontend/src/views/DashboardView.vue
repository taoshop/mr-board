<template>
  <div class="board-page">
    <div class="filter-bar">
      <el-select v-model="filters.projectId" placeholder="项目" clearable @change="fetchBoard" style="width: 180px">
        <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
      </el-select>
      <el-select v-model="filters.status" placeholder="状态" clearable @change="fetchBoard" style="width: 140px">
        <el-option label="全部" value="all" />
        <el-option label="开发中" value="open" />
        <el-option label="测试中" value="testing" />
        <el-option label="可合并" value="ready" />
        <el-option label="冲突" value="conflict" />
        <el-option label="已合并" value="merged" />
        <el-option label="已关闭" value="closed" />
        <el-option label="构建失败" value="failed" />
      </el-select>
      <el-input v-model="filters.author" placeholder="作者" clearable @change="fetchBoard" style="width: 140px" />
      <el-input v-model="filters.branch" placeholder="目标分支" clearable @change="fetchBoard" style="width: 160px" />
      <el-button type="primary" :icon="Refresh" @click="fetchBoard">刷新</el-button>
    </div>

    <div class="kanban-board" v-loading="loading">
      <div v-for="col in columns" :key="col.key" class="kanban-column" :style="{ borderTopColor: col.color }">
        <div class="column-header">
          <span class="column-title" :style="{ color: col.color }">{{ col.label }}</span>
          <el-tag size="small" type="info">{{ boardData[col.key]?.length || 0 }}</el-tag>
        </div>
        <div class="column-body" @dragover.prevent @drop="handleDrop(col.key, $event)">
          <MrCard
            v-for="mr in boardData[col.key] || []"
            :key="mr.id"
            :data="mr"
            draggable="true"
            @dragstart="handleDragStart(mr, $event)"
            @view="openDetail"
          />
          <el-empty v-if="!boardData[col.key]?.length" description="暂无数据" :image-size="60" />
        </div>
      </div>
    </div>

    <el-dialog v-model="detailVisible" title="MR 详情" width="600px">
      <div v-if="selectedMr">
        <p><strong>标题：</strong>{{ selectedMr.title }}</p>
        <p><strong>作者：</strong>{{ selectedMr.authorName }}</p>
        <p><strong>分支：</strong>{{ selectedMr.sourceBranch }} → {{ selectedMr.targetBranch }}</p>
        <p><strong>CI 状态：</strong>{{ selectedMr.ciStatus || '无' }}</p>
        <p><strong>平台状态：</strong>{{ selectedMr.platformStatus }}</p>
        <el-link :href="selectedMr.webUrl" target="_blank" type="primary">在 Git 平台打开</el-link>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, reactive } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'
import MrCard from '@/components/MrCard.vue'

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
  platformStatus?: string
  webUrl?: string
}

const loading = ref(false)
const columns = ref<Column[]>([])
const boardData = ref<Record<string, Mr[]>>({})
const projects = ref<{ id: number; name: string }[]>([])
const filters = reactive({
  projectId: null as number | null,
  status: 'all',
  author: '',
  branch: '',
})

const detailVisible = ref(false)
const selectedMr = ref<Mr | null>(null)

async function fetchColumns() {
  const res = await request.get('/api/board/columns')
  if (res.data.code === 200) {
    columns.value = res.data.data
  }
}

async function fetchProjects() {
  const res = await request.get('/api/projects')
  if (res.data.code === 200) {
    projects.value = res.data.data
  }
}

async function fetchBoard() {
  loading.value = true
  try {
    const params: Record<string, any> = {}
    if (filters.projectId) params.projectId = filters.projectId
    if (filters.status && filters.status !== 'all') params.status = filters.status
    if (filters.author) params.author = filters.author
    if (filters.branch) params.branch = filters.branch
    const res = await request.get('/api/board', { params })
    if (res.data.code === 200) {
      boardData.value = res.data.data
    }
  } catch (e) {
    ElMessage.error('获取看板数据失败')
  } finally {
    loading.value = false
  }
}

function openDetail(mr: Mr) {
  selectedMr.value = mr
  detailVisible.value = true
}

function handleDragStart(mr: Mr, event: DragEvent) {
  event.dataTransfer?.setData('text/plain', JSON.stringify(mr))
}

function handleDrop(targetStatus: string, event: DragEvent) {
  const data = event.dataTransfer?.getData('text/plain')
  if (!data) return
  const mr: Mr = JSON.parse(data)
  if (mr.boardStatus === targetStatus) return
  ElMessage.info(`拖拽 MR #${mr.platformMrId} 到 ${targetStatus}（演示，未调用后端）`)
}

onMounted(() => {
  fetchColumns()
  fetchProjects()
  fetchBoard()
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
      }
    }
  }
}
</style>
