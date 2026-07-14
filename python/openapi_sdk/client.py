"""商户支付 OpenAPI 客户端：覆盖全部 11 个端点。

仅用标准库：urllib.request（HTTP）、json、uuid、time、secrets。

约定：
- 所有请求 POST application/json，请求体 JSON，设超时。
- 每请求自动注入通用字段 merchant_no/api_key/timestamp/nonce。
- 值为 None 的字段不放入请求体、也不参与签名。
- pay 类接口用 api_secret_pay；payout 类接口用 api_secret_payout（各方法自动选对）。
- 解析统一信封 {code, message, data}：code != 0 抛 ApiError；HTTP/网络错误抛 TransportError。
- ``call_raw`` 暴露原始信封（code/message/data 全量），供调用方自行判断。
"""

from __future__ import annotations

import json
import secrets
import time
import urllib.error
import urllib.request
import uuid
from contextlib import suppress
from typing import Any, Dict, Mapping, Optional

from . import signer
from .config import Config, Environment
from .exceptions import ApiError, TransportError

__all__ = ["Client"]

# [L20] User-Agent 版本号单一事实源：从包元数据派生（与 __version__ 同源），
# 不再硬编码；源码直跑（未安装）取不到则兜底 '1.1.0'。
try:
    from importlib.metadata import PackageNotFoundError
    from importlib.metadata import version as _pkg_version

    try:
        _SDK_VERSION = _pkg_version("bebebus-merchant-openapi-sdk")
    except PackageNotFoundError:
        _SDK_VERSION = "1.1.0"
except ImportError:  # pragma: no cover —— Python <3.8 无 importlib.metadata
    _SDK_VERSION = "1.1.0"

_JSON_HEADERS = {
    "Content-Type": "application/json",
    "Accept": "application/json",
    # 显式 User-Agent：urllib 默认 UA（Python-urllib/x.y）常被 WAF/CDN（如 Cloudflare）拦成 403。
    # 版本号从包元数据单一派生（见 _SDK_VERSION）。
    "User-Agent": f"openapi-sdk-python/{_SDK_VERSION}",
}


