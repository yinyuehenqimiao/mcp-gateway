"""
MCP 网关并发压测：SSE 建连 + tools/call。

示例:
  D:\\Anaconda\\envs\\agent-study\\python.exe mcp_load_test.py ^
    --base http://localhost:18190 --slug bench --tool ping ^
    --token "<JWT>" --concurrency 50 --duration 60
"""
from __future__ import annotations

import argparse
import asyncio
import json
import statistics
import time
from dataclasses import dataclass, field
from typing import Any

import httpx


@dataclass
class Stats:
    latencies_ms: list[float] = field(default_factory=list)
    errors: list[str] = field(default_factory=list)
    ok: int = 0
    fail: int = 0

    def add_ok(self, ms: float) -> None:
        self.ok += 1
        self.latencies_ms.append(ms)

    def add_fail(self, ms: float, err: str) -> None:
        self.fail += 1
        self.latencies_ms.append(ms)
        if len(self.errors) < 20:
            self.errors.append(err)


def percentile(sorted_vals: list[float], p: float) -> float:
    if not sorted_vals:
        return 0.0
    if len(sorted_vals) == 1:
        return sorted_vals[0]
    k = (len(sorted_vals) - 1) * p
    f = int(k)
    c = min(f + 1, len(sorted_vals) - 1)
    if f == c:
        return sorted_vals[f]
    return sorted_vals[f] + (sorted_vals[c] - sorted_vals[f]) * (k - f)


async def call_tool(
    client: httpx.AsyncClient,
    base: str,
    slug: str,
    session_id: str,
    token: str,
    tool: str,
    arguments: dict[str, Any],
    req_id: int,
) -> tuple[bool, float, str]:
    url = f"{base.rstrip('/')}/mcp/{slug}/message"
    headers = {
        "Authorization": f"Bearer {token}",
        "Content-Type": "application/json",
        "Accept": "application/json",
    }
    payload = {
        "jsonrpc": "2.0",
        "id": req_id,
        "method": "tools/call",
        "params": {"name": tool, "arguments": arguments},
    }
    t0 = time.perf_counter()
    try:
        resp = await client.post(
            url,
            params={"sessionId": session_id},
            headers=headers,
            json=payload,
            timeout=30.0,
        )
        ms = (time.perf_counter() - t0) * 1000.0
        if resp.status_code != 200:
            return False, ms, f"HTTP {resp.status_code}: {resp.text[:200]}"
        data = resp.json()
        if "error" in data:
            return False, ms, f"rpc error: {data['error']}"
        result = data.get("result") or {}
        if result.get("isError"):
            text = ""
            content = result.get("content") or []
            if content and isinstance(content, list):
                text = str(content[0].get("text", ""))[:200]
            return False, ms, f"tool isError: {text}"
        return True, ms, "ok"
    except Exception as ex:  # noqa: BLE001
        ms = (time.perf_counter() - t0) * 1000.0
        return False, ms, str(ex)


async def worker(
    name: int,
    client: httpx.AsyncClient,
    base: str,
    slug: str,
    token: str,
    tool: str,
    arguments: dict[str, Any],
    session_ids: list[str],
    stop_at: float,
    stats: Stats,
    lock: asyncio.Lock,
    counter: dict[str, int],
) -> None:
    while time.perf_counter() < stop_at:
        async with lock:
            counter["n"] += 1
            req_id = counter["n"]
        sid = session_ids[req_id % len(session_ids)]
        ok, ms, err = await call_tool(client, base, slug, sid, token, tool, arguments, req_id)
        if ok:
            stats.add_ok(ms)
        else:
            stats.add_fail(ms, f"w{name}: {err}")


