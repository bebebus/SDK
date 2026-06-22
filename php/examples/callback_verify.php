<?php

declare(strict_types=1);

/**
 * 示例：回调验签 + 幂等处理 + 正确应答（代收回调与代付回调各演示一次）。
 *
 * 这是「验签 + 处理代码片段」，不是常驻 HTTP 服务。把 handlePayCallback / handlePayoutCallback
 * 的内容搬进你的 Web 框架路由处理器即可（拿原始 body → 验签 → 按 status 幂等处理 → 回 200 + "success"）。
 *
 * 直接运行（演示用，含自造的合法回调样例）：php examples/callback_verify.php
 */

require __DIR__ . '/../autoload.php';

use ProjectP\Sdk\Config;
use ProjectP\Sdk\Environment;
use ProjectP\Sdk\Signer;

// 实际部署里用真实密钥（建议从环境变量/密钥管理读取）
$config = new Config(
    merchantNo: getenv('PP_MERCHANT_NO') ?: 'M00000001',
    apiKey: getenv('PP_API_KEY') ?: 'ak_demo_key',
    apiSecretPay: getenv('PP_API_SECRET_PAY') ?: 'sk_test_pay_secret',
    apiSecretPayout: getenv('PP_API_SECRET_PAYOUT') ?: 'sk_test_payout_secret',
    environment: Environment::PRODUCTION,
);

/**
 * 代收/退款回调处理器（密钥 api_secret_pay）。
 *
 * @param string $rawBody 原始请求体（不要先反序列化再重序列化——直接 json_decode 原始字节）
 * @return array{status:int, body:string} HTTP 应答
 */
function handlePayCallback(string $rawBody, Config $config): array
{
    $payload = json_decode($rawBody, true);
    if (!is_array($payload)) {
        // 非法 JSON：拒绝，让平台重试
        return ['status' => 400, 'body' => 'invalid json'];
    }

    // 验签：除 sign 外所有字段参与，时序安全比较；代收/退款用 api_secret_pay
    if (!Signer::verifyCallback($payload, $config->apiSecretPay)) {
        // 验签失败 → 不要回成功，让平台重试
        return ['status' => 401, 'body' => 'invalid sign'];
    }

    $orderNo = (string) ($payload['out_order_no'] ?? $payload['order_no'] ?? '');
    $status = (string) ($payload['status'] ?? '');

    // 幂等：同一订单可能被回调多次。先查本地状态，已处理过的终态直接幂等返回 success。
    // if (alreadyProcessed($orderNo)) { return ack(); }

    switch ($status) {
        case 'success':
            // 入账/发货等业务（必须幂等，建议带订单号唯一约束/状态机 CAS）
            // creditOrder($orderNo, (int) $payload['actual_amount']);
            fwrite(STDOUT, "代收成功回调: order=$orderNo amount={$payload['amount']}\n");
            break;
        case 'failed':
            // 标记失败
            // markOrderFailed($orderNo);
            fwrite(STDOUT, "代收失败回调: order=$orderNo\n");
            break;
        default:
            // 未知/中间态：可忽略或记录，但仍应回 success 避免无意义重试
            fwrite(STDOUT, "代收回调其它状态: order=$orderNo status=$status\n");
    }

    // 应答：HTTP 200 + 纯文本 success（平台据此判定无需重试）
    return ['status' => 200, 'body' => 'success'];
}

/**
 * 代付回调处理器（密钥 api_secret_payout）。
 *
 * @return array{status:int, body:string}
 */
function handlePayoutCallback(string $rawBody, Config $config): array
{
    $payload = json_decode($rawBody, true);
    if (!is_array($payload)) {
        return ['status' => 400, 'body' => 'invalid json'];
    }

    // 代付回调用 api_secret_payout
    if (!Signer::verifyCallback($payload, $config->apiSecretPayout)) {
        return ['status' => 401, 'body' => 'invalid sign'];
    }

    $payoutNo = (string) ($payload['out_payout_no'] ?? $payload['payout_no'] ?? '');
    $status = (string) ($payload['status'] ?? '');

    switch ($status) {
        case 'success':
            // settlePayout($payoutNo);
            fwrite(STDOUT, "代付成功回调: payout=$payoutNo amount={$payload['amount']}\n");
            break;
        case 'failed':
            // 解冻/退款等（幂等）
            // refundFrozen($payoutNo, (string) ($payload['failed_reason'] ?? ''));
            fwrite(STDOUT, "代付失败回调: payout=$payoutNo reason=" . ($payload['failed_reason'] ?? '') . "\n");
            break;
        default:
            fwrite(STDOUT, "代付回调其它状态: payout=$payoutNo status=$status\n");
    }

    return ['status' => 200, 'body' => 'success'];
}

/* ---------------- 演示：用自造的合法回调样例跑一遍两个处理器 ---------------- */

// 代收回调样例（用 pay 密钥签名）
$payBody = [
    'merchant_no' => 'M00000001',
    'order_no' => 'P202501010001',
    'out_order_no' => '202501010001',
    'amount' => 10000,
    'actual_amount' => 10000,
    'currency' => 'PHP',
    'status' => 'success',
    'channel_order_no' => null,
];
$payBody['sign'] = Signer::sign($payBody, $config->apiSecretPay);
$payRawBody = json_encode($payBody, JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE);

$resp = handlePayCallback($payRawBody, $config);
echo "代收回调应答: HTTP {$resp['status']} body={$resp['body']}\n";

// 代付回调样例（用 payout 密钥签名）
$payoutBody = [
    'merchant_no' => 'M00000001',
    'payout_no' => 'W202501010001',
    'out_payout_no' => 'WD202501010001',
    'amount' => 100000,
    'currency' => 'PHP',
    'status' => 'success',
    'channel_order_no' => null,
];
$payoutBody['sign'] = Signer::sign($payoutBody, $config->apiSecretPayout);
$payoutRawBody = json_encode($payoutBody, JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE);

$resp2 = handlePayoutCallback($payoutRawBody, $config);
echo "代付回调应答: HTTP {$resp2['status']} body={$resp2['body']}\n";

// 反例：篡改后验签失败
$payBody['amount'] = 99999; // sign 不变 → 验签应失败
$tamperedRaw = json_encode($payBody, JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE);
$resp3 = handlePayCallback($tamperedRaw, $config);
echo "篡改回调应答（应拒绝）: HTTP {$resp3['status']} body={$resp3['body']}\n";
