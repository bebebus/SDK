<?php

declare(strict_types=1);

/**
 * 零依赖测试 runner（禁 PHPUnit）。
 *
 * 跑法：php tests/run.php
 * 退出码 0 = 全绿；非 0 = 有失败。
 *
 * 覆盖：
 *  (a) 读取 ../../test-vectors.json，对每个向量断言 buildSignBase == base 且 sign == sign；
 *  (b) 回调验签正例（pay/payout）+ 篡改一字节的反例；
 *  (c) 若干边界单测（null 跳过、布尔归一、inline 1/0、自定义基址、信封解析与异常）。
 */

require __DIR__ . '/../autoload.php';

use Merchant\Openapi\Client;
use Merchant\Openapi\Config;
use Merchant\Openapi\Environment;
use Merchant\Openapi\Exception\ApiException;
use Merchant\Openapi\Exception\TransportException;
use Merchant\Openapi\Signer;

/* ---------------------------- 轻量断言框架 ---------------------------- */

$passed = 0;
$failed = 0;
$failures = [];

/**
 * @param mixed $expected
 * @param mixed $actual
 */
function check(string $name, $expected, $actual): void
{
    global $passed, $failed, $failures;
    if ($expected === $actual) {
        $passed++;
        return;
    }
    $failed++;
    $failures[] = sprintf(
        "FAIL: %s\n  expected: %s\n  actual:   %s",
        $name,
        var_export($expected, true),
        var_export($actual, true)
    );
}

function checkTrue(string $name, bool $cond): void
{
    check($name, true, $cond);
}

function checkFalse(string $name, bool $cond): void
{
    check($name, false, $cond);
}

/* ---------------------------- (a) 向量复现 ---------------------------- */

$vectorsPath = __DIR__ . '/../../test-vectors.json';
if (!is_file($vectorsPath)) {
    fwrite(STDERR, "找不到向量文件: $vectorsPath\n");
    exit(2);
}

$raw = file_get_contents($vectorsPath);
if ($raw === false) {
    fwrite(STDERR, "读取向量文件失败\n");
    exit(2);
}

// 按「对象」解码（不传 true）：JSON {} → stdClass、JSON [] → array，
// 才能区分空对象 {} 与空数组 []（assoc 解码会把 {} 塌成 []，致空对象签名分叉）。
$doc = json_decode($raw);
if (!is_object($doc) || !isset($doc->vectors) || !is_array($doc->vectors)) {
    fwrite(STDERR, "向量文件格式异常\n");
    exit(2);
}

$vectorCount = 0;
foreach ($doc->vectors as $vec) {
    $vectorCount++;
    $vname = (string) ($vec->name ?? 'unnamed');
    $secret = (string) $vec->secret;
    // 顶层 stdClass → 关联数组（嵌套对象保持 stdClass、嵌套数组保持 array）
    $payload = (array) $vec->payload;

    $base = Signer::buildSignBase($payload, $secret);
    $sign = Signer::sign($payload, $secret);

    check("vector[$vname] base", $vec->base, $base);
    check("vector[$vname] sign", $vec->sign, $sign);
}

echo "已加载向量数: $vectorCount\n";

/* ---------------------------- (b) 回调验签 ---------------------------- */

$paySecret = 'sk_test_pay_secret_aaaaaaaaaaaaaaaa';
$payoutSecret = 'sk_test_payout_secret_bbbbbbbbbbbb';

// 代收回调：构造一段无 sign 的载荷，自己算 sign 塞回去，应验签通过
$payCallback = [
    'merchant_no' => 'M00000001',
    'order_no' => 'P202501010001',
    'out_order_no' => '202501010001',
    'amount' => 10000,
    'actual_amount' => 10000,
    'fee_amount' => 180,
    'net_amount' => 9820,
    'currency' => 'PHP',
    'status' => 'success',
    'channel_order_no' => null,
    'paid_at' => '2025-01-01T08:00:00+08:00',
];
$payCallback['sign'] = Signer::sign($payCallback, $paySecret);

checkTrue('callback pay 正例 verifyCallback', Signer::verifyCallback($payCallback, $paySecret));

