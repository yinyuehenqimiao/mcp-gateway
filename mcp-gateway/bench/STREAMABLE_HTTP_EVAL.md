# Streamable HTTP / STATELESS 评估（P3）

## 背景

业界（Spring AI Issue #3947、传输层基准）认为经典 SSE（长连接 + session 粘滞）在多实例/高并发下不如 **Streamable HTTP**，甚至 **STATELESS**。

本仓库已依赖 `spring-ai-starter-mcp-server-webmvc` 1.1.0，具备评估基础。

## 与当前架构的关系

| 组件 | 现状 | 切传输时注意 |
|------|------|----------------|
| `DynamicToolRegistry` | DB 驱动动态 OpenAPI→Tool | **必须保留**，不能换成仅注解静态 Tool |
| `McpSseController` | 自研 `/mcp/{slug}/sse` | Streamable 可双轨并存，勿直接删 SSE |
| `mcp-study-client` | Spring AI MCP Client + SSE | 需确认是否支持 Streamable |
| Cursor / 其他 Agent | 多为 SSE 或逐步支持 Streamable | 需抽样验证后再默认切换 |

## 建议验证步骤（尚未实施）

1. 在旁路启用 `spring.ai.mcp.server.protocol=STREAMABLE`（或文档中的 STATELESS），观察是否与自研 `/mcp/{slug}/**` 路由冲突。
2. 若冲突：自研 Streamable 入口（`POST /mcp/{slug}`）复用同一 `DynamicToolRegistry` + 异步 `HttpToolForwarder`。
3. 用 Cursor / study-client 各连一次；压测改为「少 session、多 call」。
4. 通过后再考虑将 SSE 降为兼容模式。

## 结论（本阶段）

P0/P1（DeferredResult + WebClient 池）优先交付。P3 仅作路线记录，**不在本轮改默认协议**。
