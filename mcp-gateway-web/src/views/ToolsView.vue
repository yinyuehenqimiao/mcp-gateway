<template>
  <div class="page-card">
    <div class="page-header">
      <div>
        <h2>已发布工具</h2>
        <p>按 MCP Server（slug）分组。每个分组有独立 SSE 地址，Agent 只看到该组工具。</p>
      </div>
      <div class="actions">
        <el-button @click="reload">热重载</el-button>
        <el-button type="primary" :loading="loading" @click="load">刷新</el-button>
      </div>
    </div>

    <el-empty v-if="!loading && groups.length === 0" description="暂无已发布 MCP，请先到「MCP 发布」发布" />

    <div v-for="group in groups" :key="group.slug" class="group">
      <div class="group-head">
        <div>
          <div class="group-title">{{ group.slug }}</div>
          <div class="mono group-url">{{ group.sseUrl }}</div>
        </div>
        <div class="group-actions">
          <el-tag type="success">{{ group.toolCount }} 个工具</el-tag>
          <el-button size="small" @click="copy(group.sseUrl)">复制 SSE</el-button>
        </div>
      </div>

      <el-table :data="group.tools" stripe empty-text="该 MCP 暂无工具">
        <el-table-column prop="toolName" label="Tool" min-width="160" />
        <el-table-column prop="httpMethod" label="Method" width="100" />
        <el-table-column prop="pathTemplate" label="Path" min-width="180" />
        <el-table-column prop="baseUrl" label="Base URL" min-width="200" show-overflow-tooltip />
        <el-table-column prop="description" label="说明" min-width="180" show-overflow-tooltip />
        <el-table-column label="Schema" width="100">
          <template #default="{ row }">
            <el-button link type="primary" @click="showSchema(row)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>

  <el-dialog v-model="schemaVisible" title="Input Schema" width="640px">
    <pre class="schema mono">{{ schemaText }}</pre>
  </el-dialog>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { gatewayApi } from '@/api'
import type { PublishedToolGroup, PublishedTool } from '@/api/types'

const loading = ref(false)
const groups = ref<PublishedToolGroup[]>([])
const schemaVisible = ref(false)
const schemaText = ref('')

async function load() {
  loading.value = true
  try {
    groups.value = await gatewayApi.publishedToolGroups()
  } finally {
    loading.value = false
  }
}

async function reload() {
  await gatewayApi.reloadTools()
  ElMessage.success('已热重载')
  await load()
}

function showSchema(row: PublishedTool) {
  try {
    schemaText.value = JSON.stringify(JSON.parse(row.inputSchema), null, 2)
  } catch {
    schemaText.value = row.inputSchema
  }
  schemaVisible.value = true
}

async function copy(text: string) {
  await navigator.clipboard.writeText(text)
  ElMessage.success('已复制')
}

onMounted(load)
</script>

<style scoped>
.actions {
  display: flex;
  gap: 10px;
}

.group {
  margin-top: 16px;
  border: 1px solid var(--mg-border);
  border-radius: 12px;
  padding: 14px;
  background: #fff;
}

.group-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
  margin-bottom: 12px;
}

.group-title {
  font-weight: 700;
  font-size: 16px;
}

.group-url {
  margin-top: 4px;
  color: var(--mg-muted);
  font-size: 12px;
}

.group-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}

.schema {
  margin: 0;
  max-height: 480px;
  overflow: auto;
  background: #0b1220;
  color: #e5e7eb;
  padding: 14px;
  border-radius: 10px;
  font-size: 12px;
  line-height: 1.55;
}
</style>
