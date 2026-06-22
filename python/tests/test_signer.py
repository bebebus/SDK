"""签名器单元测试（标准库 unittest）。

(a) 读取 ../../test-vectors.json，对每个向量断言 build_sign_base == base 且 sign == sign。
(b) 回调验签正例 + 篡改一字节的反例。
(c) 顶层布尔/整数强转、null 跳过、sign 排除等边界。

仅用标准库；命令：python3 -m unittest discover -s tests
"""

from __future__ import annotations

import json
import os
import sys
import unittest

# 让 tests/ 能 import 到 projectp_sdk（无需安装）。
_THIS_DIR = os.path.dirname(os.path.abspath(__file__))
_PKG_ROOT = os.path.dirname(_THIS_DIR)
if _PKG_ROOT not in sys.path:
    sys.path.insert(0, _PKG_ROOT)

from projectp_sdk import build_sign_base, sign, verify_callback  # noqa: E402

# 权威向量位于 SDK 仓库根（python 目录的上一级）。
_VECTORS_PATH = os.path.normpath(
    os.path.join(_PKG_ROOT, "..", "test-vectors.json")
)


def _load_vectors():
    with open(_VECTORS_PATH, "r", encoding="utf-8") as fh:
        doc = json.load(fh)
    return doc["vectors"]


class TestSignVectors(unittest.TestCase):
    """对每个标准答案向量复现 base 与 sign。"""

    @classmethod
    def setUpClass(cls):
        cls.vectors = _load_vectors()
        if not cls.vectors:
            raise AssertionError("test-vectors.json 为空，无法验证")

    def test_all_vectors_base_and_sign(self):
        for vec in self.vectors:
            name = vec["name"]
            payload = vec["payload"]
            secret = vec["secret"]
            with self.subTest(vector=name):
                base = build_sign_base(payload, secret)
                self.assertEqual(
                    base,
                    vec["base"],
                    msg=f"向量 {name} 的 base 不一致",
                )
                sig = sign(payload, secret)
                self.assertEqual(
                    sig,
                    vec["sign"],
                    msg=f"向量 {name} 的 sign 不一致",
                )

    def test_vector_count(self):
        # 防止向量文件被意外清空导致全部 subTest 静默通过。
        self.assertGreaterEqual(len(self.vectors), 10)


class TestScalarRules(unittest.TestCase):
    """顶层标量强转与过滤规则。"""

    SECRET = "sk_test_0123456789abcdef0123456789abcdef"

    def test_boolean_lowercase(self):
        base = build_sign_base({"inline": True, "disabled": False}, self.SECRET)
        self.assertEqual(
            base,
            f"disabled=false&inline=true&secret={self.SECRET}",
        )

    def test_zero_int_kept(self):
        base = build_sign_base({"count": 0, "amount": 5}, self.SECRET)
        self.assertEqual(base, f"amount=5&count=0&secret={self.SECRET}")

    def test_none_skipped(self):
        base = build_sign_base({"a": "1", "b": None, "c": "3"}, self.SECRET)
        self.assertEqual(base, f"a=1&c=3&secret={self.SECRET}")

    def test_sign_field_excluded(self):
        base = build_sign_base({"a": "1", "sign": "x"}, self.SECRET)
        self.assertEqual(base, f"a=1&secret={self.SECRET}")

    def test_keys_sorted_ascii(self):
        base = build_sign_base({"b": "2", "A": "1", "a": "3"}, self.SECRET)
        # ASCII：'A'(65) < 'a'(97) < 'b'(98)
        self.assertEqual(base, f"A=1&a=3&b=2&secret={self.SECRET}")


class TestCallbackVerify(unittest.TestCase):
    """回调验签：正例 + 篡改一字节的反例。"""

    SECRET_PAY = "sk_test_0123456789abcdef0123456789abcdef"
    SECRET_PAYOUT = "sk_payout_0123456789abcdef0123456789ab"

    def _make_deposit_callback(self):
        body = {
            "merchant_no": "M00000001",
            "order_no": "P202501010001",
            "out_order_no": "ORD202501010001",
            "amount": 10000,
            "actual_amount": 10000,
            "fee_amount": 180,
            "net_amount": 9820,
            "currency": "PHP",
            "status": "success",
            "channel_order_no": None,
            "paid_at": "2025-01-01T12:00:00Z",
        }
        body["sign"] = sign(body, self.SECRET_PAY)
        return body

    def _make_payout_callback(self):
        body = {
            "merchant_no": "M00000001",
            "payout_no": "W202501010001",
            "out_payout_no": "WD202501010001",
            "amount": 100000,
            "currency": "PHP",
            "status": "success",
            "fee_amount": 200,
            "channel_order_no": None,
            "finished_at": "2025-01-01T12:05:00Z",
            "failed_reason": None,
        }
        body["sign"] = sign(body, self.SECRET_PAYOUT)
        return body

    def test_deposit_callback_valid(self):
        body = self._make_deposit_callback()
        self.assertTrue(verify_callback(body, self.SECRET_PAY))

    def test_payout_callback_valid(self):
        body = self._make_payout_callback()
        self.assertTrue(verify_callback(body, self.SECRET_PAYOUT))

    def test_deposit_callback_tampered_one_byte_field(self):
        # 篡改业务字段一字节（amount 末位 0->1）：sign 不再匹配。
        body = self._make_deposit_callback()
        body["amount"] = 10001
        self.assertFalse(verify_callback(body, self.SECRET_PAY))

    def test_deposit_callback_tampered_sign_one_byte(self):
        # 篡改 sign 本身一字节：首字符翻转。
        body = self._make_deposit_callback()
        original = body["sign"]
        flipped = ("0" if original[0] != "0" else "1") + original[1:]
        body["sign"] = flipped
        self.assertFalse(verify_callback(body, self.SECRET_PAY))

    def test_wrong_secret_rejected(self):
        # 代收回调用代付密钥验签：必失败（密钥选择错误的保护）。
        body = self._make_deposit_callback()
        self.assertFalse(verify_callback(body, self.SECRET_PAYOUT))

    def test_missing_sign_rejected(self):
        body = {"merchant_no": "M1", "status": "success"}
        self.assertFalse(verify_callback(body, self.SECRET_PAY))

    def test_empty_sign_rejected(self):
        body = {"merchant_no": "M1", "status": "success", "sign": ""}
        self.assertFalse(verify_callback(body, self.SECRET_PAY))


if __name__ == "__main__":
    unittest.main()
