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
      <el-table-column prop="authType" label="认证" width="110" />
      <el-table-column prop="description" label="说明" min-width="160" show-overflow-tooltip />
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>

  <el-dialog v-model="visible" :title="form.id ? '编辑系统' : '新建系统'" width="560px">
    <el-form :model="form" label-width="100px">
      <el-form-item label="名称" required>
        <el-input v-model="form.name" placeholder="如 Petstore" />
      </el-form-item>
      <el-form-item label="Code" required>
        <el-input v-model="form.code" :disabled="!!form.id" placeholder="唯一标识，如 petstore" />
      </el-form-item>
      <el-form-item label="Base URL" required>
        <el-input v-model="form.baseUrl" placeholder="https://api.example.com" />
      </el-form-item>
      <el-form-item label="认证类型">
        <el-select v-model="form.authType" style="width: 100%">
          <el-option label="NONE" value="NONE" />
          <el-option label="BEARER" value="BEARER" />
          <el-option label="API_KEY" value="API_KEY" />
          <el-option label="BASIC" value="BASIC" />
        </el-select>
      </el-form-item>
      <el-form-item label="认证配置">
        <el-input
          v-model="form.authConfig"
          type="textarea"
          :rows="4"
          placeholder='BEARER: {"token":"xxx"} / API_KEY: {"headerName":"X-API-Key","apiKey":"xxx"}'
        />
      </el-form-item>
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

const loading = ref(false)
const saving = ref(false)
const visible = ref(false)
const list = ref<SystemItem[]>([])

const form = reactive({
  id: 0,
  name: '',
  code: '',
  baseUrl: '',
  authType: 'NONE',
  authConfig: '',
  description: '',
})

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
  form.authType = 'NONE'
  form.authConfig = ''
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
  form.authType = row.authType || 'NONE'
  form.authConfig = row.authConfig || ''
  form.description = row.description || ''
  visible.value = true
}

async function save() {
  if (!form.name || !form.baseUrl || (!form.id && !form.code)) {
    ElMessage.warning('请填写必填项')
    return
  }
  saving.value = true
  try {
    if (form.id) {
      await gatewayApi.updateSystem(form.id, {
        name: form.name,
        baseUrl: form.baseUrl,
        authType: form.authType,
        authConfig: form.authConfig || undefined,
        description: form.description,
      })
    } else {
      await gatewayApi.createSystem({
        name: form.name,
        code: form.code,
        baseUrl: form.baseUrl,
        authType: form.authType,
        authConfig: form.authConfig || undefined,
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
