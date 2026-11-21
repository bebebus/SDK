# 测试覆盖率与限制

覆盖率用于发现未测试的路径，不证明安全或正确。各工具采用不同口径，不应把它们的百分比相加，也不能将行覆盖率当作语句覆盖率。下述范围仅为各语言 SDK 的实现，示例、生成的附录和测试脚本不计入；评估整个项目时必须另行核对这些范围及未覆盖的语言。

## 自动执行

[CI](./.github/workflows/ci.yml) 对每次 push 和 PR 测试五语言。Node.js 使用 Node 内置覆盖率（`nodejs/src/**`），最低行覆盖率 90%、分支覆盖率 80%；Python 使用固定哈希的 coverage.py（`openapi_sdk`），分别要求语句 90%、分支 80%；Go 对根 SDK 包单独计算语句覆盖率，要求 90%，并独立运行其他子包测试。阈值只覆盖所列范围。

本地执行 Python 时先在虚拟环境运行 `python -m pip install --require-hashes -r python/requirements-coverage.txt`，再在 `python/` 中运行：

```bash
python -m coverage run --timid --branch --source=openapi_sdk -m unittest discover -s tests
python -m coverage report -m
python -m coverage json -o coverage.json
python ../_tooling/check-python-coverage.py coverage.json
```

## 补充测量

PHP：安装与 PHP 版本匹配的 Xdebug 后，在 `php/` 中执行：

```bash
XDEBUG_MODE=coverage php -d auto_prepend_file=../_tooling/php-coverage.php tests/run.php
```

此方法统计执行过程中**已加载**的 `php/src/` 文件的可执行行，不能证明未加载文件已被覆盖。测试包含本地回环 HTTP 服务，不访问外部支付接口。

Java：准备同版本的 JaCoCo agent 和 CLI，分别将其绝对路径传给以下命令。脚本中的每个 Java 测试进程都会写入同一份执行数据（也包括编译进程；报告只读取 SDK 类）：

```bash
cd java
rm -f /tmp/sdk-jacoco.exec
JAVA_TOOL_OPTIONS="-javaagent:${JACOCO_AGENT}=destfile=/tmp/sdk-jacoco.exec,append=true" bash run-tests.sh
java -jar "$JACOCO_CLI" report /tmp/sdk-jacoco.exec \
  --classfiles out/cloud/cniia/openapi/sdk --csv /tmp/sdk-jacoco.csv
```

报告的行、分支指标与其他工具的语句指标不可互换；只应汇总本次实际运行的测试进程，并在开始前删除旧执行数据。

Go 自带工具不提供与 JaCoCo 相同的分支口径；五套实现和其他受评估源码均须单独核对后，才可判断是否满足项目级徽章阈值。
