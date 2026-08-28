# MCP Gateway

将 OpenAPI / 手动录入的 HTTP 接口转换成 MCP Tools。密钥与账号请使用 `.env`（见仓库根目录 [docs/START.md](../docs/START.md)），**不要**把明文密码写进本文件的示例里。

## 入口

| 类型 | 地址 |
|------|------|
| SSE | `http://localhost:18190/mcp/{slug}/sse` |
| Streamable | `POST http://localhost:18190/mcp/{slug}` |
| 兼容旧 SSE | `http://localhost:18190/sse`（仅 `gateway.default-server-slug`） |
| 管理前端 | http://localhost:5273 |

## 启动（摘要）

```powershell
Copy-Item .env.example .env   # 填写 MYSQL_* / GATEWAY_JWT_SECRET 等
Copy-Item docker\.env.example docker\.env
cd docker
docker compose up -d
cd ..
mvn spring-boot:run
```

完整步骤、依赖与变量说明：[docs/START.md](../docs/START.md)。功能与架构：[docs/FEATURES.md](../docs/FEATURES.md)。

## 鉴权 / 限流 / 审计

- `tools/list`、`tools/call` 需 `Authorization: Bearer <token>`
- Token 可为 MCP Server 固定 Access Token，或与下游同密钥的 JWT（`GATEWAY_JWT_SECRET`）
- Redis 限流：`GATEWAY_RATE_LIMIT_*`
- 审计：RocketMQ topic 默认 `mcp-audit_topic`（与短链 topic 隔离）

## 环境变量样例

见 [`.env.example`](.env.example) 与 [`docker/.env.example`](docker/.env.example)。

## 代码结构

```text
com.mcp.gateway
├── config/
├── controller/          # 管理 REST
├── service/
│   ├── audit/           # RocketMQ 审计
│   ├── tool/            # 注册与 HTTP 转发
│   └── openapi/
└── mcp/                 # SSE + Streamable 入口
```

## 压测

见 [`bench/README.md`](bench/README.md)。
