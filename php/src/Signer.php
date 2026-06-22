<?php

declare(strict_types=1);

namespace ProjectP\Sdk;

/**
 * HMAC-SHA256 签名器（与服务端 web-shared/src/openapi/sign.ts、project-p-test/sign.js 逐字节一致）。
 *
 * 算法步骤见 SIGNING.md：
 *  1. 过滤掉 key === "sign" 以及值为 null 的字段；
 *  2. 剩余字段按 key 的 ASCII 码点升序排序；
 *  3. 标量直接转字符串（布尔特判 true/false，不加引号）；object/array 用稳定 JSON 序列化；
 *  4. 拼成 k1=v1&k2=v2&...&secret=<secret>；
 *  5. HMAC-SHA256(base, key=secret) 输出十六进制小写。
 *
 * 全部为静态方法，无状态、无第三方依赖（仅用 PHP 内建 hash_hmac / hash_equals / json_encode）。
 */
final class Signer
{
    /**
     * 构造签名 base 串（便于单测逐字节断言）。
     *
     * @param array<string,mixed> $payload 业务字段键值表（不含 sign，含 null 的会被自动跳过）
     * @param string              $secret  与 base 末尾及 HMAC key 同一个 secret
     */
    public static function buildSignBase(array $payload, string $secret): string
    {
        // 过滤：排除 sign 字段与 null 值
        $filtered = [];
        foreach ($payload as $key => $value) {
            if ($key === 'sign') {
                continue;
            }
            if ($value === null) {
                continue;
            }
            $filtered[$key] = $value;
        }

        // 排序：key 按 ASCII 码点升序（SORT_STRING = 字节序，等价 JS 默认 sort / strcmp）
        ksort($filtered, SORT_STRING);

        $parts = [];
        foreach ($filtered as $key => $value) {
            $parts[] = $key . '=' . self::signValue($value);
        }

        return implode('&', $parts) . '&secret=' . $secret;
    }

    /**
     * 计算请求体签名（HMAC-SHA256 十六进制小写）。
     *
     * @param array<string,mixed> $payload
     */
    public static function sign(array $payload, string $secret): string
    {
        $base = self::buildSignBase($payload, $secret);

        return hash_hmac('sha256', $base, $secret);
    }

    /**
     * 回调验签：按"除 sign 外所有字段参与"通用计算，时序安全比较。
     *
     * 不硬编码字段表——平台可能增删字段，只要"除 sign 外全参与"即与服务端自洽。
     *
     * @param array<string,mixed> $payload 回调 JSON 解析后的键值表（含 sign）
     * @param string              $secret  代收/退款回调用 api_secret_pay；代付回调用 api_secret_payout
     */
    public static function verifyCallback(array $payload, string $secret): bool
    {
        if (!isset($payload['sign']) || !is_string($payload['sign'])) {
            return false;
        }

        $received = $payload['sign'];
        $expected = self::sign($payload, $secret);

        // 时序安全比较，杜绝按字符逐位提前返回的时序侧信道
        return hash_equals($expected, $received);
    }

    /**
     * 取参与签名的 value 字符串：
     *  - object/array → 稳定 JSON 序列化（带引号、key 递归升序、紧凑无空格）；
     *  - 布尔 → "true"/"false"（特判，避免 PHP (string)true == "1"）；
     *  - 整数/字符串等标量 → 原始字符串形态（不加引号）。
     *
     * @param mixed $value
     */
    private static function signValue(mixed $value): string
    {
        if (is_array($value) || is_object($value)) {
            return self::stableStringify($value);
        }

        return self::scalarToString($value);
    }

    /**
     * 顶层标量强转字符串（不加引号）。
     *
     * 关键坑：PHP (string)true == "1"、(string)false == ""，必须特判成 "true"/"false"。
     * 整数 (string)10000 == "10000" 正确；浮点不应承载金额（金额是最小单位整数）。
     *
     * @param mixed $value
     */
    private static function scalarToString(mixed $value): string
    {
        if (is_bool($value)) {
            return $value ? 'true' : 'false';
        }
        if ($value === null) {
            // 调用方已过滤 null；保留分支以防嵌套调用路径（理论不可达）
            return 'null';
        }
        if (is_float($value)) {
            // 与 JSON.stringify 数字字面量对齐：整数值的 float 不带小数点
            return self::jsonNumber($value);
        }

        return (string) $value;
    }

