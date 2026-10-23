# 发版流程（RELEASE）

> 与安卓端不同，这里**一步到底**：没有"回填真实数据"环节，不需要分两阶段。
> 顺序仍然不可倒。跨平台产物（Windows zip、arm64 deb）不在本机打，交给 GitHub 的
> `release.yml` 在对应 runner 上原生构建。

## 顺序（不可倒）

```
① 改代码 → ② 改文档与版本号 → ③ tools/build.sh → ④ tools/docs-check.sh
       → ⑤ 同步产物 → ⑥ 打包 → ⑦ 真机安装确认 → ⑧ 打 tag 交给 CI → ⑨ 交付
```

先打包再改文档 = 让文档描述一个不存在的状态。

## ① 版本号只改一处

`version.txt` 是**唯一来源**（`tools/build.sh`、`tools/package.sh`、`tools/docs-check.sh` 都读它）。
改完同步：

- `README.md`：安装章节里的包名 `dsh-desktop_<ver>-1_amd64.deb`
- `docs/project/CHANGELOG-DEV.md`：新增条目（`## v<ver> · 一句话`）
- `docs/project/STATUS.md`：顶部版本与日期

## ② 文档同步（`docs-check.sh` 会查）

- `docs/project/PARITY.md`：状态列 + 末尾统计数字
- `docs/project/ROADMAP.md`：对应任务的 `done / in-progress`
- `docs/project/PITFALLS.md` + `AGENTS.md` 第 7 节：新坑两边同名
- `docs/project/TECH-DEBT.md`：清掉的债删条目，新增的债加条目
- `docs/project/CODEMAP.md`：A.2 源码表 / A.3 工具表 / A.4 工作流表

## ③④ 门禁

```sh
tools/build.sh
tools/docs-check.sh      # 必须全绿
tools/verify-files.sh    # 文件类型与解码用例
tools/verify-markdown.sh # Markdown 解析用例
```

## ⑤ 产物

| 平台 | 谁产 | 产物 | v1.0.0 实测体积 |
| --- | --- | --- | --- |
| UOS / Linux x64 | 本机 `tools/package.sh` 或 CI | `dist/dsh-desktop_<ver>-1_amd64.deb`（自带 JRE） | 91 MB |
| Linux arm64 | CI `release.yml`（ubuntu-22.04-arm） | `dsh-desktop_<ver>-1_arm64.deb` | 89 MB |
| Windows x64 | CI `release.yml`（windows-latest） | `dsh-desktop-<ver>-windows-x64.zip`，镜像自带 WiX 时另出 msi | zip 96 MB / msi 96 MB |
| 便携（Linux） | 手工，见下 | `dist/dsh-desktop-<ver>-portable-linux-x64.tar.gz`（需本机 JDK 17） | — |

**便携包**（`package.sh` 不产，手工打；`build/portable-stage` 是暂存区）：

```sh
STAGE=build/portable-stage
# 旧暂存区先挪走（不要用 rm，见 PITFALLS §rm 被 genie-trash 拦截）
[ -d "$STAGE" ] && mv "$STAGE" "build/.portable-old-$(date +%s)"
mkdir -p "$STAGE/lib"
cp build/dsh-desktop.jar "$STAGE/"
cp tools/portable/run.sh "$STAGE/run.sh" && chmod +x "$STAGE/run.sh"
for f in lib/*.jar; do
  case "$(basename $f)" in kotlin-compose-compiler-plugin*|kotlin-serialization-compiler-plugin*) continue;; esac
  cp "$f" "$STAGE/lib/"
done
tar -czf dist/dsh-desktop-<ver>-portable-linux-x64.tar.gz -C "$STAGE" .
```

**打包完必须核对三件事**（`package.sh` 已自检第一条）：

1. `dpkg-deb -c … | grep usr/share/applications` 有 `dsh-desktop.desktop`；
2. 同一条命令能看到 5 个尺寸的 hicolor png；
3. `dpkg-deb -I …` 的 `Depends` 没多出奇怪依赖。

## ⑥ 打 tag，交给 CI 出跨平台产物

本机能出的只有 amd64 deb。Windows 与 arm64 两条线由 GitHub 原生构建，所以打完本地包就推 tag：

```sh
git push gitee main
git tag -a v<ver> -m "DSH Desktop <ver>"
git push gitee v<ver>
```

随后发生两件事，顺序由 GitHub 自己保证：

1. `mirror-sync.yml`（或手工触发）把 tag 镜像到 GitHub；
2. `release.yml` 认出 `refs/tags/v*` → 三平台构建 → 建 Release 并上传
   `dsh-desktop_<ver>-1_amd64.deb`、`dsh-desktop_<ver>-1_arm64.deb`、
   `dsh-desktop-<ver>-windows-x64.zip`（+ msi，若 runner 有 WiX）与 `SHA256SUMS`。

跑没跑完：

