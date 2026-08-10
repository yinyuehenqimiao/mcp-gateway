<template>
  <el-container class="layout">
    <el-aside width="232px" class="aside">
      <div class="brand">
        <div class="brand-mark">MG</div>
        <div>
          <div class="brand-title">MCP Gateway</div>
          <div class="brand-sub">API → MCP Tools</div>
        </div>
      </div>

      <el-menu
        :default-active="active"
        router
        background-color="transparent"
        text-color="#d1d5db"
        active-text-color="#5eead4"
        class="menu"
      >
        <el-menu-item index="/dashboard">
          <el-icon><Odometer /></el-icon>
          <span>总览</span>
        </el-menu-item>
        <el-menu-item index="/systems">
          <el-icon><Monitor /></el-icon>
          <span>业务系统</span>
        </el-menu-item>
        <el-menu-item index="/apis">
          <el-icon><Connection /></el-icon>
          <span>接口管理</span>
        </el-menu-item>
        <el-menu-item index="/mcp-servers">
          <el-icon><SetUp /></el-icon>
          <span>MCP 发布</span>
        </el-menu-item>
        <el-menu-item index="/tools">
          <el-icon><Tools /></el-icon>
          <span>已发布工具</span>
        </el-menu-item>
        <el-menu-item index="/audits">
          <el-icon><Document /></el-icon>
          <span>调用审计</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="header">
        <div class="header-title">{{ title }}</div>
        <div class="header-meta mono">按 slug 接入: /mcp/&#123;slug&#125;/sse</div>
      </el-header>
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const active = computed(() => route.path)
const title = computed(() => (route.meta.title as string) || 'MCP Gateway')
</script>

<style scoped>
.layout {
  min-height: 100vh;
}

.aside {
  background: linear-gradient(180deg, #0b1220 0%, #111827 100%);
  border-right: 1px solid #1f2937;
  padding: 18px 12px;
}

.brand {
  display: flex;
  gap: 12px;
  align-items: center;
  padding: 8px 10px 22px;
}

.brand-mark {
  width: 40px;
  height: 40px;
  border-radius: 12px;
  display: grid;
  place-items: center;
  font-weight: 700;
  color: #042f2e;
  background: linear-gradient(135deg, #5eead4, #14b8a6);
}

.brand-title {
  color: #f9fafb;
  font-weight: 700;
  font-size: 15px;
}

.brand-sub {
  color: #9ca3af;
  font-size: 12px;
  margin-top: 2px;
}

.menu {
  border-right: none;
}

.header {
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: rgba(255, 255, 255, 0.86);
  backdrop-filter: blur(8px);
  border-bottom: 1px solid var(--mg-border);
}

.header-title {
  font-size: 18px;
  font-weight: 650;
}

.header-meta {
  color: var(--mg-muted);
  font-size: 12px;
}

.main {
  padding: 20px;
}
</style>