async def run(args: argparse.Namespace) -> None:
    arguments = json.loads(args.arguments) if args.arguments else {}
    limits = httpx.Limits(max_connections=max(args.concurrency * 2, 100), max_keepalive_connections=100)
    timeout = httpx.Timeout(30.0, connect=10.0)
    stop = asyncio.Event()
    sse_tasks: list[asyncio.Task] = []
    session_ids: list[str] = []

    # trust_env=False：避免本机 HTTP_PROXY 把 localhost 也代理掉导致 ConnectError
    async with httpx.AsyncClient(limits=limits, timeout=timeout, http2=False, trust_env=False) as client:
        session_count = max(1, min(args.sessions, args.concurrency))
        print(f"opening {session_count} SSE session(s)...", flush=True)

        async def hold_sse(ready: asyncio.Future[str], sse_url: str, sse_headers: dict[str, str]) -> None:
            try:
                async with client.stream("GET", sse_url, headers=sse_headers, timeout=None) as resp:
                    if resp.status_code != 200:
                        body = (await resp.aread()).decode("utf-8", errors="replace")
                        if not ready.done():
                            ready.set_exception(RuntimeError(f"SSE HTTP {resp.status_code}: {body}"))
                        return
                    event_name = None
                    async for line in resp.aiter_lines():
                        if stop.is_set():
                            break
                        if line.startswith("event:"):
                            event_name = line[6:].strip()
                        elif line.startswith("data:") and event_name == "endpoint":
                            data = line[5:].strip()
                            sid = data.split("sessionId=", 1)[1].strip()
                            if not ready.done():
                                ready.set_result(sid)
                        elif line == "":
                            event_name = None
            except Exception as ex:  # noqa: BLE001
                if not ready.done():
                    ready.set_exception(ex)

        for _ in range(session_count):
            url = f"{args.base.rstrip('/')}/mcp/{args.slug}/sse"
            headers = {"Authorization": f"Bearer {args.token}", "Accept": "text/event-stream"}
            ready: asyncio.Future[str] = asyncio.get_running_loop().create_future()
            task = asyncio.create_task(hold_sse(ready, url, headers))
            sse_tasks.append(task)
            sid = await asyncio.wait_for(ready, timeout=30.0)
            session_ids.append(sid)
            print(f"  sessionId={sid}", flush=True)

        stats = Stats()
        lock = asyncio.Lock()
        counter = {"n": 0}
        stop_at = time.perf_counter() + args.duration
        print(
            f"start load: concurrency={args.concurrency} duration={args.duration}s "
            f"tool={args.tool} slug={args.slug}",
            flush=True,
        )
        t0 = time.perf_counter()
        workers = [
            asyncio.create_task(
                worker(i, client, args.base, args.slug, args.token, args.tool, arguments, session_ids, stop_at, stats, lock, counter)
            )
            for i in range(args.concurrency)
        ]
        await asyncio.gather(*workers)
        elapsed = time.perf_counter() - t0

        stop.set()
        for t in sse_tasks:
            t.cancel()
        await asyncio.gather(*sse_tasks, return_exceptions=True)

    total = stats.ok + stats.fail
    lats = sorted(stats.latencies_ms)
    qps = total / elapsed if elapsed > 0 else 0.0
    print("\n========== RESULT ==========")
    print(f"elapsed_s     : {elapsed:.2f}")
    print(f"total         : {total}")
    print(f"ok / fail     : {stats.ok} / {stats.fail}")
    print(f"error_rate    : {(stats.fail / total * 100) if total else 0:.2f}%")
    print(f"QPS           : {qps:.2f}")
    if lats:
        print(f"latency_ms avg: {statistics.fmean(lats):.2f}")
        print(f"latency_ms p50: {percentile(lats, 0.50):.2f}")
        print(f"latency_ms p95: {percentile(lats, 0.95):.2f}")
        print(f"latency_ms p99: {percentile(lats, 0.99):.2f}")
        print(f"latency_ms max: {lats[-1]:.2f}")
    if stats.errors:
        print("sample_errors:")
        for e in stats.errors:
            print(f"  - {e}")
    print("============================\n")


def main() -> None:
    p = argparse.ArgumentParser(description="MCP gateway tools/call load test")
    p.add_argument("--base", default="http://localhost:18190")
    p.add_argument("--slug", required=True)
    p.add_argument("--tool", required=True)
    p.add_argument("--token", required=True)
    p.add_argument("--concurrency", type=int, default=20)
    p.add_argument("--duration", type=int, default=30, help="seconds")
    p.add_argument("--sessions", type=int, default=10, help="SSE sessions to keep")
    p.add_argument("--arguments", default="{}", help="JSON object for tool arguments")
    args = p.parse_args()
    asyncio.run(run(args))


if __name__ == "__main__":
    main()
