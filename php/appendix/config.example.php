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
 *   // 代付端点仅在 /api/open/v1（v2 下 payout/* 返回 404）：SDK ≥ 1.3.0 的 payout 方法会自动
 *   // 回落 v1，可复用上面的客户端；SDK 1.2.x 请为代付另建指向 base_url_payout 的客户端：
 *   $payoutClient = new \Merchant\Openapi\Client(new \Merchant\Openapi\Config(
 *       merchantNo: $cfg['merchant_no'],
 *       apiKey: $cfg['api_key'],
 *       apiSecretPay: $cfg['api_secret_pay'],
 *       apiSecretPayout: $cfg['api_secret_payout'],
 *       baseUrl: $cfg['base_url_payout'],
 *       timeoutSeconds: $cfg['timeout_seconds'],
 *   ));
 */
return [
    'merchant_no' => '<YOUR_MERCHANT_NO>',
    'api_key' => '<YOUR_API_KEY>',
    'api_secret_pay' => '<YOUR_API_SECRET_PAY>',
    'api_secret_payout' => '<YOUR_API_SECRET_PAYOUT>',
    // 正式地址请向服务商获取；代收新对接用 /api/open/v2（channel_code 仅代收 v2 使用）。
    'base_url' => 'https://api.<service_domain>/api/open/v2',
    // 代付端点仅在 v1（v2 下 payout/* 返回 404）：代付客户端请用本地址（SDK ≥ 1.3.0 自动回落可不用双客户端）。
    'base_url_payout' => 'https://api.<service_domain>/api/open/v1',
    'timeout_seconds' => 30,
];
