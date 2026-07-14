<?php

declare(strict_types=1);

/**
 * 示例：代付下单（payout/create）+ 查询可用银行 + 代付查单。
 *
 * 运行：php examples/payout_create.php
 */

require __DIR__ . '/../autoload.php';

use Merchant\Openapi\Client;
use Merchant\Openapi\Config;
use Merchant\Openapi\Environment;
use Merchant\Openapi\Exception\ApiException;
use Merchant\Openapi\Exception\TransportException;

$config = new Config(
    merchantNo: getenv('PP_MERCHANT_NO') ?: 'M00000001',
    apiKey: getenv('PP_API_KEY') ?: 'ak_demo_key',
    apiSecretPay: getenv('PP_API_SECRET_PAY') ?: 'sk_test_pay',
    apiSecretPayout: getenv('PP_API_SECRET_PAYOUT') ?: 'sk_test_payout',
    environment: Environment::SANDBOX,
);

$client = new Client($config);

try {
    // 按支付能力 bank 查可用银行，取 code 作为 bank_code。此 pay_method 不是下单的支付分组。
    $banks = $client->payoutBanksQuery([
        'pay_method' => 'bank',
        'country' => 'PH',
        'currency' => 'PHP',
    ]);
    $bankCode = $banks['banks'][0]['code'] ?? 'BDO';
    echo "选用银行编码: $bankCode\n";

    $data = $client->payoutCreate([
        'out_payout_no' => 'WD' . time(),
        'amount' => 100000, // 10.00 元
        'currency' => 'PHP',
        'pay_method' => 'bank',
        'country' => 'PH',
        'notify_url' => 'https://merchant.example.com/api/notify/payout',
        'account_no' => '1234567890',
        'account_name' => 'San Zhang',
        'bank_code' => $bankCode,
    ]);

    echo "代付受理成功:\n";
    echo "  payout_no     = {$data['payout_no']}\n";
    echo "  status        = {$data['status']}\n";
    echo "  freeze_amount = " . ($data['freeze_amount'] ?? '(空)') . "\n";

    // 查单（最终结果以异步回调与查单为准）
    $queried = $client->payoutQuery(['payout_no' => $data['payout_no']]);
    echo "查单状态: {$queried['status']}, sub_state: " . ($queried['sub_state'] ?? 'null') . "\n";
} catch (ApiException $e) {
    fwrite(STDERR, "业务错误 [{$e->apiCode}]: {$e->apiMessage}\n");
    if ($e->data !== null) {
        fwrite(STDERR, '  data: ' . json_encode($e->data, JSON_UNESCAPED_UNICODE) . "\n");
    }
    exit(1);
} catch (TransportException $e) {
    fwrite(STDERR, "传输错误: {$e->getMessage()}\n");
    exit(2);
}
