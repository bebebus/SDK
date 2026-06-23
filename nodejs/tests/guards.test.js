// 单测：安全守卫（空密钥 fail-closed / 验签异常归 false / 数值规范 / 传输 https）。
// 这些守卫只拦非法或攻击者可控的异常输入，对全部标准向量（合法载荷+非空密钥）逐字节无影响。
import { test } from 'node:test';
import assert from 'node:assert/strict';

import { sign, verifyCallback, buildSignBase } from '../src/signer.js';
import { Config, Environment } from '../src/config.js';

const SECRET = 'sk_test_0123456789abcdef0123456789abcdef';

// ---- [A] 空密钥 fail-closed ----

test('[A] sign 拒绝空/全空白/非字符串密钥', () => {
  const payload = { amount: 1 };
  assert.throws(() => sign(payload, ''), /non-empty string/);
  assert.throws(() => sign(payload, '   '), /non-empty string/);
  assert.throws(() => sign(payload, '\t\n'), /non-empty string/);
  assert.throws(() => sign(payload, null), /non-empty string/);
  assert.throws(() => sign(payload, undefined), /non-empty string/);
  assert.throws(() => sign(payload, 123), /non-empty string/);
});

test('[A] verifyCallback 空密钥在算 HMAC 前直接判否（不抛）', () => {
  const payload = { amount: 1 };
  payload.sign = 'deadbeef';
  assert.equal(verifyCallback(payload, ''), false);
  assert.equal(verifyCallback(payload, '   '), false);
  assert.equal(verifyCallback(payload, null), false);
  assert.equal(verifyCallback(payload, undefined), false);
  assert.equal(verifyCallback(payload, 123), false);
});

test('[A] 空密钥即使 sign 字段恰好为空也判否（不绕过）', () => {
  // 防御：空密钥 + 空 sign 不应被任何路径放行。
  assert.equal(verifyCallback({ amount: 1, sign: '' }, ''), false);
});

// ---- [B] 验签异常归 false（绝不抛异常冒泡）----

test('[B] verifyCallback 非对象/为 null 判否', () => {
  assert.equal(verifyCallback(null, SECRET), false);
  assert.equal(verifyCallback(undefined, SECRET), false);
  assert.equal(verifyCallback('str', SECRET), false);
  assert.equal(verifyCallback(123, SECRET), false);
});

test('[B] verifyCallback 非法 sign（非字符串/空串）判否', () => {
  assert.equal(verifyCallback({ amount: 1, sign: 123 }, SECRET), false);
  assert.equal(verifyCallback({ amount: 1, sign: '' }, SECRET), false);
  assert.equal(verifyCallback({ amount: 1, sign: null }, SECRET), false);
  assert.equal(verifyCallback({ amount: 1, sign: {} }, SECRET), false);
});

test('[B] verifyCallback 载荷含非法数值（触发数值守卫）归 false 而非抛', () => {
  // 攻击者可控载荷塞 NaN/Infinity/非整数，验签必须归 false，不冒泡异常。
  assert.equal(verifyCallback({ amount: NaN, sign: 'x' }, SECRET), false);
  assert.equal(verifyCallback({ amount: Infinity, sign: 'x' }, SECRET), false);
  assert.equal(verifyCallback({ amount: 1.5, sign: 'x' }, SECRET), false);
});

// ---- [C] 数值规范 ----

test('[C] sign 拒绝 NaN/Infinity（顶层）', () => {
  assert.throws(() => sign({ amount: NaN }, SECRET), /non-finite/);
  assert.throws(() => sign({ amount: Infinity }, SECRET), /non-finite/);
  assert.throws(() => sign({ amount: -Infinity }, SECRET), /non-finite/);
});

test('[C] sign 拒绝非整数浮点（顶层）', () => {
  assert.throws(() => sign({ amount: 1.5 }, SECRET), /non-integer/);
  assert.throws(() => sign({ amount: 0.1 }, SECRET), /non-integer/);
});

test('[C] sign 拒绝超 MAX_SAFE_INTEGER 整数（顶层）', () => {
  assert.throws(() => sign({ amount: Number.MAX_SAFE_INTEGER + 2 }, SECRET), /MAX_SAFE_INTEGER/);
});

test('[C] sign 拒绝嵌套非法数值', () => {
  assert.throws(() => sign({ extra: { amount: NaN } }, SECRET), /non-finite/);
  assert.throws(() => sign({ extra: { amount: 1.5 } }, SECRET), /non-integer/);
  assert.throws(() => sign({ items: [{ n: Infinity }] }, SECRET), /non-finite/);
});

test('[C] -0 归一为 "0"（顶层与嵌套均如此）', () => {
  // 顶层
  assert.equal(buildSignBase({ count: -0 }, SECRET), `count=0&secret=${SECRET}`);
  // 嵌套
  assert.equal(buildSignBase({ extra: { c: -0 } }, SECRET), `extra={"c":0}&secret=${SECRET}`);
});

test('[C] 合法安全整数照常签名（含 0 与大但安全的整数）', () => {
  assert.equal(buildSignBase({ amount: 0 }, SECRET), `amount=0&secret=${SECRET}`);
  assert.equal(
    buildSignBase({ amount: Number.MAX_SAFE_INTEGER }, SECRET),
    `amount=${Number.MAX_SAFE_INTEGER}&secret=${SECRET}`,
  );
});

// ---- [D] 传输 https ----

test('[D] 非 localhost 的 http baseUrl 被拒绝', () => {
  assert.throws(
    () => new Config({ merchantNo: 'M', apiKey: 'k', baseUrl: 'http://api.example.com/api/open/v1' }),
    /must use https/,
  );
});

test('[D] 非 localhost 的 https baseUrl 放行', () => {
  const c = new Config({ merchantNo: 'M', apiKey: 'k', baseUrl: 'https://api.example.com/api/open/v1' });
  assert.equal(c.baseUrl, 'https://api.example.com/api/open/v1');
});

test('[D] localhost/127.0.0.1/::1 放行 http', () => {
  assert.ok(new Config({ merchantNo: 'M', apiKey: 'k', baseUrl: 'http://localhost:3090/api/open/v1' }));
  assert.ok(new Config({ merchantNo: 'M', apiKey: 'k', baseUrl: 'http://127.0.0.1:3090/api/open/v1' }));
  assert.ok(new Config({ merchantNo: 'M', apiKey: 'k', baseUrl: 'http://[::1]:3090/api/open/v1' }));
});

test('[D] SANDBOX 预设（http 127.0.0.1）仍可构造', () => {
  const c = new Config({ merchantNo: 'M', apiKey: 'k', environment: Environment.SANDBOX });
  assert.equal(c.baseUrl, 'http://127.0.0.1:3090/api/open/v1');
});

test('[D] 非法 URL baseUrl 被拒绝', () => {
  assert.throws(
    () => new Config({ merchantNo: 'M', apiKey: 'k', baseUrl: 'not a url' }),
    /not a valid URL/,
  );
});
