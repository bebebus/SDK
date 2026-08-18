// Go SDK × dev 环境联调。凭据从环境变量读取（PP_MNO/PP_KEY/PP_PAY/PP_POUT/PP_BASE）。
// 序列与其余语言 dev_smoke 一致。运行：cd go && go run ./examples/dev_smoke
package main

import (
	"context"
	"errors"
	"fmt"
	"math/rand"
	"os"
	"time"

	"github.com/bebebus/SDK/go"
)

var pass, fail int

func ok(name string, cond bool, extra string) {
	mark := "✅"
	if cond {
		pass++
	} else {
		fail++
		mark = "❌"
	}
	if extra != "" {
		fmt.Printf("%s %s | %s\n", mark, name, extra)
	} else {
		fmt.Printf("%s %s\n", mark, name)
	}
}

// apiCode 从 error 中提取业务码（*APIError），无则返回 -1 与错误文本。
func apiCode(err error) (int, string) {
	var ae *openapi.APIError
	if errors.As(err, &ae) {
		return ae.Code, ae.Message
	}
	return -1, err.Error()
}

func main() {
	mno, key, pay, pout, base := os.Getenv("PP_MNO"), os.Getenv("PP_KEY"), os.Getenv("PP_PAY"), os.Getenv("PP_POUT"), os.Getenv("PP_BASE")
	client := openapi.NewClient(openapi.Config{MerchantNo: mno, APIKey: key, SecretPay: pay, SecretPayout: pout, BaseURL: base})
	ctx := context.Background()
	tag := fmt.Sprintf("go-%d-%d", time.Now().Unix(), rand.Intn(9000)+1000)
	fmt.Printf("[Go] base=%s merchant=%s tag=%s\n", base, mno, tag)

	// 1. pay-methods/query
	if r, err := client.PayMethodsQuery(ctx, map[string]any{"country": "PH"}); err != nil {
		_, msg := apiCode(err)
		ok("pay-methods/query", false, msg)
	} else {
		methods, _ := r.Data["methods"].([]any)
		s := ""
		for _, m := range methods {
			if mm, okc := m.(map[string]any); okc {
				s += fmt.Sprint(mm["pay_method"]) + ","
			}
		}
		ok("pay-methods/query", len(methods) > 0, s)
	}

	// 2. balance/query
	if r, err := client.BalanceQuery(ctx, map[string]any{}); err != nil {
		_, msg := apiCode(err)
		ok("balance/query", false, msg)
	} else {
		_, has := r.Data["balances"]
		ok("balance/query", has, fmt.Sprint(r.Data["balances"]))
	}

	// 3. pay/create
	outOrderNo := "sdk-" + tag
	var orderNo string
	if r, err := client.PayCreate(ctx, map[string]any{
		"out_order_no": outOrderNo, "amount": 10000, "currency": "PHP", "pay_method": "gcash", "country": "PH",
		"notify_url": "https://merchant.example.com/api/notify/pay",
		"extra":      map[string]any{"customer": map[string]any{"first_name": "San", "last_name": "Zhang", "email": "san@example.com", "phone": "09000000000"}},
	}); err != nil {
		c, msg := apiCode(err)
		ok("pay/create", false, fmt.Sprintf("code=%d %s", c, msg))
	} else {
		orderNo, _ = r.Data["order_no"].(string)
		payURL := fmt.Sprint(r.Data["pay_url"])
		if len(payURL) > 48 {
			payURL = payURL[:48]
		}
		ok("pay/create", orderNo != "", fmt.Sprintf("order_no=%s status=%v pay_url=%s", orderNo, r.Data["status"], payURL))
	}

	// 4. pay/query
	if r, err := client.PayQuery(ctx, map[string]any{"out_order_no": outOrderNo}); err != nil {
		_, msg := apiCode(err)
		ok("pay/query", false, msg)
	} else {
		ok("pay/query", r.Data["out_order_no"] == outOrderNo, fmt.Sprintf("status=%v notify_status=%v", r.Data["status"], r.Data["notify_status"]))
	}

	// 5. payout/banks/query
	var bankCode string
	if r, err := client.PayoutBanksQuery(ctx, map[string]any{"pay_method": "bank", "country": "PH", "currency": "PHP"}); err != nil {
		_, msg := apiCode(err)
		ok("payout/banks/query", false, msg)
	} else {
		banks, _ := r.Data["banks"].([]any)
		if len(banks) > 0 {
			if b0, okc := banks[0].(map[string]any); okc {
				bankCode, _ = b0["code"].(string)
			}
		}
		first := bankCode
		if first == "" {
			first = "N/A"
		}
		ok("payout/banks/query", banks != nil, fmt.Sprintf("count=%d first=%s", len(banks), first))
	}

	// 6. payout/create
	outPayoutNo := "sdkw-" + tag
	payMethod := "gcash"
	params := map[string]any{
		"out_payout_no": outPayoutNo, "amount": 10000, "currency": "PHP", "country": "PH",
		"notify_url": "https://merchant.example.com/api/notify/payout",
		"account_no": "1234567890", "account_name": "San Zhang",
	}
	if bankCode != "" {
		payMethod = "bank"
		params["bank_code"] = bankCode
	}
	params["pay_method"] = payMethod
	if r, err := client.PayoutCreate(ctx, params); err != nil {
		c, msg := apiCode(err)
		ok("payout/create", false, fmt.Sprintf("code=%d %s", c, msg))
	} else {
		ok("payout/create", r.Data["payout_no"] != nil, fmt.Sprintf("payout_no=%v status=%v freeze=%v", r.Data["payout_no"], r.Data["status"], r.Data["freeze_amount"]))
	}

	// 7. payout/query
	if r, err := client.PayoutQuery(ctx, map[string]any{"out_payout_no": outPayoutNo}); err != nil {
		_, msg := apiCode(err)
		ok("payout/query", false, msg)
	} else {
		ok("payout/query", r.Data["out_payout_no"] == outPayoutNo, fmt.Sprintf("status=%v sub_state=%v", r.Data["status"], r.Data["sub_state"]))
	}

	// 8. 负例：错误密钥签名应被服务端拒（code 100104）
	bad := openapi.NewClient(openapi.Config{MerchantNo: mno, APIKey: key, SecretPay: "deadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeef", SecretPayout: pout, BaseURL: base})
	if _, err := bad.PayQuery(ctx, map[string]any{"out_order_no": outOrderNo}); err == nil {
		ok("负例:错误签名被拒", false, "未返错误（异常）")
	} else {
		c, msg := apiCode(err)
		ok("负例:错误签名被拒", c == 100104 || c == 100000, fmt.Sprintf("code=%d %s", c, msg))
	}

	// 9. 回调验签自证
	if orderNo == "" {
		orderNo = "P_demo"
	}
	cb := map[string]any{"merchant_no": mno, "order_no": orderNo, "out_order_no": outOrderNo, "amount": 10000, "currency": "PHP", "status": "success", "paid_at": "2026-06-23T08:00:00+08:00"}
	cb["sign"] = openapi.Sign(cb, pay)
	ok("回调验签 正例", client.VerifyPayCallback(cb), "")
	tampered := map[string]any{}
	for k, v := range cb {
		tampered[k] = v
	}
	tampered["amount"] = 10001
	ok("回调验签 反例(篡改amount)", !client.VerifyPayCallback(tampered), "")

	fmt.Printf("\n[Go] 结果: %d 通过, %d 失败\n", pass, fail)
	if fail > 0 {
		os.Exit(1)
	}
}
