#!/bin/bash
# 文档一致性门禁。
#
# 检查的都是**能机检、且漂了就一定出错**的东西：
#   1. AGENTS.md 引用的八份文档是否都在
#   2. 版本号是否一处定义、多处一致
#   3. PARITY.md 末尾的统计数字是否与表格实际计数相符
#   4. PITFALLS.md 的条目与 AGENTS.md 第 7 节索引是否一一同名
#   5. 800 行/文件预算（超预算必须在 TECH-DEBT 里登记）
#   6. README 里不许留占位符
#
# 用法：tools/docs-check.sh        全绿退出 0，任一 FAIL 退出 1
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT" || exit 1

python3 - "$ROOT" <<'PY'
import os
import re
import sys
import glob

root = sys.argv[1]
fails = []
checks = 0


def check(name, ok, detail=""):
    global checks
    checks += 1
    if ok:
        print(f"  ✓ {name}")
    else:
        print(f"  ✗ {name}" + (f" — {detail}" if detail else ""))
        fails.append(name)


def read(rel):
    path = os.path.join(root, rel)
    if not os.path.exists(path):
        return None
    with open(path, encoding="utf-8") as f:
        return f.read()


# ── 1. 文档齐全 ────────────────────────────────────────────────────────────
print("▸ 1/7 文档齐全")
DOCS = [
    "AGENTS.md",
    "README.md",
    "docs/project/STATUS.md",
    "docs/project/ROADMAP.md",
    "docs/project/ARCHITECTURE.md",
    "docs/project/PARITY.md",
    "docs/project/PITFALLS.md",
    "docs/project/TECH-DEBT.md",
    "docs/project/TESTING.md",
    "docs/project/RELEASE.md",
    "docs/project/CODEMAP.md",
    "docs/project/CHANGELOG-DEV.md",
]
missing = [d for d in DOCS if not os.path.exists(os.path.join(root, d))]
check(f"{len(DOCS)} 份文档存在", not missing, f"缺：{', '.join(missing)}")

# AGENTS.md 里提到的 docs/project/*.md 必须真实存在
agents = read("AGENTS.md") or ""
referenced = set(re.findall(r"docs/project/([A-Za-z-]+\.md)", agents))
for name in sorted(referenced):
    check(f"AGENTS 引用的 {name} 存在",
          os.path.exists(os.path.join(root, "docs/project", name)))

# ── 2. 版本号 ──────────────────────────────────────────────────────────────
# 单一来源是仓库根的 version.txt：两个构建脚本都从它取，文档里的写法要跟它对上。
print("▸ 2/7 版本号一致")
pkg = read("tools/package.sh") or ""
bld = read("tools/build.sh") or ""
ver = (read("version.txt") or "").strip()
check("version.txt 定义了版本号",
      re.fullmatch(r"[0-9]+\.[0-9]+\.[0-9]+", ver) is not None,
      f"当前内容：{ver!r}")
if ver and re.fullmatch(r"[0-9]+\.[0-9]+\.[0-9]+", ver):
    check("package.sh 从 version.txt 取版本", "version.txt" in pkg, "应读 version.txt")
    check("build.sh 从 version.txt 取版本", "version.txt" in bld, "应读 version.txt")
    readme = read("README.md") or ""
    check("README 包名版本一致",
          f"dsh-desktop_{ver}-1_amd64.deb" in readme, f"应为 dsh-desktop_{ver}-1_amd64.deb")
    status = read("docs/project/STATUS.md") or ""
    check("STATUS 版本一致", f"**{ver}**" in status, f"应为 **{ver}**")
    changelog = read("docs/project/CHANGELOG-DEV.md") or ""
    check("CHANGELOG 有本版条目", f"v{ver}" in changelog, f"应为 v{ver}")

# ── 3. PARITY 统计 ─────────────────────────────────────────────────────────
print("▸ 3/7 PARITY 统计与表格相符")
parity = read("docs/project/PARITY.md") or ""
section = parity.split("## 二、能力矩阵", 1)[-1].split("## 三、", 1)[0]
counts = {"✓": 0, "△": 0, "✗": 0}
for line in section.splitlines():
    cells = [c.strip() for c in line.split("|")]
    if len(cells) >= 4 and cells[2] in counts:
        counts[cells[2]] += 1
m = re.search(r"\*\*统计\*\*：✓ (\d+) 项 · △ (\d+) 项 · ✗ (\d+) 项", parity)
if not m:
    check("PARITY 有统计行", False, "应在正文里写 **统计**：✓ N 项 · △ N 项 · ✗ N 项")
else:
    declared = {"✓": int(m.group(1)), "△": int(m.group(2)), "✗": int(m.group(3))}
    check("PARITY 统计 = 表格实际",
          declared == counts,
          f"声明 {declared}，实际 {counts}")

