export interface SystemItem {
  id: number
  name: string
  code: string
  baseUrl: string
  description?: string
  authType: string
  authConfig?: string
}

export interface ParameterDto {
  name: string
  in: 'path' | 'query' | 'header' | 'body' | string
  description?: string
  required: boolean
  type?: string
}

export interface ApiItem {
  id: number
  systemId: number
  name: string
  toolName: string
  description?: string
  httpMethod: string
  pathTemplate: string
  parametersJson?: string
  requestBodySchema?: string
  headersJson?: string
  enabled: boolean
  sourceType: string
  operationId?: string
  inputSchema?: string
}

export interface ImportResult {
  imported: number
  updated: number
  toolNames: string[]
}

export interface McpServerItem {
  id: number
  name: string
  slug: string
  description?: string
  /** JWT=业务登录令牌校验；FIXED=固定 Access Token */
  authMode?: string
  accessToken?: string
  /** JWT 模式可选：下游签发密钥 */
  jwtSecret?: string
  published: boolean
  apiIds: number[]
  sseUrl: string
  messageEndpoint: string
  /** 无状态 Streamable HTTP：POST /mcp/{slug} */
  streamableUrl?: string
}

export interface ToolPreview {
  toolName: string
  description: string
  httpMethod: string
  pathTemplate: string
  inputSchema: string
}

export interface PublishedTool {
  slug?: string
  toolName: string
  description: string
  httpMethod: string
  pathTemplate: string
  baseUrl: string
  inputSchema: string
}

export interface PublishedToolGroup {
  slug: string
  sseUrl: string
  messageEndpoint: string
  streamableUrl?: string
  toolCount: number
  tools: PublishedTool[]
}

export interface HealthInfo {
  status: string
  publishedTools: number
  publishedServers?: number
  defaultSseTools?: number
}

export interface AuditItem {
  id: number
  slug: string
  callerKeyHash?: string
  callerSubject?: string
  toolName: string
  argumentsSummary?: string
  success: boolean
  errorMessage?: string
  durationMs: number
  createdAt: string
}

export interface AuditPage {
  items: AuditItem[]
  page: number
  size: number
  total: number
  totalPages: number
}
