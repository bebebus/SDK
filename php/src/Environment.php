<?php

declare(strict_types=1);

namespace ProjectP\Sdk;

/**
 * 环境基址预设。
 *
 * PRODUCTION 为文档默认正式地址（真实正式地址按上级代理专有域名派生，可用自定义 baseUrl 覆盖）。
 * SANDBOX 为本地/联调地址。
 */
enum Environment: string
{
    case PRODUCTION = 'https://api.project-p-merchant.cniia.cloud/api/open/v1';
    case SANDBOX = 'http://127.0.0.1:3090/api/open/v1';

    /**
     * 该环境的基址 URL。
     */
    public function baseUrl(): string
    {
        return $this->value;
    }
}
