// 跨语言签名「标准答案」向量生成器。
//
// 目的：用权威实现产出一组确定的 (payload, base, sign) 向量，作为 PHP / Python /
// Java / Go / Node 五套 SDK 单元测试的共同锚点——只要某语言的签名器复现不出这里的
// base 与 sign，就说明它与服务端不一致（签名是整套 SDK 唯一的「一字节都不能错」的部分）。
//
// 严谨性保证：每个向量的 base/sign 同时用三处实现计算并断言一致后才写出：
//   1) 本文件内置 reimpl（与 web-shared/openapi/sign.ts 同语义）
//   2) project-p-merchant-onboarding/src/sign.js（盲测参考实现，导出 buildSignBase + sign）
//   3) project-p-test/sign.js（测试参考实现，导出 signPayload）
// 三者任一不一致即抛错退出，绝不产出「自说自话」的向量。
//
// 运行：node _tooling/generate-vectors.mjs   （在 project-p-sdk/ 目录下）

import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

// 两处权威实现（均为 ESM，所属包 type:module）
import { buildSignBase as onbBuildBase, sign as onbSign } from '../../project-p-merchant-onboarding/src/sign.js';
import { signPayload as testSignPayload } from '../../project-p-test/sign.js';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const OUT = path.resolve(__dirname, '..', 'test-vectors.json');

// --- 内置 reimpl（与权威实现逐字节一致，仅用于三方交叉校验） ---
function stableStringify(value) {
  if (value === null) return 'null';
  if (typeof value !== 'object') return JSON.stringify(value);
  if (Array.isArray(value)) return '[' + value.map(stableStringify).join(',') + ']';
  const keys = Object.keys(value).sort();
  return '{' + keys.map((k) => JSON.stringify(k) + ':' + stableStringify(value[k])).join(',') + '}';
}
function signValue(v) {
  if (v === null || v === undefined) return 'null';
  if (typeof v === 'object') return stableStringify(v);
  return String(v);
}
function buildBase(data, secret) {
  const keys = Object.keys(data).filter((k) => k !== 'sign' && data[k] != null).sort();
  return keys.map((k) => `${k}=${signValue(data[k])}`).join('&') + `&secret=${secret}`;
}
function hmac(base, secret) {
  return crypto.createHmac('sha256', secret).update(base, 'utf8').digest('hex');
}

const SECRET = 'sk_test_0123456789abcdef0123456789abcdef'; // 合成密钥，非任何真实凭证

// 向量集合：覆盖标量 / 排序 / null 跳过 / sign 排除 / 嵌套对象 / 数组 / 布尔与整数 /
// Unicode 与特殊字符转义（最致命的跨语言坑）/ 深层嵌套 / 真实代收代付载荷。
const VECTORS = [
  {
    name: 'pay_create_scalars',
    desc: '代收下单纯标量：校验 key 升序、整数 String() 形态、HMAC 输出',
    secret: SECRET,
    payload: {
      merchant_no: 'M00000001', api_key: 'ak_demo_key', timestamp: 1736073600,
      out_order_no: '202501010001', amount: 10000, currency: 'PHP',
      pay_method: 'gcash', country: 'PH', notify_url: 'https://merchant.example.com/api/notify/pay',
    },
  },
  {
    name: 'extra_nested_object',
    desc: 'extra.customer 嵌套对象：故意乱序，序列化后 key 递归升序、紧凑无空格',
    secret: SECRET,
    payload: {
      api_key: 'k', timestamp: 1,
      extra: { customer: { phone: '0900', email: 'a@b.com', first_name: 'San', last_name: 'Zhang' } },
    },
  },
  {
    name: 'null_skipped',
    desc: 'null 值字段不参与签名（与服务端 != null 过滤一致）',
    secret: SECRET,
    payload: { a: '1', b: null, c: null, d: '4', amount: 500 },
  },
  {
    name: 'sign_field_excluded',
    desc: 'sign 字段本身永不参与签名',
    secret: SECRET,
    payload: { a: '1', amount: 2, sign: 'should_be_ignored' },
  },
  {
    name: 'unicode_and_special_chars',
    desc: '最致命跨语言坑：嵌套字符串的 JSON 转义须对齐 JS（不转义 / 非ASCII <>& ，转义 " \\ 与控制字符）；顶层字符串用原始 String() 不加引号不转义',
    secret: SECRET,
    payload: {
      subject: '订单/支付 <A&B>', // 顶层字符串：原样 String()，含中文/斜杠/尖括号/&
      notify_url: 'https://m.example.com/cb?a=1&b=2', // 顶层字符串含 & 与 =（与服务端同样不转义，验签自洽）
      extra: { note: '中文"<>&/\\\n\t末', tag: 'plain' }, // 嵌套字符串：须 JSON 转义且与 JS 一致
    },
  },
  {
    name: 'array_values',
    desc: '数组字段：标量数组与对象数组，元素保序、对象内 key 升序',
    secret: SECRET,
    payload: {
      api_key: 'k', timestamp: 2,
      tags: ['b', 'a', 'c'],
      items: [{ z: 1, a: 2 }, { c: 3, b: 4 }],
    },
  },
  {
    name: 'boolean_and_int_toplevel',
    desc: '顶层布尔/整数强转：各语言须归一 true→"true" false→"false" 0→"0"（防 PHP (string)true="1"、Python str(True)="True"）',
    secret: SECRET,
    payload: { api_key: 'k', inline: true, disabled: false, count: 0, amount: 12345 },
  },
  {
    name: 'payout_create_bank',
    desc: '代付下单（银行类）真实载荷',
    secret: SECRET,
    payload: {
      merchant_no: 'M00000001', api_key: 'ak_demo_key', timestamp: 1736073600,
      out_payout_no: 'WD202501010001', amount: 100000, currency: 'PHP', pay_method: 'bank',
      country: 'PH', notify_url: 'https://merchant.example.com/api/notify/payout',
      account_name: 'San Zhang', account_no: '1234567890', bank_code: 'BDO',
    },
  },
  {
    name: 'single_field',
    desc: '最小载荷：单字段 + secret 收尾',
    secret: SECRET,
    payload: { amount: 1 },
  },
  {
    name: 'deep_nested',
    desc: '深层嵌套：对象套数组套对象，含嵌套整数与非ASCII',
    secret: SECRET,
    payload: { api_key: 'k', extra: { a: { z: [{ y: 1, x: 2 }], m: '中' } } },
  },
  {
    name: 'empty_and_nested_containers',
    desc: '空对象 {} 与空数组 [] 的区分：顶层空对象 extra={}，嵌套空对象 a={}、嵌套空数组 b=[]、非空数组 c=[1]（钉死 PHP 等语言的 object/list 边界，防空对象签名分叉）',
    secret: SECRET,
    payload: { api_key: 'k', extra: {}, meta: { a: {}, b: [], c: [1] } },
  },
];

