<template>
  <div class="page-card">
    <div class="page-header">
      <div>
        <h2>业务系统</h2>
        <p>登记下游服务的 baseUrl 与认证方式，供接口导入和转发使用。</p>
      </div>
      <el-button type="primary" @click="openCreate">新建系统</el-button>
    </div>

    <el-table :data="list" v-loading="loading" stripe empty-text="暂无系统">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="name" label="名称" min-width="140" />
      <el-table-column prop="code" label="Code" min-width="120" />
      <el-table-column prop="baseUrl" label="Base URL" min-width="220" show-overflow-tooltip />
      <el-table-column label="认证" width="140">
        <template #default="{ row }">
          {{ authLabel(row) }}
        </template>
      </el-table-column>
      <el-table-column prop="description" label="说明" min-width="160" show-overflow-tooltip />
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>

  <el-dialog v-model="visible" :title="form.id ? '编辑系统' : '新建系统'" width="600px">
    <el-form :model="form" label-width="120px">
      <el-form-item label="名称" required>
        <el-input v-model="form.name" placeholder="如 Demo Biz" />
      </el-form-item>
      <el-form-item label="Code" required>
        <el-input v-model="form.code" :disabled="!!form.id" placeholder="唯一标识，如 demo-biz" />
      </el-form-item>
      <el-form-item label="Base URL" required>
        <el-input v-model="form.baseUrl" placeholder="http://localhost:8081" />
      </el-form-item>

      <el-form-item label="认证方式">
        <el-select v-model="form.authMode" style="width: 100%" @change="onAuthModeChange">
          <el-option label="无认证" value="NONE" />
          <el-option label="JWT 透传（推荐）" value="JWT_PASS" />
          <el-option label="固定 Bearer Token" value="BEARER_FIXED" />
          <el-option label="API Key" value="API_KEY" />
          <el-option label="Basic 账号密码" value="BASIC" />
        </el-select>
      </el-form-item>

      <el-alert
        v-if="form.authMode === 'JWT_PASS'"
        type="success"
        :closable="false"
        show-icon
        style="margin: 0 0 16px 120px; width: calc(100% - 120px)"
        title="调用 MCP 时传入的 JWT，会原样带给下游业务接口。一般选这个即可，不用再填 Token。"
      />

      <el-form-item v-if="form.authMode === 'BEARER_FIXED'" label="Bearer Token" required>
        <el-input
          v-model="form.bearerToken"
          type="password"
          show-password
          placeholder="粘贴固定 JWT / Access Token"
        />
      </el-form-item>

      <template v-if="form.authMode === 'API_KEY'">
        <el-form-item label="Header 名">
          <el-input v-model="form.apiKeyHeader" placeholder="默认 X-API-Key" />
        </el-form-item>
        <el-form-item label="API Key" required>
          <el-input v-model="form.apiKeyValue" type="password" show-password placeholder="下游要求的 Key" />
        </el-form-item>
      </template>

      <template v-if="form.authMode === 'BASIC'">
        <el-form-item label="用户名" required>
          <el-input v-model="form.basicUsername" />
        </el-form-item>
        <el-form-item label="密码" required>
          <el-input v-model="form.basicPassword" type="password" show-password />
        </el-form-item>
      </template>

      <el-form-item label="说明">
        <el-input v-model="form.description" type="textarea" :rows="2" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { gatewayApi } from '@/api'
import type { SystemItem } from '@/api/types'

type AuthMode = 'NONE' | 'JWT_PASS' | 'BEARER_FIXED' | 'API_KEY' | 'BASIC'

const loading = ref(false)
const saving = ref(false)
const visible = ref(false)
const list = ref<SystemItem[]>([])

const form = reactive({
  id: 0,
  name: '',
  code: '',
  baseUrl: '',
  authMode: 'JWT_PASS' as AuthMode,
  bearerToken: '',
  apiKeyHeader: 'X-API-Key',
  apiKeyValue: '',
  basicUsername: '',
  basicPassword: '',
  description: '',
})

function parseAuthMode(row: SystemItem): AuthMode {
  const type = (row.authType || 'NONE').toUpperCase()
  if (type === 'NONE' || !type) return 'NONE'
  let cfg: Record<string, unknown> = {}
  try {
    cfg = row.authConfig ? JSON.parse(row.authConfig) : {}
  } catch {
    cfg = {}
  }
  if (type === 'BEARER') {
    if (cfg.useCallerToken === true || (!cfg.token && cfg.useCallerToken !== false)) {
      // 有 useCallerToken，或未配固定 token 时，按透传理解
      if (cfg.useCallerToken === true || !cfg.token) return 'JWT_PASS'
    }
    return 'BEARER_FIXED'
  }
  if (type === 'API_KEY') return 'API_KEY'
  if (type === 'BASIC') return 'BASIC'
  return 'NONE'
}

