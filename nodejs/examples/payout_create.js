// 示例：代付下单（银行类）+ 查单 + 查可用银行。
// 运行：node examples/payout_create.js
// 需提供：PP_MERCHANT_NO, PP_API_KEY, PP_API_SECRET_PAYOUT, [PP_BASE_URL]
import { Client, Config, Environment, ApiError, TransportError } from '../src/index.js';

const config = new Config({
  merchantNo: process.env.PP_MERCHANT_NO || 'M00000001',
  apiKey: process.env.PP_API_KEY || 'ak_demo_key',
  apiSecretPayout: process.env.PP_API_SECRET_PAYOUT || 'sk_test_payout',
  environment: Environment.SANDBOX,
  baseUrl: process.env.PP_BASE_URL || undefined,
});

const client = new Client(config);

async function main() {
  try {
    // 先查可用银行，取一个 bank_code
    const { data: banksData } = await client.payoutBanksQuery({
      pay_method: 'bank',
      country: 'PH',
      currency: 'PHP',
    });
    console.log('可用银行:', banksData.banks);
    const bankCode = banksData.banks?.[0]?.code || 'BDO';

    const outPayoutNo = 'WD' + Date.now();
    const { data } = await client.payoutCreate({
      out_payout_no: outPayoutNo,
      amount: 100000, // 10 元
      currency: 'PHP',
      pay_method: 'bank',
      country: 'PH',
      notify_url: 'https://merchant.example.com/api/notify/payout',
      account_no: '1234567890',
      account_name: 'San Zhang',
      bank_code: bankCode,
    });
    console.log('代付受理:', data);

    const { data: q } = await client.payoutQuery({ out_payout_no: outPayoutNo });
    console.log('代付查单:', q);
  } catch (err) {
    if (err instanceof ApiError) {
      console.error(`业务错误 code=${err.code} message=${err.message}`, err.data);
    } else if (err instanceof TransportError) {
      console.error(`传输错误 status=${err.statusCode}`, err.message);
    } else {
      throw err;
    }
  }
}

main();
