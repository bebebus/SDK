// SPDX-License-Identifier: MIT

package openapi

import (
	"context"
	"errors"
	"strings"
	"testing"
)

func TestErrorDetailsAndTransportFailures(t *testing.T) {
	cause := errors.New("network unavailable")
	for _, status := range []int{0, 503} {
		err := &TransportError{Op: "request", StatusCode: status, Err: cause}
		if !errors.Is(err, cause) || !strings.Contains(err.Error(), "network unavailable") {
			t.Fatalf("transport 错误信息或原因丢失: %v", err)
		}
	}
	if !strings.Contains((&APIError{Code: 42, Message: "declined"}).Error(), "42") {
		t.Fatal("业务错误应包含服务端代码")
	}
	c := NewClient(Config{Environment: Sandbox})
	for _, tc := range []struct {
		base      string
		body      map[string]any
		operation string
	}{
		{"http://127.0.0.1", map[string]any{"bad": make(chan int)}, "marshal"},
		{"://invalid", map[string]any{}, "request"},
	} {
		_, _, err := c.do(context.Background(), tc.base, "/x", tc.body)
		var transport *TransportError
		if !errors.As(err, &transport) || transport.Op != tc.operation {
			t.Fatalf("期望 %s 错误，实际 %v", tc.operation, err)
		}
	}
}
