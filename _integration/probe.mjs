// dev 联调连通/鉴权探针（只读接口）。凭据从 project-p-test/config.js 动态读取，不写入本文件。
import { merchant } from '../../project-p-test/config.js';
import { Client, Config } from '../nodejs/src/index.js';

const client = new Client(new Config({
  merchantNo: merchant.merchantNo,
  apiKey: merchant.apiKey,
  apiSecretPay: merchant.apiSecretPay,
  apiSecretPayout: merchant.apiSecretPayout,
  baseUrl: merchant.baseUrl,
}));

console.log('baseUrl:', merchant.baseUrl, '| merchant:', merchant.merchantNo);

async function call(name, fn) {
  try {
    const r = await fn();
    console.log(`✅ ${name}:`, JSON.stringify(r.data));
  } catch (e) {
    console.log(`❌ ${name}: ${e.name} code=${e.code ?? ''} msg=${e.message}`);
  }
}

await call('pay-methods/query', () => client.payMethodsQuery({ country: 'PH' }));
await call('balance/query', () => client.balanceQuery({}));
