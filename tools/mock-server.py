#!/usr/bin/env python3
"""
联调用的最小模拟服务端。

只实现客户端渲染需要的端点，返回结构与 dsh-remote-bridge 的
`docs/api/openapi.yaml` 一致（字段名、包裹层级、错误模型）。
它**不是**备选后端，只用于在没有 DSH 的机器上验证界面与状态机。
"""
import json
import re
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

TOKEN = "mock-device-token"
PORT = 3091

SESSIONS = [
    {
        "id": "s-1001",
        "rootId": "r-1",
        "cwd": "dsh-remote-bridge",
        "cwdAbsolute": "/media/wangke/OFFICE/workspace/dsh-remote-bridge",
        "updatedAt": int(time.time() * 1000),
        "running": False,
        "blank": False,
        "title": "桥接 v2.5.0 升级复盘",
        "workspaceId": "w-1",
        "workspaceTitle": "dsh-remote-bridge",
        "agentPreset": "default",
    },
    {
        "id": "s-1002",
        "rootId": "r-1",
        "cwd": "dsh-remote-bridge/docs",
        "updatedAt": int(time.time() * 1000) - 3_600_000,
        "running": True,
        "blank": False,
        "title": "给 openapi 补 questions 说明",
        "workspaceId": "w-1",
        "workspaceTitle": "dsh-remote-bridge",
        "pendingInteraction": "approval",
    },
    {
        "id": "s-1003",
        "rootId": "r-1",
        "cwd": "",
        "updatedAt": int(time.time() * 1000) - 86_400_000,
        "running": False,
        "blank": False,
        "title": "桌面客户端技术选型",
        "workspaceId": "w-1",
        "workspaceTitle": "dsh-remote-bridge",
    },
]

HISTORY = {
    "events": [
        {"seq": 1, "type": "user/message", "time": 1761000000000,
         "data": {"source": {"kind": "user"}, "content": [{"type": "text", "text": "帮我看下这个客户端该用什么技术栈，要求 Windows 和 UOS 都能跑，而且不要网页套壳。"}]}},
        {"seq": 2, "type": "assistant/message", "time": 1761000001000,
         "data": {"message": {"content": [
             {"type": "reasoning", "text": "用户明确排斥 WebView 套壳，所以 Electron/Tauri 都要排除。比较 Kotlin Compose Desktop 与 Qt。"},
             {"type": "text", "text": "## 结论\n\n用 **Kotlin + Compose Multiplatform Desktop**。\n\n理由有三条：\n\n1. 界面由 Skia 直接绘制，没有 HTML/WebView，符合「非网页版本」的硬要求；\n2. 仓库自带 `kotlin-sdk`，网络层与事件模型可以直接复用，不用重新对齐 60 多个端点；\n3. 同一份代码用 `jpackage` 能同时产出 Windows 的 `msi` 和 UOS 的 `deb`。\n\n代价是 JVM 运行时体积约 60–80 MB，比原生二进制大。"},
         ]}}},
        {"seq": 3, "type": "tool/call", "time": 1761000002000,
         "data": {"turn": 1, "step": 1, "callId": "call-1", "name": "Bash",
                  "arguments": "{\"command\":\"ls kotlin-sdk/src/main/kotlin\"}"}},
        {"seq": 4, "type": "tool/result", "time": 1761000002500,
         "data": {"message": {"toolCallId": "call-1", "isError": False,
                              "content": [{"type": "text", "text": "ai/deepseek/dsh/workspace/DshWorkspaceClient.kt"}]}}},
        {"seq": 5, "type": "assistant/message", "time": 1761000003000,
         "data": {"message": {"content": [
             {"type": "text", "text": "SDK 只有一个文件，860 行左右，纯 JVM 依赖（OkHttp + kotlinx.serialization），可以直接拷进桌面工程。\n\n打包命令：\n\n```bash\n./gradlew packageDeb     # UOS\ngradlew.bat packageMsi   # Windows\n```\n\nWindows 的 msi 必须在 Windows 上打，`jpackage` 不支持交叉编译。"},
         ]}}},
        {"seq": 6, "type": "turn/end", "time": 1761000003500, "data": {"reason": {"kind": "ok"}}},
    ],
    "hasMore": False,
    "historyFormatVersion": 1,
}

FILES = {
    "": [
        {"name": "README.md", "path": "README.md", "kind": "file", "size": 22848, "modifiedAt": 1761000000.0, "writable": True},
        {"name": "src", "path": "src", "kind": "directory", "size": 0, "modifiedAt": 1761000000.0, "writable": True},
        {"name": "docs", "path": "docs", "kind": "directory", "size": 0, "modifiedAt": 1761000000.0, "writable": True},
        {"name": "package.json", "path": "package.json", "kind": "file", "size": 3068, "modifiedAt": 1761000000.0, "writable": True},
        {"name": "cordis.patch.yml", "path": "cordis.patch.yml", "kind": "file", "size": 2883, "modifiedAt": 1761000000.0, "writable": True},
    ],
    "docs": [
        {"name": "api", "path": "docs/api", "kind": "directory", "size": 0, "modifiedAt": 1761000000.0, "writable": True},
        {"name": "project", "path": "docs/project", "kind": "directory", "size": 0, "modifiedAt": 1761000000.0, "writable": True},
    ],
    "src": [
        {"name": "host", "path": "src/host", "kind": "directory", "size": 0, "modifiedAt": 1761000000.0, "writable": True},
        {"name": "client", "path": "src/client", "kind": "directory", "size": 0, "modifiedAt": 1761000000.0, "writable": True},
        {"name": "index.ts", "path": "src/index.ts", "kind": "file", "size": 4096, "modifiedAt": 1761000000.0, "writable": True},
    ],
}

