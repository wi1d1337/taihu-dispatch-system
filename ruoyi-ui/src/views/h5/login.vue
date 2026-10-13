<template>
  <div class="h5-page h5-login">
    <div class="login-box">
      <div class="login-title">太湖蓝藻打捞 · 作业端</div>
      <div class="login-sub">片长 / 打捞员移动工作台</div>

      <div class="field">
        <label>账号</label>
        <input v-model.trim="form.username" type="text" placeholder="请输入账号" autocomplete="username" />
      </div>
      <div class="field">
        <label>密码</label>
        <input v-model="form.password" type="password" placeholder="请输入密码" autocomplete="current-password" />
      </div>

      <button class="btn-primary btn-block" :disabled="logging" @click="handleLogin">
        {{ logging ? '登录中…' : '登 录' }}
      </button>

      <div v-if="errMsg" class="err">{{ errMsg }}</div>
    </div>
  </div>
</template>

<script setup name="H5Login">
import { h5Login } from '@/api/taihu/h5'
import { setToken } from '@/utils/auth'
import { useRouter } from 'vue-router'

const router = useRouter()

const form = reactive({ username: '', password: '' })
const logging = ref(false)
const errMsg = ref('')

function handleLogin() {
  if (!form.username || !form.password) {
    errMsg.value = '请输入账号和密码'
    return
  }
  logging.value = true
  errMsg.value = ''
  h5Login({ username: form.username, password: form.password }).then(res => {
    // 契约：复用若依 login，返回 token；与若依同 key 存 Cookie，后续请求自动带 Bearer
    const data = res.data ?? res
    const token = data.token ?? res.token
    if (!token) {
      errMsg.value = '登录响应缺少 token，请检查后端接口'
      return
    }
    setToken(token)
    router.replace('/h5/orders')
  }).catch(e => {
    errMsg.value = (e && e.msg) ? e.msg : '登录失败，请检查账号密码'
  }).finally(() => { logging.value = false })
}
</script>

<style scoped lang="scss">
.h5-page {
  min-height: 100vh;
  background: #eef3f6;
  -webkit-tap-highlight-color: transparent;
}

.h5-login {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
}

.login-box {
  width: 100%;
  max-width: 380px;
  background: #fff;
  border-radius: 12px;
  padding: 28px 22px 34px;
  box-shadow: 0 4px 16px rgba(31, 56, 88, 0.08);

  .login-title {
    font-size: 20px;
    font-weight: 600;
    color: #17323f;
    text-align: center;
  }

  .login-sub {
    margin: 6px 0 22px;
    font-size: 13px;
    color: #909399;
    text-align: center;
  }

  .field {
    margin-bottom: 14px;

    label {
      display: block;
      font-size: 13px;
      color: #606266;
      margin-bottom: 6px;
    }

    input {
      width: 100%;
      height: 44px;
      padding: 0 12px;
      font-size: 16px;
      border: 1px solid #dcdfe6;
      border-radius: 8px;
      outline: none;
      box-sizing: border-box;

      &:focus { border-color: #1d726c; }
    }
  }

  .err {
    margin-top: 12px;
    font-size: 13px;
    color: #d93025;
    text-align: center;
  }
}

.btn-primary {
  height: 46px;
  border: none;
  border-radius: 8px;
  background: #1d726c;
  color: #fff;
  font-size: 16px;

  &:active { opacity: 0.85; }
  &:disabled { opacity: 0.6; }
}

.btn-block {
  width: 100%;
  margin-top: 8px;
}
</style>
