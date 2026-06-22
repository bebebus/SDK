<?php

declare(strict_types=1);

/**
 * 零依赖手写 PSR-4 自动加载器。
 *
 * 不需要 composer 安装即可使用 SDK：
 *   require __DIR__ . '/autoload.php';
 *   use ProjectP\Sdk\Client;
 *
 * 命名空间 ProjectP\Sdk\ 映射到 src/，与 composer.json 的 PSR-4 配置一致。
 */
spl_autoload_register(static function (string $class): void {
    $prefix = 'ProjectP\\Sdk\\';
    $baseDir = __DIR__ . '/src/';

    $len = strlen($prefix);
    if (strncmp($prefix, $class, $len) !== 0) {
        return;
    }

    $relativeClass = substr($class, $len);
    $file = $baseDir . str_replace('\\', '/', $relativeClass) . '.php';

    if (is_file($file)) {
        require $file;
    }
});
