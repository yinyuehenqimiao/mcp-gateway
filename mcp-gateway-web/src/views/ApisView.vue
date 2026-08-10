<template>
  <div class="page-card">
    <div class="page-header">
      <div>
        <h2>接口管理</h2>
        <p>支持 OpenAPI/Swagger 导入，或像 Postman 一样可视化录入参数。</p>
      </div>
      <div class="actions">
        <el-select
          v-model="systemId"
          placeholder="选择业务系统"
          style="width: 240px"
          @change="loadApis"
        >
          <el-option
            v-for="item in systems"
            :key="item.id"
            :label="`${item.name} (${item.code})`"
            :value="item.id"
          />
        </el-select>
        <el-button :disabled="!systemId" @click="openImport">导入 OpenAPI</el-button>
        <el-button type="primary" :disabled="!systemId" @click="openCreate">手动录入</el-button>
      </div>
    </div>

    <el-table :data="apis" v-loading="loading" stripe empty-text="请先选择系统或导入接口">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="toolName" label="Tool Name" min-width="150" />
      <el-table-column prop="httpMethod" label="Method" width="90" />
      <el-table-column prop="pathTemplate" label="Path" min-width="180" show-overflow-tooltip />
      <el-table-column prop="sourceType" label="来源" width="100" />
      <el-table-column prop="enabled" label="启用" width="80">
        <template #default="{ row }">
          <el-tag :type="row.enabled ? 'success' : 'info'" size="small">
            {{ row.enabled ? '是' : '否' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="description" label="说明" min-width="180" show-overflow-tooltip />
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openTest(row)">试调用</el-button>
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>

  <!-- 导入 -->
  <el-dialog v-model="importVisible" title="导入 OpenAPI / Swagger" width="720px" destroy-on-close>
    <el-alert
      type="info"
      :closable="false"
      show-icon
      title="可填 /v3/api-docs，也可直接填 swagger-ui 地址（如 http://localhost:8001/swagger-ui/index.html），网关会自动探测 JSON 文档并展开 Schemas 字段说明"
      style="margin-bottom: 14px"
    />
    <el-tabs v-model="importTab">
      <el-tab-pane label="从 URL 导入" name="url">
        <el-input
          v-model="importForm.openapiUrl"
          placeholder="http://localhost:8001/v3/api-docs 或 .../swagger-ui/index.html"
          clearable
        />
      </el-tab-pane>
      <el-tab-pane label="粘贴 JSON" name="json">
        <el-input
          v-model="importForm.openapiContent"
          type="textarea"
          :rows="14"
          placeholder="粘贴 /v3/api-docs 返回的完整 JSON"
        />
      </el-tab-pane>
      <el-tab-pane label="上传文件" name="file">
        <el-upload
          drag
          :auto-upload="false"
          :limit="1"
          accept=".json,application/json"
          :on-change="onImportFile"
        >
          <div class="el-upload__text">把 OpenAPI JSON 文件拖到此处，或 <em>点击上传</em></div>
        </el-upload>
        <div v-if="importForm.fileName" class="file-tip">已读取：{{ importForm.fileName }}</div>
      </el-tab-pane>
    </el-tabs>
    <el-form style="margin-top: 12px">
      <el-form-item label="覆盖旧导入">
        <el-switch v-model="importForm.replaceExisting" />
        <span class="hint">开启后会删除本系统中此前 OpenAPI 导入的接口</span>
      </el-form-item>
    </el-form>
    <el-alert
      v-if="importError"
      type="error"
      :closable="true"
      show-icon
      :title="importError"
      style="margin-bottom: 8px"
      @close="importError = ''"
    />
    <template #footer>
      <el-button @click="importVisible = false">取消</el-button>
      <el-button type="primary" :loading="importing" @click="doImport">开始导入</el-button>
    </template>
  </el-dialog>

  <!-- 手动录入 / 编辑 -->
  <el-dialog
    v-model="editVisible"
    :title="editForm.id ? '编辑接口' : '手动录入接口'"
    width="860px"
    destroy-on-close
    top="4vh"
  >
    <el-form :model="editForm" label-width="100px">
      <el-row :gutter="12">
        <el-col :span="12">
          <el-form-item label="名称" required>
            <el-input v-model="editForm.name" placeholder="如：按ID查商品" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="Tool Name">
            <el-input v-model="editForm.toolName" placeholder="不填则自动生成，如 getProductById" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-form-item label="请求" required>
        <div class="req-line">
          <el-select v-model="editForm.httpMethod" style="width: 120px">
            <el-option v-for="m in methods" :key="m" :label="m" :value="m" />
          </el-select>
          <el-input
            v-model="editForm.pathTemplate"
            placeholder="/api/products/{id}"
            @blur="syncPathParams"
          />
        </div>
      </el-form-item>

      <el-form-item label="说明">
        <el-input v-model="editForm.description" type="textarea" :rows="2" />
      </el-form-item>

      <el-form-item label="启用">
        <el-switch v-model="editForm.enabled" />
      </el-form-item>
    </el-form>

    <el-tabs v-model="editTab">
      <el-tab-pane label="Params（可视化）" name="params">
        <div class="section-actions">
          <el-button size="small" @click="addParam('query')">+ Query</el-button>
          <el-button size="small" @click="addParam('path')">+ Path</el-button>
          <el-button size="small" @click="addParam('header')">+ Header</el-button>
          <el-button size="small" @click="syncPathParams">从 Path 识别 {变量}</el-button>
        </div>
        <el-table :data="paramRows" size="small" empty-text="暂无参数，可点击上方按钮添加">
          <el-table-column label="位置" width="120">
            <template #default="{ row }">
              <el-select v-model="row.in" size="small">
                <el-option label="path" value="path" />
                <el-option label="query" value="query" />
                <el-option label="header" value="header" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="参数名" min-width="120">
            <template #default="{ row }">
              <el-input v-model="row.name" size="small" placeholder="id" />
            </template>
          </el-table-column>
          <el-table-column label="类型" width="120">
            <template #default="{ row }">
              <el-select v-model="row.type" size="small">
                <el-option label="string" value="string" />
                <el-option label="integer" value="integer" />
                <el-option label="number" value="number" />
                <el-option label="boolean" value="boolean" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="必填" width="70">
            <template #default="{ row }">
              <el-checkbox v-model="row.required" />
            </template>
          </el-table-column>
          <el-table-column label="说明" min-width="140">
            <template #default="{ row }">
              <el-input v-model="row.description" size="small" placeholder="说明" />
            </template>
          </el-table-column>
          <el-table-column label="" width="60">
            <template #default="{ $index }">
              <el-button link type="danger" @click="paramRows.splice($index, 1)">删</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="Body（可视化）" name="body">
        <div class="section-actions">
          <el-switch v-model="hasBody" active-text="启用 JSON Body" />
          <el-button size="small" :disabled="!hasBody" @click="addBodyField">+ 字段</el-button>
        </div>
        <el-table
          v-if="hasBody"
          :data="bodyFields"
          size="small"
          empty-text="添加 body 字段，会自动生成 schema"
        >
          <el-table-column label="字段名" min-width="140">
            <template #default="{ row }">
              <el-input v-model="row.name" size="small" placeholder="name" />
            </template>
          </el-table-column>
          <el-table-column label="类型" width="130">
            <template #default="{ row }">
              <el-select v-model="row.type" size="small">
                <el-option label="string" value="string" />
                <el-option label="integer" value="integer" />
                <el-option label="number" value="number" />
                <el-option label="boolean" value="boolean" />
                <el-option label="object" value="object" />
                <el-option label="array" value="array" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="必填" width="70">
            <template #default="{ row }">
              <el-checkbox v-model="row.required" />
            </template>
          </el-table-column>
          <el-table-column label="说明" min-width="160">
            <template #default="{ row }">
              <el-input v-model="row.description" size="small" />
            </template>
          </el-table-column>
          <el-table-column label="" width="60">
            <template #default="{ $index }">
              <el-button link type="danger" @click="bodyFields.splice($index, 1)">删</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-else description="当前接口无 Request Body（适合 GET/DELETE）" :image-size="72" />
      </el-tab-pane>

      <el-tab-pane label="高级 JSON" name="json">
        <el-alert
          type="warning"
          :closable="false"
          show-icon
          title="这里适合直接粘贴原始 JSON；保存时以可视化编辑结果为准（切换回可视化会覆盖这里）"
          style="margin-bottom: 10px"
        />
        <div class="json-block">
          <div class="json-title">parameters JSON</div>
          <el-input v-model="advancedParamsJson" type="textarea" :rows="8" />
        </div>
        <div class="json-block">
          <div class="json-title">requestBody schema JSON</div>
          <el-input v-model="advancedBodySchema" type="textarea" :rows="8" placeholder="无 body 可留空" />
        </div>
        <el-button size="small" @click="applyAdvancedJson">把上面 JSON 同步到可视化</el-button>
      </el-tab-pane>
    </el-tabs>

    <template #footer>
      <el-button @click="editVisible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="saveApi">保存</el-button>
    </template>
  </el-dialog>

  <!-- 试调用 -->
  <el-dialog v-model="testVisible" title="试调用" width="720px">
    <el-form label-width="100px">
      <el-form-item label="Tool">
        <span class="mono">{{ testForm.toolName }}</span>
      </el-form-item>
      <el-form-item label="Arguments">
        <el-input v-model="testForm.argumentsJson" type="textarea" :rows="10" />
      </el-form-item>
      <el-form-item label="Result">
        <el-input v-model="testForm.result" type="textarea" :rows="10" readonly />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="testVisible = false">关闭</el-button>
      <el-button type="primary" :loading="testing" @click="doTest">调用</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import type { UploadFile } from 'element-plus'
import { ElMessage, ElMessageBox } from 'element-plus'
import { gatewayApi } from '@/api'
import type { ApiItem, ParameterDto, SystemItem } from '@/api/types'

interface ParamRow {
  name: string
  in: string
  type: string
  required: boolean
  description: string
}

interface BodyField {
  name: string
  type: string
  required: boolean
  description: string
}

const methods = ['GET', 'POST', 'PUT', 'DELETE', 'PATCH']
const loading = ref(false)
const saving = ref(false)
const importing = ref(false)
const testing = ref(false)
const systems = ref<SystemItem[]>([])
const apis = ref<ApiItem[]>([])
const systemId = ref<number>()

const importVisible = ref(false)
const editVisible = ref(false)
const testVisible = ref(false)
const importTab = ref('url')
const editTab = ref('params')
const importError = ref('')

const importForm = reactive({
  openapiUrl: 'http://localhost:8001/swagger-ui/index.html',
  openapiContent: '',
  replaceExisting: true,
  fileName: '',
})

const editForm = reactive({
  id: 0,
  name: '',
  toolName: '',
  httpMethod: 'GET',
  pathTemplate: '',
  description: '',
  enabled: true,
})

const paramRows = ref<ParamRow[]>([])
const bodyFields = ref<BodyField[]>([])
const hasBody = ref(false)
const advancedParamsJson = ref('[]')
const advancedBodySchema = ref('')

const testForm = reactive({
  apiId: 0,
  toolName: '',
  argumentsJson: '{}',
  result: '',
})

watch(editTab, (tab) => {
  if (tab === 'json') {
    advancedParamsJson.value = JSON.stringify(paramRows.value, null, 2)
    advancedBodySchema.value = hasBody.value ? buildBodySchema() : ''
  }
})

async function loadSystems() {
  systems.value = await gatewayApi.listSystems()
  if (!systemId.value && systems.value.length) {
    systemId.value = systems.value[0].id
    await loadApis()
  }
}

async function loadApis() {
  if (!systemId.value) {
    apis.value = []
    return
  }
  loading.value = true
  try {
    apis.value = await gatewayApi.listApis(systemId.value)
  } finally {
    loading.value = false
  }
}

function openImport() {
  importError.value = ''
  importTab.value = 'url'
  importForm.openapiUrl = 'http://localhost:8001/swagger-ui/index.html'
  importForm.openapiContent = ''
  importForm.replaceExisting = true
  importForm.fileName = ''
  importVisible.value = true
}

function onImportFile(file: UploadFile) {
  const raw = file.raw
  if (!raw) return
  const reader = new FileReader()
  reader.onload = () => {
    importForm.openapiContent = String(reader.result || '')
    importForm.fileName = file.name
    importTab.value = 'json'
    ElMessage.success('文件已读取，可直接点「开始导入」')
  }
  reader.readAsText(raw, 'utf-8')
}

async function doImport() {
  if (!systemId.value) return
  importError.value = ''

  const useUrl = importTab.value === 'url'
  const content = importForm.openapiContent?.trim()
  const url = importForm.openapiUrl?.trim()

  if (useUrl && !url) {
    ElMessage.warning('请填写 OpenAPI URL')
    return
  }
  if (!useUrl && !content) {
    ElMessage.warning('请粘贴 JSON 或上传文件')
    return
  }

  importing.value = true
  try {
    const result = await gatewayApi.importOpenApi(systemId.value, {
      openapiUrl: useUrl ? url : undefined,
      openapiContent: useUrl ? undefined : content,
      replaceExisting: importForm.replaceExisting,
    })
    ElMessage.success(`导入完成：新增 ${result.imported}，更新 ${result.updated}`)
    importVisible.value = false
    await loadApis()
  } catch (e: any) {
    importError.value = e?.response?.data?.message || e?.message || '导入失败'
  } finally {
    importing.value = false
  }
}

function emptyParam(location: string): ParamRow {
  return {
    name: '',
    in: location,
    type: 'string',
    required: location === 'path',
    description: '',
  }
}

function addParam(location: string) {
  paramRows.value.push(emptyParam(location))
}

function addBodyField() {
  bodyFields.value.push({
    name: '',
    type: 'string',
    required: false,
    description: '',
  })
}

function syncPathParams() {
  const path = editForm.pathTemplate || ''
  const matches = [...path.matchAll(/\{([^}]+)\}/g)].map((m) => m[1])
  for (const name of matches) {
    const exists = paramRows.value.some((p) => p.in === 'path' && p.name === name)
    if (!exists) {
      paramRows.value.push({
        name,
        in: 'path',
        type: 'string',
        required: true,
        description: `路径参数 ${name}`,
      })
    }
  }
}

function buildBodySchema(): string {
  const properties: Record<string, { type: string; description?: string }> = {}
  const required: string[] = []
  for (const field of bodyFields.value) {
    if (!field.name?.trim()) continue
    properties[field.name.trim()] = {
      type: field.type || 'string',
      description: field.description || undefined,
    }
    if (field.required) required.push(field.name.trim())
  }
  const schema: Record<string, unknown> = {
    type: 'object',
    properties,
  }
  if (required.length) schema.required = required
  return JSON.stringify(schema)
}

function loadBodyFieldsFromSchema(schemaText?: string) {
  bodyFields.value = []
  hasBody.value = false
  if (!schemaText?.trim()) return
  try {
    const schema = JSON.parse(schemaText)
    hasBody.value = true
    const properties = schema.properties || {}
    const required: string[] = schema.required || []
    for (const [name, def] of Object.entries<any>(properties)) {
      bodyFields.value.push({
        name,
        type: def?.type || 'string',
        required: required.includes(name),
        description: def?.description || '',
      })
    }
  } catch {
    hasBody.value = true
    advancedBodySchema.value = schemaText
  }
}

function applyAdvancedJson() {
  try {
    const parsed = JSON.parse(advancedParamsJson.value || '[]')
    if (!Array.isArray(parsed)) throw new Error('parameters 必须是数组')
    paramRows.value = parsed.map((p: any) => ({
      name: p.name || '',
      in: p.in || 'query',
      type: p.type || 'string',
      required: !!p.required,
      description: p.description || '',
    }))
    loadBodyFieldsFromSchema(advancedBodySchema.value)
    editTab.value = 'params'
    ElMessage.success('已同步到可视化表单')
  } catch (e: any) {
    ElMessage.error(e?.message || 'JSON 解析失败')
  }
}

function openCreate() {
  editForm.id = 0
  editForm.name = ''
  editForm.toolName = ''
  editForm.httpMethod = 'GET'
  editForm.pathTemplate = ''
  editForm.description = ''
  editForm.enabled = true
  paramRows.value = []
  bodyFields.value = []
  hasBody.value = false
  advancedParamsJson.value = '[]'
  advancedBodySchema.value = ''
  editTab.value = 'params'
  editVisible.value = true
}

function openEdit(row: ApiItem) {
  editForm.id = row.id
  editForm.name = row.name
  editForm.toolName = row.toolName
  editForm.httpMethod = row.httpMethod
  editForm.pathTemplate = row.pathTemplate
  editForm.description = row.description || ''
  editForm.enabled = row.enabled
  try {
    const parsed = JSON.parse(row.parametersJson || '[]')
    paramRows.value = Array.isArray(parsed)
      ? parsed.map((p: any) => ({
          name: p.name || '',
          in: p.in || 'query',
          type: p.type || 'string',
          required: !!p.required,
          description: p.description || '',
        }))
      : []
  } catch {
    paramRows.value = []
  }
  loadBodyFieldsFromSchema(row.requestBodySchema)
  advancedParamsJson.value = row.parametersJson || '[]'
  advancedBodySchema.value = row.requestBodySchema || ''
  editTab.value = 'params'
  editVisible.value = true
}

async function saveApi() {
  if (!systemId.value) return
  if (!editForm.name || !editForm.httpMethod || !editForm.pathTemplate) {
    ElMessage.warning('请填写名称、Method、Path')
    return
  }

    // 若当前在高级 JSON 页，先把 JSON 解析进可视化结构
    if (editTab.value === 'json') {
      try {
        const parsed = JSON.parse(advancedParamsJson.value || '[]')
        if (!Array.isArray(parsed)) throw new Error('parameters 必须是数组')
        paramRows.value = parsed.map((p: any) => ({
          name: p.name || '',
          in: p.in || 'query',
          type: p.type || 'string',
          required: !!p.required,
          description: p.description || '',
        }))
        loadBodyFieldsFromSchema(advancedBodySchema.value)
      } catch (e: any) {
        ElMessage.error(e?.message || '高级 JSON 解析失败')
        return
      }
    }

  const parameters: ParameterDto[] = paramRows.value
    .filter((p) => p.name?.trim())
    .map((p) => ({
      name: p.name.trim(),
      in: p.in,
      type: p.type || 'string',
      required: !!p.required || p.in === 'path',
      description: p.description || p.name,
    }))

  const requestBodySchema = hasBody.value ? buildBodySchema() : undefined

  saving.value = true
  try {
    if (editForm.id) {
      await gatewayApi.updateApi(editForm.id, {
        name: editForm.name,
        toolName: editForm.toolName || undefined,
        httpMethod: editForm.httpMethod,
        pathTemplate: editForm.pathTemplate,
        description: editForm.description,
        parameters,
        requestBodySchema,
        enabled: editForm.enabled,
      })
    } else {
      await gatewayApi.createApi(systemId.value, {
        name: editForm.name,
        toolName: editForm.toolName || undefined,
        httpMethod: editForm.httpMethod,
        pathTemplate: editForm.pathTemplate,
        description: editForm.description,
        parameters,
        requestBodySchema,
        enabled: editForm.enabled,
      })
    }
    ElMessage.success('已保存')
    editVisible.value = false
    await loadApis()
  } finally {
    saving.value = false
  }
}

async function remove(row: ApiItem) {
  await ElMessageBox.confirm(`确认删除工具「${row.toolName}」？`, '提示', { type: 'warning' })
  await gatewayApi.deleteApi(row.id)
  ElMessage.success('已删除')
  await loadApis()
}

function openTest(row: ApiItem) {
  testForm.apiId = row.id
  testForm.toolName = row.toolName
  try {
    const schema = JSON.parse(row.inputSchema || '{"type":"object","properties":{}}')
    const sample: Record<string, unknown> = {}
    const properties = schema.properties || {}
    for (const [key, def] of Object.entries<any>(properties)) {
      if (key === 'body' && def?.properties) {
        const body: Record<string, unknown> = {}
        for (const [bk, bdef] of Object.entries<any>(def.properties)) {
          body[bk] = bdef?.type === 'integer' || bdef?.type === 'number' ? 0 : ''
        }
        sample.body = body
      } else {
        sample[key] = def?.type === 'integer' || def?.type === 'number' ? 0 : ''
      }
    }
    testForm.argumentsJson = JSON.stringify(sample, null, 2)
  } catch {
    testForm.argumentsJson = '{}'
  }
  testForm.result = ''
  testVisible.value = true
}

async function doTest() {
  testing.value = true
  try {
    const res = await gatewayApi.testCall(testForm.apiId, testForm.argumentsJson)
    testForm.result = res.result
  } finally {
    testing.value = false
  }
}

onMounted(loadSystems)
</script>

<style scoped>
.actions {
  display: flex;
  gap: 10px;
  align-items: center;
  flex-wrap: wrap;
}

.req-line {
  display: flex;
  gap: 8px;
  width: 100%;
}

.section-actions {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 10px;
  flex-wrap: wrap;
}

.hint {
  margin-left: 10px;
  color: var(--mg-muted);
  font-size: 12px;
}

.file-tip {
  margin-top: 8px;
  color: var(--mg-muted);
  font-size: 13px;
}

.json-block {
  margin-bottom: 12px;
}

.json-title {
  font-size: 12px;
  color: var(--mg-muted);
  margin-bottom: 6px;
}
</style>
