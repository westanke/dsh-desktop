#!/usr/bin/env python3
"""把 GitHub 的 Release 搬到 Gitee 发行版（单向、幂等）。

为什么需要它：Gitee 是主源，但安装包是 GitHub Actions 现场构建的，
所以 Release 天然长在 GitHub 上。mirror-sync 只搬 refs，不搬 Release——
结果就是国内用户打开 Gitee 仓库，代码齐全、**没有可下载的东西**。

两条路都能喂它：
  1. 默认：从 GitHub 下载该 tag 的资产再上传（CI 上走这条，带宽由 GitHub 出）；
  2. --local DIR：直接上传 DIR 里的产物（本机自己打的 deb 走这条，不用绕一圈）。

用法：
    sync-release-to-gitee.py --tag v1.0.0
    sync-release-to-gitee.py --tag v1.0.0 --local dist --include '*.deb'
    sync-release-to-gitee.py --tag v1.0.0 --dry-run

环境变量：
    GITHUB_TOKEN   读 GitHub Release 用（Actions 里给 secrets.GITHUB_TOKEN）
    GITEE_TOKEN    Gitee 私人令牌（需 repo 权限，能建发行版与传附件）
    GITHUB_REPOSITORY / GITEE_REPO   形如 owner/name
    GITEE_UPLOAD_TIMEOUT    单个附件上传超时秒数，默认 240
    GITEE_UPLOAD_ATTEMPTS   单个附件最多试几次，默认 3

退出码：0 全部就位；1 有资产没传上去（附件超限、令牌无效等）。
"""
import argparse
import fnmatch
import hashlib
import json
import os
import re
import sys
import tempfile
import time
import urllib.error
import urllib.request
from typing import List, Optional

API_GITHUB = "https://api.github.com"
API_GITEE = "https://gitee.com/api/v5"
CHUNK = 1 << 20
# 单个附件的超时与重试次数：Gitee 侧偶尔整段卡死，卡一次就换一次，别拖垮整个 job
UPLOAD_TIMEOUT = int(os.environ.get("GITEE_UPLOAD_TIMEOUT", "240"))
UPLOAD_ATTEMPTS = max(1, int(os.environ.get("GITEE_UPLOAD_ATTEMPTS", "3")))
# 自己不算自己：否则校验文件里会多出一条 SHA256SUMS 指向它自己，
# 用户 sha256sum -c 时会看到 "computed checksum did NOT match"（它算的是写入前的旧内容）。
SUMS_NAME = "SHA256SUMS"


def log(msg: str) -> None:
    print(msg, flush=True)


# ── HTTP 小工具 ───────────────────────────────────────────────────────────
def _request(url: str, headers=None, data=None, method=None, timeout=60):
    req = urllib.request.Request(url, data=data, headers=headers or {}, method=method)
    return urllib.request.urlopen(req, timeout=timeout)