function authLabel(row: SystemItem): string {
  switch (parseAuthMode(row)) {
    case 'NONE':
      return '无认证'
    case 'JWT_PASS':
      return 'JWT 透传'
    case 'BEARER_FIXED':
      return '固定 Bearer'
    case 'API_KEY':
      return 'API Key'
    case 'BASIC':
      return 'Basic'
    default:
      return row.authType || 'NONE'
  }
}

function buildAuthPayload(): { authType: string; authConfig?: string } {
  switch (form.authMode) {
    case 'NONE':
      return { authType: 'NONE', authConfig: undefined }
    case 'JWT_PASS':
      return {
        authType: 'BEARER',
        authConfig: JSON.stringify({ useCallerToken: true }),
      }
    case 'BEARER_FIXED':
      return {
        authType: 'BEARER',
        authConfig: JSON.stringify({ token: form.bearerToken.trim(), useCallerToken: false }),
      }
    case 'API_KEY':
      return {
        authType: 'API_KEY',
        authConfig: JSON.stringify({
          headerName: form.apiKeyHeader.trim() || 'X-API-Key',
          apiKey: form.apiKeyValue.trim(),
        }),
      }
    case 'BASIC':
      return {
        authType: 'BASIC',
        authConfig: JSON.stringify({
          username: form.basicUsername.trim(),
          password: form.basicPassword,
        }),
      }
  }
}

function fillAuthFields(row: SystemItem) {
  form.authMode = parseAuthMode(row)
  form.bearerToken = ''
  form.apiKeyHeader = 'X-API-Key'
  form.apiKeyValue = ''
  form.basicUsername = ''
  form.basicPassword = ''
  let cfg: Record<string, unknown> = {}
  try {
    cfg = row.authConfig ? JSON.parse(row.authConfig) : {}
  } catch {
    cfg = {}
  }
  if (form.authMode === 'BEARER_FIXED') {
    form.bearerToken = String(cfg.token || '')
  } else if (form.authMode === 'API_KEY') {
    form.apiKeyHeader = String(cfg.headerName || 'X-API-Key')
    form.apiKeyValue = String(cfg.apiKey || '')
  } else if (form.authMode === 'BASIC') {
    form.basicUsername = String(cfg.username || '')
    form.basicPassword = String(cfg.password || '')
  }
}

function onAuthModeChange() {
  // 切换时清空敏感字段，避免误带旧值
  if (form.authMode !== 'BEARER_FIXED') form.bearerToken = ''
  if (form.authMode !== 'API_KEY') {
    form.apiKeyHeader = 'X-API-Key'
    form.apiKeyValue = ''
  }
  if (form.authMode !== 'BASIC') {
    form.basicUsername = ''
    form.basicPassword = ''
  }
}

async function load() {
  loading.value = true
  try {
    list.value = await gatewayApi.listSystems()
  } finally {
    loading.value = false
  }
}

function resetForm() {
  form.id = 0
  form.name = ''
  form.code = ''
  form.baseUrl = ''
  form.authMode = 'JWT_PASS'
  form.bearerToken = ''
  form.apiKeyHeader = 'X-API-Key'
  form.apiKeyValue = ''
  form.basicUsername = ''
  form.basicPassword = ''
  form.description = ''
}

function openCreate() {
  resetForm()
  visible.value = true
}

function openEdit(row: SystemItem) {
  form.id = row.id
  form.name = row.name
  form.code = row.code
  form.baseUrl = row.baseUrl
  form.description = row.description || ''
  fillAuthFields(row)
  visible.value = true
}

async function save() {
  if (!form.name || !form.baseUrl || (!form.id && !form.code)) {
    ElMessage.warning('请填写必填项')
    return
  }
  if (form.authMode === 'BEARER_FIXED' && !form.bearerToken.trim()) {
    ElMessage.warning('请填写 Bearer Token')
    return
  }
  if (form.authMode === 'API_KEY' && !form.apiKeyValue.trim()) {
    ElMessage.warning('请填写 API Key')
    return
  }
  if (form.authMode === 'BASIC' && (!form.basicUsername.trim() || !form.basicPassword)) {
    ElMessage.warning('请填写用户名和密码')
    return
  }

  const auth = buildAuthPayload()
  saving.value = true
  try {
    if (form.id) {
      await gatewayApi.updateSystem(form.id, {
        name: form.name,
        baseUrl: form.baseUrl,
        authType: auth.authType,
        authConfig: auth.authConfig,
        description: form.description,
      })
    } else {
      await gatewayApi.createSystem({
        name: form.name,
        code: form.code,
        baseUrl: form.baseUrl,
        authType: auth.authType,
        authConfig: auth.authConfig,
        description: form.description,
      })
    }
    ElMessage.success('已保存')
    visible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function remove(row: SystemItem) {
  await ElMessageBox.confirm(`确认删除系统「${row.name}」？其下接口也会被删除。`, '提示', {
    type: 'warning',
  })
  await gatewayApi.deleteSystem(row.id)
  ElMessage.success('已删除')
  await load()
}

onMounted(load)
</script>
