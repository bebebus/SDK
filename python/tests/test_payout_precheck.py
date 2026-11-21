# SPDX-License-Identifier: MIT

"""代付反查判定。"""

from __future__ import annotations

import os
import sys
import unittest

_THIS_DIR = os.path.dirname(os.path.abspath(__file__))
_PKG_ROOT = os.path.dirname(_THIS_DIR)
if _PKG_ROOT not in sys.path:
    sys.path.insert(0, _PKG_ROOT)

from openapi_sdk import decide_payout_precheck  # noqa: E402

SAVED = {
    "out_payout_no": "merchant-order-no",
    "account_no": "account",
    "sign": "abc123",
}


def body(**overrides):
    value = {
        "out_payout_no": "merchant-order-no",
        "payout_no": "W100",
        "account_no": "account",
        "original_sign": "abc123",
    }
    value.update(overrides)
    return value


class TestPayoutPrecheck(unittest.TestCase):
    def test_match_approves_without_comparing_payout_no(self):
        self.assertEqual(decide_payout_precheck(body(), SAVED), "approve")
        self.assertEqual(
            decide_payout_precheck(body(payout_no="W-other"), SAVED), "approve"
        )

    def test_utf8_sign_compares_bytes(self):
        saved = dict(SAVED, sign="商户签")
        self.assertEqual(
            decide_payout_precheck(body(original_sign="商户签"), saved), "approve"
        )
        self.assertEqual(
            decide_payout_precheck(body(original_sign="商户钱"), saved), "reject"
        )

    def test_mismatch_rejects(self):
        self.assertEqual(
            decide_payout_precheck(body(original_sign="abc124"), SAVED), "reject"
        )
        self.assertEqual(
            decide_payout_precheck(body(original_sign="abc"), SAVED), "reject"
        )
        self.assertEqual(
            decide_payout_precheck(body(account_no="other"), SAVED), "reject"
        )
        self.assertEqual(
            decide_payout_precheck(body(out_payout_no="other"), SAVED), "reject"
        )

    def test_shape_rejects(self):
        extra = body()
        extra["extra"] = "x"
        self.assertEqual(decide_payout_precheck(extra, SAVED), "reject")
        missing = body()
        del missing["payout_no"]
        self.assertEqual(decide_payout_precheck(missing, SAVED), "reject")
        self.assertEqual(decide_payout_precheck(body(account_no=""), SAVED), "reject")
        self.assertEqual(decide_payout_precheck(body(payout_no=1), SAVED), "reject")
        self.assertEqual(decide_payout_precheck(body(), dict(SAVED, sign="")), "reject")

    def test_bad_input_rejects_without_raising(self):
        self.assertEqual(decide_payout_precheck(None, SAVED), "reject")
        self.assertEqual(decide_payout_precheck([], SAVED), "reject")
        self.assertEqual(decide_payout_precheck(body(), None), "reject")
        self.assertEqual(decide_payout_precheck("approve", SAVED), "reject")


if __name__ == "__main__":
    unittest.main()
