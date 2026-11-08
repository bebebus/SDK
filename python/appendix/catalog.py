# SPDX-License-Identifier: MIT
# 由 _tooling/reference-codes.mjs 生成；数据源 appendix/reference-codes.json。
# 标准字典与区域扩展，实际可用范围以商户查询接口为准。

COUNTRIES = [
    {"code": "PH", "name_zh": "菲律宾", "name_en": "Philippines", "currencies": ["PHP"]},
    {"code": "ID", "name_zh": "印度尼西亚", "name_en": "Indonesia", "currencies": ["IDR"]},
    {"code": "IN", "name_zh": "印度", "name_en": "India", "currencies": ["INR"]},
    {"code": "BD", "name_zh": "孟加拉国", "name_en": "Bangladesh", "currencies": ["BDT"]},
    {"code": "VN", "name_zh": "越南", "name_en": "Vietnam", "currencies": ["VND"]},
    {"code": "TH", "name_zh": "泰国", "name_en": "Thailand", "currencies": ["THB"]},
    {"code": "MM", "name_zh": "缅甸", "name_en": "Myanmar", "currencies": ["MMK"]},
    {"code": "MY", "name_zh": "马来西亚", "name_en": "Malaysia", "currencies": ["MYR"]},
    {"code": "BR", "name_zh": "巴西", "name_en": "Brazil", "currencies": ["BRL"]},
    {"code": "MX", "name_zh": "墨西哥", "name_en": "Mexico", "currencies": ["MXN"]},
    {"code": "CN", "name_zh": "中国", "name_en": "China", "currencies": ["CNY"]},
    {"code": "HK", "name_zh": "中国香港", "name_en": "Hong Kong", "currencies": ["HKD"]},
]

CURRENCIES = [
    {"code": "PHP", "name_zh": "菲律宾比索", "name_en": "Philippine peso", "kind": "fiat"},
    {"code": "IDR", "name_zh": "印尼盾", "name_en": "Indonesian rupiah", "kind": "fiat"},
    {"code": "INR", "name_zh": "印度卢比", "name_en": "Indian rupee", "kind": "fiat"},
    {"code": "BDT", "name_zh": "孟加拉塔卡", "name_en": "Bangladeshi taka", "kind": "fiat"},
    {"code": "VND", "name_zh": "越南盾", "name_en": "Vietnamese dong", "kind": "fiat"},
    {"code": "THB", "name_zh": "泰铢", "name_en": "Thai baht", "kind": "fiat"},
    {"code": "MMK", "name_zh": "缅元", "name_en": "Myanmar kyat", "kind": "fiat"},
    {"code": "MYR", "name_zh": "马来西亚林吉特", "name_en": "Malaysian ringgit", "kind": "fiat"},
    {"code": "BRL", "name_zh": "巴西雷亚尔", "name_en": "Brazilian real", "kind": "fiat"},
    {"code": "MXN", "name_zh": "墨西哥比索", "name_en": "Mexican peso", "kind": "fiat"},
    {"code": "CNY", "name_zh": "人民币", "name_en": "Chinese yuan", "kind": "fiat"},
    {"code": "USDT", "name_zh": "泰达币", "name_en": "Tether", "kind": "crypto"},
    {"code": "HKD", "name_zh": "港币", "name_en": "Hong Kong dollar", "kind": "fiat"},
]
