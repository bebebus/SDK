// SPDX-License-Identifier: MIT

// 商户代付反查判定。平台 POST 到商户自己的 HTTPS 接口，本模块不发请求。
// 请求体必须恰好是四个非空字符串：account_no、out_payout_no、original_sign、payout_no。
// original_sign 与下单时保存的 sign 按 UTF-8 字节等长比较。payout_no 必须出现，但不与下单请求比较。
// 返回值只有精确的 approve 或 reject。非法输入返回 reject，不抛异常。
import { timingSafeEqual } from 'node:crypto';

export const PAYOUT_PRECHECK_APPROVE = 'approve';
export const PAYOUT_PRECHECK_REJECT = 'reject';

const BODY_KEYS = ['account_no', 'out_payout_no', 'original_sign', 'payout_no'];
const SAVED_KEYS = ['account_no', 'out_payout_no', 'sign'];

function isPlainObject(value) {
  if (value === null || typeof value !== 'object' || Array.isArray(value)) return false;
  const proto = Object.getPrototypeOf(value);
  return proto === Object.prototype || proto === null;
}

function isNonEmptyString(value) {
  return typeof value === 'string' && value !== '';
}

export function decidePayoutPrecheck(body, saved) {
  try {
    const fieldsOk =
      isPlainObject(body) &&
      isPlainObject(saved) &&
      Object.keys(body).length === BODY_KEYS.length &&
      BODY_KEYS.every((key) => isNonEmptyString(body[key])) &&
      SAVED_KEYS.every((key) => isNonEmptyString(saved[key])) &&
      body.account_no === saved.account_no &&
      body.out_payout_no === saved.out_payout_no;
    const actual = Buffer.from(fieldsOk ? body.original_sign : '', 'utf8');
    const expected = Buffer.from(fieldsOk ? saved.sign : '', 'utf8');
    if (fieldsOk && actual.length === expected.length && timingSafeEqual(actual, expected)) {
      return PAYOUT_PRECHECK_APPROVE;
    }
    return PAYOUT_PRECHECK_REJECT;
  } catch {
    return PAYOUT_PRECHECK_REJECT;
  }
}
