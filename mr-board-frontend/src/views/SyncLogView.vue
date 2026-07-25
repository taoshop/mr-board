<template>
  <div class="sync-log-page">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>同步日志</span>
        </div>
      </template>

      <el-form :inline="true" class="filter-form">
        <el-form-item label="项目">
          <el-select v-model="query.projectId" placeholder="全部" clearable @change="fetchLogs">
            <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable @change="fetchLogs">
            <el-option label="成功" value="success" />
            <el-option label="失败" value="failed" />
            <el-option label="运行中" value="running" />
          </el-select>
        </el-form-item>
      </el-form>

      <el-table :data="logs" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="projectId" label="项目 ID" width="90" />
        <el-table-column prop="syncType" label="同步类型" width="100" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="mrCount" label="MR 数" width="80" />
        <el-table-column prop="ciCount" label="CI 数" width="80" />
        <el-table-column prop="errorMsg" label="错误信息" show-overflow-tooltip />
        <el-table-column prop="createdAt" label="开始时间" />
        <el-table-column prop="finishedAt" label="结束时间" />
      </el-table>

      <el-pagination
        v-model:current-page="query.page"
        v-model:page-size="query.size"
        :total="total"
        layout="total, prev, pager, next"
        @change="fetchLogs"
      />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'

interface SyncLog {
  id: number
  projectId: number
  syncType: string
  status: string
  mrCount: number
  ciCount: number
  errorMsg?: string
  createdAt: string
  finishedAt?: string
}

const loading = ref(false)
const logs = ref<SyncLog[]>([])
const total = ref(0)
const projects = ref<{ id: number; name: string }[]>([])
const query = reactive({
  page: 1,
  size: 20,
  projectId: null as number | null,
  status: '',
})

async function fetchLogs() {
  loading.value = true
  try {
    const params = { ...query }
    const res: any = await request.get('/admin/sync/logs', { params })
    if (res.code === 200) {
      logs.value = res.data.records
      total.value = res.data.total
    }
  } catch (e) {
    ElMessage.error('获取同步日志失败')
  } finally {
    loading.value = false
  }
}

async function fetchProjects() {
  const res: any = await request.get('/projects')
  if (res.code === 200) {
    projects.value = res.data
  }
}

function statusType(status: string) {
  const map: Record<string, string> = {
    success: 'success',
    failed: 'danger',
    running: 'warning',
  }
  return map[status] || 'info'
}

onMounted(() => {
  fetchProjects()
  fetchLogs()
})
</script>

<style scoped lang="scss">
.sync-log-page {
  .card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }

  .filter-form {
    margin-bottom: 16px;
  }
}
</style>
