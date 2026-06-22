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
     * @param string|null $baseUrl         自定义基址，覆盖 environment（用于代理专有域名/本地端口）
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
                'baseUrl is required: production base URL is provided per your agent domain '
                . '(e.g. https://api.<agent_domain>/api/open/v1)'
            );
        }
        // 已去掉末尾斜杠，拼接端点时统一不重复
        $this->baseUrl = $resolved;
    }
}
