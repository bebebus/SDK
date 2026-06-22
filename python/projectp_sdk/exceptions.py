"""SDK 异常类型。

- ``ApiError``：业务失败（统一信封 ``code != 0``），携带 code/message/data。
- ``TransportError``：HTTP 状态非 2xx、网络错误、超时、响应非合法 JSON 等传输层问题。

二者均继承自 ``ProjectPError``，调用方可只捕获基类。
"""

from __future__ import annotations

from typing import Any, Optional

__all__ = ["ProjectPError", "ApiError", "TransportError"]


class ProjectPError(Exception):
    """SDK 异常基类。"""


class ApiError(ProjectPError):
    """业务异常：统一响应信封中 ``code != 0``。

    携带原始 ``code``/``message``/``data`` 供调用方判断，不在 SDK 内穷举写死分支。
    """

    def __init__(self, code: int, message: str, data: Any = None) -> None:
        super().__init__(f"[{code}] {message}")
        self.code = code
        self.message = message
        self.data = data


class TransportError(ProjectPError):
    """传输异常：HTTP 非 2xx、网络错误、超时、响应不可解析为 JSON 等。"""

    def __init__(
        self,
        message: str,
        status_code: Optional[int] = None,
        body: Optional[str] = None,
        cause: Optional[BaseException] = None,
    ) -> None:
        super().__init__(message)
        self.status_code = status_code
        self.body = body
        self.cause = cause