// 通过 Client 便捷方法验签（密钥取自 Config）
$cfg = new Config('M00000001', 'ak_demo_key', $paySecret, $payoutSecret, Environment::SANDBOX);
$client = new Client($cfg);
checkTrue('callback pay 正例 client->verifyPayCallback', $client->verifyPayCallback($payCallback));

// 反例：篡改一字节（amount 末位 0→1），sign 不变，应验签失败
$tampered = $payCallback;
$tampered['amount'] = 10001;
checkFalse('callback pay 反例（篡改 amount 一字节）', Signer::verifyCallback($tampered, $paySecret));

// 反例：篡改 sign 本身一个十六进制字符
$tamperedSign = $payCallback;
$s = $tamperedSign['sign'];
$lastChar = substr($s, -1);
$tamperedSign['sign'] = substr($s, 0, -1) . ($lastChar === 'a' ? 'b' : 'a');
checkFalse('callback pay 反例（篡改 sign 一字节）', Signer::verifyCallback($tamperedSign, $paySecret));

// 反例：用错密钥（payout 密钥验代收回调）
checkFalse('callback pay 反例（错用 payout 密钥）', Signer::verifyCallback($payCallback, $payoutSecret));

// 反例：缺 sign 字段
$noSign = $payCallback;
unset($noSign['sign']);
checkFalse('callback 反例（缺 sign 字段）', Signer::verifyCallback($noSign, $paySecret));

// 代付回调正例
$payoutCallback = [
    'merchant_no' => 'M00000001',
    'payout_no' => 'W202501010001',
    'out_payout_no' => 'WD202501010001',
    'amount' => 100000,
    'currency' => 'PHP',
    'status' => 'success',
    'fee_amount' => 500,
    'channel_order_no' => null,
    'finished_at' => '2025-01-01T08:05:00+08:00',
    'failed_reason' => null,
];
$payoutCallback['sign'] = Signer::sign($payoutCallback, $payoutSecret);
checkTrue('callback payout 正例 verifyCallback', Signer::verifyCallback($payoutCallback, $payoutSecret));
checkTrue('callback payout 正例 client->verifyPayoutCallback', $client->verifyPayoutCallback($payoutCallback));

$payoutTampered = $payoutCallback;
$payoutTampered['status'] = 'failed';
checkFalse('callback payout 反例（篡改 status）', Signer::verifyCallback($payoutTampered, $payoutSecret));

/* ---------------------------- (c) 边界单测 ---------------------------- */

// 布尔顶层归一：true→"true" false→"false"（防 PHP (string)true=="1"）
check(
    'scalar bool 归一',
    'a=true&b=false&secret=s',
    Signer::buildSignBase(['a' => true, 'b' => false], 's')
);

// null 跳过 + sign 跳过
check(
    'null/sign 跳过',
    'a=1&secret=s',
    Signer::buildSignBase(['a' => '1', 'b' => null, 'sign' => 'x'], 's')
);

// 整数顶层
check('整数顶层', 'n=10000&secret=s', Signer::buildSignBase(['n' => 10000], 's'));

// 空对象（stdClass）→ {}，与空数组 [] → [] 区分（HIGH 项回归）
check('空对象 stdClass → {}', 'extra={}&secret=s', Signer::buildSignBase(['extra' => new \stdClass()], 's'));
check('空数组 [] → []', 'extra=[]&secret=s', Signer::buildSignBase(['extra' => []], 's'));
check(
    '嵌套空对象/空数组',
    'm={"a":{},"b":[]}&secret=s',
    Signer::buildSignBase(['m' => (object) ['a' => new \stdClass(), 'b' => []]], 's')
);

// 自定义 baseUrl 覆盖 Environment（即使选 PRODUCTION，传了 baseUrl 即生效并去尾斜杠）
$cfgCustom = new Config('M', 'k', 'p', 'q', Environment::PRODUCTION, 'https://api.agent.example.com/api/open/v1/');
check('自定义 baseUrl 覆盖（去尾斜杠）', 'https://api.agent.example.com/api/open/v1', $cfgCustom->baseUrl);