class Client:
    """商户支付 OpenAPI 客户端。

    用法::

        from openapi_sdk import Client, Config, Environment
        cfg = Config(
            merchant_no="M00000001", api_key="ak_xxx",
            api_secret_pay="sk_pay_xxx", api_secret_payout="sk_payout_xxx",
            environment=Environment.SANDBOX,
        )
        client = Client(cfg)
        resp = client.pay_create(out_order_no="ORD1", amount=10000,
                                 currency="PHP", pay_method="gcash",
                                 notify_url="https://m.example.com/cb")
    """

    def __init__(self, config: Config) -> None:
        self._config = config

    # ---- 便捷构造 -----------------------------------------------------------

    @classmethod
    def for_environment(
        cls,
        merchant_no: str,
        api_key: str,
        api_secret_pay: str,
        api_secret_payout: str,
        environment: Environment = Environment.PRODUCTION,
        base_url: Optional[str] = None,
        timeout: float = 30.0,
    ) -> "Client":
        return cls(
            Config(
                merchant_no=merchant_no,
                api_key=api_key,
                api_secret_pay=api_secret_pay,
                api_secret_payout=api_secret_payout,
                environment=environment,
                base_url=base_url,
                timeout=timeout,
            )
        )

    # ---- 公共属性 -----------------------------------------------------------

    @property
    def config(self) -> Config:
        return self._config

    # =====================================================================
    # 代收（Pay，密钥：api_secret_pay）
    # =====================================================================

    def pay_create(
        self,
        out_order_no: str,
        amount: int,
        currency: str,
        pay_method: str,
        notify_url: str,
        country: Optional[str] = None,
        return_url: Optional[str] = None,
        subject: Optional[str] = None,
        remark: Optional[str] = None,
        client_ip: Optional[str] = None,
        extra: Optional[Mapping[str, Any]] = None,
    ) -> Dict[str, Any]:
        """POST /merchant/pay/create — 代收下单。"""
        body = {
            "out_order_no": out_order_no,
            "amount": amount,
            "currency": currency,
            "pay_method": pay_method,
            "notify_url": notify_url,
            "country": country,
            "return_url": return_url,
            "subject": subject,
            "remark": remark,
            "client_ip": client_ip,
            "extra": extra,
        }
        return self._call_pay("/merchant/pay/create", body)

    def pay_query(
        self,
        order_no: Optional[str] = None,
        out_order_no: Optional[str] = None,
    ) -> Dict[str, Any]:
        """POST /merchant/pay/query — 代收查单（order_no / out_order_no 二选一）。"""
        return self._call_pay(
            "/merchant/pay/query",
            {"order_no": order_no, "out_order_no": out_order_no},
        )

    def pay_methods_query(self, country: Optional[str] = None) -> Dict[str, Any]:
        """POST /merchant/pay-methods/query — 可用支付方式。"""
        return self._call_pay("/merchant/pay-methods/query", {"country": country})

    def balance_query(self, currency: Optional[str] = None) -> Dict[str, Any]:
        """POST /merchant/balance/query — 余额查询。"""
        return self._call_pay("/merchant/balance/query", {"currency": currency})

    def pay_test_complete(
        self,
        result: str,
        order_no: Optional[str] = None,
        out_order_no: Optional[str] = None,
        actual_amount: Optional[int] = None,
    ) -> Dict[str, Any]:
        """POST /merchant/pay/test/complete — 代收测试单完成（仅测试密钥）。"""
        return self._call_pay(
            "/merchant/pay/test/complete",
            {
                "order_no": order_no,
                "out_order_no": out_order_no,
                "result": result,
                "actual_amount": actual_amount,
            },
        )

    # =====================================================================
    # 代付（Payout，密钥：api_secret_payout）
    # =====================================================================

    def payout_create(
        self,
        out_payout_no: str,
        amount: int,
        currency: str,
        pay_method: str,
        notify_url: str,
        account_no: str,
        country: Optional[str] = None,
        account_name: Optional[str] = None,
        bank_code: Optional[str] = None,
        bank_name: Optional[str] = None,
        remark: Optional[str] = None,
        client_ip: Optional[str] = None,
        extra: Optional[Mapping[str, Any]] = None,
    ) -> Dict[str, Any]:
        """POST /merchant/payout/create — 代付下单。"""
        body = {
            "out_payout_no": out_payout_no,
            "amount": amount,
            "currency": currency,
            "pay_method": pay_method,
            "notify_url": notify_url,
            "account_no": account_no,
            "country": country,
            "account_name": account_name,
            "bank_code": bank_code,
            "bank_name": bank_name,
            "remark": remark,
            "client_ip": client_ip,
            "extra": extra,
        }
        return self._call_payout("/merchant/payout/create", body)

    def payout_query(
        self,
        payout_no: Optional[str] = None,
        out_payout_no: Optional[str] = None,
    ) -> Dict[str, Any]:
        """POST /merchant/payout/query — 代付查单（payout_no / out_payout_no 二选一）。"""
        return self._call_payout(
            "/merchant/payout/query",
            {"payout_no": payout_no, "out_payout_no": out_payout_no},
        )

    def payout_banks_query(
        self,
        pay_method: str,
        country: str,
        currency: str,
    ) -> Dict[str, Any]:
        """POST /merchant/payout/banks/query — 可用银行。

        pay_method 表示支付能力（查询银行时通常固定为 bank），不是
        payout/create 中表示支付分组的同名字段。
        """
        return self._call_payout(
            "/merchant/payout/banks/query",
            {"pay_method": pay_method, "country": country, "currency": currency},
        )

    def payout_proof_query(
        self,
        payout_no: Optional[str] = None,
        out_payout_no: Optional[str] = None,
    ) -> Dict[str, Any]:
        """POST /merchant/payout/proof/query — 代付凭证查询（仅 success 可查）。"""
        return self._call_payout(
            "/merchant/payout/proof/query",
            {"payout_no": payout_no, "out_payout_no": out_payout_no},
        )

    def payout_receipt_query(
        self,
        payout_no: Optional[str] = None,
        out_payout_no: Optional[str] = None,
        lang: Optional[str] = None,
        inline: Optional[bool] = None,
    ) -> Dict[str, Any]:
        """POST /merchant/payout/receipt/query — 代付收据。

        ``inline`` 以整数 1/0 发送（避免布尔跨语言签名歧义）：
        ``True``->1 内联返回 base64 图片；``False``->0 / 省略 返回带 token 的 URL。
        """
        inline_int = None if inline is None else (1 if inline else 0)
        return self._call_payout(
            "/merchant/payout/receipt/query",
            {
                "payout_no": payout_no,
                "out_payout_no": out_payout_no,
                "lang": lang,
                "inline": inline_int,
            },
        )

    def payout_test_complete(
        self,
        result: str,
        payout_no: Optional[str] = None,
        out_payout_no: Optional[str] = None,
    ) -> Dict[str, Any]:
        """POST /merchant/payout/test/complete — 代付测试单完成（仅测试密钥）。"""
        return self._call_payout(
            "/merchant/payout/test/complete",
            {
                "payout_no": payout_no,
                "out_payout_no": out_payout_no,
                "result": result,
            },
        )

    # =====================================================================
    # 内部：请求构建 / 发送 / 解析
    # =====================================================================

    def _call_pay(self, path: str, body: Mapping[str, Any]) -> Dict[str, Any]:
        # [L14] 调用代收类端点时才校验 pay 密钥，缺失即 fail-closed。
        return self._call(path, body, self._config.require_secret_pay())

    def _call_payout(self, path: str, body: Mapping[str, Any]) -> Dict[str, Any]:
        # [L14] 调用代付类端点时才校验 payout 密钥，缺失即 fail-closed。
        return self._call(path, body, self._config.require_secret_payout())

    def call_raw(
        self, path: str, body: Mapping[str, Any], secret: str
    ) -> Dict[str, Any]:
        """发起请求并返回**原始统一信封**（不因 code != 0 抛 ApiError）。

        仍会对 HTTP / 网络 / JSON 解析失败抛 TransportError。
        供调用方需要自行处理 code 时使用。
        """
        envelope = self._request(path, body, secret)
        return envelope

    def _call(self, path: str, body: Mapping[str, Any], secret: str) -> Dict[str, Any]:
        envelope = self._request(path, body, secret)
        code = envelope.get("code")
        if code != 0:
            raise ApiError(
                code=code if isinstance(code, int) else -1,
                message=str(envelope.get("message", "")),
                data=envelope.get("data"),
            )
        data = envelope.get("data")
        return data if isinstance(data, dict) else {}

    def _build_payload(
        self, body: Mapping[str, Any], secret: str
    ) -> Dict[str, Any]:
        """注入通用字段、剔除 None、计算签名，返回最终请求体。"""
        payload: Dict[str, Any] = {}
        for k, v in body.items():
            if v is not None:
                payload[k] = v
        # 通用字段由 SDK 统一注入，且**始终覆盖**调用方同名字段（跨语言一致语义）。
        payload["merchant_no"] = self._config.merchant_no
        payload["api_key"] = self._config.api_key
        payload["timestamp"] = int(time.time())
        payload["nonce"] = self._gen_nonce()
        payload["sign"] = signer.sign(payload, secret)
        return payload

    @staticmethod
    def _gen_nonce() -> str:
        """每请求唯一 nonce：UUID4 + 随机 hex，碰撞概率可忽略。"""
        return uuid.uuid4().hex + secrets.token_hex(8)

    def _request(
        self, path: str, body: Mapping[str, Any], secret: str
    ) -> Dict[str, Any]:
        payload = self._build_payload(body, secret)
        url = self._config.base_url + path
        # allow_nan=False：NaN/Infinity 非法且与签名 base 分叉，序列化阶段即拒绝。
        data = json.dumps(
            payload, ensure_ascii=False, allow_nan=False
        ).encode("utf-8")
        request = urllib.request.Request(
            url, data=data, headers=dict(_JSON_HEADERS), method="POST"
        )

        try:
            with urllib.request.urlopen(request, timeout=self._config.timeout) as resp:
                status = resp.getcode()
                raw = resp.read().decode("utf-8")
        except urllib.error.HTTPError as exc:  # 非 2xx
            raw_body = None
            with suppress(Exception):
                raw_body = exc.read().decode("utf-8")
            raise TransportError(
                f"HTTP {exc.code} 请求失败: {url}",
                status_code=exc.code,
                body=raw_body,
                cause=exc,
            ) from exc
        except urllib.error.URLError as exc:  # DNS/连接/超时
            raise TransportError(
                f"网络请求失败: {url} ({exc.reason})", cause=exc
            ) from exc
        except Exception as exc:  # noqa: BLE001 - 其余 IO/超时
            raise TransportError(f"请求异常: {url} ({exc})", cause=exc) from exc

        try:
            envelope = json.loads(raw)
        except json.JSONDecodeError as exc:
            raise TransportError(
                f"响应非合法 JSON: {url}",
                status_code=status,
                body=raw,
                cause=exc,
            ) from exc

        if not isinstance(envelope, dict) or "code" not in envelope:
            raise TransportError(
                f"响应缺少统一信封字段(code): {url}",
                status_code=status,
                body=raw,
            )
        return envelope