    /**
     * 稳定 JSON 序列化（递归，对齐 JS JSON.stringify + key 升序）。
     *
     * - null → "null"
     * - 标量 string → 带双引号并按 JS 规则转义（不转义 /、非 ASCII、<>&）
     * - 标量 number → 字面量（无引号）
     * - 布尔 → true/false
     * - 对象（stdClass 等）→ {"k1":v1,...}（key 升序，**空对象输出 {}**）
     * - 列表数组 → [e1,e2,...]（保序，**空列表输出 []**）
     * - 关联数组 → 当 JS object 处理 {"k1":v1,...}（key 升序、紧凑无空格）
     *
     * ⚠️ 空对象 {} 的可靠表示是 stdClass / (object)[]：PHP 空数组 [] 无法与 JS object 区分，
     *   故空数组一律按 JS array 序列化为 []，需要空对象时请传 stdClass。
     *
     * @param mixed $value
     */
    private static function stableStringify(mixed $value): string
    {
        if ($value === null) {
            return 'null';
        }

        if (is_bool($value)) {
            return $value ? 'true' : 'false';
        }

        if (is_int($value)) {
            return (string) $value;
        }

        if (is_float($value)) {
            return self::jsonNumber($value);
        }

        if (is_string($value)) {
            return self::jsonString($value);
        }

        if (is_object($value)) {
            // stdClass / 其它对象 → 始终按 JS object 序列化（即使空也输出 {}）。
            return self::stringifyObject(get_object_vars($value));
        }

        if (is_array($value)) {
            // 区分列表数组（JS array）与关联数组（JS object）；空数组按列表 → []
            if (array_is_list($value)) {
                $items = [];
                foreach ($value as $item) {
                    $items[] = self::stableStringify($item);
                }

                return '[' . implode(',', $items) . ']';
            }

            // 关联数组（含非顺序键）→ JS object
            return self::stringifyObject($value);
        }

        // 其它类型（资源等）不应出现在签名载荷里；退化为 JSON 兜底
        return self::jsonString((string) $value);
    }

    /**
     * 把关联键值表按 JS object 规则序列化：key 升序、紧凑无空格、空则输出 {}。
     *
     * @param array<array-key,mixed> $assoc
     */
    private static function stringifyObject(array $assoc): string
    {
        $keys = array_keys($assoc);
        usort($keys, static fn ($a, $b): int => strcmp((string) $a, (string) $b));

        $pairs = [];
        foreach ($keys as $key) {
            $pairs[] = self::jsonString((string) $key) . ':' . self::stableStringify($assoc[$key]);
        }

        return '{' . implode(',', $pairs) . '}';
    }

    /**
     * 嵌套字符串值的 JSON 编码（带引号），对齐 JS JSON.stringify。
     *
     * 必须加 JSON_UNESCAPED_SLASHES（默认会把 / 转 \/）和 JSON_UNESCAPED_UNICODE
     * （默认会把非 ASCII 转 \uXXXX）。不加 JSON_HEX_* —— <>& 默认即不转义，符合规范。
     */
    private static function jsonString(string $value): string
    {
        $encoded = json_encode($value, JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE);
        if ($encoded === false) {
            throw new \RuntimeException('签名序列化失败：无法 JSON 编码字符串 (' . json_last_error_msg() . ')');
        }

        return $encoded;
    }

    /**
     * 数字字面量（无引号），对齐 JS JSON.stringify 的 number。
     */
    private static function jsonNumber(float $value): string
    {
        $encoded = json_encode($value, JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE);
        if ($encoded === false) {
            throw new \RuntimeException('签名序列化失败：无法 JSON 编码数字 (' . json_last_error_msg() . ')');
        }

        return $encoded;
    }
}