function emit() {
  const out = [];
  for (const v of VECTORS) {
    const base = buildBase(v.payload, v.secret);
    const sign = hmac(base, v.secret);

    // 三方交叉校验
    const onbBase = onbBuildBase(v.payload, v.secret);
    const onbS = onbSign(v.payload, v.secret);
    const testS = testSignPayload(v.payload, v.secret);
    const checks = [
      ['reimpl.base == onboarding.buildSignBase', base, onbBase],
      ['reimpl.sign == onboarding.sign', sign, onbS],
      ['onboarding.sign == project-p-test.signPayload', onbS, testS],
    ];
    for (const [label, a, b] of checks) {
      if (a !== b) {
        console.error(`[FATAL] 向量 ${v.name} 三方校验失败：${label}\n  A=${a}\n  B=${b}`);
        process.exit(1);
      }
    }
    out.push({ name: v.name, desc: v.desc, secret: v.secret, payload: v.payload, base, sign });
  }
  return out;
}

const vectors = emit();
const doc = {
  _comment:
    '跨语言签名标准答案向量。由 project-p-sdk/_tooling/generate-vectors.mjs 经三处权威实现交叉校验后生成。' +
    '各语言 SDK 单测须对每个向量复现 base 与 sign（HMAC-SHA256 hex 小写）。',
  algorithm: {
    filter: '排除 key==="sign"，且跳过值为 null/undefined 的字段',
    sort: 'key 按 ASCII/码点升序',
    value: '顶层标量用 String(v)（无引号）；object/array 用稳定 JSON 序列化（key 递归升序、紧凑无空格、嵌套标量用 JSON.stringify 带引号）',
    json_escape: '嵌套 JSON 序列化须对齐 JS JSON.stringify：不转义 / ，不转义非 ASCII，不做 HTML(<>&) 转义；转义 " \\ 及控制字符(\\b\\f\\n\\r\\t 与其余 \\u00XX)',
    boolean: '布尔统一序列化为 true/false（顶层强转也须如此，勿用各语言默认 cast）',
    join: 'key=value 用 & 连接，末尾追加 &secret=<secret>',
    hmac: 'HMAC-SHA256(base, key=secret)，输出十六进制小写',
    secret: 'pay 类接口/回调用 api_secret_pay；payout 类用 api_secret_payout',
  },
  vectors,
};
fs.writeFileSync(OUT, JSON.stringify(doc, null, 2) + '\n', 'utf8');
console.log(`已写出 ${vectors.length} 个向量到 ${OUT}（三方交叉校验全部通过）`);
