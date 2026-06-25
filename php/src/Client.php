<?php

declare(strict_types=1);

namespace Merchant\Openapi;

use Merchant\Openapi\Exception\ApiException;
use Merchant\Openapi\Exception\TransportException;

/**
 * 商户支付 OpenAPI 客户端，覆盖全部 11 个端点。
 *
 * 通用约定：
 *  - 每请求自动注入 merchant_no / api_key / timestamp(Unix 秒) / nonce(唯一随机串)。
 *  - 值为 null 的字段不放入请求体也不参与签名（由 Signer 过滤）。
 *  - pay 类接口用 api_secret_pay 签名；payout 类用 api_secret_payout（各方法自动选对）。
 *  - 解析统一信封 {code,message,data}；code !== 0 抛 ApiException；HTTP/网络错误抛 TransportException。
 *  - 金额参数一律最小单位整数（10000 = 1 元）。
 */
final class Client
{
    /**
     * [L19] SDK 版本号单一事实源：UA 由此常量派生（不再硬编码）。
     * release.sh 发版时按此常量 sed 同步（与 composer.json 一致）。
     */
    public const VERSION = '1.1.1';

    /** @var callable(string,string,int):array{status:int,body:string} 注入的 HTTP 执行器（默认 cURL） */
    private $httpExecutor;

    /** 最近一次原始响应信封（保留拿原始响应的途径） */
    private ?array $lastRawResponse = null;

    /**
     * @param Config                                                 $config
     * @param (callable(string,string,int):array{status:int,body:string})|null $httpExecutor
     *        可选注入的 HTTP 执行器（url, jsonBody, timeoutSeconds）→ {status, body}；默认走 cURL。
     *        测试可注入桩，不联网。
     */
    public function __construct(
        private readonly Config $config,
        ?callable $httpExecutor = null,
    ) {
        $this->httpExecutor = $httpExecutor ?? [$this, 'curlExecute'];
    }

    // ====================== 代收（Pay，密钥 api_secret_pay）======================

    /**
     * 代收下单 POST /merchant/pay/create。
     *
     * @param array{
     *   out_order_no:string, amount:int, currency:string, pay_method:string,
     *   notify_url:string, country?:string|null, return_url?:string|null,
     *   subject?:string|null, remark?:string|null, client_ip?:string|null, extra?:array|null
     * } $params
     * @return array<string,mixed> data
     */
    public function payCreate(array $params): array
    {
        return $this->callPay('/merchant/pay/create', $params);
    }

    /**
     * 代收查单 POST /merchant/pay/query。order_no 或 out_order_no 二选一。
     *
     * @param array{order_no?:string|null, out_order_no?:string|null} $params
     * @return array<string,mixed> data
     */
    public function payQuery(array $params): array
    {
        return $this->callPay('/merchant/pay/query', $params);
    }

    /**
     * 可用支付方式 POST /merchant/pay-methods/query。country 可选过滤。
     *
     * @param array{country?:string|null} $params
     * @return array<string,mixed> data（含 methods[]）
     */
    public function payMethodsQuery(array $params = []): array
    {
        return $this->callPay('/merchant/pay-methods/query', $params);
    }

    /**
     * 余额查询 POST /merchant/balance/query。currency 可选过滤。
     *
     * @param array{currency?:string|null} $params
     * @return array<string,mixed> data（含 balances[]）
     */
    public function balanceQuery(array $params = []): array
    {
        return $this->callPay('/merchant/balance/query', $params);
    }

    /**
     * 代收测试单完成 POST /merchant/pay/test/complete（仅测试密钥）。
     * order_no 或 out_order_no 二选一 + result(success|failed) + actual_amount(int, 可选)。
     *
     * @param array{order_no?:string|null, out_order_no?:string|null, result:string, actual_amount?:int|null} $params
     * @return array<string,mixed> data
     */
    public function payTestComplete(array $params): array
    {
        return $this->callPay('/merchant/pay/test/complete', $params);
    }

    // ====================== 代付（Payout，密钥 api_secret_payout）======================

    /**
     * 代付下单 POST /merchant/payout/create。
     *
     * @param array{
     *   out_payout_no:string, amount:int, currency:string, pay_method:string,
     *   notify_url:string, account_no:string, country?:string|null, account_name?:string|null,
     *   bank_code?:string|null, bank_name?:string|null, remark?:string|null,
     *   client_ip?:string|null, extra?:array|null
     * } $params
     * @return array<string,mixed> data
     */
    public function payoutCreate(array $params): array
    {
        return $this->callPayout('/merchant/payout/create', $params);
    }

    /**
     * 代付查单 POST /merchant/payout/query。payout_no 或 out_payout_no 二选一。
     *
     * @param array{payout_no?:string|null, out_payout_no?:string|null} $params
     * @return array<string,mixed> data
     */
    public function payoutQuery(array $params): array
    {
        return $this->callPayout('/merchant/payout/query', $params);
    }

