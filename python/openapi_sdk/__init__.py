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

__version__ = "1.0.0"
