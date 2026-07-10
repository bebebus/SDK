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

// 数值规范守卫：参与签名的数字必须是安全整数。
//  - NaN/Infinity 无合法 JSON 表示，拒绝；
//  - 非整数浮点拒绝（合约要求金额等用整数最小单位，避免 1.0/1 跨语言分叉）；
//  - 绝对值超过 Number.MAX_SAFE_INTEGER 的整数拒绝（精度不可靠，可能与服务端不一致）。
// 不改变合法整数的字符串形态，故对全部标准向量逐字节无影响。-0 在调用处单独归一为 "0"。
function assertSafeIntegerNumber(n) {
  if (!Number.isFinite(n)) {
    throw new TypeError(`Sign: non-finite number is not allowed (got ${n}); use an integer minor unit`);
  }
  if (!Number.isInteger(n)) {
    throw new TypeError(
      `Sign: non-integer number is not allowed (got ${n}); use an integer minor unit (e.g. cents)`,
    );
  }
  if (!Number.isSafeInteger(n)) {
    throw new TypeError(
      `Sign: integer exceeds Number.MAX_SAFE_INTEGER (got ${n}); precision is not reliable`,
    );
  }
}

// 数字归一为签名字符串：-0 归一为 "0"，其余安全整数用 String()。
function numberForSign(n) {
  assertSafeIntegerNumber(n);
  // Object.is(n, -0) 为真时 String(-0) === "0"，此处仍显式归一以表意清晰。
  if (Object.is(n, -0)) return '0';
  return String(n);
}

// 稳定 JSON 序列化（递归、key 升序、紧凑无空格）。
export function stableStringify(value) {
  if (value === null) return 'null';

  const t = typeof value;
  if (t === 'string') return jsonString(value);
  if (t === 'number') {
    // [C 数值规范] 嵌套数字同样要求安全整数：拒绝 NaN/Infinity、非整数浮点、超安全整数范围。
    // 标准向量的嵌套数字均为小安全整数，故逐字节不受影响。
    return numberForSign(value);
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
    // [C 数值规范] 顶层数字拒绝 NaN/Infinity/非整数/超安全整数，-0 归一为 "0"。
    return numberForSign(v);
  }
  // string 及其它原样 String()
  return String(v);
}

// [A 空密钥 fail-closed] 判定密钥是否为非法空密钥：非字符串、空串或全空白。
// 用于从根上禁止用空密钥签名/验签——任何标准向量的密钥都是非空字符串，故不受影响。
function isBlankSecret(secret) {
  return typeof secret !== 'string' || secret.trim() === '';
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
  // [A 空密钥 fail-closed] 空/全空白/非字符串密钥一律拒绝，从根上禁止空密钥签名。
  if (isBlankSecret(secret)) {
    throw new TypeError('Sign: secret must be a non-empty string');
  }
  const base = buildSignBase(payload, secret);
  return createHmac('sha256', secret).update(base, 'utf8').digest('hex');
}

// 回调验签（时序安全比较）。按"除 sign 外所有字段参与"通用计算，不硬编码字段表。
// payload：解析后的回调键值表（含 sign）。secret：代收/退款回调用 api_secret_pay，代付回调用 api_secret_payout。
export function verifyCallback(payload, secret) {
  // [A 空密钥 fail-closed] 计算任何 HMAC 之前先拦空密钥：空/全空白/非字符串一律判否，绝不继续算签名比较。
  if (isBlankSecret(secret)) return false;
  // [B 验签异常归 false] 回调体非对象/为 null → 判否。
  if (!payload || typeof payload !== 'object') return false;
  const provided = payload.sign;
  // [B] 提供的 sign 非字符串/空串 → 判否（攻击者可控字段不抛异常）。
  if (typeof provided !== 'string' || provided.length === 0) return false;
  // [B] hex 预校验：接口 sign 恒为小写 64 位十六进制（HMAC-SHA256），
  // 非此格式（含大小写混写/非 hex 字符/长度异常）一律提前判否，不进 HMAC 计算。
  // 与 python/php/java/go 验签器对齐。合法回调 sign 必为小写 hex，故 11 标准向量不受影响。
  if (!/^[0-9a-f]{64}$/.test(provided)) return false;

  // [B] 计算期望签名时任何异常（如载荷含非法数值触发数值规范守卫）一律归 false，绝不冒泡。
  let expected;
  try {
    expected = sign(payload, secret);
  } catch {
    return false;
  }
  const a = Buffer.from(expected, 'utf8');
  const b = Buffer.from(provided, 'utf8');
  // timingSafeEqual 要求等长；长度不同直接判否（仍尽量减少早退泄露）。
  if (a.length !== b.length) return false;
  return timingSafeEqual(a, b);
}
