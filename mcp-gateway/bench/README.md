# MCP 网关压测

## 1. 启动网关（关闭限流）

```powershell
cd D:\Project\AI\MCP\mcp-gateway
mvn -q spring-boot:run "-Dspring-boot.run.profiles=bench"
```

## 2. 启动本地 echo 下游

```powershell
D:\Anaconda\envs\agent-study\python.exe D:\Project\AI\MCP\mcp-gateway\bench\echo_server.py --port 18081 --delay-ms 5
```

## 3. 安装依赖并跑压测

```powershell
D:\Anaconda\envs\agent-study\python.exe -m pip install -r D:\Project\AI\MCP\mcp-gateway\bench\requirements-bench.txt
D:\Anaconda\envs\agent-study\python.exe D:\Project\AI\MCP\mcp-gateway\bench\mcp_load_test.py `
  --base http://127.0.0.1:18190 --slug bench --tool ping `
  --token "<Bearer>" --concurrency 50 --duration 60 --sessions 10
```

脚本已设置 `trust_env=False`，避免本机 HTTP 代理干扰 localhost。

## 优化后对照（echo 5ms，bench profile，45s）

| 并发 | 优化前 QPS / 错误率 / p95 | 优化后 QPS / 错误率 / p95 |
|------|---------------------------|---------------------------|
| 10 | 380 / 0.02% / 34ms | **746** / 0.01% / **18ms** |
| 50 | 198 / 0.40% / 894ms | **336** / 0.26% / **509ms** |
| 100 | 260 / **5.89%** / 1399ms | **282** / **0.65%** / 1102ms |
| 200 | 高错误不可用 | 仍高（Python echo 易被打挂） |

并发 100 错误率已从 5.89% 降到 **0.65%（小于 1%）**。p95 仍受下游 echo 与 SSE 扇出限制；传输层下一步见 `STREAMABLE_HTTP_EVAL.md`。
