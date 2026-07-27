<template>
  <div class="git-source-page">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>Git 源配置</span>
          <el-button type="primary" @click="openDialog()">新增 Git 源</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="list" stripe>
        <el-table-column prop="name" label="名称" />
        <el-table-column label="平台">
          <template #default="{ row }">
            <el-tag :type="row.platformType === 2 ? '' : 'warning'" size="small">
              {{ row.platformType === 2 ? 'GitHub' : 'GitLab' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="apiBaseUrl" label="API 地址" show-overflow-tooltip />
        <el-table-column label="最近同步">
          <template #default="{ row }">
            <span v-if="syncingMap[row.id]" style="color: #409eff">
              <el-icon class="is-loading"><Loading /></el-icon> 同步中...
            </span>
            <span v-else-if="lastSyncResult[row.id]">
              <el-tag :type="lastSyncResult[row.id].ok ? 'success' : 'danger'" size="small" style="margin-right: 6px">
                {{ lastSyncResult[row.id].ok ? '成功' : '失败' }}
              </el-tag>
              <span v-if="lastSyncResult[row.id].mrCount != null && lastSyncResult[row.id].ok" style="font-size: 12px; color: #909399">
                MR:{{ lastSyncResult[row.id].mrCount }} CI:{{ lastSyncResult[row.id].ciCount }}
              </span>
            </span>
            <span v-else style="color: #c0c4cc">—</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="320">
          <template #default="{ row }">
            <el-button link type="primary" :loading="testingMap[row.id]" @click="testConnection(row.id)">
              {{ testingMap[row.id] ? '测试中...' : '测试连接' }}
            </el-button>
            <el-button link type="primary" :loading="syncingMap[row.id]" @click="handleSync(row.id)">
              {{ syncingMap[row.id] ? '同步中...' : '同步' }}
            </el-button>
            <el-button link type="primary" @click="openDialog(row)">编辑</el-button>
            <el-button link type="danger" @click="handleDelete(row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑 Git 源' : '新增 Git 源'" width="500px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="名称" required>
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="平台类型" required>
          <el-select v-model="form.platformType" style="width: 100%">
            <el-option label="GitLab" :value="1" />
            <el-option label="GitHub" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item label="API 地址" required>
          <el-input v-model="form.apiBaseUrl" placeholder="https://gitlab.example.com/api/v4" />
        </el-form-item>
        <el-form-item label="Access Token" required>
          <el-input v-model="form.accessToken" type="password" show-password placeholder="输入 Token（保存后加密存储）" />
        </el-form-item>
        <el-form-item label="Webhook 密钥">
          <el-input v-model="form.webhookSecret" placeholder="可选" />
        </el-form-item>
        <el-form-item label="项目路径">
          <el-input
            v-model="projectPathsText"
            type="textarea"
            :rows="3"
            placeholder="每行一个项目路径，如 group/project&#10;保存后按 Cron 自动同步 MR"
          />
          <div v-if="form.id" class="form-tip">编辑时仅追加新项目，已关联项目不受影响</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">{{ saving ? '保存中...' : '保存' }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, reactive } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Loading } from '@element-plus/icons-vue'
import request from '@/utils/request'

interface GitSource {
  id: number
  name: string
  platformType: number
  apiBaseUrl: string
  accessToken?: string
  webhookSecret?: string
  createdAt?: string
}

interface SyncResult {
  ok: boolean
  mrCount?: number
  ciCount?: number
}

const loading = ref(false)
const saving = ref(false)
const list = ref<GitSource[]>([])
const dialogVisible = ref(false)
const projectPathsText = ref('')
const testingMap = reactive<Record<number, boolean>>({})
const syncingMap = reactive<Record<number, boolean>>({})
const lastSyncResult = reactive<Record<number, SyncResult>>({})

const form = reactive<Partial<GitSource>>({
  platformType: 1,
})

async function fetchList() {
  loading.value = true
  try {
    const res: any = await request.get('/admin/git-sources', { params: { page: 1, size: 1000 } })
    if (res.code === 200) {
      list.value = res.data.records || res.data
      // 刷新后恢复最近同步结果
      for (const src of list.value) {
        loadLastSyncResult(src.id)
      }
    }
  } catch {
    ElMessage.error('获取列表失败')
  } finally {
    loading.value = false
  }
}

/** 从 API 加载每个 Git 源的最近同步结果（解决刷新后丢失） */
async function loadLastSyncResult(gitSourceId: number) {
  try {
    const res: any = await request.get('/admin/sync/logs', {
      params: { gitSourceId, page: 1, size: 1 },
    })
    if (res.code === 200 && res.data?.records?.length > 0) {
      const latest = res.data.records[0]
      if (latest.status !== 'running') {
        lastSyncResult[gitSourceId] = {
          ok: latest.status === 'success',
          mrCount: latest.mrCount,
          ciCount: latest.ciCount,
        }
      }
    }
  } catch {
    // 静默失败，不影响列表加载
  }
}

function openDialog(row?: GitSource) {
  projectPathsText.value = ''
  if (row) {
    Object.assign(form, { ...row, accessToken: '' })
  } else {
    Object.assign(form, { id: undefined, name: '', platformType: 1, apiBaseUrl: '', accessToken: '', webhookSecret: '' })
  }
  dialogVisible.value = true
}

async function handleSave() {
  if (!form.name || !form.apiBaseUrl || (!form.id && !form.accessToken)) {
    ElMessage.warning('请填写必填项')
    return
  }
  saving.value = true
  try {
    const projectPaths = projectPathsText.value
      .split('\n')
      .map((p) => p.trim())
      .filter(Boolean)
    if (form.id) {
      await request.put(`/admin/git-sources/${form.id}`, { ...form, projectPaths })
    } else {
      await request.post('/admin/git-sources', { ...form, projectPaths })
    }
    ElMessage.success('保存成功，项目将按 Cron 周期自动同步 MR')
    dialogVisible.value = false
    fetchList()
  } catch (err: any) {
    // 拦截器已统一弹错误，此处补充场景提示
    ElMessage.error(err?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function handleSync(id: number) {
  if (syncingMap[id]) return
  syncingMap[id] = true
  delete lastSyncResult[id]
  try {
    const res: any = await request.post(`/admin/git-sources/${id}/sync`, null, { params: { type: 'full' } })
    if (res.code === 200) {
      ElMessage.success(res.data || '同步任务已触发')
      await pollSyncStatus(id)
    }
  } catch (err: any) {
    lastSyncResult[id] = { ok: false }
    ElMessage.error(err?.message || '同步触发失败')
  } finally {
    syncingMap[id] = false
  }
}

async function pollSyncStatus(gitSourceId: number, maxAttempts = 30, intervalMs = 2000) {
  for (let i = 0; i < maxAttempts; i++) {
    await new Promise((r) => setTimeout(r, intervalMs))
    try {
      const res: any = await request.get('/admin/sync/logs', { params: { gitSourceId, page: 1, size: 1 } })
      if (res.code === 200 && res.data?.records?.length > 0) {
        const latest = res.data.records[0]
        if (latest.status !== 'running') {
          if (latest.status === 'success') {
            lastSyncResult[gitSourceId] = { ok: true, mrCount: latest.mrCount, ciCount: latest.ciCount }
            ElMessage.success(`同步完成：处理 MR ${latest.mrCount || 0} 个，CI ${latest.ciCount || 0} 个`)
          } else {
            lastSyncResult[gitSourceId] = { ok: false }
            ElMessage.error(`同步失败：${latest.errorMsg || '未知错误'}`)
          }
          return
        }
      }
    } catch {
      // 轮询失败继续
    }
  }
  lastSyncResult[gitSourceId] = { ok: false }
  ElMessage.warning('同步状态获取超时，请稍后查看同步日志确认结果')
}

async function handleDelete(id: number) {
  try {
    await ElMessageBox.confirm('确定删除该 Git 源？关联的同步日志和历史数据将保留。', '提示', { type: 'warning' })
    await request.delete(`/admin/git-sources/${id}`)
    delete lastSyncResult[id]
    ElMessage.success('Git 源已删除')
    fetchList()
  } catch {
    // cancel
  }
}

async function testConnection(id: number) {
  if (testingMap[id]) return
  testingMap[id] = true
  try {
    const res: any = await request.post(`/admin/git-sources/${id}/test`)
    if (res.code === 200 && res.data) {
      ElMessage.success(typeof res.data === 'string' ? `连接成功 — ${res.data}` : 'Git API 连接正常，Token 有效')
    }
    // 失败时拦截器已弹 message：res.message 来自后端
  } catch (err: any) {
    // 网络错误 / 超时 / 后端 500 — 拦截器已弹 "网络错误" 或后端 message
    // 额外弹详细提示
    const detail = err?.response?.data?.message || err?.message || ''
    if (detail) {
      ElMessage.error(`连接测试失败：${detail}`)
    }
  } finally {
    testingMap[id] = false
  }
}

onMounted(fetchList)
</script>

<style scoped lang="scss">
.git-source-page {
  .card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }

  .form-tip {
    font-size: 12px;
    color: #909399;
    line-height: 1.5;
  }
}
</style>
