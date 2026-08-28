# MCP 多租户网关（学习仓库）

将 OpenAPI / 手动录入的 HTTP 接口转成 MCP Tools，供 Cursor、自研 Agent 接入。支持 **SSE** 与 **Streamable HTTP** 双轨入口，工具调用审计经 **RocketMQ** 异步落库。

```text
Agent / Cursor
   │  SSE: GET /mcp/{slug}/sse + POST /message
   │  或 Streamable: POST /mcp/{slug}
   ▼
mcp-gateway (:18190)
   │  DynamicToolRegistry + WebClient
   ▼
下游业务 HTTP（如 demo-biz :8081）
```

| 目录 | 说明 |
|------|------|
| `mcp-gateway/` | 网关后端（Spring Boot） |
| `mcp-gateway-web/` | 管理前端（Vue3 + Vite，端口 5273） |
| `demo-biz/` | 示例下游业务（JWT + OpenAPI） |
| `docs/` | 启动与功能说明 |

## 快速开始

完整步骤见 **[docs/START.md](docs/START.md)**。密钥一律走 `.env`（仓库只提交 `.env.example`）。

```powershell
# 1) 复制环境变量样例并自行填写
Copy-Item mcp-gateway\.env.example mcp-gateway\.env
Copy-Item mcp-gateway\docker\.env.example mcp-gateway\docker\.env
Copy-Item demo-biz\.env.example demo-biz\.env

# 2) 依赖：MySQL（compose）+ Redis +（可选）RocketMQ
cd mcp-gateway\docker
docker compose up -d
# Redis / RocketMQ 可复用本机已有容器，见 docs/START.md

# 3) 启动
cd ..\..\mcp-gateway
mvn spring-boot:run

cd ..\mcp-gateway-web
npm install
npm run dev
```

- 管理台：http://localhost:5273  
- 网关健康：http://localhost:18190/gateway/health  
- SSE：`http://localhost:18190/mcp/{slug}/sse`  
- Streamable：`POST http://localhost:18190/mcp/{slug}`

## 安全说明

- **禁止**把真实密码、JWT Secret、Access Token 写入已提交的 `application.yml`。
- 本地使用 `.env`；Git 已忽略 `.env`，仅上传 `.env.example`（只有变量名）。
- 推送前请确认 `git status` 中没有 `.env` 或含密钥的文件。

## 文档

- [启动与配置](docs/START.md)
- [功能与架构](docs/FEATURES.md)
- [网关 README](mcp-gateway/README.md)
- [压测](mcp-gateway/bench/README.md)
