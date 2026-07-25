<template>
  <div class="git-source-page">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>Git 源配置</span>
          <el-button type="primary" @click="openDialog()">新增 Git 源</el-button>
        </div>
      </template>

      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column prop="name" label="名称" />
        <el-table-column prop="platformType" label="平台">
          <template #default="{ row }">
            {{ row.platformType === 2 ? 'GitHub' : 'GitLab' }}
          </template>
        </el-table-column>
        <el-table-column prop="apiBaseUrl" label="API 地址" show-overflow-tooltip />
        <el-table-column prop="createdAt" label="创建时间" />
        <el-table-column label="操作" width="280">
          <template #default="{ row }">
            <el-button link type="primary" @click="testConnection(row.id)">测试连接</el-button>
            <el-button link type="primary" @click="handleSync(row.id)">同步</el-button>
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
        <el-button type="primary" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, reactive } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
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

const loading = ref(false)
const list = ref<GitSource[]>([])
const dialogVisible = ref(false)
const projectPathsText = ref('')
const form = reactive<Partial<GitSource>>({
  platformType: 1,
})

async function fetchList() {
  loading.value = true
  try {
    const res: any = await request.get('/admin/git-sources', { params: { page: 1, size: 1000 } })
    if (res.code === 200) {
      list.value = res.data.records || res.data
    }
  } catch (e) {
    ElMessage.error('获取列表失败')
  } finally {
    loading.value = false
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
    ElMessage.success('保存成功，项目将按 Cron 周期自动同步')
    dialogVisible.value = false
    fetchList()
  } catch (e) {
    ElMessage.error('保存失败')
  }
}

async function handleSync(id: number) {
  try {
    const res: any = await request.post(`/admin/git-sources/${id}/sync`, null, {
      params: { type: 'full' },
    })
    if (res.code === 200) {
      ElMessage.success(res.data || '同步任务已触发')
    }
  } catch (e) {
    ElMessage.error('同步触发失败')
  }
}

async function handleDelete(id: number) {
  try {
    await ElMessageBox.confirm('确定删除该 Git 源？', '提示', { type: 'warning' })
    await request.delete(`/admin/git-sources/${id}`)
    ElMessage.success('删除成功')
    fetchList()
  } catch (e) {
    // cancel
  }
}

async function testConnection(id: number) {
  try {
    const res: any = await request.post(`/admin/git-sources/${id}/test`)
    if (res.code === 200 && res.data) {
      ElMessage.success('连接成功')
    } else {
      ElMessage.error('连接失败')
    }
  } catch (e) {
    ElMessage.error('连接测试失败')
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
