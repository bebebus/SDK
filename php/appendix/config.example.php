<?php

declare(strict_types=1);

/**
 * 商户 OpenAPI PHP SDK 配置模板（数组）。
 *
 * 复制为本目录外的 config.php 后，把占位符换成商户后台凭据。
 * 正式环境必须使用 https；勿把真实密钥提交进仓库。
 *
 * 使用：
 *   $cfg = require __DIR__ . '/config.php';
 *   $client = new \Merchant\Openapi\Client(new \Merchant\Openapi\Config(
 *       merchantNo: $cfg['merchant_no'],
 *       apiKey: $cfg['api_key'],
 *       apiSecretPay: $cfg['api_secret_pay'],
 *       apiSecretPayout: $cfg['api_secret_payout'],
 *       baseUrl: $cfg['base_url'],
 *       timeoutSeconds: $cfg['timeout_seconds'],
 *   ));
 */
return [
    'merchant_no' => '<YOUR_MERCHANT_NO>',
    'api_key' => '<YOUR_API_KEY>',
    'api_secret_pay' => '<YOUR_API_SECRET_PAY>',
    'api_secret_payout' => '<YOUR_API_SECRET_PAYOUT>',
    // 正式地址请向服务商获取。
    'base_url' => 'https://api.<service_domain>/api/open/v1',
    'timeout_seconds' => 30,
];
