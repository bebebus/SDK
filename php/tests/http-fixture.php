<?php
// SPDX-License-Identifier: MIT

declare(strict_types=1);

// 仅供测试进程在本地回环地址运行，不连接任何外部服务。
if (str_starts_with($_SERVER['REQUEST_URI'], '/redirect/')) {
    header('Location: /target', true, 302);
    exit;
}
header('Content-Type: application/json');
echo json_encode(['code' => 0, 'data' => [
    'path' => $_SERVER['REQUEST_URI'],
    'request' => json_decode(file_get_contents('php://input'), true),
]]);
