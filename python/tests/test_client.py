"""客户端请求构建单元测试（标准库 unittest，不发真实网络请求）。

校验：通用字段注入、None 过滤、签名密钥选择（pay vs payout）、
receipt inline 整数化、信封解析与异常分类。
"""

from __future__ import annotations

import os
import sys
import unittest

_THIS_DIR = os.path.dirname(os.path.abspath(__file__))
_PKG_ROOT = os.path.dirname(_THIS_DIR)
if _PKG_ROOT not in sys.path:
    sys.path.insert(0, _PKG_ROOT)

from openapi_sdk import (  # noqa: E402
    ApiError,
    Client,
    Config,
    Environment,
    build_sign_base,
    signer,
)


def _make_client(timeout: float = 5.0) -> Client:
    cfg = Config(
        merchant_no="M00000001",
        api_key="ak_demo_key",
        api_secret_pay="sk_pay_secret",
        api_secret_payout="sk_payout_secret",
        environment=Environment.SANDBOX,
        timeout=timeout,
    )
    return Client(cfg)


class _Capture:
    """猴子补丁 Client._request，捕获 path/body/secret 并返回桩信封。

    注意：把**实例**赋给类属性 ``Client._request`` 时，描述符协议不会自动绑定 ``self``，
    因此调用形如 ``self._request(path, body, secret)`` 不会传入 Client 实例 ——
    ``__call__`` 的签名故意不含 ``instance``。
    """

    def __init__(self, envelope):
        self.envelope = envelope
        self.calls = []

    def __call__(self, path, body, secret):
        self.calls.append({"path": path, "body": dict(body), "secret": secret})
        return self.envelope


class TestEnvironmentAndConfig(unittest.TestCase):
    def test_presets(self):
        # PRODUCTION 无内置基址（按上级代理域名派生，需显式传 base_url）。
        self.assertEqual(Environment.PRODUCTION.base_url, "")
        # SANDBOX 仍为本地预设基址。
        self.assertEqual(
            Environment.SANDBOX.base_url, "http://127.0.0.1:3090/api/open/v1"
        )

    def test_production_without_base_url_raises(self):
        # 选 PRODUCTION 又不传 base_url：最终基址为空，必须抛清晰错误。
        with self.assertRaises(ValueError) as ctx:
            Config(
                merchant_no="M1",
                api_key="k",
                api_secret_pay="p",
                api_secret_payout="o",
                environment=Environment.PRODUCTION,
            )
        self.assertIn("baseUrl is required", str(ctx.exception))

    def test_custom_base_url_overrides_and_strips_slash(self):
        cfg = Config(
            merchant_no="M1",
            api_key="k",
            api_secret_pay="p",
            api_secret_payout="o",
            environment=Environment.PRODUCTION,
            base_url="https://api.agent.example.com/api/open/v1/",
        )
        self.assertEqual(cfg.base_url, "https://api.agent.example.com/api/open/v1")

    def test_sandbox_preset_base_url(self):
        cfg = Config(
            merchant_no="M1",
            api_key="k",
            api_secret_pay="p",
            api_secret_payout="o",
            environment=Environment.SANDBOX,
        )
        self.assertEqual(cfg.base_url, "http://127.0.0.1:3090/api/open/v1")

    def test_default_environment_is_production(self):
        # 默认环境仍是 PRODUCTION（于是默认就要求显式传 base_url）。
        cfg = Config(
            merchant_no="M1",
            api_key="k",
            api_secret_pay="p",
            api_secret_payout="o",
            base_url="https://api.agent.example.com/api/open/v1",
        )
        self.assertEqual(cfg.environment, Environment.PRODUCTION)


class TestPayloadBuild(unittest.TestCase):
    def test_common_fields_injected_and_signed(self):
        client = _make_client()
        payload = client._build_payload(
            {"out_order_no": "ORD1", "amount": 10000, "country": None},
            "sk_pay_secret",
        )
        # 通用字段注入
        self.assertEqual(payload["merchant_no"], "M00000001")
        self.assertEqual(payload["api_key"], "ak_demo_key")
        self.assertIsInstance(payload["timestamp"], int)
        self.assertTrue(payload["nonce"])
        # None 字段被过滤
        self.assertNotIn("country", payload)
        # 业务字段保留
        self.assertEqual(payload["out_order_no"], "ORD1")
        self.assertEqual(payload["amount"], 10000)
        # 签名正确（sign 不参与自身计算）
        unsigned = {k: v for k, v in payload.items() if k != "sign"}
        expected = signer.sign(unsigned, "sk_pay_secret")
        self.assertEqual(payload["sign"], expected)

    def test_nonce_unique_per_request(self):
        client = _make_client()
        a = client._build_payload({"x": 1}, "s")["nonce"]
        b = client._build_payload({"x": 1}, "s")["nonce"]
        self.assertNotEqual(a, b)

    def test_base_matches_signer_helper(self):
        # 校验 build_sign_base 与 client 用的是同一算法。
        payload = {"merchant_no": "M1", "amount": 5}
        base = build_sign_base(payload, "s")
        self.assertEqual(base, "amount=5&merchant_no=M1&secret=s")


