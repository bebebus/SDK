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
            "timeout",
        ):
            self.assertIn(key, cfg)
        self.assertTrue(str(cfg["base_url"]).startswith("https://"))
        self.assertTrue(str(cfg["base_url"]).endswith("/api/open/v1"))

    def test_catalog(self) -> None:
        with open(os.path.join(_APPENDIX, "catalog.py"), encoding="utf-8") as fh:
            src = fh.read()
        countries = _assign_value(src, "COUNTRIES")
        currencies = _assign_value(src, "CURRENCIES")
        ph = next(c for c in countries if c["code"] == "PH")
        self.assertIn("PHP", ph["currencies"])
        usdt = next(c for c in currencies if c["code"] == "USDT")
        self.assertEqual(usdt["kind"], "crypto")

    def test_error_codes(self) -> None:
        with open(os.path.join(_APPENDIX, "error-codes.py"), encoding="utf-8") as fh:
            errors = _assign_value(fh.read(), "ERROR_CODES")
        codes = {e["code"] for e in errors}
        self.assertIn("100001", codes)
        self.assertIn("300404", codes)
        self.assertEqual(next(e for e in errors if e["code"] == "100000")["retryable"], "depends")
        # 300304（建单暂不可用，insert-first fail-closed）是当前唯一约定非 200 的错误码，固定 HTTP 503。
        known_non_200 = {"300304": 503}
        for e in errors:
            expected = known_non_200.get(e["code"], 200)
            self.assertEqual(e["http"], expected, f"{e['code']} http={e['http']}")
