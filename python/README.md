# 商户支付 OpenAPI — Python SDK

[![PyPI](https://img.shields.io/pypi/v/bebebus-merchant-openapi-sdk?label=PyPI)](https://pypi.org/project/bebebus-merchant-openapi-sdk/) [![License: MIT](https://img.shields.io/badge/license-MIT-green)](LICENSE)

**零第三方依赖**的 Python 3 SDK：HTTP 用 `urllib.request`，签名用 `hmac`/`hashlib`，测试用标准库 `unittest`。无需 `pip install` 任何运行时依赖。

签名算法与服务端签名实现逐字节一致，单测对 [`../test-vectors.json`](../test-vectors.json) 全量复现 `base` 与 `sign`。

## 引入（无需安装依赖）

把 `openapi_sdk/` 目录放进你的项目（或把本目录加入 `sys.path` / `PYTHONPATH`）即可：

```python
import sys
sys.path.insert(0, "/path/to/python")

from openapi_sdk import Client, Config, Environment
```

可选地用标准打包安装（仍零依赖）：

```bash
cd python
pip install .        # 或 python3 -m build；本身不拉任何第三方运行时依赖
```

## 快速开始

```python
from openapi_sdk import Client, Config, Environment, ApiError, TransportError

config = Config(
    merchant_no="M00000001",
    api_key="ak_xxx",
    api_secret_pay="sk_pay_xxx",        # pay 类接口 + 代收/退款回调
    api_secret_payout="sk_payout_xxx",  # payout 类接口 + 代付回调
    environment=Environment.SANDBOX,    # 或 Environment.PRODUCTION（须显式传 base_url）
)
client = Client(config)

try:
    # 金额是最小单位整数：10000 = 1 元
    order = client.pay_create(
        out_order_no="ORD1", amount=10000, currency="PHP",
        pay_method="gcash", country="PH",
        notify_url="https://m.example.com/api/notify/pay",
    )
    print(order["pay_url"])
except ApiError as e:        # 业务失败 code != 0
    print(e.code, e.message, e.data)
except TransportError as e:  # HTTP / 网络 / 超时
    print(e, e.status_code)
```

## 双环境与自定义基址

| 环境 | 基址 |
|------|------|
| `Environment.PRODUCTION` | 无内置基址，**必须显式传 `base_url`** |
| `Environment.SANDBOX` | `http://127.0.0.1:3090/api/open/v1` |

正式真实地址按上级代理专有域名派生（`https://api.<agent_domain>/api/open/v1`），用 `base_url=` 显式传入。选 `PRODUCTION` 又不传 `base_url` 会抛 `ValueError`（提示 `baseUrl is required`）：

```python
config = Config(
    merchant_no="M00000001", api_key="ak_xxx",
    api_secret_pay="...", api_secret_payout="...",
    base_url="https://api.agent.example.com/api/open/v1",  # 正式环境必传
)
```

## 全部 11 个端点

代收（密钥 `api_secret_pay`，自动选用）：

| 方法 | 端点 |
|------|------|
| `pay_create(...)` | `/merchant/pay/create` |
| `pay_query(order_no=, out_order_no=)` | `/merchant/pay/query` |
| `pay_methods_query(country=)` | `/merchant/pay-methods/query` |
| `balance_query(currency=)` | `/merchant/balance/query` |
| `pay_test_complete(result=, ...)` | `/merchant/pay/test/complete`（仅测试密钥） |

代付（密钥 `api_secret_payout`，自动选用）：

| 方法 | 端点 |
|------|------|
| `payout_create(...)` | `/merchant/payout/create` |
| `payout_query(payout_no=, out_payout_no=)` | `/merchant/payout/query` |
| `payout_banks_query(pay_method=, country=, currency=)` | `/merchant/payout/banks/query` |
| `payout_proof_query(payout_no=, out_payout_no=)` | `/merchant/payout/proof/query` |
| `payout_receipt_query(..., inline=)` | `/merchant/payout/receipt/query` |
| `payout_test_complete(result=, ...)` | `/merchant/payout/test/complete`（仅测试密钥） |

约定：

- 每请求自动注入 `merchant_no`/`api_key`/`timestamp`(Unix 秒)/唯一 `nonce` 与 `sign`。
- 值为 `None` 的参数不放入请求体、也不参与签名。
- `payout_receipt_query` 的 `inline` 以整数 **1/0** 发送（`True`→1 内联 base64 图片；`False`→0 返回带 token 的 URL）。
- 金额是最小单位整数（`10000 = 1 元`），用 `int` 类型传入。
- 成功返回 `data`（dict）；`code != 0` 抛 `ApiError`（携带 `code`/`message`/`data`）；HTTP/网络错误抛 `TransportError`。
- 需要原始信封时用 `client.call_raw(path, body, secret)`（不因 `code != 0` 抛异常）。

## 签名工具（可单独使用）

```python
from openapi_sdk import build_sign_base, sign, verify_callback

base = build_sign_base(payload, secret)   # 逐字节可断言的签名 base
sig  = sign(payload, secret)              # HMAC-SHA256 -> hex 小写
ok   = verify_callback(callback, secret)  # 时序安全（hmac.compare_digest），字段无关
```

## 回调验签 + 处理

见 [`examples/callback_verify.py`](examples/callback_verify.py)：解析原始 body → `verify_callback`（时序安全）→ 按 `status` 幂等处理（success/failed）→ 应答 **HTTP 200 + 纯文本 `success`**。代收回调用 `api_secret_pay`、代付回调用 `api_secret_payout`，示例各演示一次。验签失败不回成功，让平台重试；处理务必幂等（同一订单可能多次回调）。

## 示例

```bash
cd python
python3 examples/pay_create.py
python3 examples/payout_create.py
python3 examples/callback_verify.py   # 自演示：造签名回调 -> 验签 -> 应答 -> 篡改反例
```

## 跑测试

```bash
cd python
python3 -m unittest discover -s tests
```

测试包含：

- 读取 `../test-vectors.json`，对每个向量断言 `build_sign_base == base` 且 `sign == sign`；
- 回调验签正例 + 篡改一字节反例（含错误密钥、缺 sign）；
- 客户端请求构建（通用字段注入、`None` 过滤、密钥选择、`inline` 整数化、信封解析与异常分类）。
