# gh-aw Codex 安装完整性

两个 Agentic Workflow 的 agent 与威胁检测任务均使用相同的 Codex CLI。gh-aw v0.84.3 固定了版本，但生成的全局 npm 安装没有校验锁文件。这里将 npm 清单和各平台包的完整性哈希嵌入生成工作流，在 runner 的临时目录执行 `npm ci --ignore-scripts`。临时目录已被 gh-aw 挂载到防火墙容器；全局命令链接仍使用 Node 工具目录，供 gh-aw 原有的 PATH 查找使用。安装内容来自工作流本身，不能由待审 PR 的检出内容替换。

修改或重新编译 `.github/workflows/*.md` 后：

1. 使用项目指定的 gh-aw 版本编译，检查其他 action pin 与预期一致。
2. 若编译器的 Codex 版本变化，将 `.github/codex-engine-package.json` 和 `.github/codex-engine-package-lock.json` 一起更新为相同的精确版本，并审查来源及各平台哈希。
3. 运行 `node .github/scripts/pin-gh-aw-codex.cjs`，再运行 `node .github/scripts/test-gh-aw-codex.cjs`。
4. 检查生成文件差异并提交。CI 会验证嵌入内容、实际安装和错误哈希拒绝；若生成步骤数量或引擎版本变化，脚本会失败，需先审查上游变化。

该后处理仅替换安装步骤，不修改 gh-aw 的 firewall、检测任务或 safe-output 权限。未来若 gh-aw 原生支持哈希锁定，应评估移除此后处理。
