<template>
  <div class="user-management">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>用户管理</span>
          <div class="header-actions">
            <el-button type="danger" :disabled="!selectedRows.length" @click="handleBatchDelete">批量删除</el-button>
            <el-button type="primary" @click="handleAdd">新增用户</el-button>
          </div>
        </div>
      </template>

      <el-table :data="userList" v-loading="loading" stripe @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="55" />
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="username" label="用户名" />
        <el-table-column prop="displayName" label="显示名" />
        <el-table-column prop="email" label="邮箱" />
        <el-table-column label="角色">
          <template #default="{ row }">
            <el-tag v-for="(r, i) in row.roles" :key="i" size="small" style="margin-right: 4px">{{ r }}</el-tag>
            <span v-if="!row.roles?.length" style="color: #909399">—</span>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" />
        <el-table-column label="操作" width="180">
          <template #default="{ row }">
            <el-button size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button size="small" type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="page"
        v-model:page-size="size"
        :total="total"
        layout="total, prev, pager, next"
        @current-change="fetchUsers"
      />
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="500px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="用户名">
          <el-input v-model="form.username" :disabled="isEdit" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" placeholder="留空则不修改" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="form.email" />
        </el-form-item>
        <el-form-item label="显示名">
          <el-input v-model="form.displayName" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'

const loading = ref(false)
const userList = ref([])
const page = ref(1)
const size = ref(20)
const total = ref(0)
const selectedRows = ref<any[]>([])

const dialogVisible = ref(false)
const dialogTitle = ref('新增用户')
const isEdit = ref(false)
const currentId = ref<number | null>(null)

const form = reactive({
  username: '',
  password: '',
  email: '',
  displayName: '',
})

function handleSelectionChange(val: any[]) {
  selectedRows.value = val
}

async function handleBatchDelete() {
  const ids = selectedRows.value.map((row) => row.id)
  if (!ids.length) return
  try {
    await ElMessageBox.confirm(`确认删除选中的 ${ids.length} 个用户？`, '提示', { type: 'warning' })
    await request.delete('/admin/users/batch', { params: { ids: ids.join(',') } })
    ElMessage.success('批量删除成功')
    selectedRows.value = []
    fetchUsers()
  } catch {
    // cancelled
  }
}

async function fetchUsers() {
  loading.value = true
  try {
    const res: any = await request.get('/admin/users', {
      params: { page: page.value, size: size.value },
    })
    userList.value = res.data.records || []
    total.value = res.data.total || 0
  } finally {
    loading.value = false
  }
}

function handleAdd() {
  isEdit.value = false
  dialogTitle.value = '新增用户'
  currentId.value = null
  form.username = ''
  form.password = ''
  form.email = ''
  form.displayName = ''
  dialogVisible.value = true
}

function handleEdit(row: any) {
  isEdit.value = true
  dialogTitle.value = '编辑用户'
  currentId.value = row.id
  form.username = row.username
  form.password = ''
  form.email = row.email
  form.displayName = row.displayName
  dialogVisible.value = true
}

async function handleSubmit() {
  try {
    if (isEdit.value && currentId.value) {
      await request.put(`/admin/users/${currentId.value}`, form)
      ElMessage.success('更新成功')
    } else {
      await request.post('/admin/users', form)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    fetchUsers()
  } catch (err: any) {
    ElMessage.error(err.message || '操作失败')
  }
}

async function handleDelete(row: any) {
  try {
    await ElMessageBox.confirm('确认删除该用户？', '提示', { type: 'warning' })
    await request.delete(`/admin/users/${row.id}`)
    ElMessage.success('删除成功')
    fetchUsers()
  } catch {
    // cancelled
  }
}

onMounted(fetchUsers)
</script>

<style scoped>
.user-management {
  .card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }
  .header-actions {
    display: flex;
    gap: 10px;
  }
}
</style>