def github_json(path: str, token: str):
    headers = {"Accept": "application/vnd.github+json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    with _request(API_GITHUB + path, headers) as resp:
        return json.load(resp)


def gitee_json(path: str, token: str, data: Optional[dict] = None, method: Optional[str] = None):
    """调 Gitee API v5。GET 把 access_token 放 query，POST 放表单体并带 Content-Type。

    踩过的坑：POST 不带 Content-Type: application/x-www-form-urlencoded 时，
    Gitee 一律回 400 Bad Request，且不会告诉你缺了什么（curl -d 默认会带上，
    所以手测能过、代码过不了）。
    """
    import urllib.parse

    params = {"access_token": token}
    if data:
        params.update(data)
    if method in ("POST", "PATCH", "DELETE"):
        body = urllib.parse.urlencode(params).encode()
        headers = {
            "Accept": "application/json",
            "Content-Type": "application/x-www-form-urlencoded",
        }
        with _request(API_GITEE + path, headers, body, method) as resp:
            raw = resp.read()
    else:
        url = API_GITEE + path + "?" + urllib.parse.urlencode(params)
        with _request(url, {"Accept": "application/json"}) as resp:
            raw = resp.read()
    return json.loads(raw) if raw else {}


def download(url: str, dest: str, token: str):
    """下载 Release 资产。api.github.com 的资产地址要给 token + octet-stream。"""
    headers = {"Accept": "application/octet-stream"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    req = urllib.request.Request(url, headers=headers)
    with urllib.request.urlopen(req, timeout=600) as resp, open(dest, "wb") as out:
        while True:
            buf = resp.read(CHUNK)
            if not buf:
                break
            out.write(buf)


def upload_attachment(repo: str, release_id: int, path: str, token: str, timeout: int = 240):
    """上传一个附件。

    踩过的坑：Gitee 的 API 文档写附件字段叫 file[]，但实际只认 file——
    用 file[] 时不管令牌放表单还是放 URL，一律回 400 {"messages":["file is invalid"]}，
    而且连几 KB 的文本文件都拒，很容易误判成"文件太大"。

    另一个坑（v1.0.2 发版踩过）：GitHub 的跑机往 Gitee 传附件偶尔会**整段卡死**，
    一个 38 MB 的包传到 15 分钟都没返回，把整个 job 拖到超时被 cancel，一个附件都没落地。
    所以超时不能给 900 秒（那是 job 级预算），要按单个附件给，卡住就换一次重来。
    """
    boundary = "----gitee-upload-boundary"
    name = os.path.basename(path)
    parts = []
    parts.append(
        f"--{boundary}\r\n"
        'Content-Disposition: form-data; name="access_token"\r\n\r\n'
        f"{token}\r\n"
    )
    parts.append(
        f"--{boundary}\r\n"
        f'Content-Disposition: form-data; name="file"; filename="{name}"\r\n'
        "Content-Type: application/octet-stream\r\n\r\n"
    )
    with open(path, "rb") as f:
        head = "".join(parts).encode()
        tail = f"\r\n--{boundary}--\r\n".encode()
        body = head + f.read() + tail
    req = urllib.request.Request(
        f"{API_GITEE}/repos/{repo}/releases/{release_id}/attach_files",
        data=body,
        headers={
            "Content-Type": f"multipart/form-data; boundary={boundary}",
            "Accept": "application/json",
        },
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        raw = resp.read()
    return json.loads(raw) if raw else {}


def sha256_of(path: str) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for buf in iter(lambda: f.read(CHUNK), b""):
            h.update(buf)
    return h.hexdigest()


# ── 主流程 ────────────────────────────────────────────────────────────────
VERSION_IN_NAME = re.compile(r"\d+\.\d+\.\d+")


def collect_local(directory: str, include: List[str], tag: str) -> List[str]:
    """收集目录里的产物，顺手挡掉**别的版本**的遗留文件。

    dist/ 是新旧版本共存的（打新版不会删旧包），`--local dist` 一把梭会把上个版本的
    包也传进这个 tag 的发行版——v1.0.2 补发时踩过：Gitee 上多出 1.0.1 的三个包。
    文件名里的版本号与 tag 不一致的一律跳过，只警告不报错。
    """
    tag_version = tag.lstrip("vV")
    found = []
    for name in sorted(os.listdir(directory)):
        if name == SUMS_NAME:
            continue
        if include and not any(fnmatch.fnmatch(name, pat) for pat in include):
            continue
        versions = set(VERSION_IN_NAME.findall(name))
        if versions and tag_version not in versions:
            log(f"· 跳过其它版本的遗留文件 {name}（本 tag 是 {tag}）")
            continue
        full = os.path.join(directory, name)
        if os.path.isfile(full):
            found.append(full)
    return found


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--tag", required=True, help="要搬的 tag，如 v1.0.0")
    ap.add_argument("--local", help="直接从本目录上传产物，不再从 GitHub 下载")
    ap.add_argument("--include", action="append", default=[], help="只上传匹配的文件名，可重复")
    ap.add_argument("--title", help="Gitee 发行版标题，默认 DSH Desktop <tag>")
    ap.add_argument("--dry-run", action="store_true", help="只打印要做的事")
    args = ap.parse_args()

    github_repo = os.environ.get("GITHUB_REPOSITORY", "westanke/dsh-desktop")
    gitee_repo = os.environ.get("GITEE_REPO", "westanke/dsh-desktop")
    gh_token = os.environ.get("GITHUB_TOKEN", "")
    gitee_token = os.environ.get("GITEE_TOKEN", "")
    # 干跑要在验令牌之前：没配 GITEE_TOKEN 的机器上也该能先看一眼会做什么
    if args.dry_run:
        log(f"[dry-run] {github_repo}@{args.tag} → Gitee {gitee_repo}（local={args.local}）")
        return 0

    if not gitee_token:
        log("::error::环境变量 GITEE_TOKEN 未设置（需要能建发行版、传附件的 Gitee 令牌）")
        return 1

    # ── 1. 先把两边的"账"对上：Gitee 上已经有什么，就不用再下载 ──────────
    # 之前是先整包下载再逐件比对，结果每次补跑都要从 GitHub 拖 500 MB，
    # 只为发现"这些早就传过了"。现在先查 Gitee，只下缺的那几个。
    work = tempfile.mkdtemp(prefix="gitee-release-")
    files: List[str] = []
    rel = None
    if args.local:
        files = collect_local(args.local, args.include, args.tag)
        if not files:
            log(f"::error::{args.local} 里没有匹配的文件")
            return 1
        body = "由 tools/package.sh 在本机构建的产物。"
    else:
        try:
            rel = github_json(f"/repos/{github_repo}/releases/tags/{args.tag}", gh_token)
        except urllib.error.HTTPError as e:
            log(f"::error::读不到 GitHub Release {args.tag}：{e.code} {e.reason}")
            return 1
        body = (rel.get("body") or "").strip() or "由 GitHub Actions 构建。"
        if not rel.get("assets"):
            log(f"::error::GitHub Release {args.tag} 没有资产")
            return 1

    # ── 2. 找到或创建 Gitee 发行版 ───────────────────────────────────────
    releases = gitee_json(f"/repos/{gitee_repo}/releases", gitee_token)
    target = next((r for r in releases if r.get("tag_name") == args.tag), None)
    if target:
        release_id = target["id"]
        existing = {a.get("name") for a in target.get("assets", [])}
        log(f"▸ Gitee 已有 {args.tag} 发行版（id={release_id}），现有附件 {len(existing)} 个")
        # 本地模式先建壳再补附件时，标题说明还是占位文案，这里改成 GitHub 那份
        if not args.local and body and body != (target.get("body") or "").strip():
            patched = gitee_json(
                f"/repos/{gitee_repo}/releases/{release_id}",
                gitee_token,
                {"tag_name": args.tag, "name": args.title or f"DSH Desktop {args.tag}", "body": body},
                method="PATCH",
            )
            if patched.get("id"):
                log("✓ 已同步发行说明")
            else:
                log(f"::warning::发行说明没能更新：{patched}")
    else:
        created = gitee_json(
            f"/repos/{gitee_repo}/releases",
            gitee_token,
            {
                "tag_name": args.tag,
                "name": args.title or f"DSH Desktop {args.tag}",
                "body": body,
                "target_commitish": "main",
            },
            method="POST",
        )
        release_id = created.get("id")
        existing = set()
        if not release_id:
            log(f"::error::建 Gitee 发行版失败：{created}")
            return 1
        log(f"▸ 已建 Gitee 发行版 {args.tag}（id={release_id}）")

    # ── 3. 只下载 Gitee 上还缺的那几个 ───────────────────────────────────
    if rel is not None:
        for asset in rel.get("assets", []):
            # --include 在下载前就过滤：省得把 90 MB 的 deb 拉下来只为跳过它
            if args.include and not any(
                fnmatch.fnmatch(asset["name"], pat) for pat in args.include
            ):
                log(f"· 跳过不匹配 --include 的资产 {asset['name']}")
                continue
            if asset["name"] in existing:
                log(f"· Gitee 上已有 {asset['name']}，不必下载")
                continue
            dest = os.path.join(work, asset["name"])
            log(f"↓ 下载 {asset['name']} · {asset['size'] // 1048576} MB")
            download(asset["url"], dest, gh_token)
            files.append(dest)
        if not files:
            log(f"✅ Gitee {args.tag} 的资产已全部就位，无需再传")
            return 0

    # 校验和跟着一起发，用户装之前能自己核一遍。
    # 只有本地模式需要自己算——从 GitHub 拉的那边，SHA256SUMS 本身就是一份资产。
    if args.local:
        sums_path = os.path.join(args.local, SUMS_NAME)
        lines = [f"{sha256_of(p)}  {os.path.basename(p)}" for p in files]
        with open(sums_path, "w", encoding="utf-8") as f:
            f.write("\n".join(lines) + "\n")
        files = files + [sums_path]

    # ── 4. 逐个上传，同名的跳过（幂等）────────────────────────────────────
    # 单件超时 + 重试：卡死的那一次只浪费 timeout 秒，不会把整个 job 拖垮
    failed = []
    for path in files:
        name = os.path.basename(path)
        if name in existing:
            # Gitee 没有"覆盖同名附件"的接口（删除要 attachment id，而发行版详情里不返回），
            # 所以重跑就跳过，保证幂等：新 tag 才是一份全新的发行版。
            log(f"· 已有同名附件，跳过 {name}")
            continue
        size_mb = os.path.getsize(path) / 1048576
        ok = False
        for attempt in range(1, UPLOAD_ATTEMPTS + 1):
            # 先打日志再发请求：卡住时日志正好停在"正在传 谁"，一眼看出是谁噎住了
            log(f"↑ 上传 {name}（{size_mb:.1f} MB）· 第 {attempt}/{UPLOAD_ATTEMPTS} 次")
            try:
                upload_attachment(gitee_repo, release_id, path, gitee_token, UPLOAD_TIMEOUT)
                log(f"✓ 已上传 {name}")
                ok = True
                break
            except urllib.error.HTTPError as e:
                detail = e.read().decode("utf-8", "replace")[:300]
                log(f"✗ 上传失败 {name}：HTTP {e.code} {detail}")
                if e.code < 500:      # 4xx 是请求本身不对，重试也没用
                    break
            except OSError as e:
                # socket.timeout / URLError / 连接被对端掐断都落在这里
                log(f"✗ 上传失败 {name}：{e.__class__.__name__}: {e}")
            if attempt < UPLOAD_ATTEMPTS:
                wait = 5 * attempt
                log(f"   {wait}s 后重试 {name}")
                time.sleep(wait)
        if not ok:
            failed.append(name)

    if failed:
        # 定时补缺的调用方（mirror-sync）用 GITEE_SOFT_FAIL=1：Gitee 传参本来就不稳，
        # 缺件只出 warning、不把每 3 小时的定时任务整片染红；真正的发版路径仍然判红。
        soft = os.environ.get("GITEE_SOFT_FAIL") == "1"
        log(f"::{('warning' if soft else 'error')}::{len(failed)} 个资产没传上去：{', '.join(failed)}")
        return 0 if soft else 1
    log(f"✅ 完成：Gitee {gitee_repo} 发行版 {args.tag}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
