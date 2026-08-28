# 启动与配置

本文说明如何在本机拉起 **mcp-gateway / 管理前端 / demo-biz**，以及环境变量如何配置。

## 1. 前置依赖

| 依赖 | 默认地址 | 说明 |
|------|----------|------|
| MySQL 8 | `127.0.0.1:3307` | `mcp-gateway/docker/docker-compose.yml` |
| Redis | `127.0.0.1:6379` | 限流；可复用已有容器（如 `shortlink-redis`） |
| RocketMQ NameServer | `127.0.0.1:9876` | 审计异步；可复用已有 `shortlink-rmq-namesrv` |
| JDK 17+ / Maven | — | 后端 |
| Node.js 18+ | — | 前端 |

> 审计 topic 默认 `mcp-audit_topic`，**不要**占用短链的 `short-link-stats_topic`。

## 2. 环境变量（密钥不进仓库）

每个模块提供 `.env.example`（仅变量名）。本地复制为 `.env` 并填写：

```powershell
Copy-Item mcp-gateway\.env.example mcp-gateway\.env
Copy-Item mcp-gateway\docker\.env.example mcp-gateway\docker\.env
Copy-Item demo-biz\.env.example demo-biz\.env
```

| 文件 | 用途 |
|------|------|
| `mcp-gateway/.env` | 网关 JDBC / Redis / JWT / MQ |
| `mcp-gateway/docker/.env` | compose 启动 MySQL 的 root/业务密码 |
| `demo-biz/.env` | 演示登录账号与 JWT 签发密钥 |

约定：

- `GATEWAY_JWT_SECRET` 与 `DEMO_JWT_SECRET` **必须相同**（同一 JWT 可当 MCP API Key）。
- `mcp-gateway` 的 `MYSQL_USERNAME` / `MYSQL_PASSWORD` 须与 `docker/.env` 中 `MYSQL_USER` / `MYSQL_PASSWORD` 一致。
- Spring Boot 通过 `spring.config.import=optional:file:.env[.properties]` 加载同目录 `.env`（在对应模块根目录执行 `mvn spring-boot:run`）。

`.env` 已被 `.gitignore` 忽略，**不会**推到 GitHub。

## 3. 启动顺序

### 3.1 MySQL

```powershell
cd mcp-gateway\docker
docker compose up -d
```

### 3.2 Redis / RocketMQ（若尚未运行）

```powershell
docker start shortlink-redis
docker start shortlink-rmq-namesrv shortlink-rmq-broker
```

若没有短链容器，请自行启动 Redis（6379）与 RocketMQ NameServer（9876），并在 `.env` 中改地址。

### 3.3 网关（18190）

```powershell
cd mcp-gateway
# 确保已存在 .env
mvn spring-boot:run
```

压测可关限流：

```powershell
mvn spring-boot:run "-Dspring-boot.run.profiles=bench"
```

就绪：日志出现 `Tomcat started on port 18190`，或访问 `GET /gateway/health`。

### 3.4 管理前端（5273）

```powershell
cd mcp-gateway-web
npm install
npm run dev
```

打开 http://localhost:5273

### 3.5 可选：demo-biz（8081）

```powershell
cd demo-biz
# 确保已存在 .env
mvn spring-boot:run
```

登录拿 JWT（账号密码来自你填写的 `DEMO_USERNAME` / `DEMO_PASSWORD`）：

```http
POST http://localhost:8081/api/auth/login
Content-Type: application/json

{"username":"<DEMO_USERNAME>","password":"<DEMO_PASSWORD>"}
```

## 4. 接入 Agent

### SSE（兼容 Cursor / 学习客户端）

```json
{
  "mcpServers": {
    "demo-biz": {
      "url": "http://localhost:18190/mcp/demo-biz/sse",
      "headers": {
        "Authorization": "Bearer <你的 token 或 JWT>"
      }
    }
  }
}
```

### Streamable HTTP（无 Session）

```http
POST http://localhost:18190/mcp/{slug}
Authorization: Bearer <token>
Content-Type: application/json
MCP-Protocol-Version: 2026-07-28
Mcp-Method: tools/call
Mcp-Name: <toolName>

{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"<toolName>","arguments":{}}}
```

## 5. 变量一览（名称）

### mcp-gateway

见 `mcp-gateway/.env.example`：`MYSQL_*`、`REDIS_*`、`GATEWAY_JWT_SECRET`、`ROCKETMQ_NAME_SERVER`、`GATEWAY_AUDIT_MQ_*` 等。

### docker MySQL

见 `mcp-gateway/docker/.env.example`：`MYSQL_ROOT_PASSWORD`、`MYSQL_USER`、`MYSQL_PASSWORD` 等。

### demo-biz

见 `demo-biz/.env.example`：`DEMO_JWT_SECRET`、`DEMO_USERNAME`、`DEMO_PASSWORD` 等。

## 6. 常见问题

| 现象 | 排查 |
|------|------|
| 启动报缺少 `MYSQL_PASSWORD` / `GATEWAY_JWT_SECRET` | 未创建 `.env`，或未在模块根目录启动 |
| `ERR_CONNECTION_REFUSED :5273` | 前端未起：`npm run dev` |
| `ERR_CONNECTION_REFUSED :18190` | 网关未起，或 MySQL/Redis 连不上 |
| 审计无入库 | 检查 RocketMQ NameServer、`GATEWAY_AUDIT_MQ_ENABLED`、topic 是否为 `mcp-audit_topic` |
| JWT 鉴权失败 | 网关与 demo-biz 的 JWT Secret 是否一致 |