// PRODUCTION 无内置地址：不传 baseUrl 必须抛清晰错误（正式地址按上级代理专有域名派生）
$prodThrew = false;
try {
    new Config('M', 'k', 'p', 'q', Environment::PRODUCTION);
} catch (\InvalidArgumentException $e) {
    $prodThrew = true;
    checkTrue('PRODUCTION 缺 baseUrl 错误含提示', str_contains($e->getMessage(), 'baseUrl is required'));
}
checkTrue('PRODUCTION 缺 baseUrl 抛 InvalidArgumentException', $prodThrew);

// SANDBOX 预设仍内置本地基址
$cfgSand = new Config('M', 'k', 'p', 'q', Environment::SANDBOX);
check('SANDBOX 基址', 'http://127.0.0.1:3090/api/open/v1', $cfgSand->baseUrl);

/* ---- 用注入的 HTTP 桩验证：请求构建（通用字段/nonce/签名/null 过滤）+ 信封解析 + 密钥选择 ---- */

// 捕获最近一次发出的请求体
$captured = ['url' => null, 'body' => null];
$stub = function (string $url, string $json, int $timeout) use (&$captured): array {
    $captured['url'] = $url;
    $captured['body'] = json_decode($json, true);
    return ['status' => 200, 'body' => json_encode(['code' => 0, 'message' => 'ok', 'data' => ['echo' => true]])];
};

$stubClient = new Client(
    new Config('M00000001', 'ak_demo_key', $paySecret, $payoutSecret, Environment::SANDBOX),
    $stub
);

// pay/create：null 字段不应进入请求体；通用字段齐全；sign 用 pay 密钥
$data = $stubClient->payCreate([
    'out_order_no' => '202501010001',
    'amount' => 10000,
    'currency' => 'PHP',
    'pay_method' => 'gcash',
    'country' => 'PH',
    'notify_url' => 'https://merchant.example.com/api/notify/pay',
    'return_url' => null, // 应被过滤
    'subject' => null,    // 应被过滤
]);

check('stub payCreate 返回 data', ['echo' => true], $data);
check('stub payCreate URL', 'http://127.0.0.1:3090/api/open/v1/merchant/pay/create', $captured['url']);
checkTrue('stub 通用字段 merchant_no', $captured['body']['merchant_no'] === 'M00000001');
checkTrue('stub 通用字段 api_key', $captured['body']['api_key'] === 'ak_demo_key');
checkTrue('stub 通用字段 timestamp 是整数', is_int($captured['body']['timestamp']));
checkTrue('stub 通用字段 nonce 存在且非空', is_string($captured['body']['nonce']) && $captured['body']['nonce'] !== '');
checkTrue('stub 含 sign', isset($captured['body']['sign']) && is_string($captured['body']['sign']));
checkFalse('stub null 字段 return_url 被过滤', array_key_exists('return_url', $captured['body']));
checkFalse('stub null 字段 subject 被过滤', array_key_exists('subject', $captured['body']));

// 验证 sign 确实用 pay 密钥算（拿请求体去掉 sign 自算应一致）
$bodyNoSign = $captured['body'];
$receivedSign = $bodyNoSign['sign'];
unset($bodyNoSign['sign']);
check('stub payCreate sign 用 pay 密钥', Signer::sign($bodyNoSign, $paySecret), $receivedSign);

// nonce 每请求唯一
$firstNonce = $captured['body']['nonce'];
$stubClient->payQuery(['out_order_no' => '202501010001']);
$secondNonce = $captured['body']['nonce'];
checkTrue('nonce 每请求唯一', $firstNonce !== $secondNonce);

// payout 类用 payout 密钥
$stubClient->payoutCreate([
    'out_payout_no' => 'WD1',
    'amount' => 100000,
    'currency' => 'PHP',
    'pay_method' => 'bank',
    'notify_url' => 'https://m.example.com/cb',
    'account_no' => '123',
    'account_name' => 'San Zhang',
    'bank_code' => 'BDO',
]);
$pbody = $captured['body'];
$psign = $pbody['sign'];
unset($pbody['sign']);
check('stub payoutCreate sign 用 payout 密钥', Signer::sign($pbody, $payoutSecret), $psign);

