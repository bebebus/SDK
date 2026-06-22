<?php

declare(strict_types=1);

namespace Merchant\Openapi;

/**
 * 环境基址预设。
 *
 * PRODUCTION 无内置 URL：正式真实地址按上级代理专有域名派生（形如
 * https://api.<agent_domain>/api/open/v1），必须由调用方通过自定义 baseUrl 显式提供。
 * SANDBOX 为本地/联调地址。
 */
enum Environment: string
{
    case PRODUCTION = '';
    case SANDBOX = 'http://127.0.0.1:3090/api/open/v1';

    /**
     * 该环境的内置基址 URL（PRODUCTION 为空串，表示需显式传 baseUrl）。
     */
    public function baseUrl(): string
    {
        return $this->value;
    }
}
