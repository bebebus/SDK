// 示例：回调验签 + 幂等处理 + 正确应答（代收回调 + 代付回调各演示一次）。
//
// 这是「验签 + 处理」代码片段，不是常驻 HTTP 服务。文件包含两部分：
//   1) handlePayCallback / handlePayoutCallback：纯逻辑（拿原始 body 文本 -> 解析 -> 验签 -> 按 status 幂等处理）
//   2) 文件末尾用示例密钥构造一条"合法回调"自演示一次，直接 node examples/callback_verify.js 即可看到输出
//
// 接入真实 HTTP 框架时，把 handle* 的入参换成请求原始 body，把返回的 { httpStatus, text }
// 写回响应即可：成功应答为 HTTP 200 + 纯文本 success。
import { Client, Config, Environment, sign } from '../src/index.js';

const SECRET_PAY = process.env.PP_API_SECRET_PAY || 'sk_test_pay';
const SECRET_PAYOUT = process.env.PP_API_SECRET_PAYOUT || 'sk_test_payout';

const client = new Client(
  new Config({
    merchantNo: process.env.PP_MERCHANT_NO || 'M00000001',
    apiKey: process.env.PP_API_KEY || 'ak_demo_key',
    apiSecretPay: SECRET_PAY,
    apiSecretPayout: SECRET_PAYOUT,
    environment: Environment.SANDBOX,
  }),
);

// 已处理订单集合（实际应落库/Redis 做幂等，这里仅演示）。
const processedOrders = new Set();
const processedPayouts = new Set();

// 标准成功应答：HTTP 200 + 纯文本 success。
const ACK_SUCCESS = { httpStatus: 200, text: 'success' };
// 验签失败/异常时不返回成功；同一订单可能再次收到回调（返回非 success 文本即可）。
const ACK_REJECT = { httpStatus: 400, text: 'invalid signature' };

// 代收回调处理（密钥 api_secret_pay）。rawBody：HTTP 请求原始 body 文本。
export function handlePayCallback(rawBody) {
  let payload;
  try {
    payload = JSON.parse(rawBody);
  } catch {
    return ACK_REJECT;
  }

  // 时序安全验签（验签器只依赖"除 sign 外全部字段参与"，不硬编码字段表）。
  if (!client.verifyPayCallback(payload)) {
    return ACK_REJECT;
  }

  // 幂等：同一订单可能被多次回调。
  if (processedOrders.has(payload.out_order_no)) {
    return ACK_SUCCESS; // 已处理过，照样回成功避免重复处理
  }

  switch (payload.status) {
    case 'success':
      // TODO: 标记订单已支付、按实付金额入账（actual_amount/fee_amount/net_amount）
      console.log(`[pay] 订单 ${payload.out_order_no} 支付成功，实付=${payload.actual_amount ?? payload.amount}`);
      processedOrders.add(payload.out_order_no);
      break;
    case 'failed':
      console.log(`[pay] 订单 ${payload.out_order_no} 支付失败`);
      processedOrders.add(payload.out_order_no);
      break;
    default:
      console.log(`[pay] 订单 ${payload.out_order_no} 非终态 status=${payload.status}，忽略`);
  }
  return ACK_SUCCESS;
}

// 代付回调处理（密钥 api_secret_payout）。
export function handlePayoutCallback(rawBody) {
  let payload;
  try {
    payload = JSON.parse(rawBody);
  } catch {
    return ACK_REJECT;
  }

  if (!client.verifyPayoutCallback(payload)) {
    return ACK_REJECT;
  }

  if (processedPayouts.has(payload.out_payout_no)) {
    return ACK_SUCCESS;
  }

  switch (payload.status) {
    case 'success':
      console.log(`[payout] 代付 ${payload.out_payout_no} 出款成功`);
      processedPayouts.add(payload.out_payout_no);
      break;
    case 'failed':
      console.log(`[payout] 代付 ${payload.out_payout_no} 失败，原因=${payload.failed_reason ?? '-'}（应解冻/退回余额）`);
      processedPayouts.add(payload.out_payout_no);
      break;
    default:
      console.log(`[payout] 代付 ${payload.out_payout_no} 非终态 status=${payload.status}，忽略`);
  }
  return ACK_SUCCESS;
}

// ---- 自演示：用示例密钥构造合法回调，跑一次看输出 ----
function demo() {
  // 代收成功回调（密钥 api_secret_pay）
  const payBody = {
    merchant_no: 'M00000001',
    order_no: 'P202501010001',
    out_order_no: '202501010001',
    amount: 10000,
    actual_amount: 10000,
    fee_amount: 30,
    net_amount: 9970,
    currency: 'PHP',
    status: 'success',
    channel_order_no: null,
    paid_at: '2025-01-05T12:00:00Z',
  };
  payBody.sign = sign(payBody, SECRET_PAY);
  console.log('代收回调应答 ->', handlePayCallback(JSON.stringify(payBody)));
  console.log('重复回调（幂等）应答 ->', handlePayCallback(JSON.stringify(payBody)));

  // 篡改一字节 -> 验签失败
  const tampered = { ...payBody, amount: 99999 };
  console.log('被篡改回调应答 ->', handlePayCallback(JSON.stringify(tampered)));

  // 代付失败回调（密钥 api_secret_payout）
  const payoutBody = {
    merchant_no: 'M00000001',
    payout_no: 'W202501010001',
    out_payout_no: 'WD202501010001',
    amount: 100000,
    currency: 'PHP',
    status: 'failed',
    fee_amount: 0,
    channel_order_no: null,
    finished_at: '2025-01-05T12:05:00Z',
    failed_reason: 'account_invalid',
  };
  payoutBody.sign = sign(payoutBody, SECRET_PAYOUT);
  console.log('代付回调应答 ->', handlePayoutCallback(JSON.stringify(payoutBody)));
}

// 仅当直接运行本文件时跑 demo（被 import 时不跑）。
if (import.meta.url === `file://${process.argv[1]}`) {
  demo();
}
