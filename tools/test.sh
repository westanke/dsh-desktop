#!/bin/bash
# 纯逻辑测试套件：**不依赖 Gradle、不依赖真机/模拟器，离线可跑**。
#
# 用法：
#   tools/test.sh                    # 跑全部离线套件（约 30 秒）
#   tools/test.sh <history.json>     # 额外用真实历史跑 verify-history 与 verify-markdown
#
# 怎么拿 history.json（令牌先解出来，别提交这个文件）：
#   curl -s -H "Authorization: Bearer $TOKEN" \
#     http://127.0.0.1:3090/api/v1/chat/sessions/<id>/messages > h.json
#
# 套件清单（各自也可单独跑，新增套件时往 OFFLINE_SUITES 里加一行）：
#   verify-files      文件类型判定与文本解码（49 项）
#   verify-markdown   Markdown 解析 + 链接分类 + 行号后缀（内置 37 项）
#   verify-parity     第二批对标纯函数：档位/版本/渠道/清单/资产/schema 摊平（82 项）
#   verify-history    历史投影回归 + mergeLatestPage（需要 history.json）
#
# 为什么是"Kotlin main + 手写断言"而不是 JUnit：本机 4GB 地址空间会打死 Gradle，
# lib/ 里也没有测试框架（TECH-DEBT DEBT-001）。断言助手在 tools/VerifySupport.kt。
# 退出码：0 全过 · 1 有套件失败 · 2 用法错误。
set -uo pipefail   # 刻意不用 -e：要收齐所有套件的结果，不能第一个红就退

ROOT="$(cd "$(dirname "$0")/.." && pwd)"

OFFLINE_SUITES=(verify-files verify-markdown verify-parity)

if [ "${1:-}" = "-h" ] || [ "${1:-}" = "--help" ]; then
  sed -n '2,26p' "$0" | sed 's/^# \{0,1\}//; s/\*\*//g'
  exit 0
fi

HISTORY_INPUT="${1:-}"
if [ -n "$HISTORY_INPUT" ] && [ ! -f "$HISTORY_INPUT" ]; then
  echo "找不到 history.json：$HISTORY_INPUT" >&2
  exit 2
fi

overall=0
ran=0

for suite in "${OFFLINE_SUITES[@]}"; do
  echo "═══ $suite ═══"
  if bash "$ROOT/tools/$suite.sh"; then
    echo "► $suite 通过"
  else
    echo "► $suite 失败"
    overall=1
  fi
  ran=$((ran + 1))
  echo
done

echo "═══ verify-history ═══"
if [ -n "$HISTORY_INPUT" ]; then
  if bash "$ROOT/tools/verify-history.sh" "$HISTORY_INPUT"; then
    echo "► verify-history 通过"
  else
    echo "► verify-history 失败"
    overall=1
  fi
  # 同一份真实数据再过一遍 Markdown（表格形状断言在内置用例里没有）
  if bash "$ROOT/tools/verify-markdown.sh" "$HISTORY_INPUT"; then
    echo "► verify-markdown（真实数据）通过"
  else
    echo "► verify-markdown（真实数据）失败"
    overall=1
  fi
  ran=$((ran + 2))
else
  echo "  （跳过：没有 history.json。传一个路径即跑；它只读文件、不联网）"
fi

echo
echo "═══ 汇总 ═══"
if [ "$overall" -eq 0 ]; then
  echo "✓ 纯逻辑套件全部通过（离线 $ran 项）"
else
  echo "✗ 有套件失败（离线 $ran 项）——往上翻看具体用例"
fi
exit "$overall"
