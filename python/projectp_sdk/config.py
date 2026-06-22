"""环境与客户端配置。

- ``Environment``：预设基址枚举（PRODUCTION / SANDBOX）。
- ``Config``：商户号 + API Key + 双密钥 + 基址 + 超时。

支持显式传入自定义 ``base_url`` 覆盖预设（用于上级代理专有域名或本地端口）。
"""

from __future__ import annotations

from enum import Enum
from typing import Optional

__all__ = ["Environment", "Config"]


class Environment(Enum):
    """预设环境基址。"""

    PRODUCTION = "https://api.project-p-merchant.cniia.cloud/api/open/v1"
    SANDBOX = "http://127.0.0.1:3090/api/open/v1"

    @property
    def base_url(self) -> str:
        return self.value


class Config:
    """客户端配置。

    密钥分两套：``api_secret_pay`` 用于 pay 类接口与代收/退款回调；
    ``api_secret_payout`` 用于 payout 类接口与代付回调。客户端各方法自动选对密钥。

    基址优先级：显式 ``base_url`` > ``environment``。``base_url`` 末尾斜杠会被去除。
    """

    def __init__(
        self,
        merchant_no: str,
        api_key: str,
        api_secret_pay: str,
        api_secret_payout: str,
        environment: Environment = Environment.PRODUCTION,
        base_url: Optional[str] = None,
        timeout: float = 30.0,
    ) -> None:
        if not merchant_no:
            raise ValueError("merchant_no 不能为空")
        if not api_key:
            raise ValueError("api_key 不能为空")

        self.merchant_no = merchant_no
        self.api_key = api_key
        self.api_secret_pay = api_secret_pay
        self.api_secret_payout = api_secret_payout
        self.environment = environment
        resolved = base_url if base_url else environment.base_url
        self.base_url = resolved.rstrip("/")
        self.timeout = timeout
