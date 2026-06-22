// 示例：代付下单（payout/create）+ 查单 + 凭证查询。
//
// 运行：go run ./examples/payout_create
package main

import (
	"context"
	"errors"
	"fmt"

	"github.com/bebebus/SDK/go"
)

func main() {
	client := openapi.NewClient(openapi.Config{
		MerchantNo:   "M00000001",
		APIKey:       "ak_demo_key",
		SecretPay:    "sk_test_pay_xxxxxxxxxxxxxxxxxxxxxxxx",
		SecretPayout: "sk_test_payout_xxxxxxxxxxxxxxxxxxxxxxxx",
		Environment:  openapi.Sandbox,
	})

	ctx := context.Background()

	// 银行类代付前，可先查可用银行拿 bank_code。
	if banks, err := client.PayoutBanksQuery(ctx, map[string]any{
		"pay_method": "bank",
		"country":    "PH",
		"currency":   "PHP",
	}); err == nil {
		fmt.Printf("可用银行: %v\n", banks.Data["banks"])
	}

	resp, err := client.PayoutCreate(ctx, map[string]any{
		"out_payout_no": "WD202501010001", // 商户代付单号（幂等键，唯一）。
		"amount":        100000,           // 最小单位整数。
		"currency":      "PHP",
		"pay_method":    "bank",
		"country":       "PH",
		"notify_url":    "https://merchant.example.com/api/notify/payout",
		"account_no":    "1234567890",
		"account_name":  "San Zhang",
		"bank_code":     "BDO", // 银行类必填；钱包类（gcash）可不传。
	})
	if err != nil {
		var apiErr *openapi.APIError
		if errors.As(err, &apiErr) {
			fmt.Printf("代付受理失败 code=%d message=%s\n", apiErr.Code, apiErr.Message)
			return
		}
		fmt.Printf("传输失败: %v\n", err)
		return
	}

	payoutNo := resp.Data["payout_no"]
	fmt.Printf("代付已受理: payout_no=%v status=%v freeze=%v\n",
		payoutNo, resp.Data["status"], resp.Data["freeze_amount"])

	// 查单（最终结果以异步回调与查单为准）。
	if q, err := client.PayoutQuery(ctx, map[string]any{"out_payout_no": "WD202501010001"}); err == nil {
		fmt.Printf("查单: status=%v sub_state=%v\n", q.Data["status"], q.Data["sub_state"])
	}

	// 凭证查询（仅 success 可查）。
	if proof, err := client.PayoutProofQuery(ctx, map[string]any{"out_payout_no": "WD202501010001"}); err == nil {
		fmt.Printf("凭证: %v\n", proof.Data["proof_url"])
	}
}
