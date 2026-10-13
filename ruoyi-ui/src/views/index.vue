<template>
  <div class="app-container home">
    <div class="welcome-card">
      <h1 class="welcome-title">太湖蓝藻水华预报与打捞调度系统</h1>
      <p class="welcome-subtitle">面向太湖蓝藻监测、水华预报与打捞调度的一体化管理平台</p>
      <p class="welcome-date">今天是 {{ today }}</p>
    </div>
  </div>
</template>

<script setup name="Index">
const now = new Date()
const today = `${now.getFullYear()} 年 ${now.getMonth() + 1} 月 ${now.getDate()} 日`
</script>

<style lang="scss" scoped>
.home {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: calc(100vh - 84px);
}

.welcome-card {
  text-align: center;
  padding: 60px 40px;

  .welcome-title {
    font-size: 30px;
    font-weight: 600;
    color: #1b4f72;
    margin: 0 0 16px;
  }

  .welcome-subtitle {
    font-size: 16px;
    color: #606266;
    margin: 0 0 12px;
  }

  .welcome-date {
    font-size: 14px;
    color: #909399;
    margin: 0;
  }
}
</style>
