"""签名器：HMAC-SHA256 -> 十六进制小写。

与服务端签名实现逐字节一致。
算法权威定义见 ../SIGNING.md，可复现 ../test-vectors.json 的 base 与 sign。

仅用 Python 标准库：json / hmac / hashlib。
"""

from __future__ import annotations

import hashlib
import hmac
import json
import re
from typing import Any, Mapping

__all__ = ["build_sign_base", "sign", "verify_callback"]

# 合法 sign 必须是非空的十六进制串（HMAC-SHA256 输出 64 位小写 hex）。
# 预校验拦截攻击者构造的非 hex/非 ASCII sign，避免 compare_digest 对非 ASCII 抛 TypeError。
_HEX_RE = re.compile(r"[0-9a-fA-F]+")


def _secret_is_blank(secret: Any) -> bool:
    """密钥是否为空（fail-closed 判定）：非字符串、空串、纯空白一律视为空。"""
    return not isinstance(secret, str) or secret.strip() == ""


def _stable_stringify(value: Any) -> str:
    """嵌套 object/array 的稳定 JSON 序列化，对齐 JS ``JSON.stringify`` + key 升序。

    - ``ensure_ascii=False``：非 ASCII（中文/emoji）原样保留，不转 ``\\uXXXX``。
    - ``separators=(',', ':')``：紧凑无空格。
    - ``sort_keys=True``：对象 key 递归升序。

    Python ``json.dumps`` 不转义 ``/`` 与 ``<>&``，与 JS 默认行为一致；
    会转义 ``"`` ``\\`` 及控制字符（``\\b\\f\\n\\r\\t`` 与其余 ``\\u00XX``），亦与 JS 一致。

    ``allow_nan=False``：NaN/Infinity 在 JSON 里非法，且与 JS 序列化分叉，一律拒绝（抛 ``ValueError``）。
    """
    return json.dumps(
        value,
        ensure_ascii=False,
        separators=(",", ":"),
        sort_keys=True,
        allow_nan=False,
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
    # 拒绝浮点：合约要求金额等用整数最小单位。Python ``json.dumps({x:1.0})`` 产出 ``1.0``、
    # ``str(1.0) == "1.0"``，与 JS ``Number(1.0)->"1"`` 分叉，且 NaN/Infinity 不可签名。
    # （``bool`` 已在上方先行处理，不受影响。）
    if isinstance(v, float):
        raise ValueError(
            f"签名字段不接受浮点数（值={v!r}）：金额等请用整数最小单位（int）"
        )
    # 其余标量（int / str），用原始字符串形态、不加引号。
    if isinstance(v, int):
        # 归一 -0（Python int 下 -0 即 0，仅作防御性显式归一）。
        return str(v if v != 0 else 0)
    return str(v)


def build_sign_base(payload: Mapping[str, Any], secret: str) -> str:
    """构造签名 base 字符串（不计算 HMAC，便于逐字节断言）。

    步骤：
    1. 过滤掉键名为 ``sign`` 的字段，以及值为 ``None`` 的字段。
    2. 剩余字段按键名 ASCII（码点）升序排序。
    3. 每个字段拼成 ``key=value``，用 ``&`` 连接。
    4. 末尾追加 ``&secret=<secret>``。

    ``secret`` 为空串/纯空白/非字符串时抛 ``ValueError``：从根上禁止空密钥签名。
    """
    if _secret_is_blank(secret):
        raise ValueError("secret 不能为空：禁止用空密钥签名")
    keys = sorted(k for k, v in payload.items() if k != "sign" and v is not None)
    parts = [f"{k}={_value_for_sign(payload[k])}" for k in keys]
    parts.append(f"secret={secret}")
    return "&".join(parts)


def sign(payload: Mapping[str, Any], secret: str) -> str:
    """对 payload 计算签名，返回十六进制小写字符串。

    ``secret`` 为空（空串/纯空白/非字符串）时抛 ``ValueError``，
    在计算任何 HMAC 之前拒绝，从根上禁止空密钥签名。
    """
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

    fail-closed 守卫（任何非法/攻击者可控输入一律返回 ``False``，绝不抛异常冒泡）：
    - ``secret`` 为空串/纯空白/非字符串 → 立即返回 ``False``（在算任何 HMAC 之前）。
    - ``payload`` 非映射（``None``/非对象）→ ``False``。
    - 提供的 ``sign`` 非字符串/为空/非十六进制 → ``False``（非 hex 预校验，
      避免 ``hmac.compare_digest`` 对非 ASCII 抛 ``TypeError``）。
    - 计算期望签名时若 payload 含非法字段（如浮点/NaN）抛错 → 吞掉并返回 ``False``。
    """
    # [A] 空密钥 fail-closed：在计算任何 HMAC 之前拒绝。
    if _secret_is_blank(secret):
        return False
    # [B] 回调体非映射（None/非对象）→ 拒绝。
    if not isinstance(payload, Mapping):
        return False
    provided = payload.get("sign")
    if not isinstance(provided, str) or not provided:
        return False
    # [B] 非十六进制 sign 预校验：非法字符/非 ASCII 直接拒绝，
    # 避免传给 compare_digest 抛 TypeError（其要求两侧同为 ASCII 字节/str）。
    if not _HEX_RE.fullmatch(provided):
        return False
    # build_sign_base 自身已排除 sign 字段，无需事先剔除。
    try:
        expected = sign(payload, secret)
    except (ValueError, TypeError):
        # payload 含非法签名字段（浮点/NaN/Infinity 等）→ 验签失败，不抛冒泡。
        return False
    return hmac.compare_digest(expected, provided)
