<?php

declare(strict_types=1);

namespace Merchant\Openapi\Exception;

/**
 * 传输异常：网络/cURL 错误、非 2xx HTTP 状态、响应非合法 JSON 信封等。
 *
 * 与 ApiException（业务码非 0）区分：传输层失败时调用方通常需重试或排查网络。
 */
final class TransportException extends \RuntimeException
{
    /**
     * @param string      $message    错误描述
     * @param int|null    $httpStatus HTTP 状态码（如有）
     * @param string|null $rawBody    原始响应体（如有，便于排查）
     */
    public function __construct(
        string $message,
        public readonly ?int $httpStatus = null,
        public readonly ?string $rawBody = null,
        ?\Throwable $previous = null,
    ) {
        parent::__construct($message, 0, $previous);
    }
}
