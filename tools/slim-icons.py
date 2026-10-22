#!/usr/bin/env python3
"""把 material-icons-extended 瘦成"只有本工程用到的那些图标"。

动机：extended 一个 jar 36 MB（2000+ 个 Filled 图标 × 5 种风格），
而本工程只 import 23 个 —— 这一项占了安装包的四成。

做法：按 `src/main/kotlin` 里的 import 生成白名单，重写 jar，只留白名单对应的
`*Kt.class`。**顶层公共类（`Icons` 对象）不在输出里**：它们在
material-icons-core jar 中，core 会照常一起打包，删掉这里的副本免得类路径上
出现两份同名类。

常用图标（Add / Close / KeyboardArrowDown / List …）core 本来就有，extended
**不重复带** —— 所以"白名单里没在 extended 找到"不等于出错，只要 core 里有就行。
一个都找不到才判定白名单抓错（比如 import 写法变了），直接退出 1：
宁可打包失败，也不要静默少一个图标。

用法：
    tools/slim-icons.py <extended.jar> <输出.jar>
"""
import re
import sys
import zipfile
from pathlib import Path

# import androidx.compose.material.icons[.automirrored].filled.XXX  →  filled/XXXKt.class
IMPORT_RE = re.compile(
    r"^\s*import\s+androidx\.compose\.material\.icons(?:\.automirrored)?\.filled\.([A-Za-z0-9_]+)\s*$",
    re.MULTILINE,
)
# 图标资源目录：icons/<风格>/、icons/automirrored/<风格>/。里面的 class 名不规整
# （twotone 里有 `_9mpKt` 这种以数字开头的），一律整目录删，不做白名单匹配。
ICON_DIR_RE = re.compile(r"^androidx/compose/material/icons/(\w+/)?(filled|outlined|rounded|sharp|twotone)/")


def used_icon_names() -> set:
    names = set()
    src = Path(__file__).resolve().parent.parent / "src" / "main" / "kotlin"
    for path in src.rglob("*.kt"):
        names.update(IMPORT_RE.findall(path.read_text(encoding="utf-8", errors="replace")))
    return names


def icon_names_in(jar: Path) -> set:
    """这个 jar 提供的 filled 图标名（去掉 Kt 后缀）。"""
    if jar is None or not jar.exists():
        return set()
    with zipfile.ZipFile(jar) as z:
        return {
            m.group(2)
            for m in (re.match(r"^androidx/compose/material/icons/(automirrored/)?filled/([A-Za-z0-9_]+)Kt\.class$", n) for n in z.namelist())
            if m
        }


def main() -> int:
    if len(sys.argv) != 3:
        print(__doc__)
        return 2
    src, dst = Path(sys.argv[1]), Path(sys.argv[2])
    wanted = used_icon_names()
    if not wanted:
        print("✗ 没从源码抓到任何 filled 图标 import，白名单为空 —— 中止（会删光）", file=sys.stderr)
        return 1
    # core 就在同目录；常用图标归它提供
    core = sorted(src.parent.glob("material-icons-core-*.jar"))
    core_icons = icon_names_in(core[0]) if core else set()

    kept, dropped, dropped_bytes = 0, 0, 0
    with zipfile.ZipFile(src) as zin, zipfile.ZipFile(dst, "w", zipfile.ZIP_DEFLATED) as zout:
        for info in zin.infolist():
            name = info.filename
            m = re.match(r"^androidx/compose/material/icons/(automirrored/)?filled/([A-Za-z0-9_]+)Kt\.class$", name)
            if m:
                if m.group(2) in wanted:
                    zout.writestr(info, zin.read(name))
                    kept += 1
                else:
                    dropped += 1
                    dropped_bytes += info.file_size
                continue
            # 其余图标目录（outlined/rounded/sharp/twotone）一概不带；
            # 顶层公共类（Icons 等）也不带 —— core 里有
            if ICON_DIR_RE.match(name) and not name.endswith("/"):
                dropped += 1
                dropped_bytes += info.file_size
                continue
            if name.endswith((".SF", ".RSA", ".DSA")):  # 签名：删掉比留个失效签名干净
                continue
            zout.writestr(info, zin.read(name))

    provided = {n.rsplit("/", 1)[-1][:-len("Kt.class")] for n in
                zipfile.ZipFile(dst).namelist() if n.endswith("Kt.class")}
    missing = wanted - provided - core_icons
    if missing:
        print(
            f"✗ 白名单里 {len(missing)} 个图标 extended 和 core 都找不到：{sorted(missing)}"
            "（import 写法变了？）",
            file=sys.stderr,
        )
        return 1

    print(
        f"   图标瘦身：{src.stat().st_size / 1048576:.1f} MB → {dst.stat().st_size / 1048576:.1f} MB"
        f"（留 {kept} 个，另 {len(wanted & core_icons)} 个由 core 提供，"
        f"删 {dropped} 个 / {dropped_bytes / 1048576:.1f} MB）"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
