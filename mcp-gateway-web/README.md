# MCP Gateway Web

Vue 3 + Element Plus 管理端，用于管理业务系统、导入 OpenAPI、发布 MCP Server。

## 启动

先确保后端 `mcp-gateway` 已启动（`http://localhost:18090`），以及 MySQL 容器在跑：

```bash
docker start mcp-gateway-mysql
cd D:\Project\AI\MCP\mcp-gateway
mvn spring-boot:run
```

再启动前端：

```bash
cd D:\Project\AI\MCP\mcp-gateway-web
npm install
npm run dev
```

浏览器打开：http://localhost:5173

开发环境通过 Vite 代理转发 `/api`、`/gateway` 到后端 `18090`。

## 页面

| 页面 | 功能 |
|------|------|
| 总览 | 健康状态、接入地址、工具摘要 |
| 业务系统 | 下游系统 CRUD / 认证配置 |
| 接口管理 | OpenAPI 导入、手动录入、试调用 |
| MCP 发布 | 绑定接口、发布/下线 |
| 已发布工具 | 查看当前 SSE 暴露的 tools |
