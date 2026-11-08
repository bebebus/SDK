// SPDX-License-Identifier: MIT

package openapi

import "crypto/subtle"

const (
	// PayoutPrecheckApprove 是商户反查通过时要原样写进 HTTP 200 正文的 ASCII 文本。
	PayoutPrecheckApprove = "approve"
	// PayoutPrecheckReject 是任一字段不匹配或输入非法时的正文。平台把除此以外的响应都视为拒绝。
	PayoutPrecheckReject = "reject"
)

// DecidePayoutPrecheck 判定商户代付反查的响应正文。平台 POST 到商户自己的 HTTPS 接口，本函数不发请求。
//
// body 必须恰好包含四个非空字符串：account_no、out_payout_no、original_sign、payout_no。
// saved 提供下单时保存的 account_no、out_payout_no 和 sign。original_sign 与 sign 按 UTF-8 字节等长比较。
// payout_no 必须出现且非空，不与下单请求比较。接受 map[string]string 或 map[string]any。
// 非法输入返回 reject，不 panic。
func DecidePayoutPrecheck(body any, saved any) (text string) {
	defer func() {
		if recover() != nil {
			text = PayoutPrecheckReject
		}
	}()

	bodyFields, ok := exactPrecheckBody(body)
	if !ok {
		return PayoutPrecheckReject
	}
	savedAccount, savedOut, savedSign, ok := precheckSaved(saved)
	if !ok || bodyFields["account_no"] != savedAccount || bodyFields["out_payout_no"] != savedOut {
		return PayoutPrecheckReject
	}
	actual := []byte(bodyFields["original_sign"])
	expected := []byte(savedSign)
	if len(actual) == len(expected) && subtle.ConstantTimeCompare(actual, expected) == 1 {
		return PayoutPrecheckApprove
	}
	return PayoutPrecheckReject
}

func exactPrecheckBody(value any) (map[string]string, bool) {
	const account = "account_no"
	const outNo = "out_payout_no"
	const original = "original_sign"
	const payoutNo = "payout_no"
	switch m := value.(type) {
	case map[string]string:
		if len(m) != 4 {
			return nil, false
		}
		out := map[string]string{
			account: m[account], outNo: m[outNo], original: m[original], payoutNo: m[payoutNo],
		}
		for _, key := range []string{account, outNo, original, payoutNo} {
			if _, present := m[key]; !present || out[key] == "" {
				return nil, false
			}
		}
		return out, true
	case map[string]any:
		if len(m) != 4 {
			return nil, false
		}
		out := make(map[string]string, 4)
		for _, key := range []string{account, outNo, original, payoutNo} {
			text, isString := m[key].(string)
			if !isString || text == "" {
				return nil, false
			}
			out[key] = text
		}
		return out, true
	default:
		return nil, false
	}
}

func precheckSaved(value any) (account, outNo, sign string, ok bool) {
	switch m := value.(type) {
	case map[string]string:
		account, outNo, sign = m["account_no"], m["out_payout_no"], m["sign"]
	case map[string]any:
		var accountOK, outOK, signOK bool
		account, accountOK = m["account_no"].(string)
		outNo, outOK = m["out_payout_no"].(string)
		sign, signOK = m["sign"].(string)
		if !accountOK || !outOK || !signOK {
			return "", "", "", false
		}
	default:
		return "", "", "", false
	}
	if account == "" || outNo == "" || sign == "" {
		return "", "", "", false
	}
	return account, outNo, sign, true
}
