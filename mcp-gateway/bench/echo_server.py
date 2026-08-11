"""
极轻量 echo：供 MCP 网关压测隔离下游延迟。
用法: python echo_server.py [--port 18081] [--delay-ms 5]
"""
from __future__ import annotations

import argparse
import json
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--port", type=int, default=18081)
    parser.add_argument("--delay-ms", type=int, default=5)
    args = parser.parse_args()
    delay = max(0, args.delay_ms) / 1000.0

    class Handler(BaseHTTPRequestHandler):
        def log_message(self, fmt: str, *a) -> None:  # noqa: N802
            return

        def do_GET(self) -> None:  # noqa: N802
            if delay:
                time.sleep(delay)
            body = json.dumps({"ok": True, "path": self.path, "service": "bench-echo"}).encode()
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)

    server = ThreadingHTTPServer(("127.0.0.1", args.port), Handler)
    print(f"bench-echo listening on http://127.0.0.1:{args.port} delay_ms={args.delay_ms}", flush=True)
    server.serve_forever()


if __name__ == "__main__":
    main()