// receipt inline 归一为整数 1/0
$stubClient->payoutReceiptQuery(['out_payout_no' => 'WD1', 'inline' => true]);
check('receipt inline true→1', 1, $captured['body']['inline']);
checkTrue('receipt inline 是整数', is_int($captured['body']['inline']));
$stubClient->payoutReceiptQuery(['out_payout_no' => 'WD1', 'inline' => false]);
check('receipt inline false→0', 0, $captured['body']['inline']);

// 业务码非 0 → ApiException（携带 code/message/data）
$errStub = fn (string $u, string $j, int $t): array => [
    'status' => 200,
    'body' => json_encode(['code' => 100104, 'message' => '签名错误', 'data' => ['missing_fields' => ['x']]]),
];
$errClient = new Client(new Config('M', 'k', $paySecret, $payoutSecret, Environment::SANDBOX), $errStub);
$caught = null;
try {
    $errClient->payQuery(['out_order_no' => 'x']);
} catch (ApiException $e) {
    $caught = $e;
}
checkTrue('业务码非0 抛 ApiException', $caught instanceof ApiException);
check('ApiException code', 100104, $caught?->apiCode);
check('ApiException message', '签名错误', $caught?->apiMessage);
check('ApiException data', ['missing_fields' => ['x']], $caught?->data);

// HTTP 非 2xx → TransportException
$httpErrStub = fn (string $u, string $j, int $t): array => ['status' => 502, 'body' => 'Bad Gateway'];
$httpErrClient = new Client(new Config('M', 'k', $paySecret, $payoutSecret, Environment::SANDBOX), $httpErrStub);
$caughtT = null;
try {
    $httpErrClient->payQuery(['out_order_no' => 'x']);
} catch (TransportException $e) {
    $caughtT = $e;
}
checkTrue('HTTP 非2xx 抛 TransportException', $caughtT instanceof TransportException);
check('TransportException httpStatus', 502, $caughtT?->httpStatus);

// 非法 JSON 信封 → TransportException
$badJsonStub = fn (string $u, string $j, int $t): array => ['status' => 200, 'body' => 'not json'];
$badJsonClient = new Client(new Config('M', 'k', $paySecret, $payoutSecret, Environment::SANDBOX), $badJsonStub);
$caughtJ = null;
try {
    $badJsonClient->payQuery(['out_order_no' => 'x']);
} catch (TransportException $e) {
    $caughtJ = $e;
}
checkTrue('非法 JSON 抛 TransportException', $caughtJ instanceof TransportException);

// 原始响应保留途径
checkTrue('lastRawResponse 可取', is_array($stubClient->lastRawResponse()));

/* ---------------------------- (d) fail-closed 守卫 ---------------------------- */

// [A] 空/空白 secret：sign 抛错、verifyCallback 直接 false（算 HMAC 前就拒绝）
$guardPayload = ['amount' => 10000, 'api_key' => 'ak_demo_key'];
$guardSign = Signer::sign($guardPayload, $paySecret);
$guardCallback = $guardPayload + ['sign' => $guardSign];

$emptySignThrew = false;
try {
    Signer::sign($guardPayload, '');
} catch (\InvalidArgumentException $e) {
    $emptySignThrew = true;
}
checkTrue('[A] 空 secret sign 抛 InvalidArgumentException', $emptySignThrew);

$blankSignThrew = false;
try {
    Signer::sign($guardPayload, "  \t ");
} catch (\InvalidArgumentException $e) {
    $blankSignThrew = true;
}
checkTrue('[A] 全空白 secret sign 抛错', $blankSignThrew);

checkFalse('[A] 空 secret verifyCallback=false', Signer::verifyCallback($guardCallback, ''));
checkFalse('[A] 全空白 secret verifyCallback=false', Signer::verifyCallback($guardCallback, "\n\t "));
checkTrue('[A] 合法 secret verifyCallback=true', Signer::verifyCallback($guardCallback, $paySecret));