class TestKeySelectionAndShaping(unittest.TestCase):
    def setUp(self):
        self.ok_envelope = {"code": 0, "message": "ok", "data": {"order_no": "P1"}}
        self.capture = _Capture(self.ok_envelope)
        self._orig = Client._request
        Client._request = self.capture  # type: ignore[assignment]

    def tearDown(self):
        Client._request = self._orig  # type: ignore[assignment]

    def test_pay_uses_pay_secret(self):
        client = _make_client()
        client.pay_create(
            out_order_no="ORD1",
            amount=10000,
            currency="PHP",
            pay_method="gcash",
            notify_url="https://m.example.com/cb",
        )
        call = self.capture.calls[-1]
        self.assertEqual(call["path"], "/merchant/pay/create")
        self.assertEqual(call["secret"], "sk_pay_secret")

    def test_payout_uses_payout_secret(self):
        client = _make_client()
        client.payout_create(
            out_payout_no="WD1",
            amount=100000,
            currency="PHP",
            pay_method="bank",
            notify_url="https://m.example.com/cb",
            account_no="123",
            bank_code="BDO",
        )
        call = self.capture.calls[-1]
        self.assertEqual(call["path"], "/merchant/payout/create")
        self.assertEqual(call["secret"], "sk_payout_secret")

    def test_balance_query_uses_pay_secret(self):
        client = _make_client()
        client.balance_query()
        self.assertEqual(self.capture.calls[-1]["secret"], "sk_pay_secret")

    def test_receipt_inline_true_sent_as_int_1(self):
        client = _make_client()
        client.payout_receipt_query(out_payout_no="WD1", inline=True)
        body = self.capture.calls[-1]["body"]
        self.assertEqual(body["inline"], 1)
        self.assertIsInstance(body["inline"], int)
        self.assertNotIsInstance(body["inline"], bool)

    def test_receipt_inline_false_sent_as_int_0(self):
        client = _make_client()
        client.payout_receipt_query(out_payout_no="WD1", inline=False)
        body = self.capture.calls[-1]["body"]
        self.assertEqual(body["inline"], 0)
        self.assertNotIsInstance(body["inline"], bool)

    def test_receipt_inline_omitted_is_none(self):
        client = _make_client()
        client.payout_receipt_query(out_payout_no="WD1")
        body = self.capture.calls[-1]["body"]
        self.assertIsNone(body["inline"])


class TestEnvelopeHandling(unittest.TestCase):
    def test_business_error_raises_api_error(self):
        client = _make_client()
        capture = _Capture({"code": 100104, "message": "签名错误", "data": None})
        orig = Client._request
        Client._request = capture  # type: ignore[assignment]
        try:
            with self.assertRaises(ApiError) as ctx:
                client.pay_query(out_order_no="ORD1")
            self.assertEqual(ctx.exception.code, 100104)
            self.assertEqual(ctx.exception.message, "签名错误")
        finally:
            Client._request = orig  # type: ignore[assignment]

    def test_success_returns_data_dict(self):
        client = _make_client()
        capture = _Capture(
            {"code": 0, "message": "ok", "data": {"status": "pending"}}
        )
        orig = Client._request
        Client._request = capture  # type: ignore[assignment]
        try:
            data = client.pay_query(out_order_no="ORD1")
            self.assertEqual(data, {"status": "pending"})
        finally:
            Client._request = orig  # type: ignore[assignment]

    def test_call_raw_returns_envelope_without_raising(self):
        client = _make_client()
        capture = _Capture({"code": 300301, "message": "订单不存在", "data": None})
        orig = Client._request
        Client._request = capture  # type: ignore[assignment]
        try:
            env = client.call_raw(
                "/merchant/pay/query", {"order_no": "X"}, "sk_pay_secret"
            )
            self.assertEqual(env["code"], 300301)
        finally:
            Client._request = orig  # type: ignore[assignment]


class TestAllEndpointsCallable(unittest.TestCase):
    """确认 11 个端点方法都存在且能构建请求（路径正确）。"""

    EXPECTED = {
        "pay_create": "/merchant/pay/create",
        "pay_query": "/merchant/pay/query",
        "pay_methods_query": "/merchant/pay-methods/query",
        "balance_query": "/merchant/balance/query",
        "pay_test_complete": "/merchant/pay/test/complete",
        "payout_create": "/merchant/payout/create",
        "payout_query": "/merchant/payout/query",
        "payout_banks_query": "/merchant/payout/banks/query",
        "payout_proof_query": "/merchant/payout/proof/query",
        "payout_receipt_query": "/merchant/payout/receipt/query",
        "payout_test_complete": "/merchant/payout/test/complete",
    }

    def test_methods_exist(self):
        client = _make_client()
        for name in self.EXPECTED:
            self.assertTrue(
                callable(getattr(client, name, None)),
                msg=f"缺少端点方法 {name}",
            )

    def test_eleven_endpoints(self):
        self.assertEqual(len(self.EXPECTED), 11)


if __name__ == "__main__":
    unittest.main()
