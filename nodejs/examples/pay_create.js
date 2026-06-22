// 示例：代收下单 + 查单。
// 运行：node examples/pay_create.js
// 需在环境变量提供凭证（或直接改下面常量）：
//   PP_MERCHANT_NO, PP_API_KEY, PP_API_SECRET_PAY, [PP_BASE_URL]
import { Client, Config, Environment, ApiError, TransportError } from '../src/index.js';

const config = new Config({
  merchantNo: process.env.PP_MERCHANT_NO || 'M00000001',
  apiKey: process.env.PP_API_KEY || 'ak_demo_key',
  apiSecretPay: process.env.PP_API_SECRET_PAY || 'sk_test_pay',
  // 双环境：默认 PRODUCTION；本地联调用 SANDBOX；代理专有域名用 baseUrl 覆盖。
  environment: Environment.SANDBOX,
  baseUrl: process.env.PP_BASE_URL || undefined,
});

const client = new Client(config);

async function main() {
  try {
    const outOrderNo = 'DEMO' + Date.now();
    const { data } = await client.payCreate({
      out_order_no: outOrderNo,
      amount: 10000, // 1 元（最小单位整数）
      currency: 'PHP',
      pay_method: 'gcash',
      country: 'PH',
      notify_url: 'https://merchant.example.com/api/notify/pay',
      subject: '测试商品',
      extra: {
        customer: { first_name: 'San', last_name: 'Zhang', email: 'san@example.com' },
      },
    });
    console.log('下单成功:', data);

    // 查单（用商户订单号）
    const { data: q } = await client.payQuery({ out_order_no: outOrderNo });
    console.log('查单结果:', q);
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
