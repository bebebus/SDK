<?php

declare(strict_types=1);

/**
 * 示例：代收下单（pay/create）+ 代收查单（pay/query）。
 *
 * 运行：php examples/pay_create.php
 * （需服务端可达；这里用 SANDBOX 本地基址，也可换 PRODUCTION 或自定义代理域名。）
 */

require __DIR__ . '/../autoload.php';

use Merchant\Openapi\Client;
use Merchant\Openapi\Config;
use Merchant\Openapi\Environment;
use Merchant\Openapi\Exception\ApiException;
use Merchant\Openapi\Exception\TransportException;

// 凭证从环境变量读取，避免硬编码
$config = new Config(
    merchantNo: getenv('PP_MERCHANT_NO') ?: 'M00000001',
    apiKey: getenv('PP_API_KEY') ?: 'ak_demo_key',
    apiSecretPay: getenv('PP_API_SECRET_PAY') ?: 'sk_test_pay',
    apiSecretPayout: getenv('PP_API_SECRET_PAYOUT') ?: 'sk_test_payout',
    environment: Environment::SANDBOX, // 正式用 Environment::PRODUCTION + 显式 baseUrl
    // baseUrl: 'https://api.<agent_domain>/api/open/v1', // 正式按上级代理专有域名显式传入
);

$client = new Client($config);

try {
    // 金额是最小单位整数：10000 = 1.00 元
    $data = $client->payCreate([
        'out_order_no' => 'DEMO' . time(),
        'amount' => 10000,
        'currency' => 'PHP',
        'pay_method' => 'gcash',
        'country' => 'PH',
        'notify_url' => 'https://merchant.example.com/api/notify/pay',
        'subject' => '订单/支付 <demo>',
        'extra' => [
            'customer' => [
                'first_name' => 'San',
                'last_name' => 'Zhang',
                'email' => 'san@example.com',
                'phone' => '0900000000',
            ],
        ],
    ]);

    echo "下单成功:\n";
    echo "  order_no    = {$data['order_no']}\n";
    echo "  status      = {$data['status']}\n";
    echo "  pay_url     = " . ($data['pay_url'] ?? '(空)') . "\n";

    // 用返回的 order_no 查单
    $queried = $client->payQuery(['order_no' => $data['order_no']]);
    echo "查单状态: {$queried['status']}\n";
} catch (ApiException $e) {
    // 业务错误：携带 code/message/data
    fwrite(STDERR, "业务错误 [{$e->apiCode}]: {$e->apiMessage}\n");
    if ($e->data !== null) {
        fwrite(STDERR, '  data: ' . json_encode($e->data, JSON_UNESCAPED_UNICODE) . "\n");
    }
    exit(1);
} catch (TransportException $e) {
    // 网络/HTTP 错误
    fwrite(STDERR, "传输错误: {$e->getMessage()}\n");
    exit(2);
}