README_PREVIEW = """# dsh-remote-bridge

把 DSH 内核的私有 remote 协议翻译成版本化 REST，供手机与第三方客户端远程访问这台机器。

## 对外接口

REST 基址：

    http://HOST:PORT/api/v1

除健康检查和配对交换外，请求需要设备令牌：

    Authorization: Bearer DEVICE_TOKEN

| 接口 | 用途 |
| --- | --- |
| GET /healthz | 查看服务状态、API 版本和插件版本 |
| POST /pairings/exchange | 使用一次性配对码换取设备令牌 |
| GET /roots | 列出设备可访问的文件根 |
| WS /events | 接收聊天增量、运行、审批和文件变更事件 |

完整字段见 docs/api/openapi.yaml。
"""


def envelope_error(code, message, request_id="mock"):
    return {"error": {"code": code, "message": message, "requestId": request_id}}


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, fmt, *args):
        pass

    def _send(self, payload, status=200, extra=None):
        body = json.dumps(payload).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        for key, value in (extra or {}).items():
            self.send_header(key, value)
        self.end_headers()
        self.wfile.write(body)

    def _authorized(self):
        header = self.headers.get("Authorization", "")
        return header == f"Bearer {TOKEN}"

    def do_POST(self):
        path = self.path.split("?")[0]
        length = int(self.headers.get("Content-Length") or 0)
        if length:
            self.rfile.read(length)

        if path == "/api/v1/pairings/exchange":
            self._send({
                "token": TOKEN,
                "device": {
                    "id": "dev-mock-01",
                    "name": "UOS 桌面端",
                    "scopes": ["chat.read", "chat.write", "files.read", "files.write", "files.delete"],
                    "rootIds": ["r-1"],
                },
            })
            return
        if not self._authorized():
            self._send(envelope_error("UNAUTHORIZED", "Device token required"), 401)
            return
        if path.endswith("/cancel") or path.endswith("/archive"):
            self._send({"ok": True}, 202)
            return
        if re.match(r"^/api/v1/chat/sessions/[^/]+/messages$", path):
            self._send({"accepted": True}, 202)
            return
        self._send({"ok": True}, 200)

    def do_GET(self):
        path = self.path.split("?")[0]

        if path == "/api/v1/healthz":
            self._send({"ok": True, "version": "0.2.1-rc.2", "pluginVersion": "2.5.0-mock"})
            return
        if not self._authorized():
            self._send(envelope_error("UNAUTHORIZED", "Device token required"), 401)
            return

        if path == "/api/v1/devices/self":
            self._send({
                "id": "dev-mock-01",
                "name": "UOS 桌面端",
                "scopes": ["chat.read", "chat.write", "files.read", "files.write", "files.delete"],
                "rootIds": ["r-1"],
            })
        elif path == "/api/v1/roots":
            self._send({"items": [{"id": "r-1", "label": "dsh-remote-bridge", "createdAt": 1761000000000},
                                  {"id": "r-2", "label": "工作区根目录", "createdAt": 1761000000000}]})
        elif re.match(r"^/api/v1/roots/[^/]+/entries$", path):
            query = self.path.split("?", 1)[1] if "?" in self.path else ""
            match = re.search(r"(?:^|&)path=([^&]*)", query)
            from urllib.parse import unquote
            requested = unquote(match.group(1)) if match else ""
            entries = FILES.get(requested, [])
            self._send({"path": requested, "entries": entries, "nextCursor": None})
        elif re.match(r"^/api/v1/roots/[^/]+/content$", path):
            body = README_PREVIEW.encode("utf-8")
            self.send_response(200)
            self.send_header("Content-Type", "text/markdown; charset=utf-8")
            self.send_header("Content-Length", str(len(body)))
            self.send_header("ETag", '"mock-etag-1"')
            self.end_headers()
            self.wfile.write(body)
        elif path == "/api/v1/chat/workspaces":
            self._send({"items": [{
                "id": "w-1", "title": "dsh-remote-bridge", "rootId": "r-1",
                "path": "dsh-remote-bridge",
                "createdAt": "2026-10-01T09:00:00Z", "updatedAt": "2026-10-21T10:00:00Z",
            }]})
        elif path == "/api/v1/chat/sessions":
            self._send({"items": SESSIONS})
        elif re.match(r"^/api/v1/chat/sessions/[^/]+/messages$", path):
            self._send(HISTORY)
        elif re.match(r"^/api/v1/chat/sessions/[^/]+/models$", path):
            self._send({
                "current": {"provider": "deepseek", "model": "deepseek-v4.1-flash", "reasoningEffort": "medium"},
                "routable": True,
                "groups": [{
                    "id": "deepseek", "name": "DeepSeek",
                    "models": [
                        {"id": "deepseek-v4.1-flash", "name": "V4.1 Flash", "description": "快，日常够用"},
                        {"id": "deepseek-v4.1-pro", "name": "V4.1 Pro", "description": "复杂推理"},
                    ],
                }],
                "failures": [],
            })
        elif re.match(r"^/api/v1/chat/sessions/[^/]+/approvals$", path):
            self._send({"items": []})
        elif path == "/api/v1/trash":
            self._send({"items": []})
        else:
            self._send(envelope_error("NOT_FOUND", "No such endpoint", ), 404)


if __name__ == "__main__":
    server = ThreadingHTTPServer(("127.0.0.1", PORT), Handler)
    print(f"mock bridge listening on http://127.0.0.1:{PORT}/api/v1 (token={TOKEN})")
    server.serve_forever()
