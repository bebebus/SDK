<?php
// PHP SDK × dev 环境联调。凭据从环境变量读取（PP_MNO/PP_KEY/PP_PAY/PP_POUT/PP_BASE）。
// 序列与 dev_smoke.mjs 完全一致，便于跨语言对比。
declare(strict_types=1);

require __DIR__ . '/../php/autoload.php';

use ProjectP\Sdk\Client;
use ProjectP\Sdk\Config;
use ProjectP\Sdk\Environment;
use ProjectP\Sdk\Signer;
use ProjectP\Sdk\Exception\ApiException;

$mno = getenv('PP_MNO');
$key = getenv('PP_KEY');
$pay = getenv('PP_PAY');
$pout = getenv('PP_POUT');
$base = getenv('PP_BASE');

$cfg = new Config($mno, $key, $pay, $pout, Environment::PRODUCTION, $base);
$client = new Client($cfg);
$tag = 'php-' . time() . '-' . random_int(1000, 9999);
$pass = 0; $fail = 0;
function ok(string $n, bool $c, string $extra = ''): void {
    global $pass, $fail;
    $c ? $pass++ : $fail++;
    echo ($c ? '✅' : '❌') . " $n" . ($extra !== '' ? ' | ' . $extra : '') . "\n";
}

echo "[PHP] base=$base merchant=$mno tag=$tag\n";

// 1. pay-methods/query
try { $d = $client->payMethodsQuery(['country' => 'PH']); $m = $d['methods'] ?? []; ok('pay-methods/query', count($m) > 0, implode(',', array_column($m, 'pay_method'))); }
catch (\Throwable $e) { ok('pay-methods/query', false, get_class($e) . ' ' . $e->getMessage()); }

// 2. balance/query
try { $d = $client->balanceQuery([]); ok('balance/query', isset($d['balances']) && is_array($d['balances']), json_encode($d['balances'], JSON_UNESCAPED_UNICODE)); }
catch (\Throwable $e) { ok('balance/query', false, get_class($e) . ' ' . $e->getMessage()); }

// 3. pay/create
$outOrderNo = "sdk-$tag";
$orderNo = null;
try {
    $d = $client->payCreate([
        'out_order_no' => $outOrderNo, 'amount' => 10000, 'currency' => 'PHP', 'pay_method' => 'gcash', 'country' => 'PH',
        'notify_url' => 'https://merchant.example.com/api/notify/pay',
        'extra' => ['customer' => ['first_name' => 'San', 'last_name' => 'Zhang', 'email' => 'san@example.com', 'phone' => '09000000000']],
    ]);
    $orderNo = $d['order_no'] ?? null;
    ok('pay/create', $orderNo !== null, "order_no=$orderNo status=" . ($d['status'] ?? '') . ' pay_url=' . substr((string)($d['pay_url'] ?? ''), 0, 48));
} catch (ApiException $e) { ok('pay/create', false, "ApiException code={$e->apiCode} {$e->apiMessage} " . json_encode($e->data, JSON_UNESCAPED_UNICODE)); }
catch (\Throwable $e) { ok('pay/create', false, get_class($e) . ' ' . $e->getMessage()); }

// 4. pay/query
try { $d = $client->payQuery(['out_order_no' => $outOrderNo]); ok('pay/query', ($d['out_order_no'] ?? '') === $outOrderNo, 'status=' . ($d['status'] ?? '') . ' notify_status=' . ($d['notify_status'] ?? '')); }
catch (\Throwable $e) { ok('pay/query', false, get_class($e) . ' ' . $e->getMessage()); }

// 5. payout/banks/query
$bankCode = null;
try { $d = $client->payoutBanksQuery(['pay_method' => 'bank', 'country' => 'PH', 'currency' => 'PHP']); $banks = $d['banks'] ?? []; $bankCode = $banks[0]['code'] ?? null; ok('payout/banks/query', is_array($banks), 'count=' . count($banks) . ' first=' . ($bankCode ?? 'N/A')); }
catch (\Throwable $e) { ok('payout/banks/query', false, get_class($e) . ' ' . $e->getMessage()); }

// 6. payout/create
$outPayoutNo = "sdkw-$tag";
try {
    $params = [
        'out_payout_no' => $outPayoutNo, 'amount' => 10000, 'currency' => 'PHP',
        'pay_method' => $bankCode ? 'bank' : 'gcash', 'country' => 'PH',
        'notify_url' => 'https://merchant.example.com/api/notify/payout',
        'account_no' => '1234567890', 'account_name' => 'San Zhang',
    ];
    if ($bankCode) { $params['bank_code'] = $bankCode; }
    $d = $client->payoutCreate($params);
    ok('payout/create', isset($d['payout_no']), 'payout_no=' . ($d['payout_no'] ?? '') . ' status=' . ($d['status'] ?? '') . ' freeze=' . ($d['freeze_amount'] ?? ''));
} catch (ApiException $e) { ok('payout/create', false, "ApiException code={$e->apiCode} {$e->apiMessage} " . json_encode($e->data, JSON_UNESCAPED_UNICODE)); }
catch (\Throwable $e) { ok('payout/create', false, get_class($e) . ' ' . $e->getMessage()); }

// 7. payout/query
try { $d = $client->payoutQuery(['out_payout_no' => $outPayoutNo]); ok('payout/query', ($d['out_payout_no'] ?? '') === $outPayoutNo, 'status=' . ($d['status'] ?? '') . ' sub_state=' . ($d['sub_state'] ?? '')); }
catch (\Throwable $e) { ok('payout/query', false, get_class($e) . ' ' . $e->getMessage()); }

// 8. 负例：错误密钥签名应被服务端拒（code 100104）
try {
    $bad = new Client(new Config($mno, $key, str_repeat('deadbeef', 8), $pout, Environment::PRODUCTION, $base));
    $bad->payQuery(['out_order_no' => $outOrderNo]);
    ok('负例:错误签名被拒', false, '未抛错（异常）');
} catch (ApiException $e) { ok('负例:错误签名被拒', $e->apiCode === 100104 || $e->apiCode === 100000, "code={$e->apiCode} {$e->apiMessage}"); }
catch (\Throwable $e) { ok('负例:错误签名被拒', false, get_class($e) . ' ' . $e->getMessage()); }

// 9. 回调验签自证
$cb = ['merchant_no' => $mno, 'order_no' => $orderNo ?? 'P_demo', 'out_order_no' => $outOrderNo, 'amount' => 10000, 'currency' => 'PHP', 'status' => 'success', 'paid_at' => '2026-06-23T08:00:00+08:00'];
$cb['sign'] = Signer::sign($cb, $pay);
ok('回调验签 正例', $client->verifyPayCallback($cb) === true);
$tampered = $cb; $tampered['amount'] = 10001;
ok('回调验签 反例(篡改amount)', $client->verifyPayCallback($tampered) === false);

echo "\n[PHP] 结果: $pass 通过, $fail 失败\n";
exit($fail > 0 ? 1 : 0);
