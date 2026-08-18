<?php

declare(strict_types=1);

namespace Merchant\Openapi;

/**
 * 客户端配置：商户凭证 + 双密钥 + 基址 + 超时。
 *
 * 基址解析优先级：自定义 baseUrl > Environment 预设。两者都给时以 baseUrl 为准。
 * PRODUCTION 无内置 URL，因此选 PRODUCTION 时必须显式传 baseUrl，否则抛错。
 */
final class Config
{
    public readonly string $baseUrl;

    /**
     * @param string      $merchantNo      商户号
     * @param string      $apiKey          API Key
     * @param string      $apiSecretPay    代收类密钥（pay/*、pay-methods、balance、代收/退款回调）
     * @param string      $apiSecretPayout 代付类密钥（payout/*、代付回调）
     * @param Environment $environment     环境预设基址（baseUrl 为 null 时生效）
     * @param string|null $baseUrl         自定义基址，覆盖 environment（用于服务商地址/本地端口）
     * @param int         $timeoutSeconds  HTTP 超时（秒）
     */
    public function __construct(
        public readonly string $merchantNo,
        public readonly string $apiKey,
        public readonly string $apiSecretPay,
        public readonly string $apiSecretPayout,
        Environment $environment = Environment::PRODUCTION,
        ?string $baseUrl = null,
        public readonly int $timeoutSeconds = 30,
    ) {
        $resolved = $baseUrl !== null ? $baseUrl : $environment->baseUrl();
        $resolved = rtrim($resolved, '/');
        if ($resolved === '') {
            // 选了 PRODUCTION（或显式空串）又没传 baseUrl：正式无内置地址，必须显式提供。
            throw new \InvalidArgumentException(
                'baseUrl is required: obtain the production URL from your service provider '
                . '(e.g. https://api.<domain>/api/open/v1)'
            );
        }

        // 传输安全：非本地联调地址一律强制 https，杜绝凭据/签名明文走 http 被中间人窃取。
        // 仅 localhost / 127.0.0.1 / [::1] 放行 http（兼容本地端口联调，含 SANDBOX 预设）。
        self::assertTransportSecurity($resolved);

        // 已去掉末尾斜杠，拼接端点时统一不重复
        $this->baseUrl = $resolved;
    }

    /**
     * 校验 baseUrl 传输安全：非 localhost 必须 https，否则拒绝构造。
     *
     * 放行 http 的主机白名单：localhost、127.0.0.1、[::1]（IPv6 回环）。
     * 其余任何 http://（含 0.0.0.0、内网 IP、域名）一律拒绝——本地联调请用上述回环地址。
     */
    private static function assertTransportSecurity(string $url): void
    {
        $scheme = strtolower((string) (parse_url($url, PHP_URL_SCHEME) ?? ''));
        if ($scheme === 'https') {
            return;
        }
        if ($scheme !== 'http') {
            throw new \InvalidArgumentException(sprintf(
                'baseUrl 协议非法：只允许 https（本地联调可用 http://localhost），收到: %s',
                $url
            ));
        }

        // scheme === 'http'：仅本地回环主机放行
        $host = strtolower((string) (parse_url($url, PHP_URL_HOST) ?? ''));
        $localHosts = ['localhost', '127.0.0.1', '::1', '[::1]'];
        if (!in_array($host, $localHosts, true)) {
            throw new \InvalidArgumentException(sprintf(
                'baseUrl 必须使用 https（仅 localhost/127.0.0.1 允许 http 本地联调），收到: %s',
                $url
            ));
        }
    }
}
