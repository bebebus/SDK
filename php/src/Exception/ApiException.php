<?php

declare(strict_types=1);

namespace ProjectP\Sdk\Exception;

/**
 * 业务异常：统一信封 {code,message,data} 中 code !== 0 时抛出。
 *
 * 携带原始 code / message / data，供调用方按错误码判断（不要穷举写死分支）。
 */
final class ApiException extends \RuntimeException
{
    /**
     * @param int                       $apiCode 业务错误码（非 0）
     * @param string                    $apiMessage 业务消息
     * @param array<string,mixed>|null  $data    业务 data（可能含 missing_fields 等）
     * @param array<string,mixed>|null  $raw     完整原始响应信封
     */
    public function __construct(
        public readonly int $apiCode,
        public readonly string $apiMessage,
        public readonly ?array $data = null,
        public readonly ?array $raw = null,
    ) {
        parent::__construct(
            sprintf('OpenAPI 业务错误 [%d]: %s', $apiCode, $apiMessage),
            $apiCode
        );
    }
}
