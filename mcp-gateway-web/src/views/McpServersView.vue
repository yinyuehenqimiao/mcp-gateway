<template>
  <div class="page-card">
    <div class="page-header">
      <div>
        <h2>MCP 发布</h2>
        <p>
          每个 MCP Server 有独立 SSE：
          <span class="mono">http://localhost:18190/mcp/&#123;slug&#125;/sse</span>
        </p>
      </div>
      <el-button type="primary" @click="openCreate">创建 MCP Server</el-button>
    </div>

    <el-alert
      type="info"
      :closable="false"
      show-icon
      style="margin-bottom: 14px"
      title="两层凭证其实是同一条 JWT：① Agent→网关（本页接入方式）；② 网关→业务（业务系统选「JWT 透传」）。推荐：MCP 选「业务登录 JWT」+ 填下游签发密钥；Agent 的 jwt-token 填登录拿到的 JWT，不要再单独造一个 Access Token。"
    />

    <el-table :data="servers" v-loading="loading" stripe empty-text="暂无 MCP Server">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="name" label="名称" min-width="140" />
      <el-table-column prop="slug" label="Slug" min-width="120" />
      <el-table-column label="接入方式" width="130">
        <template #default="{ row }">
          {{ row.authMode === 'FIXED' ? '固定令牌' : '业务 JWT' }}
        </template>
      </el-table-column>
      <el-table-column prop="published" label="状态" width="110">
        <template #default="{ row }">
          <el-tag :type="row.published ? 'success' : 'info'" size="small">
            {{ row.published ? '已发布' : '未发布' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="绑定 API" width="100">
        <template #default="{ row }">{{ row.apiIds?.length || 0 }}</template>
      </el-table-column>
      <el-table-column prop="sseUrl" label="SSE" min-width="240" show-overflow-tooltip />
      <el-table-column label="操作" width="340" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="copySse(row)">复制SSE</el-button>
          <el-button link type="primary" @click="preview(row)">预览工具</el-button>
          <el-button link type="primary" @click="togglePublish(row)">
            {{ row.published ? '下线' : '发布' }}
          </el-button>
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>

  <el-dialog v-model="visible" :title="form.id ? '编辑 MCP Server' : '创建 MCP Server'" width="720px">
    <el-form :model="form" label-width="120px">
      <el-form-item label="名称" required>
        <el-input v-model="form.name" />
      </el-form-item>
      <el-form-item label="Slug" required>
        <el-input v-model="form.slug" :disabled="!!form.id" placeholder="如 shortlink / demo-biz" />
      </el-form-item>
      <el-form-item label="说明">
        <el-input v-model="form.description" type="textarea" :rows="2" />
      </el-form-item>

      <el-form-item label="接入方式" required>
        <el-select v-model="form.authMode" style="width: 100%">
          <el-option label="业务登录 JWT（推荐）" value="JWT" />
          <el-option label="固定 Access Token" value="FIXED" />
        </el-select>
      </el-form-item>

      <el-alert
        v-if="form.authMode === 'JWT'"
        type="success"
        :closable="false"
        show-icon
        style="margin: 0 0 12px 120px; width: calc(100% - 120px)"
        title="此模式下不需要填 Access Token。Agent 的 jwt-token = 业务登录 JWT；下方密钥 = 业务签发该 JWT 的 secret（短链默认 change-me-in-production-32chars!!，可被 SHORTLINK_JWT_SECRET 覆盖）。"
      />

      <el-form-item v-if="form.authMode === 'JWT'" label="JWT 密钥">
        <el-input
          v-model="form.jwtSecret"
          type="password"
          show-password
          placeholder="短链默认：change-me-in-production-32chars!!；留空则用网关默认（仅适合 demo-biz）"
        />
      </el-form-item>

      <el-alert
        v-if="form.authMode === 'FIXED'"
        type="warning"
        :closable="false"
        show-icon
        style="margin: 0 0 12px 120px; width: calc(100% - 120px)"
        title="固定令牌：Agent 的 jwt-token 必须与下方 Access Token 字符串完全一致（可把登录 JWT 粘进来）。网关只做相等校验，不再验签；业务侧仍可能自己验 JWT。"
      />

      <el-form-item v-if="form.authMode === 'FIXED'" label="Access Token" required>
        <el-input
          v-model="form.accessToken"
          type="textarea"
          :rows="3"
          placeholder="与 Agent jwt-token 填同一串；可直接粘贴当前登录 JWT（过期后需同步改这里和 Agent）"
        />
      </el-form-item>

      <el-form-item label="绑定接口" required>
        <el-select
          v-model="form.apiIds"
          multiple
          filterable
          collapse-tags
          collapse-tags-tooltip
          style="width: 100%"
          placeholder="选择要发布的接口"
        >
          <el-option-group v-for="group in apiGroups" :key="group.systemId" :label="group.label">
            <el-option
              v-for="api in group.apis"
              :key="api.id"
              :label="`${api.toolName}  ${api.httpMethod} ${api.pathTemplate}`"
              :value="api.id"
            />
          </el-option-group>
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">保存</el-button>
    </template>
  </el-dialog>

  <el-drawer v-model="previewVisible" title="工具预览" size="520px">
    <el-table :data="previewTools" stripe>
      <el-table-column prop="toolName" label="Tool" min-width="140" />
      <el-table-column prop="httpMethod" label="Method" width="90" />
      <el-table-column prop="pathTemplate" label="Path" min-width="160" />
    </el-table>
  </el-drawer>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { gatewayApi } from '@/api'
import type { ApiItem, McpServerItem, SystemItem, ToolPreview } from '@/api/types'

const loading = ref(false)
const saving = ref(false)
const visible = ref(false)
const previewVisible = ref(false)
const servers = ref<McpServerItem[]>([])
const systems = ref<SystemItem[]>([])
const allApis = ref<ApiItem[]>([])
const previewTools = ref<ToolPreview[]>([])

const form = reactive({
  id: 0,
  name: '',
  slug: 'default',
  description: '',
  authMode: 'JWT',
  accessToken: '',
  jwtSecret: '',
  apiIds: [] as number[],
})

const apiGroups = computed(() =>
  systems.value.map((system) => ({
    systemId: system.id,
    label: `${system.name} (${system.code})`,
    apis: allApis.value.filter((api) => api.systemId === system.id),
  })),
)

async function load() {
  loading.value = true
  try {
    servers.value = await gatewayApi.listMcpServers()
    systems.value = await gatewayApi.listSystems()
    const apiLists = await Promise.all(systems.value.map((s) => gatewayApi.listApis(s.id)))
    allApis.value = apiLists.flat()
  } finally {
    loading.value = false
  }
}

function openCreate() {
  form.id = 0
  form.name = ''
  form.slug = ''
  form.description = ''
  form.authMode = 'JWT'
  form.accessToken = ''
  form.jwtSecret = ''
  form.apiIds = []
  visible.value = true
}

function openEdit(row: McpServerItem) {
  form.id = row.id
  form.name = row.name
  form.slug = row.slug
  form.description = row.description || ''
  form.authMode = row.authMode === 'FIXED' ? 'FIXED' : 'JWT'
  form.accessToken = row.accessToken || ''
  form.jwtSecret = row.jwtSecret || ''
  form.apiIds = [...(row.apiIds || [])]
  visible.value = true
}

async function save() {
  if (!form.name || !form.slug || !form.apiIds.length) {
    ElMessage.warning('请填写名称、Slug，并至少绑定一个接口')
    return
  }
  if (form.authMode === 'FIXED' && !form.accessToken.trim()) {
    ElMessage.warning('固定令牌模式请填写 Access Token')
    return
  }
  saving.value = true
  try {
    const payload = {
      name: form.name,
      description: form.description,
      authMode: form.authMode,
      accessToken: form.authMode === 'FIXED' ? form.accessToken : '',
      jwtSecret: form.authMode === 'JWT' ? form.jwtSecret : '',
      apiIds: form.apiIds,
    }
    if (form.id) {
      await gatewayApi.updateMcpServer(form.id, payload)
    } else {
      await gatewayApi.createMcpServer({
        ...payload,
        slug: form.slug,
      })
    }
    ElMessage.success('已保存')
    visible.value = false
    await load()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || e?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function togglePublish(row: McpServerItem) {
  const next = !row.published
  await gatewayApi.publishMcpServer(row.id, next)
  ElMessage.success(next ? '已发布' : '已下线')
  await load()
}

async function copySse(row: McpServerItem) {
  const url = row.sseUrl || `http://localhost:18190/mcp/${row.slug}/sse`
  await navigator.clipboard.writeText(url)
  ElMessage.success('已复制 SSE 地址')
}

async function preview(row: McpServerItem) {
  previewTools.value = await gatewayApi.previewTools(row.id)
  previewVisible.value = true
}

async function remove(row: McpServerItem) {
  await ElMessageBox.confirm(`确认删除 MCP Server「${row.name}」？`, '提示', { type: 'warning' })
  await gatewayApi.deleteMcpServer(row.id)
  ElMessage.success('已删除')
  await load()
}

onMounted(load)
</script>
