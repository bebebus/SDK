package appendix

import "testing"

func TestExampleConfigFields(t *testing.T) {
	keys := []string{
		"merchant_no",
		"api_key",
		"api_secret_pay",
		"api_secret_payout",
		"base_url",
		"base_url_payout",
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
