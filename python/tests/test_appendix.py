"""附录配置与国家/币种数据文件。"""

from __future__ import annotations

import ast
import os
import unittest

_THIS_DIR = os.path.dirname(os.path.abspath(__file__))
_APPENDIX = os.path.join(os.path.dirname(_THIS_DIR), "appendix")


def _assign_value(src: str, name: str):
    tree = ast.parse(src)
    for node in tree.body:
        if isinstance(node, ast.Assign):
            for target in node.targets:
                if isinstance(target, ast.Name) and target.id == name:
                    return ast.literal_eval(node.value)
    raise AssertionError(f"missing {name}")


class TestAppendix(unittest.TestCase):
    def test_config_example(self) -> None:
        with open(os.path.join(_APPENDIX, "config.example.py"), encoding="utf-8") as fh:
            cfg = _assign_value(fh.read(), "CONFIG")
        for key in (
            "merchant_no",
            "api_key",
            "api_secret_pay",
            "api_secret_payout",
            "base_url",
            "base_url_payout",
            "timeout",
        ):
            self.assertIn(key, cfg)
        self.assertTrue(str(cfg["base_url"]).startswith("https://"))
        self.assertTrue(str(cfg["base_url"]).endswith("/api/open/v2"))
        self.assertTrue(str(cfg["base_url_payout"]).endswith("/api/open/v1"))

    def test_catalog(self) -> None:
        with open(os.path.join(_APPENDIX, "catalog.py"), encoding="utf-8") as fh:
            src = fh.read()
        countries = _assign_value(src, "COUNTRIES")
        currencies = _assign_value(src, "CURRENCIES")
        ph = next(c for c in countries if c["code"] == "PH")
        self.assertIn("PHP", ph["currencies"])
        usdt = next(c for c in currencies if c["code"] == "USDT")
        self.assertEqual(usdt["kind"], "crypto")
