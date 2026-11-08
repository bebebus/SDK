<?php
// SPDX-License-Identifier: MIT

declare(strict_types=1);

namespace Merchant\Openapi;

/**
 * 商户代付反查判定。平台 POST 到商户自己的 HTTPS 接口，本类不发请求。
 *
 * 请求体必须恰好是四个非空字符串：account_no、out_payout_no、original_sign、payout_no。
 * original_sign 与下单时保存的 sign 按 UTF-8 字节等长比较。payout_no 必须出现，但不与下单请求比较。
 * 返回值只有精确的 approve 或 reject。非法输入返回 reject，不抛异常。
 */
final class PayoutPrecheck
{
    public const APPROVE = 'approve';
    public const REJECT = 'reject';

    /**
     * @param mixed $body  平台 POST 的 JSON 对象（json_decode 关联数组）
     * @param mixed $saved 下单时保存的 out_payout_no、account_no、sign
     */
    public static function decide($body, $saved): string
    {
        try {
            if (!is_array($body) || !is_array($saved) || array_is_list($body)) {
                return self::REJECT;
            }
            $keys = ['account_no', 'out_payout_no', 'original_sign', 'payout_no'];
            $ok = count($body) === 4;
            foreach ($keys as $key) {
                if (!$ok) {
                    break;
                }
                $ok = isset($body[$key]) && is_string($body[$key]) && $body[$key] !== '';
            }
            foreach (['account_no', 'out_payout_no', 'sign'] as $key) {
                if (!$ok) {
                    break;
                }
                $ok = isset($saved[$key]) && is_string($saved[$key]) && $saved[$key] !== '';
            }
            if ($ok) {
                $ok = $body['account_no'] === $saved['account_no']
                    && $body['out_payout_no'] === $saved['out_payout_no']
                    && strlen($saved['sign']) === strlen($body['original_sign'])
                    && hash_equals($saved['sign'], $body['original_sign']);
            }

            return $ok ? self::APPROVE : self::REJECT;
        } catch (\Throwable) {
            return self::REJECT;
        }
    }
}
