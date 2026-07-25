<template>
  <div class="forbidden-page">
    <el-result status="403" title="403" sub-title="抱歉，您无权访问此页面">
      <template #extra>
        <el-button type="primary" @click="$router.push('/')">返回首页</el-button>
      </template>
    </el-result>
  </div>
</template>

<script setup lang="ts">
</script>

<style scoped>
.forbidden-page {
  padding-top: 100px;
}
</style>
