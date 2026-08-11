"""
稳定假下游：asyncio + aiohttp，适合网关高并发压测。

相对 ThreadingHTTPServer：事件循环 + 连接复用，不会因线程爆炸出现 Connection refused。

用法:
  python echo_server.py --port 18081 --delay-ms 5
  python echo_server.py --port 18081 --delay-ms 0 --workers 1
"""
from __future__ import annotations

import argparse
import asyncio
import json
import time
from typing import Any

from aiohttp import web


def build_app(delay_ms: int) -> web.Application:
    delay = max(0, delay_ms) / 1000.0
    started = time.time()
    stats: dict[str, Any] = {"requests": 0, "inflight": 0}

    async def handle_ping(request: web.Request) -> web.Response:
        stats["requests"] += 1
        stats["inflight"] += 1
        try:
            if delay:
                await asyncio.sleep(delay)
            body = {
                "ok": True,
                "path": request.path_qs,
                "service": "bench-echo-stable",
                "delay_ms": delay_ms,
                "uptime_s": round(time.time() - started, 1),
            }
            return web.json_response(body)
        finally:
            stats["inflight"] -= 1

    async def handle_echo(request: web.Request) -> web.Response:
        stats["requests"] += 1
        stats["inflight"] += 1
        try:
            if delay:
                await asyncio.sleep(delay)
            payload: Any
            if request.can_read_body:
                try:
                    payload = await request.json()
                except Exception:  # noqa: BLE001
                    raw = await request.read()
                    payload = raw.decode("utf-8", errors="replace")
            else:
                payload = None
            return web.json_response(
                {
                    "ok": True,
                    "method": request.method,
                    "path": request.path_qs,
                    "service": "bench-echo-stable",
                    "payload": payload,
                }
            )
        finally:
            stats["inflight"] -= 1

    async def handle_health(_: web.Request) -> web.Response:
        return web.json_response(
            {
                "status": "UP",
                "service": "bench-echo-stable",
                "requests": stats["requests"],
                "inflight": stats["inflight"],
                "uptime_s": round(time.time() - started, 1),
            }
        )

    app = web.Application()
    app.router.add_get("/ping", handle_ping)
    app.router.add_get("/health", handle_health)
    app.router.add_route("*", "/echo", handle_echo)
    app.router.add_route("*", "/{tail:.*}", handle_ping)
    return app


def main() -> None:
    parser = argparse.ArgumentParser(description="Stable fake downstream for MCP gateway bench")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=18081)
    parser.add_argument("--delay-ms", type=int, default=5)
    parser.add_argument("--backlog", type=int, default=2048)
    args = parser.parse_args()

    app = build_app(args.delay_ms)
    print(
        f"bench-echo-stable listening on http://{args.host}:{args.port} "
        f"delay_ms={args.delay_ms} backlog={args.backlog}",
        flush=True,
    )
    web.run_app(
        app,
        host=args.host,
        port=args.port,
        backlog=args.backlog,
        access_log=None,
        print=None,
        handle_signals=True,
        shutdown_timeout=3.0,
    )


if __name__ == "__main__":
    main()
