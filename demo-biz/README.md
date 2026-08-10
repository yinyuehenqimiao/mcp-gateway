# demo-biz

简单 Spring Boot 单体项目：商品 / 订单增删改查 + Swagger（springdoc），内存假数据，方便导入 MCP Gateway 学习。

## 启动

```bash
cd D:\Project\AI\MCP\demo-biz
mvn spring-boot:run
```

- 端口：`8081`
- Swagger UI：http://localhost:8081/swagger-ui.html
- OpenAPI JSON：http://localhost:8081/v3/api-docs

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

## 导入 MCP Gateway（自己操作）

1. 启动本项目
2. 打开管理端 http://localhost:5173
3. 新建业务系统：`baseUrl = http://localhost:8081`
4. 接口管理 → 导入 OpenAPI，URL 填：`http://localhost:8081/v3/api-docs`
5. 创建 MCP Server 并绑定接口 → 发布

也可直接在 Swagger UI 先把接口调通，再导入 MCP。
