// SPDX-License-Identifier: MIT

package openapi

import (
	"encoding/json"
	"math"
	"reflect"
	"testing"
)

func TestSigningScalarTypesAndInvalidValues(t *testing.T) {
	cases := []struct {
		value any
		want  string
	}{
		{"x", "x"}, {true, "true"}, {int(1), "1"}, {int8(-2), "-2"}, {int16(-3), "-3"},
		{int32(-4), "-4"}, {int64(-5), "-5"}, {uint(6), "6"}, {uint8(7), "7"},
		{uint16(8), "8"}, {uint32(9), "9"}, {uint64(10), "10"},
		{json.Number("12345678901234567890"), "12345678901234567890"}, {float32(42), "42"}, {float64(0), "0"},
	}
	for _, tc := range cases {
		if got := scalarToString(tc.value); got != tc.want {
			t.Errorf("%T: got %q want %q", tc.value, got, tc.want)
		}
	}
	for _, value := range []any{math.NaN(), math.Inf(1), 1.5, json.Number("1e3"), make(chan int)} {
		t.Run(reflect.TypeOf(value).String(), func(t *testing.T) {
			defer func() {
				if recover() == nil {
					t.Errorf("非法值 %v 未拒绝", value)
				}
			}()
			_ = scalarToString(value)
		})
	}
}

func TestSigningTypedContainersAndPointers(t *testing.T) {
	type nested struct {
		Count    int    `json:"count"`
		Hidden   string `json:"-"`
		Optional string `json:"optional,omitempty"`
	}
	item := nested{Count: 3, Hidden: "private"}
	data := map[string]any{"items": []any{&item, (*nested)(nil)}, "integer_keys": map[int]string{7: "seven"}}
	base := BuildSignBase(data, "secret")
	want := `integer_keys={"7":"seven"}&items=[{"count":3},null]&secret=secret`
	if base != want {
		t.Fatalf("签名原文: got %s want %s", base, want)
	}
	var empty any
	if got := reflectToSignable(reflect.ValueOf(&empty)); got != nil {
		t.Fatalf("空指针值: %v", got)
	}
}
