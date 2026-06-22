#!/usr/bin/env bash
#
# 编译并运行 Java SDK 单元测试（无 Maven/JUnit，纯 JDK）。
#
# 步骤：
#   1. javac 编译 src/main/java + tests 到 out/
#   2. java 运行断言 runner（VectorTest），读取 ../test-vectors.json
#   3. 断言失败 → runner 非 0 退出 → 本脚本非 0 退出
#
# 用法：bash run-tests.sh
#
set -euo pipefail

# 切到脚本所在目录，保证相对路径（../test-vectors.json）稳定
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

OUT_DIR="out"
SRC_DIR="src/main/java"
TEST_DIR="tests"

echo "==> 清理输出目录 $OUT_DIR"
rm -rf "$OUT_DIR"
mkdir -p "$OUT_DIR"

echo "==> 收集源文件"
SOURCES=$(find "$SRC_DIR" "$TEST_DIR" -name '*.java')

echo "==> 编译 (javac, release 17)"
javac --release 17 -encoding UTF-8 -d "$OUT_DIR" $SOURCES

echo "==> 运行测试 (VectorTest)"
# -Dfile.encoding=UTF-8 保证非 ASCII 向量正确读取与比较
java -Dfile.encoding=UTF-8 -cp "$OUT_DIR" VectorTest

echo "==> 测试通过"
