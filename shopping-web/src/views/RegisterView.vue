<template>
  <div class="auth-page">
    <el-card class="auth-card" shadow="hover">
      <h2>注册</h2>
      <p class="subtitle">创建账号后即可开始导购咨询</p>
      <el-form :model="form" @submit.prevent="onSubmit">
        <el-form-item label="用户名">
          <el-input v-model="form.username" autocomplete="username" placeholder="3-32位字母数字或下划线" />
        </el-form-item>
        <el-form-item label="昵称">
          <el-input v-model="form.displayName" placeholder="可选" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" show-password autocomplete="new-password" placeholder="至少8位" />
        </el-form-item>
        <el-button type="primary" native-type="submit" :loading="loading" style="width: 100%">
          注册
        </el-button>
      </el-form>
      <div class="footer-link">
        已有账号？
        <router-link to="/login">去登录</router-link>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { register } from '../api/http'

const router = useRouter()
const loading = ref(false)
const form = reactive({ username: '', password: '', displayName: '' })

async function onSubmit() {
  if (!form.username.trim() || !form.password) {
    ElMessage.warning('请填写用户名和密码')
    return
  }
  loading.value = true
  try {
    await register(form.username.trim(), form.password, form.displayName.trim() || undefined)
    ElMessage.success('注册成功')
    await router.replace('/chat')
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '注册失败')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.auth-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
}
.auth-card {
  width: 100%;
  max-width: 420px;
}
h2 {
  margin: 0 0 8px;
}
.subtitle {
  margin: 0 0 24px;
  color: #909399;
}
.footer-link {
  margin-top: 16px;
  text-align: center;
  color: #606266;
}
</style>