    /**
     * 可用银行 POST /merchant/payout/banks/query。pay_method 必填 + country(法币必填) + currency(可选)。
     *
     * @param array{pay_method:string, country?:string|null, currency?:string|null} $params
     * @return array<string,mixed> data（含 banks[]）
     */
    public function payoutBanksQuery(array $params): array
    {
        return $this->callPayout('/merchant/payout/banks/query', $params);
    }

    /**
     * 代付凭证查询 POST /merchant/payout/proof/query。payout_no 或 out_payout_no 二选一，仅 success 可查。
     *
     * @param array{payout_no?:string|null, out_payout_no?:string|null} $params
     * @return array<string,mixed> data
     */
    public function payoutProofQuery(array $params): array
    {
        return $this->callPayout('/merchant/payout/proof/query', $params);
    }

    /**
     * 代付收据 POST /merchant/payout/receipt/query。
     * payout_no 或 out_payout_no 二选一 + lang(可选) + inline(可选 bool)。
     *
     * inline 以整数 1/0 发送（避免布尔签名歧义）：true→1（内联 base64 图片），false/省略→0（带 token 的 URL）。
     *
     * @param array{payout_no?:string|null, out_payout_no?:string|null, lang?:string|null, inline?:bool|int|null} $params
     * @return array<string,mixed> data
     */
    public function payoutReceiptQuery(array $params): array
    {
        if (array_key_exists('inline', $params) && $params['inline'] !== null) {
            // 归一为整数 1/0；接受 bool 或 int 入参
            $params['inline'] = ($params['inline'] === true || $params['inline'] === 1 || $params['inline'] === '1') ? 1 : 0;
        }

        return $this->callPayout('/merchant/payout/receipt/query', $params);
    }

    /**
     * 代付测试单完成 POST /merchant/payout/test/complete（仅测试密钥）。
     * payout_no 或 out_payout_no 二选一 + result(success|failed)。
     *
     * @param array{payout_no?:string|null, out_payout_no?:string|null, result:string} $params
     * @return array<string,mixed> data
     */
    public function payoutTestComplete(array $params): array
    {
        return $this->callPayout('/merchant/payout/test/complete', $params);
    }

    // ====================== 回调验签（便捷转发到 Signer）======================

    /**
     * 代收/退款回调验签（密钥 api_secret_pay，时序安全比较）。
     *
     * @param array<string,mixed> $payload 回调 JSON 解析后的键值表（含 sign）
     */
    public function verifyPayCallback(array $payload): bool
    {
        return Signer::verifyCallback($payload, $this->config->apiSecretPay);
    }

    /**
     * 代付回调验签（密钥 api_secret_payout，时序安全比较）。
     *
     * @param array<string,mixed> $payload
     */
    public function verifyPayoutCallback(array $payload): bool
    {
        return Signer::verifyCallback($payload, $this->config->apiSecretPayout);
    }

    /**
     * 最近一次成功解析的原始响应信封 {code,message,data}（保留拿原始响应的途径）。
     *
     * @return array<string,mixed>|null
     */
    public function lastRawResponse(): ?array
    {
        return $this->lastRawResponse;
    }

    // ====================== 内部：请求构建与发送 ======================

    /**
     * pay 类调用（用 api_secret_pay）。
     *
     * @param array<string,mixed> $params
     * @return array<string,mixed>
     */
    private function callPay(string $path, array $params): array
    {
        return $this->dispatch($path, $params, $this->config->apiSecretPay);
    }

    /**
     * payout 类调用（用 api_secret_payout）。
     *
     * @param array<string,mixed> $params
     * @return array<string,mixed>
     */
    private function callPayout(string $path, array $params): array
    {
        return $this->dispatch($path, $params, $this->config->apiSecretPayout);
    }

    /**
     * 构建请求体（注入通用字段 + 过滤 null + 签名）→ 发送 → 解析信封 → 返回 data。
     *
     * @param array<string,mixed> $params
     * @return array<string,mixed>
     */
    private function dispatch(string $path, array $params, string $secret): array
    {
        $payload = $this->buildPayload($params);
        $payload['sign'] = Signer::sign($payload, $secret);

        $url = $this->config->baseUrl . $path;
        $json = $this->encodeBody($payload);

        [$status, $body] = $this->sendRaw($url, $json);

        return $this->parseEnvelope($status, $body);
    }

    /**
     * 注入通用字段并丢弃值为 null 的业务字段（不放入请求体也不参与签名）。
     *
     * @param array<string,mixed> $params
     * @return array<string,mixed>
     */
    private function buildPayload(array $params): array
    {
        $payload = [];
        foreach ($params as $key => $value) {
            if ($value === null) {
                continue;
            }
            $payload[$key] = $value;
        }

        // 通用字段由 SDK 统一注入，且**始终覆盖**调用方同名字段（跨语言一致语义）。
        $payload['merchant_no'] = $this->config->merchantNo;
        $payload['api_key'] = $this->config->apiKey;
        $payload['timestamp'] = time();
        $payload['nonce'] = $this->generateNonce();

        return $payload;
    }

