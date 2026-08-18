# 商户 OpenAPI Python SDK 配置模板。
# 复制为工程内 config.py 后替换占位符。正式环境必须 https；勿提交真实密钥。
#
# 使用：
#   from openapi_sdk import Client, Config
#   client = Client(Config(
#       merchant_no=CONFIG["merchant_no"],
#       api_key=CONFIG["api_key"],
#       api_secret_pay=CONFIG["api_secret_pay"],
#       api_secret_payout=CONFIG["api_secret_payout"],
#       base_url=CONFIG["base_url"],
#       timeout=CONFIG["timeout"],
#   ))

CONFIG = {
    "merchant_no": "<YOUR_MERCHANT_NO>",
    "api_key": "<YOUR_API_KEY>",
    "api_secret_pay": "<YOUR_API_SECRET_PAY>",
    "api_secret_payout": "<YOUR_API_SECRET_PAYOUT>",
    "base_url": "https://api.<service_domain>/api/open/v1",
    "timeout": 30.0,
}
