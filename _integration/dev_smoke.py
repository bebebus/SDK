"""Python SDK × dev 环境联调。凭据从环境变量读取（PP_MNO/PP_KEY/PP_PAY/PP_POUT/PP_BASE）。
序列与 dev_smoke.mjs / dev_smoke.php 完全一致，便于跨语言对比。"""
import os
import sys
import time
import random

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "python"))
from openapi_sdk import Client, Config, sign, verify_callback, ApiError, TransportError  # noqa: E402

mno, key, pay, pout, base = (os.environ[k] for k in ("PP_MNO", "PP_KEY", "PP_PAY", "PP_POUT", "PP_BASE"))
client = Client(Config(mno, key, pay, pout, base_url=base))
tag = f"py-{int(time.time())}-{random.randint(1000, 9999)}"
counters = {"pass": 0, "fail": 0}


def ok(name: str, cond: bool, extra: str = "") -> None:
    counters["pass" if cond else "fail"] += 1
    print(f"{'✅' if cond else '❌'} {name}" + (f" | {extra}" if extra else ""))


print(f"[Python] base={base} merchant={mno} tag={tag}")

# 1. pay-methods/query
try:
    d = client.pay_methods_query(country="PH")
    methods = d.get("methods", [])
    ok("pay-methods/query", len(methods) > 0, ",".join(m["pay_method"] for m in methods))
except Exception as e:  # noqa: BLE001
    ok("pay-methods/query", False, f"{type(e).__name__} {e}")

# 2. balance/query
try:
    d = client.balance_query()
    ok("balance/query", isinstance(d.get("balances"), list), str(d.get("balances")))
except Exception as e:  # noqa: BLE001
    ok("balance/query", False, f"{type(e).__name__} {e}")

# 3. pay/create
out_order_no = f"sdk-{tag}"
order_no = None
try:
    d = client.pay_create(
        out_order_no=out_order_no, amount=10000, currency="PHP", pay_method="gcash", country="PH",
        notify_url="https://merchant.example.com/api/notify/pay",
        extra={"customer": {"first_name": "San", "last_name": "Zhang", "email": "san@example.com", "phone": "09000000000"}},
    )
    order_no = d.get("order_no")
    ok("pay/create", order_no is not None, f"order_no={order_no} status={d.get('status')} pay_url={str(d.get('pay_url') or '')[:48]}")
except ApiError as e:
    ok("pay/create", False, f"ApiError code={e.code} {e.message} {e.data}")
except Exception as e:  # noqa: BLE001
    ok("pay/create", False, f"{type(e).__name__} {e}")

# 4. pay/query
try:
    d = client.pay_query(out_order_no=out_order_no)
    ok("pay/query", d.get("out_order_no") == out_order_no, f"status={d.get('status')} notify_status={d.get('notify_status')}")
except Exception as e:  # noqa: BLE001
    ok("pay/query", False, f"{type(e).__name__} {e}")

# 5. payout/banks/query
bank_code = None
try:
    d = client.payout_banks_query(pay_method="bank", country="PH", currency="PHP")
    banks = d.get("banks", [])
    bank_code = banks[0]["code"] if banks else None
    ok("payout/banks/query", isinstance(banks, list), f"count={len(banks)} first={bank_code or 'N/A'}")
except Exception as e:  # noqa: BLE001
    ok("payout/banks/query", False, f"{type(e).__name__} {e}")

# 6. payout/create
out_payout_no = f"sdkw-{tag}"
try:
    d = client.payout_create(
        out_payout_no=out_payout_no, amount=10000, currency="PHP",
        pay_method="bank" if bank_code else "gcash", country="PH",
        notify_url="https://merchant.example.com/api/notify/payout",
        account_no="1234567890", account_name="San Zhang", bank_code=bank_code,
    )
    ok("payout/create", d.get("payout_no") is not None, f"payout_no={d.get('payout_no')} status={d.get('status')} freeze={d.get('freeze_amount')}")
except ApiError as e:
    ok("payout/create", False, f"ApiError code={e.code} {e.message} {e.data}")
except Exception as e:  # noqa: BLE001
    ok("payout/create", False, f"{type(e).__name__} {e}")

# 7. payout/query
try:
    d = client.payout_query(out_payout_no=out_payout_no)
    ok("payout/query", d.get("out_payout_no") == out_payout_no, f"status={d.get('status')} sub_state={d.get('sub_state')}")
except Exception as e:  # noqa: BLE001
    ok("payout/query", False, f"{type(e).__name__} {e}")

# 8. 负例：错误密钥签名应被服务端拒（code 100104）
try:
    bad = Client(Config(mno, key, "deadbeef" * 8, pout, base_url=base))
    bad.pay_query(out_order_no=out_order_no)
    ok("负例:错误签名被拒", False, "未抛错（异常）")
except ApiError as e:
    ok("负例:错误签名被拒", e.code in (100104, 100000), f"code={e.code} {e.message}")
except Exception as e:  # noqa: BLE001
    ok("负例:错误签名被拒", False, f"{type(e).__name__} {e}")

# 9. 回调验签自证
cb = {"merchant_no": mno, "order_no": order_no or "P_demo", "out_order_no": out_order_no, "amount": 10000, "currency": "PHP", "status": "success", "paid_at": "2026-06-23T08:00:00+08:00"}
cb["sign"] = sign(cb, pay)
ok("回调验签 正例", verify_callback(cb, pay) is True)
ok("回调验签 反例(篡改amount)", verify_callback({**cb, "amount": 10001}, pay) is False)

print(f"\n[Python] 结果: {counters['pass']} 通过, {counters['fail']} 失败")
sys.exit(1 if counters["fail"] else 0)
