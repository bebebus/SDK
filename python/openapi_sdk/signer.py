"""签名器：HMAC-SHA256 -> 十六进制小写。

与服务端签名实现逐字节一致。
算法权威定义见 ../SIGNING.md，可复现 ../test-vectors.json 的 base 与 sign。

仅用 Python 标准库：json / hmac / hashlib。
"""

from __future__ import annotations

import hashlib
import hmac
import json
from typing import Any, Dict, Mapping

__all__ = ["build_sign_base", "sign", "verify_callback"]


def _stable_stringify(value: Any) -> str:
    """嵌套 object/array 的稳定 JSON 序列化，对齐 JS ``JSON.stringify`` + key 升序。

    - ``ensure_ascii=False``：非 ASCII（中文/emoji）原样保留，不转 ``\\uXXXX``。
    - ``separators=(',', ':')``：紧凑无空格。
    - ``sort_keys=True``：对象 key 递归升序。

    Python ``json.dumps`` 不转义 ``/`` 与 ``<>&``，与 JS 默认行为一致；
    会转义 ``"`` ``\\`` 及控制字符（``\\b\\f\\n\\r\\t`` 与其余 ``\\u00XX``），亦与 JS 一致。
    """
    return json.dumps(
        value,
        ensure_ascii=False,
        separators=(",", ":"),
        sort_keys=True,
    )


def _value_for_sign(v: Any) -> str:
    """单字段取值的字符串形态（不加引号）。

    - object / array -> 稳定 JSON 序列化。
    - 标量 -> 原始字符串形态。

    关键坑：必须先判 ``bool`` 再判 ``int``（``bool`` 是 ``int`` 子类），
    且布尔须归一为 ``true``/``false``（``str(True) == "True"`` 是错的）。
    """
    if v is None:
        # 调用方在 build_sign_base 已过滤 None；此处兜底，与服务端 null 一致。
        return "null"
    if isinstance(v, bool):
        return "true" if v else "false"
    if isinstance(v, (dict, list)):
        return _stable_stringify(v)
    # 其余标量（int / str / float），用原始字符串形态、不加引号。
    return str(v)


def build_sign_base(payload: Mapping[str, Any], secret: str) -> str:
    """构造签名 base 字符串（不计算 HMAC，便于逐字节断言）。

    步骤：
    1. 过滤掉键名为 ``sign`` 的字段，以及值为 ``None`` 的字段。
    2. 剩余字段按键名 ASCII（码点）升序排序。
    3. 每个字段拼成 ``key=value``，用 ``&`` 连接。
    4. 末尾追加 ``&secret=<secret>``。
    """
    keys = sorted(k for k, v in payload.items() if k != "sign" and v is not None)
    parts = [f"{k}={_value_for_sign(payload[k])}" for k in keys]
    parts.append(f"secret={secret}")
    return "&".join(parts)


def sign(payload: Mapping[str, Any], secret: str) -> str:
    """对 payload 计算签名，返回十六进制小写字符串。"""
    base = build_sign_base(payload, secret)
    return hmac.new(
        secret.encode("utf-8"),
        base.encode("utf-8"),
        hashlib.sha256,
    ).hexdigest()


def verify_callback(payload: Mapping[str, Any], secret: str) -> bool:
    """回调验签（字段无关、时序安全）。

    取回调表里除 ``sign`` 外的所有字段，用本算法算出期望签名，
    与回调携带的 ``sign`` 做时序安全比较（``hmac.compare_digest``）。

    代收/退款回调用 ``api_secret_pay``；代付回调用 ``api_secret_payout``。
    """
    provided = payload.get("sign")
    if not isinstance(provided, str) or not provided:
        return False
    # build_sign_base 自身已排除 sign 字段，无需事先剔除。
    expected = sign(payload, secret)
    return hmac.compare_digest(expected, provided)
