# demo-biz

简单 Spring Boot 单体：商品 / 订单增删改查 + **JWT 鉴权** + Swagger，方便导入 MCP Gateway 联调。

## 启动

```bash
cd D:\Project\AI\MCP\demo-biz
mvn spring-boot:run
```

- 端口：`8081`
- Swagger UI：http://localhost:8081/swagger-ui.html
- OpenAPI JSON：http://localhost:8081/v3/api-docs（文档接口不需 JWT）

## 登录拿 JWT

```http
POST http://localhost:8081/api/auth/login
Content-Type: application/json

{"username":"demo","password":"demo123"}
```

返回 `accessToken`。之后调用 `/api/products/**`、`/api/orders/**` 需：

```http
Authorization: Bearer <accessToken>
```

默认密钥见 `application.yml` 的 `demo.jwt.secret`，需与 mcp-gateway 的 `gateway.jwt.secret` 一致。

## 与 MCP Gateway 联调要点

可以把 **同一个 JWT** 当作 MCP 的 API Key：

1. Cursor / Client 连接 `http://localhost:18190/mcp/demo-biz/sse` 时带 `Authorization: Bearer <jwt>`
2. `tools/list` / `tools/call` 无 Key 会被拒绝
3. 网关转发下游时会把该 Bearer 透传给 demo-biz

业务系统建议配置：

- `authType`: `BEARER`
- `authConfig`: `{"useCallerToken":true}`

## 假数据

### 商品

| ID | 名称 | 分类 | 价格 | 库存 | 上架 |
|----|------|------|------|------|------|
| p1001 | 机械键盘 | 数码 | 299 | 50 | 是 |
| p1002 | 显示器 27寸 | 数码 | 1299 | 20 | 是 |
| p1003 | 办公椅 | 家具 | 599 | 15 | 是 |
| p1004 | 保温杯 | 日用 | 69 | 200 | 否 |

### 订单

| ID | 用户 | 商品 | 数量 | 状态 |
|----|------|------|------|------|
| o2001 | u1 | p1001 | 2 | PAID |
| o2002 | u1 | p1002 | 1 | CREATED |
| o2003 | u2 | p1003 | 1 | CANCELLED |
