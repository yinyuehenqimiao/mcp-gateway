<template>
  <div class="grid">
    <div class="page-card stat">
      <div class="label">网关状态</div>
      <div class="value" :class="health?.status === 'UP' ? 'ok' : 'bad'">
        {{ health?.status || 'UNKNOWN' }}
      </div>
    </div>
    <div class="page-card stat">
      <div class="label">已发布 MCP</div>
      <div class="value">{{ health?.publishedServers ?? servers.filter(s => s.published).length }}</div>
    </div>
    <div class="page-card stat">
      <div class="label">已发布工具</div>
      <div class="value">{{ health?.publishedTools ?? '-' }}</div>
    </div>
    <div class="page-card stat">
      <div class="label">业务系统</div>
      <div class="value">{{ systems.length }}</div>
    </div>
  </div>

  <div class="page-card" style="margin-top: 16px">
    <div class="page-header">
      <div>
        <h2>接入信息（分组）</h2>
        <p>每个 MCP Server 使用独立 SSE，Agent 只看到该组工具。</p>
      </div>
      <el-button type="primary" :loading="loading" @click="refresh">刷新</el-button>
    </div>

    <el-table :data="groups" empty-text="暂无已发布 MCP" stripe>
      <el-table-column prop="slug" label="Slug" width="140" />
      <el-table-column prop="toolCount" label="工具数" width="90" />
      <el-table-column prop="sseUrl" label="SSE 地址" min-width="260">
        <template #default="{ row }">
          <span class="mono">{{ row.sseUrl }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="streamableUrl" label="Streamable" min-width="240">
        <template #default="{ row }">
          <span class="mono">{{ row.streamableUrl }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="200">
        <template #default="{ row }">
          <el-button link type="primary" @click="copy(row.sseUrl)">复制 SSE</el-button>
          <el-button link type="primary" @click="copy(row.streamableUrl)">复制 Streamable</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-alert
      style="margin-top: 14px"
      type="info"
      :closable="false"
      show-icon
      title="双轨入口：SSE=/mcp/{slug}/sse；无状态 Streamable=POST /mcp/{slug}。旧地址 /sse 仅加载 slug=default"
    />
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { gatewayApi } from '@/api'
import type { HealthInfo, McpServerItem, PublishedToolGroup, SystemItem } from '@/api/types'

const loading = ref(false)
const health = ref<HealthInfo | null>(null)
const systems = ref<SystemItem[]>([])
const servers = ref<McpServerItem[]>([])
const groups = ref<PublishedToolGroup[]>([])

async function refresh() {
  loading.value = true
  try {
    const [h, s, m, g] = await Promise.allSettled([
      gatewayApi.health(),
      gatewayApi.listSystems(),
      gatewayApi.listMcpServers(),
      gatewayApi.publishedToolGroups(),
    ])
    if (h.status === 'fulfilled') health.value = h.value
    if (s.status === 'fulfilled') systems.value = s.value
    if (m.status === 'fulfilled') servers.value = m.value
    if (g.status === 'fulfilled') groups.value = g.value
  } finally {
    loading.value = false
  }
}

async function copy(text: string) {
  await navigator.clipboard.writeText(text)
  ElMessage.success('已复制')
}

onMounted(refresh)
</script>

<style scoped>
.grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.stat .label {
  color: var(--mg-muted);
  font-size: 13px;
}

.stat .value {
  margin-top: 10px;
  font-size: 28px;
  font-weight: 700;
}

.stat .value.ok {
  color: #0f766e;
}

.stat .value.bad {
  color: #b91c1c;
}

@media (max-width: 960px) {
  .grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
