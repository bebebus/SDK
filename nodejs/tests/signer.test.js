// 单测：对 ../../test-vectors.json 的每个向量复现 base 与 sign。
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';

import { buildSignBase, sign, verifyCallback } from '../src/signer.js';

const __dirname = dirname(fileURLToPath(import.meta.url));
const vectorsPath = resolve(__dirname, '../../test-vectors.json');
const vectors = JSON.parse(readFileSync(vectorsPath, 'utf8')).vectors;

test('test-vectors.json 已加载且非空', () => {
  assert.ok(Array.isArray(vectors));
  assert.ok(vectors.length >= 10, `expected >=10 vectors, got ${vectors.length}`);
});

for (const v of vectors) {
  test(`vector: ${v.name} — buildSignBase 与服务端逐字节一致`, () => {
    const base = buildSignBase(v.payload, v.secret);
    assert.equal(base, v.base, `base mismatch for ${v.name}`);
  });

  test(`vector: ${v.name} — sign 与标准答案一致`, () => {
    const s = sign(v.payload, v.secret);
    assert.equal(s, v.sign, `sign mismatch for ${v.name}`);
  });
}

// 回调验签：正例（用算出的 sign 装回 payload）+ 篡改一字节反例。
test('verifyCallback 正例：合法签名通过', () => {
  const secret = 'sk_test_0123456789abcdef0123456789abcdef';
  const payload = {
    merchant_no: 'M00000001',
    order_no: 'P202501010001',
    out_order_no: '202501010001',
    amount: 10000,
    currency: 'PHP',
    status: 'success',
    channel_order_no: null, // null 字段不参与签名
    paid_at: '2025-01-05T12:00:00Z',
  };
  payload.sign = sign(payload, secret);
  assert.equal(verifyCallback(payload, secret), true);
});

test('verifyCallback 反例：篡改业务字段一字节后失败', () => {
  const secret = 'sk_test_0123456789abcdef0123456789abcdef';
  const payload = {
    merchant_no: 'M00000001',
    order_no: 'P202501010001',
    amount: 10000,
    currency: 'PHP',
    status: 'success',
  };
  payload.sign = sign(payload, secret);
  // 篡改金额（一处变化）
  const tampered = { ...payload, amount: 10001 };
  assert.equal(verifyCallback(tampered, secret), false);
});

test('verifyCallback 反例：篡改 sign 自身一字节后失败', () => {
  const secret = 'sk_test_0123456789abcdef0123456789abcdef';
  const payload = { merchant_no: 'M00000001', amount: 10000, status: 'success' };
  payload.sign = sign(payload, secret);
  const flipped = payload.sign[0] === '0' ? '1' : '0';
  const tampered = { ...payload, sign: flipped + payload.sign.slice(1) };
  assert.equal(verifyCallback(tampered, secret), false);
});

test('verifyCallback 反例：用错密钥（代付密钥验代收回调）失败', () => {
  const payPayload = { merchant_no: 'M00000001', amount: 10000, status: 'success' };
  payPayload.sign = sign(payPayload, 'secret_pay');
  assert.equal(verifyCallback(payPayload, 'secret_payout'), false);
});

test('verifyCallback 反例：缺 sign 字段直接判否', () => {
  assert.equal(verifyCallback({ amount: 1 }, 'k'), false);
  assert.equal(verifyCallback(null, 'k'), false);
});
