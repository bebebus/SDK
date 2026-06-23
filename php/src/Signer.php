<?php

declare(strict_types=1);

namespace Merchant\Openapi;

/**
 * HMAC-SHA256 签名器（与服务端签名实现逐字节一致）。
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
     * 空密钥 fail-closed：secret 为空串/全空白一律拒绝签名（从根上禁止用空密钥产出签名，
     * 否则攻击者用空密钥即可伪造任意签名）。合法非空密钥的签名结果与服务端逐字节一致。
     *
     * @param array<string,mixed> $payload
     */
    public static function sign(array $payload, string $secret): string
    {
        if (!self::isUsableSecret($secret)) {
            throw new \InvalidArgumentException('secret 不能为空：禁止使用空密钥签名');
        }

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
        // 空密钥 fail-closed：secret 为空串/全空白/非可用 → 在算任何 HMAC 之前直接判失败，
        // 绝不继续走 sign() 比较（杜绝"空密钥也能验过"的伪造入口）。
        if (!self::isUsableSecret($secret)) {
            return false;
        }

        // 攻击者可控的非法 sign（缺失/非字符串/含非十六进制字符/长度异常）一律判失败。
        // 服务端签名固定为 64 个小写十六进制字符（HMAC-SHA256 hex）。
        if (!isset($payload['sign']) || !is_string($payload['sign'])) {
            return false;
        }
        $received = $payload['sign'];
        if (preg_match('/\A[0-9a-f]{64}\z/', $received) !== 1) {
            return false;
        }

        // 验签全程不抛异常冒泡：非法载荷（数值越界/非整数浮点等）只判失败，不打断回调处理。
        try {
            $expected = self::sign($payload, $secret);
        } catch (\Throwable $e) {
            return false;
        }

        // 时序安全比较，杜绝按字符逐位提前返回的时序侧信道
        return hash_equals($expected, $received);
    }

    /**
     * 大整数安全的回调验签便捷方法：先用 JSON_BIGINT_AS_STRING 解析原始回调体，
     * 把超出 PHP 整数范围的大整数保留为字符串再验签，避免精度丢失导致签名分叉。
     *
     * 回调验签**建议优先使用本方法**（而非先 json_decode 再 verifyCallback），尤其当回调可能
     * 携带大整数（如 64 位订单号/金额）时。原始体非合法 JSON 对象 → 直接返回 false（不抛）。
     *
     * @param string $rawBody 回调请求的原始 body（未经任何解析）
     * @param string $secret  代收/退款回调用 api_secret_pay；代付回调用 api_secret_payout
     */
    public static function verifyCallbackRaw(string $rawBody, string $secret): bool
    {
        // 保大整数为字符串：JSON_BIGINT_AS_STRING 使超范围整数解析为 string 而非丢精度的 float。
        $decoded = json_decode($rawBody, true, 512, JSON_BIGINT_AS_STRING);
        if (!is_array($decoded)) {
            // 非对象/非合法 JSON（含顶层标量、null、解析失败）→ 验签失败，绝不抛异常冒泡。
            return false;
        }

        return self::verifyCallback($decoded, $secret);
    }

    /**
     * 判断 secret 是否可用于签名：必须是去除首尾空白后非空的字符串。
     *
     * 类型由声明保证为 string；这里只拦空串与全空白串（fail-closed 根因）。
     */
    private static function isUsableSecret(string $secret): bool
    {
        return trim($secret) !== '';
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
     * 浮点一律拒绝（fail-fast）：金额按合约本就是整数最小单位，float 经 json_encode 大额会产生
     * 科学计数法（如 1.0E+20），与其它语言 Number→string 口径分叉，故直接抛错而非冒险序列化。
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
            self::rejectFloat($value);
        }
        if (is_int($value)) {
            // -0 在 PHP 整数中不存在；整数直接强转即正确（含 0）
            return (string) $value;
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
            // 嵌套浮点同样拒绝：大额浮点 json_encode 会产科学计数法，跨语言序列化分叉。
            self::rejectFloat($value);
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
     * 拒绝参与签名的浮点值（fail-fast）。
     *
     * - NaN / Infinity：非法数值，无确定字符串表示，必拒；
     * - 其余浮点（含整数值的 float 如 1.0、大额浮点）：合约要求金额用整数最小单位，
     *   float 经 json_encode 大额会产科学计数法（1.0E+20）与其它语言分叉，一律拒绝。
     *
     * 提示调用方改用整数（最小单位）。
     */
    private static function rejectFloat(float $value): never
    {
        if (is_nan($value)) {
            throw new \InvalidArgumentException('签名数值非法：不允许 NaN，请使用整数（最小单位）');
        }
        if (is_infinite($value)) {
            throw new \InvalidArgumentException('签名数值非法：不允许 Infinity，请使用整数（最小单位）');
        }

        throw new \InvalidArgumentException(sprintf(
            '签名数值非法：不允许浮点数 (%s)，金额等数值请使用整数最小单位（如 10000 表示 1 元）',
            var_export($value, true)
        ));
    }
}
