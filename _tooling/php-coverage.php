<?php
// SPDX-License-Identifier: MIT

declare(strict_types=1);

// 作为 auto_prepend_file 加载；只统计测试进程中已加载的 SDK 源文件。
xdebug_start_code_coverage(XDEBUG_CC_UNUSED | XDEBUG_CC_DEAD_CODE);
register_shutdown_function(static function (): void {
    $source = realpath(__DIR__ . '/../php/src') . DIRECTORY_SEPARATOR;
    $covered = 0;
    $missed = 0;
    foreach (xdebug_get_code_coverage() as $file => $lines) {
        if (!str_starts_with($file, $source)) {
            continue;
        }
        foreach ($lines as $hits) {
            if ($hits > 0) {
                $covered++;
            } elseif ($hits === -1) {
                $missed++;
            }
        }
    }
    $total = $covered + $missed;
    if ($total > 0) {
        printf("PHP 已加载源码可执行行覆盖率: %d/%d = %.2f%%\n", $covered, $total, 100 * $covered / $total);
    } else {
        fwrite(STDERR, "PHP 未采集到源码覆盖率\n");
    }
});
