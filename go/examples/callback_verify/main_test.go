// SPDX-License-Identifier: MIT

package main

import (
	"encoding/json"
	"net/http"
	"testing"
)

func TestProcessCallbackRequiresSingleJSONObject(t *testing.T) {
	for _, input := range []string{"null", "[]", `{} {}`, `{}junk`, `{"status":`} {
		called := false
		status, _ := processCallback(func(map[string]any) bool { called = true; return true }, []byte(input))
		if status != http.StatusBadRequest || called {
			t.Errorf("非法输入 %q 应先拒绝: status=%d verify=%v", input, status, called)
		}
	}
	status, reply := processCallback(func(payload map[string]any) bool {
		value, ok := payload["amount"].(json.Number)
		return ok && value.String() == "9007199254740993"
	}, []byte(`{"status":"success","amount":9007199254740993}  `))
	if status != http.StatusOK || reply != "success" {
		t.Fatalf("有效回调: %d %q", status, reply)
	}
	status, _ = processCallback(func(map[string]any) bool { return false }, []byte(`{"status":"success"}`))
	if status != http.StatusForbidden {
		t.Fatalf("验签失败应拒绝: %d", status)
	}
}
