const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { execFileSync, spawnSync } = require('node:child_process');
const root = path.resolve(__dirname, '../..');
execFileSync(process.execPath, [path.join(__dirname, 'pin-gh-aw-codex.cjs'), '--check'], { stdio: 'inherit' });
const lock = JSON.parse(fs.readFileSync(path.join(root, '.github/codex-engine-package-lock.json')));
const version = lock.packages['node_modules/@openai/codex'].version;
const workflow = fs.readFileSync(path.join(root, '.github/workflows/repository-maintainer.lock.yml'), 'utf8');
const step = workflow.match(/      - name: Install Codex CLI \(integrity locked\)\n        run: \|\n((?:          .*\n)+)/);
assert.ok(step, '未找到生成的安装步骤');
const script = step[1].replace(/^          /gm, '');
const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'gh-aw-codex-test-'));
try {
  const prefix = path.join(dir, 'prefix');
  fs.mkdirSync(path.join(prefix, 'bin'), { recursive: true });
  const env = { ...process.env, RUNNER_TEMP: dir, npm_config_prefix: prefix, npm_config_cache: path.join(dir, 'cache') };
  execFileSync('bash', ['-c', script], { env, stdio: 'inherit' });
  assert.match(execFileSync(path.join(prefix, 'bin/codex'), ['--version'], { encoding: 'utf8' }), new RegExp(`\\b${version.replaceAll('.', '\\.')}\\b`));
  const installDir = path.join(dir, 'gh-aw/codex-engine');
  const installed = JSON.parse(fs.readFileSync(path.join(installDir, 'package-lock.json')));
  installed.packages['node_modules/@openai/codex'].integrity = 'sha512-' + 'A'.repeat(86) + '==';
  fs.writeFileSync(path.join(installDir, 'package-lock.json'), JSON.stringify(installed));
  const result = spawnSync('npm', ['ci', '--prefix', installDir, '--ignore-scripts', '--no-audit', '--no-fund'], { env, encoding: 'utf8' });
  assert.notEqual(result.status, 0, '损坏哈希的安装不应成功');
  assert.match(result.stderr, /EINTEGRITY|integrity checksum failed/i);
  console.log('Codex 安装、执行和错误哈希拒绝验证通过');
} finally { fs.rmSync(dir, { recursive: true, force: true }); }