// [B] 攻击者可控的非法 sign（非字符串/非hex/长度异常/大写）一律 false，绝不抛
$nonStr = $guardCallback;
$nonStr['sign'] = 12345;
checkFalse('[B] sign 非字符串=false', Signer::verifyCallback($nonStr, $paySecret));
$nonHex = $guardCallback;
$nonHex['sign'] = str_repeat('z', 64);
checkFalse('[B] sign 非hex=false', Signer::verifyCallback($nonHex, $paySecret));
$badLen = $guardCallback;
$badLen['sign'] = str_repeat('a', 63);
checkFalse('[B] sign 长度异常=false', Signer::verifyCallback($badLen, $paySecret));
$upper = $guardCallback;
$upper['sign'] = strtoupper($guardSign);
checkFalse('[B] sign 大写hex=false（服务端固定小写）', Signer::verifyCallback($upper, $paySecret));

// [C] 浮点拒绝（NaN/Infinity/非整数/整数值float/-0.0），整数仍正常
foreach (['NaN' => NAN, 'Infinity' => INF, '-Infinity' => -INF, '1.5' => 1.5, '1.0' => 1.0, '-0.0' => -0.0] as $label => $fv) {
    $threw = false;
    try {
        Signer::buildSignBase(['amount' => $fv], 's');
    } catch (\InvalidArgumentException $e) {
        $threw = true;
    }
    checkTrue("[C] 浮点 $label 拒绝", $threw);
}
$nestedFloatThrew = false;
try {
    Signer::buildSignBase(['extra' => ['x' => 1.5]], 's');
} catch (\InvalidArgumentException $e) {
    $nestedFloatThrew = true;
}
checkTrue('[C] 嵌套浮点拒绝', $nestedFloatThrew);
check('[C] 整数 0 仍正常 → "0"', 'count=0&secret=s', Signer::buildSignBase(['count' => 0], 's'));

// 大整数安全验签便捷方法 verifyCallbackRaw
$rawOk = json_encode($guardCallback, JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE);
checkTrue('verifyCallbackRaw 正例', Signer::verifyCallbackRaw($rawOk, $paySecret));
checkFalse('verifyCallbackRaw 非JSON=false', Signer::verifyCallbackRaw('not json', $paySecret));
checkFalse('verifyCallbackRaw 顶层标量=false', Signer::verifyCallbackRaw('123', $paySecret));
checkFalse('verifyCallbackRaw null=false', Signer::verifyCallbackRaw('null', $paySecret));
checkFalse('verifyCallbackRaw 空secret=false', Signer::verifyCallbackRaw($rawOk, ''));

// [D] Config 传输安全：非 localhost http 拒绝；https/本地回环放行
$httpDomainThrew = false;
try {
    new Config('M', 'k', 'p', 'q', Environment::PRODUCTION, 'http://api.example.com/api/open/v1');
} catch (\InvalidArgumentException $e) {
    $httpDomainThrew = true;
}
checkTrue('[D] http 非本地域名拒绝', $httpDomainThrew);

$ftpThrew = false;
try {
    new Config('M', 'k', 'p', 'q', Environment::PRODUCTION, 'ftp://x/api');
} catch (\InvalidArgumentException $e) {
    $ftpThrew = true;
}
checkTrue('[D] 非 http(s) 协议拒绝', $ftpThrew);

$cfgHttps = new Config('M', 'k', 'p', 'q', Environment::PRODUCTION, 'https://api.example.com/api/open/v1');
check('[D] https 通过', 'https://api.example.com/api/open/v1', $cfgHttps->baseUrl);
$cfgLocal = new Config('M', 'k', 'p', 'q', Environment::PRODUCTION, 'http://localhost:3090/api/open/v1');
check('[D] http localhost 放行', 'http://localhost:3090/api/open/v1', $cfgLocal->baseUrl);
$cfgLoop = new Config('M', 'k', 'p', 'q', Environment::PRODUCTION, 'http://127.0.0.1:3090/api/open/v1');
check('[D] http 127.0.0.1 放行', 'http://127.0.0.1:3090/api/open/v1', $cfgLoop->baseUrl);

/* ---------------------------- 汇总 ---------------------------- */

echo "\n";
foreach ($failures as $f) {
    echo $f . "\n";
}

$total = $passed + $failed;
echo str_repeat('-', 48) . "\n";
echo sprintf("测试结果: %d 通过, %d 失败 (共 %d 断言)\n", $passed, $failed, $total);

if ($failed > 0) {
    echo "RESULT: FAILED\n";
    exit(1);
}

echo "RESULT: PASSED\n";
exit(0);
