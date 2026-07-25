<template>
  <div class="profile-page">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>个人设置</span>
        </div>
      </template>

      <el-alert
        v-if="userStore.mustChangePassword"
        title="首次登录必须修改默认密码"
        type="warning"
        :closable="false"
        show-icon
        style="margin-bottom: 20px"
        description="检测到您使用的是默认密码，请修改密码后再使用系统其他功能。"
      />

      <el-form :model="form" label-width="100px" style="max-width: 500px" :rules="formRules" ref="formRef">
        <el-form-item label="用户名">
          <el-input v-model="form.username" disabled />
        </el-form-item>
        <el-form-item label="显示名称">
          <el-input v-model="form.displayName" placeholder="请输入显示名称" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="form.email" placeholder="请输入邮箱" />
        </el-form-item>
        <el-form-item label="部门">
          <el-input v-model="form.department" disabled />
        </el-form-item>
        <el-form-item label="新密码">
          <el-input v-model="form.password" type="password" placeholder="留空则不修改" show-password />
        </el-form-item>
        <el-form-item label="确认密码">
          <el-input v-model="form.confirmPassword" type="password" placeholder="再次输入新密码" show-password />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSubmit" :loading="saving">保存修改</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'
import { useUserStore } from '@/stores/user'
import type { FormInstance, FormRules } from 'element-plus'

const router = useRouter()
const userStore = useUserStore()
const saving = ref(false)
const formRef = ref<FormInstance>()

const form = reactive({
  username: '',
  displayName: '',
  email: '',
  department: '',
  password: '',
  confirmPassword: '',
})

const formRules = computed<FormRules>(() => ({
  password: userStore.mustChangePassword
    ? [
        { required: true, message: '首次登录必须修改密码', trigger: 'blur' },
        { min: 6, message: '密码长度至少6位', trigger: 'blur' },
      ]
    : [],
  confirmPassword: userStore.mustChangePassword
    ? [
        { required: true, message: '请再次输入新密码', trigger: 'blur' },
        {
          validator: (_rule, value, callback) => {
            if (value !== form.password) {
              callback(new Error('两次输入的密码不一致'))
            } else {
              callback()
            }
          },
          trigger: 'blur',
        },
      ]
    : [],
}))

async function fetchProfile() {
  try {
    const res: any = await request.get('/auth/profile')
    if (res.code === 200) {
      const data = res.data
      form.username = data.username || ''
      form.displayName = data.displayName || ''
      form.email = data.email || ''
      form.department = data.department || ''
    }
  } catch (err: any) {
    ElMessage.error(err.message || '获取资料失败')
  }
}

async function handleSubmit() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return

    if (form.password && form.password !== form.confirmPassword) {
      ElMessage.error('两次输入的密码不一致')
      return
    }

    saving.value = true
    try {
      const payload: any = {
        displayName: form.displayName,
        email: form.email,
      }
      if (form.password) {
        payload.password = form.password
      }
      const res: any = await request.put('/auth/profile', payload)
      if (res.code === 200) {
        // 清除强制修改密码标志
        userStore.setMustChangePassword(false)
        ElMessage.success('保存成功')
        form.password = ''
        form.confirmPassword = ''
        // 刷新本地用户信息
        const meRes: any = await request.get('/auth/me')
        if (meRes.code === 200) {
          userStore.setUserInfo(meRes.data)
        }
        // 如果是从强制改密跳转过来的，修改完成后跳转到看板
        router.push('/dashboard')
      } else {
        ElMessage.error(res.message || '保存失败')
      }
    } catch (err: any) {
      ElMessage.error(err.message || '保存失败')
    } finally {
      saving.value = false
    }
  })
}

onMounted(fetchProfile)
</script>

<style scoped>
.profile-page {
  padding: 20px;
}
.card-header {
  font-weight: 600;
}
</style>