    /**
     * 生成每请求唯一 nonce（32 个十六进制字符的随机串）。
     */
    private function generateNonce(): string
    {
        return bin2hex(random_bytes(16));
    }

    /**
     * 请求体 JSON 编码（与签名一致的 flag，确保发送字节与签名口径相符）。
     *
     * @param array<string,mixed> $payload
     */
    private function encodeBody(array $payload): string
    {
        $json = json_encode($payload, JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE);
        if ($json === false) {
            throw new TransportException('请求体 JSON 编码失败: ' . json_last_error_msg());
        }

        return $json;
    }

    /**
     * 发送原始请求，返回 [httpStatus, body]。
     *
     * @return array{0:int,1:string}
     */
    private function sendRaw(string $url, string $json): array
    {
        $result = ($this->httpExecutor)($url, $json, $this->config->timeoutSeconds);

        return [(int) $result['status'], (string) $result['body']];
    }

    /**
     * 默认 HTTP 执行器：cURL（核心扩展，非第三方）。
     *
     * @return array{status:int,body:string}
     */
    private function curlExecute(string $url, string $json, int $timeoutSeconds): array
    {
        $ch = curl_init();
        if ($ch === false) {
            throw new TransportException('cURL 初始化失败（请确认已启用 ext-curl）');
        }

        curl_setopt_array($ch, [
            CURLOPT_URL => $url,
            CURLOPT_POST => true,
            CURLOPT_POSTFIELDS => $json,
            CURLOPT_RETURNTRANSFER => true,
            CURLOPT_HTTPHEADER => [
                'Content-Type: application/json',
                'Accept: application/json',
                // 自识别 User-Agent：避免被 WAF/CDN（如 Cloudflare）按默认 UA 拦成 403。
                // 版本号从 self::VERSION 单一派生。
                'User-Agent: merchant-openapi-sdk-php/' . self::VERSION,
            ],
            CURLOPT_TIMEOUT => $timeoutSeconds,
            CURLOPT_CONNECTTIMEOUT => $timeoutSeconds,
            // —— 传输安全钉死 ——
            // 强制校验对端证书与主机名（默认即 true/2，这里显式写死防被全局配置/ini 弱化）。
            CURLOPT_SSL_VERIFYPEER => true,
            CURLOPT_SSL_VERIFYHOST => 2,
            // 只允许 https 与 http（localhost 联调走 http）；屏蔽 file://、ftp:// 等危险协议，
            // 杜绝被恶意 baseUrl 诱导走非预期协议。
            CURLOPT_PROTOCOLS => CURLPROTO_HTTPS | CURLPROTO_HTTP,
            // 不自动跟随重定向：避免 302 把请求（含签名载荷）转发到第三方域名造成凭据外泄。
            CURLOPT_FOLLOWLOCATION => false,
        ]);

        $body = curl_exec($ch);
        if ($body === false) {
            $err = curl_error($ch);
            $errno = curl_errno($ch);
            throw new TransportException(sprintf('HTTP 请求失败 (cURL %d): %s', $errno, $err));
        }

        $status = (int) curl_getinfo($ch, CURLINFO_HTTP_CODE);
        // 不调用 curl_close()：自 PHP 8.0 起句柄由 GC 自动释放，curl_close() 在 8.5 已废弃。

        return ['status' => $status, 'body' => (string) $body];
    }

    /**
     * 解析统一信封：HTTP 非 2xx 或非合法 JSON → TransportException；code !== 0 → ApiException；否则返回 data。
     *
     * @return array<string,mixed>
     */
    private function parseEnvelope(int $status, string $body): array
    {
        if ($status < 200 || $status >= 300) {
            throw new TransportException(
                sprintf('HTTP 状态异常: %d', $status),
                $status,
                $body
            );
        }

        $decoded = json_decode($body, true);
        if (!is_array($decoded)) {
            throw new TransportException(
                '响应不是合法 JSON 信封: ' . json_last_error_msg(),
                $status,
                $body
            );
        }

        $this->lastRawResponse = $decoded;

        if (!array_key_exists('code', $decoded)) {
            throw new TransportException('响应信封缺少 code 字段', $status, $body);
        }

        $code = (int) $decoded['code'];
        if ($code !== 0) {
            $message = isset($decoded['message']) ? (string) $decoded['message'] : '';
            $data = (isset($decoded['data']) && is_array($decoded['data'])) ? $decoded['data'] : null;
            throw new ApiException($code, $message, $data, $decoded);
        }

        return (isset($decoded['data']) && is_array($decoded['data'])) ? $decoded['data'] : [];
    }
}
