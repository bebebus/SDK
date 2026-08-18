"""示例：代收下单 + 查单。

运行（无需安装依赖，直接用源码）::

    python3 examples/pay_create.py

请把下面的凭证替换为你的真实值，或改用环境变量。
"""

from __future__ import annotations

import os
import sys

# 让示例脚本直接 import 源码包（无需安装）。
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from openapi_sdk import ApiError, Client, Config, Environment, TransportError


def main() -> None:
    config = Config(
        merchant_no=os.environ.get("PP_MERCHANT_NO", "M00000001"),
        api_key=os.environ.get("PP_API_KEY", "ak_demo_key"),
        api_secret_pay=os.environ.get("PP_SECRET_PAY", "sk_pay_demo"),
        api_secret_payout=os.environ.get("PP_SECRET_PAYOUT", "sk_payout_demo"),
        # 切换正式 / 沙箱：Environment.PRODUCTION / Environment.SANDBOX
        # 正式环境地址请向服务商获取，用 base_url= 覆盖：
        #   base_url="https://api.<domain>/api/open/v1"
        environment=Environment.SANDBOX,
    )
    client = Client(config)

    try:
        # 金额是最小单位整数：10000 = 1 元。
        order = client.pay_create(
            out_order_no="ORD20250101000001",
            amount=10000,
            currency="PHP",
            pay_method="gcash",
            country="PH",
            notify_url="https://merchant.example.com/api/notify/pay",
            subject="测试订单",
            extra={
                "customer": {
                    "first_name": "San",
                    "last_name": "Zhang",
                    "email": "san@example.com",
                    "phone": "09000000000",
                }
            },
        )
        print("下单成功:", order)
        print("收银台:", order.get("pay_url") or order.get("qrcode_content"))

        # 查单（order_no 或 out_order_no 二选一）。
        queried = client.pay_query(out_order_no="ORD20250101000001")
        print("查单状态:", queried.get("status"))

    except ApiError as exc:
        print(f"业务失败 code={exc.code} message={exc.message} data={exc.data}")
    except TransportError as exc:
        print(f"传输失败: {exc} (status={exc.status_code})")


if __name__ == "__main__":
    main()
