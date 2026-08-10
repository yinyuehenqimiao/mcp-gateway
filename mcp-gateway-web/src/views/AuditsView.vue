<template>
  <div class="page-card">
    <div class="page-header">
      <div>
        <h2>调用审计</h2>
        <p>记录 MCP tools/call：谁调用、哪个工具、参数摘要、耗时与成败。</p>
      </div>
      <el-button type="primary" :loading="loading" @click="load">刷新</el-button>
    </div>

    <el-form :inline="true" class="filters" @submit.prevent>
      <el-form-item label="Slug">
        <el-input v-model="filters.slug" clearable placeholder="如 demo-biz" style="width: 160px" />
      </el-form-item>
      <el-form-item label="工具名">
        <el-input v-model="filters.toolName" clearable placeholder="toolName" style="width: 180px" />
      </el-form-item>
      <el-form-item label="结果">
        <el-select v-model="filters.success" clearable placeholder="全部" style="width: 120px">
          <el-option label="成功" :value="true" />
          <el-option label="失败" :value="false" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="resetFilters">重置</el-button>
      </el-form-item>
    </el-form>

    <el-table :data="items" v-loading="loading" stripe empty-text="暂无审计记录（发起 tools/call 后会出现）">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="createdAt" label="时间" width="170">
        <template #default="{ row }">
          {{ formatTime(row.createdAt) }}
        </template>
      </el-table-column>
      <el-table-column prop="slug" label="Slug" width="120" />
      <el-table-column prop="toolName" label="工具" min-width="150" show-overflow-tooltip />
      <el-table-column label="调用方" min-width="140">
        <template #default="{ row }">
          <div>{{ row.callerSubject || '-' }}</div>
          <div class="mono muted">{{ shortHash(row.callerKeyHash) }}</div>
        </template>
      </el-table-column>
      <el-table-column label="参数摘要" min-width="200">
        <template #default="{ row }">
          <el-button link type="primary" @click="showArgs(row)">查看</el-button>
          <span class="muted preview">{{ previewArgs(row.argumentsSummary) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="结果" width="90">
        <template #default="{ row }">
          <el-tag :type="row.success ? 'success' : 'danger'" size="small">
            {{ row.success ? '成功' : '失败' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="durationMs" label="耗时" width="90">
        <template #default="{ row }">{{ row.durationMs }} ms</template>
      </el-table-column>
      <el-table-column label="错误" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">
          <span class="error-text">{{ row.errorMessage || '-' }}</span>
        </template>
      </el-table-column>
    </el-table>

    <div class="pager">
      <el-pagination
        background
        layout="total, sizes, prev, pager, next"
        :total="total"
        v-model:current-page="page"
        v-model:page-size="size"
        :page-sizes="[10, 20, 50, 100]"
        @current-change="load"
        @size-change="onSizeChange"
      />
    </div>
  </div>

  <el-dialog v-model="argsVisible" title="参数摘要" width="640px">
    <pre class="schema mono">{{ argsText }}</pre>
  </el-dialog>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { gatewayApi } from '@/api'
import type { AuditItem } from '@/api/types'

const loading = ref(false)
const items = ref<AuditItem[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const argsVisible = ref(false)
const argsText = ref('')

const filters = reactive<{
  slug: string
  toolName: string
  success: boolean | undefined
}>({
  slug: '',
  toolName: '',
  success: undefined,
})

async function load() {
  loading.value = true
  try {
    const data = await gatewayApi.listAudits({
      slug: filters.slug || undefined,
      toolName: filters.toolName || undefined,
      success: filters.success,
      page: page.value - 1,
      size: size.value,
    })
    items.value = data.items || []
    total.value = data.total || 0
  } finally {
    loading.value = false
  }
}

function search() {
  page.value = 1
  load()
}

function resetFilters() {
  filters.slug = ''
  filters.toolName = ''
  filters.success = undefined
  page.value = 1
  load()
}

function onSizeChange() {
  page.value = 1
  load()
}

function formatTime(value?: string) {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 19)
}

function shortHash(hash?: string) {
  if (!hash) return ''
  return hash.length > 12 ? hash.slice(0, 12) + '…' : hash
}

function previewArgs(raw?: string) {
  if (!raw) return '-'
  return raw.length > 40 ? raw.slice(0, 40) + '…' : raw
}

function showArgs(row: AuditItem) {
  const raw = row.argumentsSummary || ''
  try {
    argsText.value = JSON.stringify(JSON.parse(raw), null, 2)
  } catch {
    argsText.value = raw || '(空)'
  }
  argsVisible.value = true
}

onMounted(load)
</script>

<style scoped>
.filters {
  margin-bottom: 8px;
}

.pager {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}

.muted {
  color: var(--mg-muted);
  font-size: 12px;
}

.preview {
  margin-left: 6px;
}

.error-text {
  color: #b91c1c;
}

.schema {
  margin: 0;
  max-height: 420px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-all;
  background: #0f172a;
  color: #e2e8f0;
  padding: 12px;
  border-radius: 8px;
  font-size: 12px;
}
</style>