# ── 4. 坑位同名 ────────────────────────────────────────────────────────────
print("▸ 4/7 坑位索引与正文同名")
pitfalls = read("docs/project/PITFALLS.md") or ""
bodies = re.findall(r"^### (.+)$", pitfalls, re.M)
bodies = [b.strip() for b in bodies]

# AGENTS 第 7 节：形如 `- \`名字\``
sec = agents.split("## 7. 坑位索引", 1)[-1].split("## 8.", 1)[0]
index = [x.strip() for x in re.findall(r"^- `([^`]+)`", sec, re.M)]

check("PITFALLS 条目非空", len(bodies) > 0, f"实际 {len(bodies)} 条")
check("AGENTS 索引非空", len(index) > 0, f"实际 {len(index)} 条")

only_body = [b for b in bodies if b not in index]
only_index = [i for i in index if i not in bodies]
check("正文条目都在索引里", not only_body, f"漏列：{' / '.join(only_body)}")
check("索引条目都在正文里", not only_index, f"正文缺：{' / '.join(only_index)}")

# ── 5. 代码地图与代码双向一致 ──────────────────────────────────────────────
print("▸ 5/7 CODEMAP 与代码双向一致")
codemap = read("docs/project/CODEMAP.md") or ""

src_files = {
    os.path.basename(p)
    for p in glob.glob(os.path.join(root, "src/**/*.kt"), recursive=True)
}
# A.2 表里每行形如 `src/Xxx.kt` 或 `src/data/Xxx.kt`，只比对文件名
listed = {
    os.path.basename(p)
    for p in re.findall(r"`src/([A-Za-z0-9_/]+\.kt)`", codemap)
}

check("CODEMAP 登记了源码文件", len(listed) > 0, "A.2 表格里没找到 `src/Xxx.kt`")
unlisted = sorted(src_files - listed)
ghost = sorted(listed - src_files)
check("src 下每个 .kt 都登记了", not unlisted, f"未登记：{', '.join(unlisted)}")
check("登记的 .kt 都还存在", not ghost, f"已不存在：{', '.join(ghost)}")

# A.3 里引用的 tools/ 与 build-windows.bat 必须存在
tool_refs = set(re.findall(r"`((?:tools/|build-windows\.bat)[^`\s]*)`", codemap))
missing_tools = [p for p in sorted(tool_refs) if not os.path.exists(os.path.join(root, p.rstrip("/")))]
check("CODEMAP 引用的工具脚本都存在", not missing_tools, f"缺：{', '.join(missing_tools)}")

# B 表里写的 `DshApi.xxx()` / `ApiExt.xxx()` 必须真有这个方法
api_src = (read("src/main/kotlin/io/github/westanke/dshdesktop/data/DshApi.kt") or "") + \
          (read("src/main/kotlin/io/github/westanke/dshdesktop/data/ApiExt.kt") or "")
method_refs = set(re.findall(r"(?:DshApi|ApiExt)\.([A-Za-z][A-Za-z0-9]*)\(", codemap))
missing_methods = [
    m for m in sorted(method_refs)
    if f"fun {m}(" not in api_src and f".{m}(" not in api_src
]
check("接口表引用的方法都存在", not missing_methods, f"缺：{', '.join(missing_methods)}")

# ── 6. 行数预算 ────────────────────────────────────────────────────────────
print("▸ 6/7 行数预算 800")
debt = read("docs/project/TECH-DEBT.md") or ""
over = []
for path in glob.glob(os.path.join(root, "src/**/*.kt"), recursive=True):
    with open(path, encoding="utf-8", errors="ignore") as f:
        n = sum(1 for _ in f)
    if n > 800:
        base = os.path.basename(path)
        over.append((os.path.relpath(path, root), n, base in debt))
for rel, n, registered in over:
    check(f"{rel} 超预算但已登记", registered, f"{n} 行，需在 TECH-DEBT 例外表登记")
if not over:
    check("无文件超预算", True)

# ── 6. 占位符 ──────────────────────────────────────────────────────────────
print("▸ 7/7 无占位符")
readme = read("README.md") or ""
marks = [w for w in ("TODO", "TBD", "FIXME", "待补充") if w in readme]
check("README 无 TODO/TBD/待补充", not marks, f"含：{', '.join(marks)}")

# ── 收尾 ───────────────────────────────────────────────────────────────────
print()
if fails:
    print(f"✗ {len(fails)}/{checks} 项未通过：")
    for f in fails:
        print(f"   · {f}")
    sys.exit(1)
print(f"✓ 全部通过（{checks} 项检查）")
PY
