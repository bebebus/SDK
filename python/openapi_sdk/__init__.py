"""商户支付 OpenAPI Python SDK（零第三方依赖）。

公开 API::

    from openapi_sdk import (
        Client, Config, Environment,
        sign, build_sign_base, verify_callback,
        ApiError, TransportError, OpenApiError,
    )
"""

from __future__ import annotations

from .client import Client
from .config import Config, Environment
from .exceptions import ApiError, OpenApiError, TransportError
from .signer import build_sign_base, sign, verify_callback

__all__ = [
    "Client",
    "Config",
    "Environment",
    "sign",
    "build_sign_base",
    "verify_callback",
    "ApiError",
    "TransportError",
    "OpenApiError",
]

# [L20] 版本号单一事实源：从已安装包元数据（pyproject 的 version=1.1.0）派生，
# 避免与 pyproject 自相矛盾；源码直跑（未安装）时取不到则兜底 '1.1.0'。
try:
    from importlib.metadata import PackageNotFoundError, version as _pkg_version

    try:
        __version__ = _pkg_version("bebebus-merchant-openapi-sdk")
    except PackageNotFoundError:
        __version__ = "1.1.0"
except ImportError:  # pragma: no cover —— Python <3.8 无 importlib.metadata
    __version__ = "1.1.0"
