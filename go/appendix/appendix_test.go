package appendix

import "testing"

func TestExampleConfigFields(t *testing.T) {
	keys := []string{
		"merchant_no",
		"api_key",
		"api_secret_pay",
		"api_secret_payout",
		"base_url",
		"timeout_ms",
	}
	for _, key := range keys {
		if _, ok := ExampleConfig[key]; !ok {
			t.Fatalf("missing %s", key)
		}
	}
	baseURL, _ := ExampleConfig["base_url"].(string)
	if len(baseURL) < 8 || baseURL[:8] != "https://" {
		t.Fatalf("base_url must be https: %v", ExampleConfig["base_url"])
	}
}

func TestCatalogHasPHAndUSDT(t *testing.T) {
	foundPH := false
	for _, c := range Countries {
		if c.Code != "PH" {
			continue
		}
		foundPH = true
		hasPHP := false
		for _, cur := range c.Currencies {
			if cur == "PHP" {
				hasPHP = true
			}
		}
		if !hasPHP {
			t.Fatal("PH missing PHP")
		}
	}
	if !foundPH {
		t.Fatal("missing PH")
	}
	foundUSDT := false
	for _, c := range Currencies {
		if c.Code != "USDT" {
			continue
		}
		foundUSDT = true
		if c.Kind != "crypto" {
			t.Fatalf("USDT kind=%s", c.Kind)
		}
	}
	if !foundUSDT {
		t.Fatal("missing USDT")
	}
}

func TestErrorCodes(t *testing.T) {
	if len(ErrorCodes) == 0 {
		t.Fatal("empty ErrorCodes")
	}
	// 300304（建单暂不可用，insert-first fail-closed）是当前唯一约定非 200 的错误码，固定 HTTP 503。
	knownNon200 := map[string]int{"300304": 503}
	found := false
	for _, e := range ErrorCodes {
		want := 200
		if v, ok := knownNon200[e.Code]; ok {
			want = v
		}
		if e.HTTP != want {
			t.Fatalf("%s http=%d, want %d", e.Code, e.HTTP, want)
		}
		if e.Code == "100000" {
			found = true
			if e.Retryable != "depends" {
				t.Fatalf("100000 retryable=%s", e.Retryable)
			}
		}
	}
	if !found {
		t.Fatal("missing 100000")
	}
}
