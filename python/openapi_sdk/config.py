"""环境与客户端配置。

- ``Environment``：预设环境枚举（PRODUCTION / SANDBOX）。
- ``Config``：商户号 + API Key + 双密钥 + 基址 + 超时。

正式环境（PRODUCTION）没有内置基址：请向服务商获取正式地址
（``https://api.<domain>/api/open/v1``），并显式传入 ``base_url``。
沙箱（SANDBOX）保留本地预设基址，便于本地联调。
"""

from __future__ import annotations

from enum import Enum
from typing import Optional
from urllib.parse import urlparse

__all__ = ["Environment", "Config"]


def _host_is_local(host: Optional[str]) -> bool:
    """是否本地回环 host（放行 http 以兼容本地联调）。"""
    return host in ("localhost", "127.0.0.1", "::1")


class Environment(Enum):
    """预设环境。

    - ``PRODUCTION``：无内置基址（空字符串），必须显式传 ``base_url``。
    - ``SANDBOX``：本地预设基址。
    """

    PRODUCTION = ""
    SANDBOX = "http://127.0.0.1:3090/api/open/v1"

    @property
    def base_url(self) -> str:
        return self.value


class Config:
    """客户端配置。

    密钥分两套：``api_secret_pay`` 用于 pay 类接口与代收/退款回调；
    ``api_secret_payout`` 用于 payout 类接口与代付回调。客户端各方法自动选对密钥。

    基址优先级：显式 ``base_url`` > ``environment`` 预设。``base_url`` 末尾斜杠会被去除。
    若最终基址为空（如选了 PRODUCTION 又未传 ``base_url``）则抛 ``ValueError``。
    """

    def __init__(
        self,
        merchant_no: str,
        api_key: str,
        api_secret_pay: str = "",
        api_secret_payout: str = "",
        environment: Environment = Environment.PRODUCTION,
        base_url: Optional[str] = None,
        timeout: float = 30.0,
    ) -> None:
        if not merchant_no:
            raise ValueError("merchant_no 不能为空")
        if not api_key:
            raise ValueError("api_key 不能为空")
        # [L14] 按需校验（对齐 java/go 的 requireSecret* 模式）：构造期允许只配
        # 一套密钥（只跑代收或只跑代付）；缺哪套密钥要到调用对应业务方法时才
        # fail-closed（见 require_secret_pay / require_secret_payout）。绝不空密钥签名。
        self.merchant_no = merchant_no
        self.api_key = api_key
        self.api_secret_pay = api_secret_pay if isinstance(api_secret_pay, str) else ""
        self.api_secret_payout = (
            api_secret_payout if isinstance(api_secret_payout, str) else ""
        )
        self.environment = environment
        resolved = base_url if base_url else environment.base_url
        if not resolved:
            raise ValueError(
                "baseUrl is required: obtain the production URL from your service "
                "provider (e.g. https://api.<domain>/api/open/v1)"
            )
        resolved = resolved.rstrip("/")
        # [D] 传输层 fail-closed：
        # - 仅允许 http/https scheme（拒绝 file:// 等，规避 urllib FileHandler 风险）。
        # - 非本地回环 host 强制 https（localhost/127.0.0.1/::1 放行 http 便于本地联调）。
        parsed = urlparse(resolved)
        scheme = parsed.scheme.lower()
        if scheme not in ("http", "https"):
            raise ValueError(
                f"base_url 仅支持 http/https，拒绝 scheme={scheme!r}: {resolved}"
            )
        if scheme == "http" and not _host_is_local(parsed.hostname):
            raise ValueError(
                "base_url 非本地地址必须使用 https://（仅 localhost/127.0.0.1 放行 http）："
                f" {resolved}"
            )
        self.base_url = resolved
        self.timeout = timeout

    # [L14] 按需取密钥：调用对应业务方法时才校验，缺失即 fail-closed（绝不空密钥签名）。
    def require_secret_pay(self) -> str:
        """取代收/退款密钥（pay 类）。未配置则抛 ValueError。"""
        if not isinstance(self.api_secret_pay, str) or self.api_secret_pay.strip() == "":
            raise ValueError("未配置 api_secret_pay，无法调用 pay 类接口或验签代收回调")
        return self.api_secret_pay

    def require_secret_payout(self) -> str:
        """取代付密钥（payout 类）。未配置则抛 ValueError。"""
        if (
            not isinstance(self.api_secret_payout, str)
            or self.api_secret_payout.strip() == ""
        ):
            raise ValueError("未配置 api_secret_payout，无法调用 payout 类接口或验签代付回调")
        return self.api_secret_payout
