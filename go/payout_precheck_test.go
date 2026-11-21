// SPDX-License-Identifier: MIT

package openapi

import (
	"encoding/json"
	"testing"
)

func TestDecidePayoutPrecheck(t *testing.T) {
	saved := map[string]any{
		"out_payout_no": "merchant-order-no",
		"account_no":    "account",
		"sign":          "abc123",
	}
	body := map[string]any{
		"out_payout_no": "merchant-order-no",
		"payout_no":     "W100",
		"account_no":    "account",
		"original_sign": "abc123",
	}
	if got := DecidePayoutPrecheck(body, saved); got != PayoutPrecheckApprove {
		t.Fatalf("match: %s", got)
	}
	body["payout_no"] = "W-other"
	if got := DecidePayoutPrecheck(body, saved); got != PayoutPrecheckApprove {
		t.Fatalf("payout_no is not compared: %s", got)
	}

	savedText := map[string]string{
		"out_payout_no": "merchant-order-no",
		"account_no":    "account",
		"sign":          "商户签",
	}
	bodyText := map[string]string{
		"out_payout_no": "merchant-order-no",
		"payout_no":     "W100",
		"account_no":    "account",
		"original_sign": "商户签",
	}
	if got := DecidePayoutPrecheck(bodyText, savedText); got != PayoutPrecheckApprove {
		t.Fatalf("utf-8 match: %s", got)
	}
	bodyText["original_sign"] = "商户钱"
	if got := DecidePayoutPrecheck(bodyText, savedText); got != PayoutPrecheckReject {
		t.Fatalf("utf-8 mismatch: %s", got)
	}

	rejects := []map[string]any{
		{"out_payout_no": "merchant-order-no", "payout_no": "W100", "account_no": "account", "original_sign": "abc124"},
		{"out_payout_no": "merchant-order-no", "payout_no": "W100", "account_no": "account", "original_sign": "abc"},
		{"out_payout_no": "merchant-order-no", "payout_no": "W100", "account_no": "other", "original_sign": "abc123"},
		{"out_payout_no": "other", "payout_no": "W100", "account_no": "account", "original_sign": "abc123"},
		{"out_payout_no": "merchant-order-no", "payout_no": "W100", "account_no": "account", "original_sign": "abc123", "extra": "x"},
		{"out_payout_no": "merchant-order-no", "account_no": "account", "original_sign": "abc123"},
		{"out_payout_no": "merchant-order-no", "payout_no": "W100", "account_no": "", "original_sign": "abc123"},
		{"out_payout_no": "merchant-order-no", "payout_no": 1, "account_no": "account", "original_sign": "abc123"},
	}
	for i, item := range rejects {
		if got := DecidePayoutPrecheck(item, saved); got != PayoutPrecheckReject {
			t.Fatalf("case %d: %s", i, got)
		}
	}
	if got := DecidePayoutPrecheck(body, map[string]any{"out_payout_no": "merchant-order-no", "account_no": "account", "sign": ""}); got != PayoutPrecheckReject {
		t.Fatalf("empty saved sign: %s", got)
	}
	for _, item := range []any{nil, []any{}, "approve", map[string]any{}} {
		if got := DecidePayoutPrecheck(item, saved); got != PayoutPrecheckReject {
			t.Fatalf("bad body %v: %s", item, got)
		}
		if got := DecidePayoutPrecheck(body, item); got != PayoutPrecheckReject {
			t.Fatalf("bad saved %v: %s", item, got)
		}
	}
}

func FuzzDecidePayoutPrecheckNeverPanics(f *testing.F) {
	f.Add(`{"out_payout_no":"m","payout_no":"W","account_no":"a","original_sign":"s"}`, `{"out_payout_no":"m","account_no":"a","sign":"s"}`)
	f.Add(`[]`, `null`)
	f.Add(`{"original_sign":1}`, `{"sign":"s"}`)
	f.Fuzz(func(t *testing.T, bodyJSON, savedJSON string) {
		var body, saved any
		_ = json.Unmarshal([]byte(bodyJSON), &body)
		_ = json.Unmarshal([]byte(savedJSON), &saved)
		text := DecidePayoutPrecheck(body, saved)
		if text != PayoutPrecheckApprove && text != PayoutPrecheckReject {
			t.Fatalf("decision %q", text)
		}
	})
}
