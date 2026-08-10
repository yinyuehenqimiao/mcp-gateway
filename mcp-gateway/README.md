# MCP Gateway

将 **OpenAPI / Swagger / 手动录入** 的 HTTP 接口，统一转换成 MCP Tools，供 Cursor、Codex 或多 Agent 直接接入。

```text
Cursor / Codex / 自研 Agent
        │  MCP SSE: /mcp/{slug}/sse
        ▼
   mcp-gateway (:18190)
        │  管理 API 落库 MySQL，运行时按 slug 动态注册 Tool
        ▼
   下游业务 HTTP API
```

## 已就绪环境

| 组件 | 说明 |
|------|------|
| MySQL 容器 | `mcp-gateway-mysql`，镜像 `mysql:8.0.36`，内存上限 **512MB**，端口 **3307** |
| Redis 容器 | `shortlink-redis`，镜像 `redis:7.2`，内存上限 **128MB**，端口 **6379** |
| 数据库 | `mcp_gateway` / 用户 `mcp` / 密码 `mcp_pass_123` |
| 网关端口 | `18190` |
| 分组 MCP SSE | `http://localhost:18190/mcp/{slug}/sse` |
| 兼容入口 | `http://localhost:18190/sse`（仅加载 `gateway.default-server-slug`） |

## 鉴权 / 限流 / 审计

### API Key（可用下游 JWT）

- `tools/list`、`tools/call` **必须**带 `Authorization: Bearer <token>`
- 校验方式：
  1. 与 MCP Server 的 `accessToken` 精确匹配；或
  2. `gateway.jwt.enabled=true` 时，接受与 demo-biz **同密钥**签发的 JWT
- 转发下游时默认透传该 Bearer；业务系统可配 `authType=BEARER` + `authConfig={"useCallerToken":true}`

### Redis 限流

- 按调用方 Key（token hash）与按 `slug+tool` 双维度
- 配置：`gateway.rate-limit.per-key-per-minute` / `per-tool-per-minute`
- 超额返回 `RATE_LIMITED`（HTTP 429 / JSON-RPC `-32029`）

### 调用审计

- 表：`tool_call_audit`（谁/工具/参数摘要/耗时/成败）
- 查询：`GET /api/audits?slug=&toolName=&success=&page=0&size=20`

## 启动

### 1. MySQL + Redis

```bash
cd docker
docker compose up -d
```

### 2. 网关

```bash
mvn spring-boot:run
```

健康检查：

```bash
curl http://localhost:18190/gateway/health
```

### 3. 管理前端（Vue3）

```bash
cd D:\Project\AI\MCP\mcp-gateway-web
npm install
npm run dev
```

打开：http://localhost:5273

## 核心流程

1. **创建业务系统**（下游 `baseUrl`）
2. **导入 OpenAPI**（URL / JSON 内容）或 **手动录入 API**
3. **创建 MCP Server** 并绑定 API
4. **发布** → Agent 通过 `/mcp/{slug}/sse` 看到该组 Tools

## 管理 API 速查

### 创建业务系统

```http
POST /api/systems
{
  "name": "Demo Biz",
  "code": "demo-biz",
  "baseUrl": "http://localhost:8081",
  "description": "下游业务服务"
}
```

### 导入 OpenAPI

```http
POST /api/systems/{systemId}/import/openapi
{
  "openapiUrl": "http://localhost:8081/v3/api-docs",
  "replaceExisting": true
}
```

或直接传文档内容：

```http
POST /api/systems/{systemId}/import/openapi
{
  "openapiContent": "{...openapi json...}",
  "replaceExisting": true
}
```

### 手动录入接口

```http
POST /api/systems/{systemId}/apis
{
  "name": "按 ID 查询商品",
  "toolName": "getProductById",
  "description": "GET /api/products/{id}",
  "httpMethod": "GET",
  "pathTemplate": "/api/products/{id}",
  "parameters": [
    { "name": "id", "in": "path", "required": true, "type": "string", "description": "商品 ID" }
  ]
}
```

### 创建并发布 MCP Server

```http
POST /api/mcp-servers
{
  "name": "Demo Biz MCP",
  "slug": "demo-biz",
  "description": "商品/订单工具组",
  "apiIds": [1, 2, 3]
}

POST /api/mcp-servers/{id}/publish?published=true
```

### 调试

- 已发布工具：`GET /gateway/tools`（可按 `?slug=` 过滤）
- 预览某 MCP Server 工具：`GET /api/mcp-servers/{id}/tools`
- 热重载工具：`POST /api/mcp-servers/reload`
- 试调用：`POST /api/apis/test-call`

## 接入 Cursor

```json
{
  "mcpServers": {
    "demo-biz": {
      "url": "http://localhost:18190/mcp/demo-biz/sse"
    }
  }
}
```

## 接入自研 / 多 Agent

1. 连接 `http://localhost:18190/mcp/{slug}/sse`
2. `initialize` → `notifications/initialized`
3. `tools/list` 获取该分组工具
4. `tools/call` 调用；网关转发到下游 HTTP

也可直接读：`GET /gateway/tools?slug={slug}`

## 代码结构

```text
com.mcp.gateway
├── config/                 # 配置与 CORS
├── common/exception/       # 业务异常与统一处理
├── controller/             # 管理 REST API
├── dto/                    # 请求/响应 DTO
├── domain/
│   ├── entity/             # JPA 实体
│   └── repository/         # Spring Data 仓库
├── service/                # 业务编排
│   ├── openapi/            # OpenAPI 解析
│   └── tool/               # MCP 工具注册与 HTTP 转发
└── mcp/                    # 按 slug 的 SSE / message 协议入口
```

## 数据模型

- `biz_system`：业务系统 / 下游 baseUrl / 认证
- `api_endpoint`：接口定义（OpenAPI 导入或手动）
- `mcp_server`：发布给 Agent 的 MCP 服务（按 slug 分组）
- `mcp_server_api`：MCP 服务绑定的接口集合

## 说明

- 支持 OpenAPI 3.x 与 Swagger 2.0 JSON
- 工具入参统一为 JSON Schema：`path/query/header` 平铺，请求体放在 `body`
- 系统级认证：`NONE` / `BEARER` / `API_KEY` / `BASIC`
- 兼容入口 `/sse` 仅加载 `gateway.default-server-slug`；多 Agent 请使用 `/mcp/{slug}/sse`
