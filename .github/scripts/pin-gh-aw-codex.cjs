// gh-aw v0.84.3 不支持锁定引擎安装哈希；重新编译后运行本脚本。
const fs = require('node:fs');
const path = require('node:path');
const root = path.resolve(__dirname, '../..');
const manifestText = fs.readFileSync(path.join(root, '.github/codex-engine-package.json'), 'utf8');
const lockText = fs.readFileSync(path.join(root, '.github/codex-engine-package-lock.json'), 'utf8');
const manifest = JSON.parse(manifestText);
const lock = JSON.parse(lockText);
const version = manifest.dependencies?.['@openai/codex'];
if (!/^\d+\.\d+\.\d+$/.test(version || '') || lock.packages?.['']?.dependencies?.['@openai/codex'] !== version || lock.packages?.['node_modules/@openai/codex']?.version !== version) throw Error('Codex 版本或锁文件不一致');
for (const [name, item] of Object.entries(lock.packages)) {
  if (name && (!/^sha512-[A-Za-z0-9+/]+=*$/.test(item.integrity || '') || !/^https:\/\/registry\.npmjs\.org\//.test(item.resolved || ''))) throw Error(`依赖缺少可信来源或哈希: ${name}`);
}
const encode = text => Buffer.from(text).toString('base64');
const replacement = `      - name: Install Codex CLI (integrity locked)
        run: |
          set -euo pipefail
          engine_dir="${'${RUNNER_TEMP}'}/gh-aw/codex-engine"
          mkdir -p "$engine_dir"
          printf '%s' '${encode(manifestText)}' | base64 -d > "$engine_dir/package.json"
          printf '%s' '${encode(lockText)}' | base64 -d > "$engine_dir/package-lock.json"
          cd "$engine_dir"
          npm ci --prefix "$engine_dir" --ignore-scripts --no-audit --no-fund
          ln -sf "$engine_dir/node_modules/.bin/codex" "$(npm prefix -g)/bin/codex"
`;
const original = `      - name: Install Codex CLI\n        run: npm install --ignore-scripts -g @openai/codex@${version}\n`;
const paths = ['repository-maintainer', 'pull-request-reviewer'].map(n => path.join(root, `.github/workflows/${n}.lock.yml`));
const check = process.argv.includes('--check');
const changes = paths.map(file => {
  const text = fs.readFileSync(file, 'utf8');
  if (!text.includes(`"codex":"${version}"`)) throw Error(`${file}: 编译器引擎版本与锁文件不一致`);
  const originalCount = text.split(original).length - 1;
  const replacementCount = text.split(replacement).length - 1;
  if (!((originalCount === 2 && replacementCount === 0) || (originalCount === 0 && replacementCount === 2))) throw Error(`${file}: 安装步骤数量或内容与预期不符`);
  const result = originalCount ? text.replaceAll(original, replacement) : text;
  if (check && result !== text) throw Error(`${file}: 需运行 node .github/scripts/pin-gh-aw-codex.cjs`);
  return [file, result, text];
});
if (!check) for (const [file, result, text] of changes) if (result !== text) fs.writeFileSync(file, result);
console.log(`gh-aw Codex ${version} 完整性锁定检查通过`);
