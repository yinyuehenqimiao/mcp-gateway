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
  accessToken?: string
  published: boolean
  apiIds: number[]
  sseUrl: string
  messageEndpoint: string
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
  toolCount: number
  tools: PublishedTool[]
}

export interface HealthInfo {
  status: string
  publishedTools: number
  publishedServers?: number
  defaultSseTools?: number
}
