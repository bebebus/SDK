"""商户代付反查判定。平台 POST 到商户自己的 HTTPS 接口，本模块不发请求。"""

from __future__ import annotations

import hmac
from typing import Any

PAYOUT_PRECHECK_APPROVE = "approve"
PAYOUT_PRECHECK_REJECT = "reject"

_BODY_KEYS = frozenset(("account_no", "out_payout_no", "original_sign", "payout_no"))
_SAVED_KEYS = ("account_no", "out_payout_no", "sign")


def _nonempty_str(value: Any) -> bool:
    return isinstance(value, str) and value != ""


def decide_payout_precheck(body: Any, saved: Any) -> str:
    """返回精确的 ``approve`` 或 ``reject``。非法输入返回 ``reject``，不抛异常。

    ``body`` 必须恰好包含四个非空字符串：``account_no``、``out_payout_no``、
    ``original_sign``、``payout_no``。``original_sign`` 与下单时保存的 ``sign``
    按 UTF-8 字节等长比较。``payout_no`` 必须出现，但不与下单请求比较。
    """
    try:
        fields_ok = (
            isinstance(body, dict)
            and isinstance(saved, dict)
            and set(body) == _BODY_KEYS
            and all(_nonempty_str(body[key]) for key in _BODY_KEYS)
            and all(_nonempty_str(saved.get(key)) for key in _SAVED_KEYS)
            and body["account_no"] == saved["account_no"]
            and body["out_payout_no"] == saved["out_payout_no"]
        )
        actual = body["original_sign"].encode("utf-8") if fields_ok else b""
        expected = saved["sign"].encode("utf-8") if fields_ok else b""
        if (
            fields_ok
            and len(actual) == len(expected)
            and hmac.compare_digest(actual, expected)
        ):
            return PAYOUT_PRECHECK_APPROVE
        return PAYOUT_PRECHECK_REJECT
    except Exception:
        return PAYOUT_PRECHECK_REJECT
