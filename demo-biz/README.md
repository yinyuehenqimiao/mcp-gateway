# demo-biz

示例下游业务服务：JWT 登录 + 商品/订单 API + OpenAPI（`/v3/api-docs`），供 mcp-gateway 导入与转发联调。

## 配置

密钥与演示账号走环境变量，**不要**提交 `.env`：

```powershell
Copy-Item .env.example .env
# 填写 DEMO_JWT_SECRET / DEMO_USERNAME / DEMO_PASSWORD
# DEMO_JWT_SECRET 必须与网关 GATEWAY_JWT_SECRET 相同
```

变量说明见 [`.env.example`](.env.example) 与 [docs/START.md](../docs/START.md)。

## 启动

```powershell
mvn spring-boot:run
```

默认端口由 `SERVER_PORT` 控制（样例为 8081）。

## 登录拿 JWT

```http
POST http://localhost:8081/api/auth/login
Content-Type: application/json

{"username":"<DEMO_USERNAME>","password":"<DEMO_PASSWORD>"}
```

返回 `accessToken`。业务接口需：

```http
Authorization: Bearer <accessToken>
```

## 与网关联调

1. Agent 连接 `http://localhost:18190/mcp/demo-biz/sse`（或 Streamable `POST /mcp/demo-biz`）时带同一 JWT  
2. 业务系统认证选「JWT 透传」时，网关会把该 Bearer 转给 demo-biz  