```sh
gh run list --repo westanke/dsh-desktop --limit 5
gh run watch --repo westanke/dsh-desktop
```

想先验一遍再发版：在 Actions 页手工 `workflow_dispatch` 触发 `release.yml`，
它只构建、不建 Release，产物留在 run 的 artifact 里。

## ⑥′ Gitee 发行版（主源上不能没东西可下）

`push --mirror` 只搬 refs，**不搬 Release**。产物又是 GitHub 的机器建的，
于是会出现"Gitee 上代码齐全、下载页空空"的尴尬。所以有一条反方向的路：
由 `release.yml` 发版当场把产物搬回 Gitee，漏了则由 `mirror-sync.yml` 每 3 小时补。

```sh
# 补发：把 GitHub 上某个 tag 的 Release 整个搬到 Gitee
export GITEE_TOKEN=<Gitee 私人令牌>
python3 scripts/ci/sync-release-to-gitee.py --tag v1.0.0

# 或者直接传本机刚打的产物（不再绕 GitHub 下载一遍）
python3 scripts/ci/sync-release-to-gitee.py --tag v1.0.0 --local dist --include '*.deb'
```

搬完自己核一遍（Gitee 的 HEAD 不返回 Content-Length，只能流式取真哈希）：

```sh
curl -sL https://gitee.com/westanke/dsh-desktop/releases/download/v1.0.0/SHA256SUMS \
  | sha256sum -c - --ignore-missing   # 或逐个 curl | sha256sum 比对
```

**踩过的坑（v1.0.2）**：GitHub 的跑机往 Gitee 传附件会整段卡死——一个 38 MB 的包
传到 15 分钟没返回，把 `publish` job 拖到超时被 cancel。结果 GitHub Release 14 个资产齐全，
Gitee 一个都没落地，而 job 状态是红的。现在的处置：

- 脚本按**单个附件**给超时（`GITEE_UPLOAD_TIMEOUT`，默认 240 秒）并重试
  （`GITEE_UPLOAD_ATTEMPTS`，默认 3 次，4xx 不重试），一个包噎住不影响其余；
- 先查 Gitee 已有附件，**只下载缺的那几个**（否则每次补跑都白拖 500 MB）；
- `publish` job 超时从 15 分钟放到 40 分钟；
- Gitee 那一步失败只 warning、不判红——GitHub Release 已经是可交付物；
  定时补缺的 `mirror-sync` releases job 也带 `GITEE_SOFT_FAIL=1`，缺件只出 warning。

所以看到 `publish` 黄了**先查 Gitee 资产数**，缺就补一次（不用重打 tag）：

```sh
gh workflow run mirror-sync.yml --ref main     # 它的 releases job 会把最新 Release 搬过去
```

`gh run view <id>` 里 releases job 的日志会逐件打 `↑ 上传 … / ✓ 已上传 …`，
卡在谁身上一眼可见。

**本机补发时别踩这个**：`dist/` 是新旧版本共存的，`--local dist` 会把上个版本的包也传进
这个 tag（v1.0.2 就传进了 1.0.1 的三个包）。脚本现在按「文件名里的版本号必须等于 tag」
过滤，只警告不报错。真传错了没法单个删——Gitee 的 `assets` 只给 name/url、不给附件 id，
只能整个发行版删掉重建：

```sh
curl -s -X DELETE "https://gitee.com/api/v5/repos/westanke/dsh-desktop/releases/<id>?access_token=$GITEE_TOKEN"
# 204 即删掉；再用 --local 重跑一次会自动重建（源码包 zip/tar.gz 会再生）
```

## ⑦ 交付清单

给用户时说清三件事，别只说"修好了"：

| 要说的 | 为什么 |
| --- | --- |
| 装哪个文件、怎么装 | `sudo dpkg -i dist/dsh-desktop_<ver>-1_amd64.deb`（arm64 换 `-1_arm64.deb`，Windows 解压 zip 直接运行） |
| 装完看哪里 | 菜单「DSH 桌面」、`~/Desktop/dsh-desktop.desktop` |
| 哪些没验证 | 真机安装、界面渲染、Windows 包——**没验证就明说** |

## ⑧ 真机确认（当前必做）

本沙箱装不了包（`/var/log`、`/usr/share` 只读），所以**每次出包都必须由用户在真机装一次**。
装完检查：

```sh
ls /usr/share/applications/dsh-desktop.desktop
ls ~/Desktop/dsh-desktop.desktop
dpkg -l | grep dsh-desktop
```

## 产物目录纪律

- `dist/` 只放**能直接给用户**的文件；中间物在 `build/` 下。
- `build/` 里的旧目录用 `mv` 挪走（`build/.xxx-old-<ts>`），**不要 `rm`**（见 `PITFALLS.md` §rm 被 genie-trash 拦截）。
- 不把 `.diag/` 里的诊断数据（含真实会话内容、令牌）打进任何产物，用完即清。
