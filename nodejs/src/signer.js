// 签名器：HMAC-SHA256 -> hex 小写。
// 算法与 SIGNING.md / test-vectors.json 逐字节一致。
// 关键点：
//  - 过滤 key==="sign" 与值为 null/undefined 的字段
//  - 剩余字段按 key 的 ASCII（码点）升序排序
//  - 顶层标量用 String(v)（无引号）；object/array 用稳定 JSON 序列化
//  - 稳定 JSON：递归 key 升序、紧凑无空格、嵌套标量用 JSON.stringify（带引号且对齐 JS 转义）
//  - base：k=v 用 & 连接，末尾追加 &secret=<secret>
//  - sign：HMAC_SHA256(base, key=secret)，hex 小写
import { createHmac, timingSafeEqual } from 'node:crypto';

// JS 默认 JSON.stringify 的字符串转义已满足：不转义 / 、不转义非 ASCII、不做 <>& 的 HTML 转义，
// 仅转义 " \ 与控制字符。因此嵌套字符串直接用 JSON.stringify(v) 即可。
function jsonString(str) {
  return JSON.stringify(str);
}

// 稳定 JSON 序列化（递归、key 升序、紧凑无空格）。
export function stableStringify(value) {
  if (value === null) return 'null';

  const t = typeof value;
  if (t === 'string') return jsonString(value);
  if (t === 'number') {
    // NaN/Infinity 无合法 JSON 表示；按 JS JSON.stringify 行为序列化为 null。
    if (!Number.isFinite(value)) return 'null';
    return String(value);
  }
  if (t === 'bigint') return value.toString();
  if (t === 'boolean') return value ? 'true' : 'false';

  if (Array.isArray(value)) {
    // 数组保序；元素中的 undefined/function 按 JSON.stringify 行为序列化为 null。
    const parts = value.map((el) => {
      if (el === undefined || typeof el === 'function' || typeof el === 'symbol') {
        return 'null';
      }
      return stableStringify(el);
    });
    return '[' + parts.join(',') + ']';
  }

  if (t === 'object') {
    // 跳过值为 undefined/function/symbol 的键（与 JSON.stringify 一致），key 升序。
    const keys = Object.keys(value)
      .filter((k) => {
        const v = value[k];
        return v !== undefined && typeof v !== 'function' && typeof v !== 'symbol';
      })
      .sort();
    const parts = keys.map((k) => jsonString(k) + ':' + stableStringify(value[k]));
    return '{' + parts.join(',') + '}';
  }

  // undefined/function/symbol 在对象/数组层已被处理；顶层不应到达这里。
  return 'null';
}

// 顶层标量取值字符串（无引号），布尔归一为 true/false。
function valueForSign(v) {
  const t = typeof v;
  if (t === 'object') {
    // object / array（null 已在过滤阶段剔除）
    return stableStringify(v);
  }
  if (t === 'boolean') return v ? 'true' : 'false';
  if (t === 'bigint') return v.toString();
  if (t === 'number') {
    if (!Number.isFinite(v)) return 'null';
    return String(v);
  }
  // string 及其它原样 String()
  return String(v);
}

// 构造签名 base 字符串（不含 HMAC 计算），便于逐字节断言。
export function buildSignBase(payload, secret) {
  const keys = Object.keys(payload)
    .filter((k) => k !== 'sign' && payload[k] !== null && payload[k] !== undefined)
    .sort();
  const pairs = keys.map((k) => `${k}=${valueForSign(payload[k])}`);
  pairs.push(`secret=${secret}`);
  return pairs.join('&');
}

// 计算签名：HMAC-SHA256(base, key=secret) -> hex 小写。
export function sign(payload, secret) {
  const base = buildSignBase(payload, secret);
  return createHmac('sha256', secret).update(base, 'utf8').digest('hex');
}

// 回调验签（时序安全比较）。按"除 sign 外所有字段参与"通用计算，不硬编码字段表。
// payload：解析后的回调键值表（含 sign）。secret：代收/退款回调用 api_secret_pay，代付回调用 api_secret_payout。
export function verifyCallback(payload, secret) {
  if (!payload || typeof payload !== 'object') return false;
  const provided = payload.sign;
  if (typeof provided !== 'string' || provided.length === 0) return false;

  const expected = sign(payload, secret);
  const a = Buffer.from(expected, 'utf8');
  const b = Buffer.from(provided, 'utf8');
  // timingSafeEqual 要求等长；长度不同直接判否（仍尽量减少早退泄露）。
  if (a.length !== b.length) return false;
  return timingSafeEqual(a, b);
}
