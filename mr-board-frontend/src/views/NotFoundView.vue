<template>
  <div class="not-found">
    <el-result status="404" title="404" sub-title="页面不存在">
      <template #extra>
        <el-button type="primary" @click="$router.push('/')">返回首页</el-button>
      </template>
    </el-result>
  </div>
</template>

<script setup lang="ts">
</script>

<style scoped>
.not-found {
  padding-top: 100px;
}
</style>
