'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const automerge = require('./dependabot-automerge.cjs');

test('只接受稳定版同主版本的 deps-dev 更新', () => {
  assert.ok(automerge.parseUpgrade('chore(deps-dev): bump biome from 2.5.5 to 2.5.6'));
  assert.ok(automerge.parseUpgrade('chore(deps-dev): bump tool from 2.5.5 to 2.6.0'));
  assert.equal(automerge.parseUpgrade('chore(deps-dev): bump tool from 2.5.5 to 3.0.0'), null);
  assert.equal(automerge.parseUpgrade('chore(deps): bump tool from 2.5.5 to 2.5.6'), null);
  assert.equal(automerge.parseUpgrade('chore(deps-dev): bump tool from 2.5.5 to 2.6.0-rc.1'), null);
});

test('只接受单一生态的清单和锁文件', () => {
  const labels = new Set(['dependencies', 'javascript']);
  const files = [
    { filename: 'nodejs/package.json', additions: 1, deletions: 1 },
    { filename: 'nodejs/package-lock.json', additions: 10, deletions: 10 },
  ];
  assert.equal(automerge.classifyFiles(files, labels), 'javascript');
  assert.equal(
    automerge.classifyFiles([...files, { filename: 'nodejs/src/index.js' }], labels),
    null,
  );
  assert.equal(automerge.classifyFiles(files, new Set([...labels, 'php'])), null);
});

test('要求同一 head SHA 的 ci 与 CodeQL 最新运行均成功', () => {
  const passed = [
    { id: 1, name: 'ci', event: 'pull_request', status: 'completed', conclusion: 'success' },
    { id: 2, name: 'CodeQL', event: 'pull_request', status: 'completed', conclusion: 'success' },
  ];
  assert.equal(automerge.requiredChecksPassed(passed), true);
  assert.equal(
    automerge.requiredChecksPassed([...passed, { ...passed[0], id: 3, conclusion: 'failure' }]),
    false,
  );
  assert.equal(automerge.requiredChecksPassed(passed.slice(0, 1)), false);
});

test('要求全部 check runs 都已完成且结论为绿色', () => {
  assert.equal(
    automerge.allCheckRunsPassed([
      { status: 'completed', conclusion: 'success' },
      { status: 'completed', conclusion: 'neutral' },
      { status: 'completed', conclusion: 'skipped' },
    ]),
    true,
  );
  assert.equal(
    automerge.allCheckRunsPassed([{ status: 'in_progress', conclusion: null }]),
    false,
  );
  assert.equal(
    automerge.allCheckRunsPassed([{ status: 'completed', conclusion: 'failure' }]),
    false,
  );
  assert.equal(automerge.allCheckRunsPassed([]), false);
});
