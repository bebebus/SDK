"""示例：代付下单（银行类）+ 查单 + 查银行列表。

运行::

    python3 examples/payout_create.py
"""

from __future__ import annotations

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from projectp_sdk import ApiError, Client, Config, Environment, TransportError


def main() -> None:
    config = Config(
        merchant_no=os.environ.get("PP_MERCHANT_NO", "M00000001"),
        api_key=os.environ.get("PP_API_KEY", "ak_demo_key"),
        api_secret_pay=os.environ.get("PP_SECRET_PAY", "sk_pay_demo"),
        api_secret_payout=os.environ.get("PP_SECRET_PAYOUT", "sk_payout_demo"),
        environment=Environment.SANDBOX,
    )
    client = Client(config)

    try:
        # 银行类代付前可先查可用银行列表，取 code 作为 bank_code。
        banks = client.payout_banks_query(pay_method="bank", country="PH", currency="PHP")
        print("可用银行:", banks.get("banks"))

        payout = client.payout_create(
            out_payout_no="WD20250101000001",
            amount=100000,  # 10 元
            currency="PHP",
            pay_method="bank",
            country="PH",
            notify_url="https://merchant.example.com/api/notify/payout",
            account_no="1234567890",
            account_name="San Zhang",
            bank_code="BDO",  # 银行类必填，取自 banks/query
        )
        print("代付受理:", payout)

        # 查单（payout_no 或 out_payout_no 二选一）。
        queried = client.payout_query(out_payout_no="WD20250101000001")
        print("代付状态:", queried.get("status"), "子态:", queried.get("sub_state"))

        # 成功后可查凭证 / 收据：
        # proof = client.payout_proof_query(out_payout_no="WD20250101000001")
        # receipt = client.payout_receipt_query(out_payout_no="WD20250101000001", inline=True)

    except ApiError as exc:
        print(f"业务失败 code={exc.code} message={exc.message} data={exc.data}")
    except TransportError as exc:
        print(f"传输失败: {exc} (status={exc.status_code})")


if __name__ == "__main__":
    main()
