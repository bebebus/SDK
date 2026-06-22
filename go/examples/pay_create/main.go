// 示例：代收下单（pay/create）。
//
// 运行：go run ./examples/pay_create
// 默认指向 Sandbox（http://127.0.0.1:3090），改 Config 即可切到 Production 或自定义代理域名。
package main

import (
	"context"
	"errors"
	"fmt"
	"time"

	projectp "github.com/bebebus/SDK/go"
)

func main() {
	client := projectp.NewClient(projectp.Config{
		MerchantNo:   "M00000001",
		APIKey:       "ak_demo_key",
		SecretPay:    "sk_test_pay_xxxxxxxxxxxxxxxxxxxxxxxx",
		SecretPayout: "sk_test_payout_xxxxxxxxxxxxxxxxxxxxxxxx",
		Environment:  projectp.Sandbox, // 或 projectp.Production；用 BaseURL 覆盖为代理专有域名。
		Timeout:      30 * time.Second,
	})

	ctx := context.Background()

	// 业务字段：值为 nil 的字段不入请求体也不参与签名。
	resp, err := client.PayCreate(ctx, map[string]any{
		"out_order_no": "202501010001", // 商户订单号（幂等键，唯一）。
		"amount":       10000,          // 最小单位整数，10000 = 1 元。
		"currency":     "PHP",
		"pay_method":   "gcash",
		"country":      "PH",
		"notify_url":   "https://merchant.example.com/api/notify/pay",
		"subject":      "测试商品",
		"extra": map[string]any{ // 嵌套对象参与签名，SDK 自动稳定序列化。
			"customer": map[string]any{
				"first_name": "San",
				"last_name":  "Zhang",
				"email":      "san@example.com",
			},
		},
	})
	if err != nil {
		var apiErr *projectp.APIError
		if errors.As(err, &apiErr) {
			fmt.Printf("业务失败 code=%d message=%s data=%v\n", apiErr.Code, apiErr.Message, apiErr.Data)
			return
		}
		fmt.Printf("传输失败: %v\n", err)
		return
	}

	fmt.Printf("下单成功: order_no=%v status=%v pay_url=%v\n",
		resp.Data["order_no"], resp.Data["status"], resp.Data["pay_url"])
}
