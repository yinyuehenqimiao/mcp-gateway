# 功能与架构

## 1. 产品能力

| 能力 | 说明 |
|------|------|
| OpenAPI → MCP Tools | 按业务系统导入 OpenAPI/Swagger，或手动录入接口 |
| 多租户 MCP | 按 `slug` 发布独立工具组 |
| 双轨传输 | **SSE**（长连接 + `sessionId`）与 **Streamable HTTP**（无状态 `POST /mcp/{slug}`）并存 |
| 鉴权 | MCP Server 固定 Access Token，或与下游同密钥的 JWT |
| 限流 | Redis：按调用方 Key、按 `slug+tool` |
| 审计 | `tools/call` 经 RocketMQ（`mcp-audit_topic`）异步写入 `tool_call_audit` |
| 管理台 | Vue 管理业务系统、API、MCP 发布；展示 `sseUrl` / `streamableUrl` |

## 2. 通信架构（三者）

```text
Agent ──传输层（SSE 或 Streamable）──► mcp-gateway ──HTTP──► 真实工具后端
                                              │
                                              └──► RocketMQ ──► MySQL 审计表
```

- Agent **不直接**访问业务后端。
- 网关用 `DynamicToolRegistry` 将 `toolName` 映射为下游 `method + path + baseUrl`。
- SSE 与 Streamable 差在 **Agent↔网关**；**网关↔后端** 都是普通 HTTP。

详见学习笔记中的 SSE / Streamable 对照（本地笔记或仓库讨论总结）。

## 3. 传输对照

| | SSE | Streamable |
|--|-----|------------|
| 入口 | `GET /mcp/{slug}/sse` + `POST .../message?sessionId=` | `POST /mcp/{slug}` |
| Session | 需要 | 无 |
| 典型客户端 | Cursor / mcp-study-client | 压测脚本 / 新客户端 |
| 头校验 | 无（本实现） | 可选 `Mcp-Method` / `Mcp-Name`，不一致返回 `-32020` |

## 4. 核心代码位置

| 模块 | 路径 |
|------|------|
| SSE | `mcp-gateway/.../mcp/McpSseController.java` |
| Streamable | `mcp-gateway/.../mcp/McpStreamableController.java` |
| 工具注册 / 转发 | `service/tool/DynamicToolRegistry`、`HttpToolForwarder` |
| 审计 MQ | `service/audit/ToolCallAuditProducer`、`ToolCallAuditConsumer` |
| 管理 API | `controller/*` |
| 前端 | `mcp-gateway-web/` |

## 5. 管理台主流程

1. 登记业务系统（`baseUrl`、下游认证方式）  
2. 导入 OpenAPI 或手动录入 API  
3. 创建 MCP Server，绑定 API，配置鉴权模式  
4. 发布 → Agent 通过 SSE 或 Streamable 调用该 `slug` 下工具  

## 6. 压测

见 `mcp-gateway/bench/README.md`：

```powershell
python mcp_load_test.py --transport streamable --slug bench --tool ping --token <TOKEN> --concurrency 100 --duration 45
```

`--transport sse|streamable` 可对照两种入口。
