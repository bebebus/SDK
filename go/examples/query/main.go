// 示例：查询类端点（pay/query、pay-methods/query、balance/query、payout/query、payout/receipt/query）。
//
// 运行：go run ./examples/query
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

	// 代收查单：order_no 或 out_order_no 二选一。
	resp, err := client.PayQuery(ctx, map[string]any{"out_order_no": "202501010001"})
	printResult("pay/query", resp, err)

	// 可用支付方式（country 可选过滤）。
	resp, err = client.PayMethodsQuery(ctx, map[string]any{"country": "PH"})
	printResult("pay-methods/query", resp, err)

	// 余额查询（currency 可选过滤）。
	resp, err = client.BalanceQuery(ctx, map[string]any{})
	printResult("balance/query", resp, err)

	// 代付查单。
	resp, err = client.PayoutQuery(ctx, map[string]any{"out_payout_no": "WD202501010001"})
	printResult("payout/query", resp, err)

	// 代付收据：inline 以整数 1/0 发送，true=内联 base64 图片，false=带 token 的 URL。
	resp, err = client.PayoutReceiptQuery(ctx, map[string]any{"out_payout_no": "WD202501010001", "lang": "zh-CN"}, false)
	printResult("payout/receipt/query", resp, err)

	// 测试单完成（仅测试密钥）。
	resp, err = client.PayTestComplete(ctx, map[string]any{"out_order_no": "202501010001", "result": "success"})
	printResult("pay/test/complete", resp, err)
	resp, err = client.PayoutTestComplete(ctx, map[string]any{"out_payout_no": "WD202501010001", "result": "success"})
	printResult("payout/test/complete", resp, err)
}

func printResult(name string, resp *openapi.Response, err error) {
	if err != nil {
		var apiErr *openapi.APIError
		if errors.As(err, &apiErr) {
			fmt.Printf("[%s] 业务错误 code=%d message=%s\n", name, apiErr.Code, apiErr.Message)
			return
		}
		fmt.Printf("[%s] 传输错误: %v\n", name, err)
		return
	}
	fmt.Printf("[%s] data=%v\n", name, resp.Data)
}
