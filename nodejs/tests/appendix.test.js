import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { test } from 'node:test';
import assert from 'node:assert/strict';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');

test('appendix/error-codes.json 含错误码总表', () => {
  const doc = JSON.parse(readFileSync(join(root, 'appendix/error-codes.json'), 'utf8'));
  assert.ok(Array.isArray(doc.errors));
  assert.ok(doc.errors.some((e) => e.code === '100001' && e.retryable === 'no'));
  assert.ok(doc.errors.some((e) => e.code === '100000' && e.retryable === 'depends'));
  // 300304（建单暂不可用，insert-first fail-closed）是当前唯一约定非 200 的错误码，固定 HTTP 503。
  const knownNon200 = { '300304': 503 };
  assert.ok(doc.errors.every((e) => e.http === (knownNon200[e.code] ?? 200)));
});
