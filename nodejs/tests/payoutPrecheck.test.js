import assert from 'node:assert/strict';
import { test } from 'node:test';

import { decidePayoutPrecheck, PAYOUT_PRECHECK_APPROVE, PAYOUT_PRECHECK_REJECT } from '../src/payoutPrecheck.js';

const saved = {
  out_payout_no: 'merchant-order-no',
  account_no: 'account',
  sign: 'abc123',
};

function body(overrides = {}) {
  return {
    out_payout_no: 'merchant-order-no',
    payout_no: 'W100',
    account_no: 'account',
    original_sign: 'abc123',
    ...overrides,
  };
}

test('字段和下单 sign 一致时返回 approve', () => {
  assert.equal(decidePayoutPrecheck(body(), saved), PAYOUT_PRECHECK_APPROVE);
});

test('payout_no 不参与比较', () => {
  assert.equal(decidePayoutPrecheck(body({ payout_no: 'W-other' }), saved), 'approve');
});

test('UTF-8 多字节签名按字节比较', () => {
  const mark = { ...saved, sign: '商户签' };
  assert.equal(decidePayoutPrecheck(body({ original_sign: '商户签' }), mark), 'approve');
  assert.equal(decidePayoutPrecheck(body({ original_sign: '商户钱' }), mark), 'reject');
});

test('同长度错误签名、不同长度、账号或商户单号不一致都拒绝', () => {
  assert.equal(decidePayoutPrecheck(body({ original_sign: 'abc124' }), saved), 'reject');
  assert.equal(decidePayoutPrecheck(body({ original_sign: 'abc' }), saved), 'reject');
  assert.equal(decidePayoutPrecheck(body({ account_no: 'other' }), saved), 'reject');
  assert.equal(decidePayoutPrecheck(body({ out_payout_no: 'other' }), saved), 'reject');
});

test('多字段、缺字段、空串、非字符串都拒绝', () => {
  assert.equal(decidePayoutPrecheck({ ...body(), extra: 'x' }, saved), PAYOUT_PRECHECK_REJECT);
  const missing = body();
  delete missing.payout_no;
  assert.equal(decidePayoutPrecheck(missing, saved), 'reject');
  assert.equal(decidePayoutPrecheck(body({ account_no: '' }), saved), 'reject');
  assert.equal(decidePayoutPrecheck(body({ payout_no: 1 }), saved), 'reject');
  assert.equal(decidePayoutPrecheck(body(), { ...saved, sign: '' }), 'reject');
});

test('非法输入返回 reject 且不抛异常', () => {
  assert.equal(decidePayoutPrecheck(null, saved), 'reject');
  assert.equal(decidePayoutPrecheck([], saved), 'reject');
  assert.equal(decidePayoutPrecheck(body(), null), 'reject');
  assert.equal(decidePayoutPrecheck('approve', saved), 'reject');
});
