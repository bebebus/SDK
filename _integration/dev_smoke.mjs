// Node SDK × dev 环境联调。凭据从环境变量读取（PP_MNO/PP_KEY/PP_PAY/PP_POUT/PP_BASE）。
// 序列与 dev_smoke.php 完全一致，便于跨语言对比。
import { Client, Config, sign } from '../nodejs/src/index.js';

const env = process.env;
const cfg = new Config({
  merchantNo: env.PP_MNO,
  apiKey: env.PP_KEY,
  apiSecretPay: env.PP_PAY,
  apiSecretPayout: env.PP_POUT,
  baseUrl: env.PP_BASE,
});
const client = new Client(cfg);
const tag = `node-${Date.now()}-${Math.floor(Math.random() * 1e4)}`;
let pass = 0, fail = 0;
const ok = (n, c, extra = '') => { (c ? pass++ : fail++); console.log(`${c ? '✅' : '❌'} ${n}${extra ? ' | ' + extra : ''}`); };

console.log(`[Node] base=${env.PP_BASE} merchant=${env.PP_MNO} tag=${tag}`);

// 1. pay-methods/query
let methods = [];
try { const r = await client.payMethodsQuery({ country: 'PH' }); methods = r.data.methods || []; ok('pay-methods/query', methods.length > 0, methods.map(m => m.pay_method).join(',')); }
catch (e) { ok('pay-methods/query', false, `${e.name} ${e.code ?? ''} ${e.message}`); }

// 2. balance/query
try { const r = await client.balanceQuery({}); ok('balance/query', Array.isArray(r.data.balances), JSON.stringify(r.data.balances)); }
catch (e) { ok('balance/query', false, `${e.name} ${e.code ?? ''} ${e.message}`); }

// 3. pay/create（gcash/PH/PHP，含 customer 以满足上游必填）
const outOrderNo = `sdk-${tag}`;
let orderNo = null;
try {
  const r = await client.payCreate({
    out_order_no: outOrderNo, amount: 10000, currency: 'PHP', pay_method: 'gcash', country: 'PH',
    notify_url: 'https://merchant.example.com/api/notify/pay',
    extra: { customer: { first_name: 'San', last_name: 'Zhang', email: 'san@example.com', phone: '09000000000' } },
  });
  orderNo = r.data.order_no;
  ok('pay/create', !!orderNo, `order_no=${orderNo} status=${r.data.status} pay_url=${(r.data.pay_url || '').slice(0, 48)}`);
} catch (e) { ok('pay/create', false, `${e.name} ${e.code ?? ''} ${e.message} ${JSON.stringify(e.data ?? '')}`); }

// 4. pay/query（按商户单号）
try { const r = await client.payQuery({ out_order_no: outOrderNo }); ok('pay/query', r.data.out_order_no === outOrderNo, `status=${r.data.status} notify_status=${r.data.notify_status}`); }
catch (e) { ok('pay/query', false, `${e.name} ${e.code ?? ''} ${e.message}`); }

// 5. payout/banks/query
let bankCode = null;
try { const r = await client.payoutBanksQuery({ pay_method: 'bank', country: 'PH', currency: 'PHP' }); const banks = r.data.banks || []; bankCode = banks[0]?.code; ok('payout/banks/query', Array.isArray(banks), `count=${banks.length} first=${bankCode ?? 'N/A'}`); }
catch (e) { ok('payout/banks/query', false, `${e.name} ${e.code ?? ''} ${e.message}`); }

// 6. payout/create（冻结余额；bank 类需 bank_code）
const outPayoutNo = `sdkw-${tag}`;
try {
  const r = await client.payoutCreate({
    out_payout_no: outPayoutNo, amount: 10000, currency: 'PHP', pay_method: bankCode ? 'bank' : 'gcash', country: 'PH',
    notify_url: 'https://merchant.example.com/api/notify/payout',
    account_no: '1234567890', account_name: 'San Zhang', ...(bankCode ? { bank_code: bankCode } : {}),
  });
  ok('payout/create', !!r.data.payout_no, `payout_no=${r.data.payout_no} status=${r.data.status} freeze=${r.data.freeze_amount ?? ''}`);
} catch (e) { ok('payout/create', false, `${e.name} ${e.code ?? ''} ${e.message} ${JSON.stringify(e.data ?? '')}`); }

// 7. payout/query
try { const r = await client.payoutQuery({ out_payout_no: outPayoutNo }); ok('payout/query', r.data.out_payout_no === outPayoutNo, `status=${r.data.status} sub_state=${r.data.sub_state ?? ''}`); }
catch (e) { ok('payout/query', false, `${e.name} ${e.code ?? ''} ${e.message}`); }

// 8. 负例：错误密钥签名应被服务端拒（code 100104）
try {
  const badClient = new Client(new Config({ merchantNo: env.PP_MNO, apiKey: env.PP_KEY, apiSecretPay: 'deadbeef'.repeat(8), apiSecretPayout: env.PP_POUT, baseUrl: env.PP_BASE }));
  await badClient.payQuery({ out_order_no: outOrderNo });
  ok('负例:错误签名被拒', false, '未抛错（异常）');
} catch (e) { ok('负例:错误签名被拒', e.code === 100104 || e.code === 100000, `code=${e.code ?? ''} ${e.message}`); }

// 9. 回调验签自证（用 SDK 自签 → 验签 true；篡改 → false）
const cb = { merchant_no: env.PP_MNO, order_no: orderNo || 'P_demo', out_order_no: outOrderNo, amount: 10000, currency: 'PHP', status: 'success', paid_at: '2026-06-23T08:00:00+08:00' };
cb.sign = sign(cb, env.PP_PAY);
ok('回调验签 正例', client.verifyPayCallback(cb) === true);
ok('回调验签 反例(篡改amount)', client.verifyPayCallback({ ...cb, amount: 10001 }) === false);

console.log(`\n[Node] 结果: ${pass} 通过, ${fail} 失败`);
process.exit(fail > 0 ? 1 : 0);
