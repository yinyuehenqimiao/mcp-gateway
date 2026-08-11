# MCP 网关压测

## 1. 启动网关（关闭限流）

```powershell
cd D:\Project\AI\MCP\mcp-gateway
mvn -q spring-boot:run "-Dspring-boot.run.profiles=bench"
```

## 2. 启动本地稳定假下游（aiohttp）

```powershell
D:\Anaconda\envs\agent-study\python.exe -m pip install -q -r D:\Project\AI\MCP\mcp-gateway\bench\requirements-bench.txt
D:\Anaconda\envs\agent-study\python.exe D:\Project\AI\MCP\mcp-gateway\bench\echo_server.py --port 18081 --delay-ms 5
```

健康检查：`GET http://127.0.0.1:18081/health`。默认 `/ping` 固定延迟 `delay-ms`（压测隔离下游）。

## 3. 安装依赖并跑压测

```powershell
D:\Anaconda\envs\agent-study\python.exe -m pip install -r D:\Project\AI\MCP\mcp-gateway\bench\requirements-bench.txt
D:\Anaconda\envs\agent-study\python.exe D:\Project\AI\MCP\mcp-gateway\bench\mcp_load_test.py `
  --base http://127.0.0.1:18190 --slug bench --tool ping `
  --token "<Bearer>" --concurrency 50 --duration 60 --sessions 10
```

脚本已设置 `trust_env=False`，避免本机 HTTP 代理干扰 localhost。

## 优化后对照（稳定 aiohttp echo 5ms，bench profile，45s，Streamable）

| 并发 | QPS | 错误率 | p50 | p95 |
|------|-----|--------|-----|-----|
| 10 | **538** | **0.00%** | 16ms | **33ms** |
| 50 | 301 | **0.00%** | 96ms | 546ms |
| 100 | 255 | **0.00%** | 238ms | 1210ms |
| 200 | 193 | **0.00%** | 619ms | 3439ms |

同条件 SSE 并发 100：QPS **216** / 错误率 **0.00%** / p95 1401ms（低于 Streamable 的 255 QPS）。

此前 Python `ThreadingHTTPServer` 在高并发下易 `Connection refused`；现改为 aiohttp 假下游后错误率清零，剩余延迟主要来自网关侧排队与 MQ 审计热路径开销。
