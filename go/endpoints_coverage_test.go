// SPDX-License-Identifier: MIT

package openapi

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"
)

func TestAllEndpointRoutesAndSecrets(t *testing.T) {
	var path string
	var payload map[string]any
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		path = r.URL.Path
		if err := json.NewDecoder(r.Body).Decode(&payload); err != nil {
			t.Errorf("decode: %v", err)
		}
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write([]byte(`{"code":0,"data":{"ok":true}}`))
	}))
	defer server.Close()
	c := NewClient(Config{BaseURL: server.URL, MerchantNo: "M1", APIKey: "key", SecretPay: "pay", SecretPayout: "payout"})
	cases := []struct {
		path   string
		secret string
		call   func(context.Context, map[string]any) (*Response, error)
	}{
		{"/merchant/pay/create", "pay", c.PayCreate},
		{"/merchant/pay/query", "pay", c.PayQuery},
		{"/merchant/pay-methods/query", "pay", c.PayMethodsQuery},
		{"/merchant/countries/query", "pay", c.CountriesQuery},
		{"/merchant/groups/query", "pay", c.GroupsQuery},
		{"/merchant/balance/query", "pay", c.BalanceQuery},
		{"/merchant/pay/test/complete", "pay", c.PayTestComplete},
		{"/merchant/payout/create", "payout", c.PayoutCreate},
		{"/merchant/payout/query", "payout", c.PayoutQuery},
		{"/merchant/payout/banks/query", "payout", c.PayoutBanksQuery},
		{"/merchant/payout/proof/query", "payout", c.PayoutProofQuery},
		{"/merchant/payout/test/complete", "payout", c.PayoutTestComplete},
	}
	for _, tc := range cases {
		t.Run(tc.path, func(t *testing.T) {
			result, err := tc.call(context.Background(), map[string]any{"order_no": "test"})
			if err != nil {
				t.Fatal(err)
			}
			if path != tc.path || result.Data["ok"] != true {
				t.Fatalf("path=%q result=%+v", path, result)
			}
			if !VerifyCallback(payload, tc.secret) {
				t.Fatal("请求签名与密钥不匹配")
			}
		})
	}
	pay := map[string]any{"order_no": "test"}
	pay["sign"] = Sign(pay, "pay")
	payout := map[string]any{"payout_no": "test"}
	payout["sign"] = Sign(payout, "payout")
	if !c.VerifyPayCallback(pay) || !c.VerifyPayoutCallback(payout) || c.VerifyPayCallback(payout) {
		t.Fatal("回调密钥选择错误")
	}
}
