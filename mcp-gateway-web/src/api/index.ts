import http from './http'
import type {
  ApiItem,
  AuditPage,
  HealthInfo,
  ImportResult,
  McpServerItem,
  ParameterDto,
  PublishedTool,
  PublishedToolGroup,
  SystemItem,
  ToolPreview,
} from './types'

export const gatewayApi = {
  health: () => http.get<HealthInfo>('/gateway/health').then((r) => r.data),
  /** 按 MCP Server 分组的已发布工具 */
  publishedToolGroups: () => http.get<PublishedToolGroup[]>('/gateway/tools').then((r) => r.data),
  publishedToolsBySlug: (slug: string) =>
    http.get<PublishedTool[]>('/gateway/tools', { params: { slug } }).then((r) => r.data),

  listSystems: () => http.get<SystemItem[]>('/api/systems').then((r) => r.data),
  createSystem: (body: Partial<SystemItem>) =>
    http.post<SystemItem>('/api/systems', body).then((r) => r.data),
  updateSystem: (id: number, body: Partial<SystemItem>) =>
    http.put<SystemItem>(`/api/systems/${id}`, body).then((r) => r.data),
  deleteSystem: (id: number) => http.delete(`/api/systems/${id}`),

  listApis: (systemId: number) =>
    http.get<ApiItem[]>(`/api/systems/${systemId}/apis`).then((r) => r.data),
  createApi: (
    systemId: number,
    body: {
      name: string
      toolName?: string
      description?: string
      httpMethod: string
      pathTemplate: string
      parameters?: ParameterDto[]
      requestBodySchema?: string
      headersJson?: string
      enabled?: boolean
    },
  ) => http.post<ApiItem>(`/api/systems/${systemId}/apis`, body).then((r) => r.data),
  updateApi: (id: number, body: Record<string, unknown>) =>
    http.put<ApiItem>(`/api/apis/${id}`, body).then((r) => r.data),
  deleteApi: (id: number) => http.delete(`/api/apis/${id}`),
  importOpenApi: (
    systemId: number,
    body: { openapiUrl?: string; openapiContent?: string; replaceExisting?: boolean },
  ) =>
    http
      .post<ImportResult>(`/api/systems/${systemId}/import/openapi`, body)
      .then((r) => r.data),
  testCall: (apiId: number, argumentsJson: string) =>
    http
      .post<{ result: string }>('/api/apis/test-call', { apiId, argumentsJson })
      .then((r) => r.data),

  listMcpServers: () => http.get<McpServerItem[]>('/api/mcp-servers').then((r) => r.data),
  createMcpServer: (body: {
    name: string
    slug: string
    description?: string
    accessToken?: string
    apiIds: number[]
  }) => http.post<McpServerItem>('/api/mcp-servers', body).then((r) => r.data),
  updateMcpServer: (id: number, body: Record<string, unknown>) =>
    http.put<McpServerItem>(`/api/mcp-servers/${id}`, body).then((r) => r.data),
  publishMcpServer: (id: number, published = true) =>
    http
      .post<McpServerItem>(`/api/mcp-servers/${id}/publish`, null, { params: { published } })
      .then((r) => r.data),
  deleteMcpServer: (id: number) => http.delete(`/api/mcp-servers/${id}`),
  previewTools: (id: number) =>
    http.get<ToolPreview[]>(`/api/mcp-servers/${id}/tools`).then((r) => r.data),
  reloadTools: () =>
    http.post<{ servers: string[]; groups: Array<{ slug: string; count: number; tools: string[] }> }>(
      '/api/mcp-servers/reload',
    ).then((r) => r.data),

  listAudits: (params: {
    slug?: string
    toolName?: string
    success?: boolean
    page?: number
    size?: number
  }) => http.get<AuditPage>('/api/audits', { params }).then((r) => r.data),
}
