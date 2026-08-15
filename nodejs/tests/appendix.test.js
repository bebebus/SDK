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
  assert.ok(doc.errors.every((e) => e.http === 200));
});
