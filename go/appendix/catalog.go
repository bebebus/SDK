package appendix

// Country 是国家 ISO 附录项。
type Country struct {
	Code       string
	NameZh     string
	NameEn     string
	Currencies []string
}

// Currency 是币种附录项。
type Currency struct {
	Code   string
	NameZh string
	NameEn string
	Kind   string
}

// Countries 国家 ISO 附录（平台标准项）。
var Countries = []Country{
	{Code: "PH", NameZh: "菲律宾", NameEn: "Philippines", Currencies: []string{"PHP"}},
	{Code: "ID", NameZh: "印度尼西亚", NameEn: "Indonesia", Currencies: []string{"IDR"}},
	{Code: "IN", NameZh: "印度", NameEn: "India", Currencies: []string{"INR"}},
	{Code: "BD", NameZh: "孟加拉国", NameEn: "Bangladesh", Currencies: []string{"BDT"}},
	{Code: "VN", NameZh: "越南", NameEn: "Vietnam", Currencies: []string{"VND"}},
	{Code: "TH", NameZh: "泰国", NameEn: "Thailand", Currencies: []string{"THB"}},
	{Code: "MM", NameZh: "缅甸", NameEn: "Myanmar", Currencies: []string{"MMK"}},
	{Code: "MY", NameZh: "马来西亚", NameEn: "Malaysia", Currencies: []string{"MYR"}},
	{Code: "BR", NameZh: "巴西", NameEn: "Brazil", Currencies: []string{"BRL"}},
	{Code: "MX", NameZh: "墨西哥", NameEn: "Mexico", Currencies: []string{"MXN"}},
	{Code: "CN", NameZh: "中国", NameEn: "China", Currencies: []string{"CNY"}},
}

// Currencies 币种附录（平台标准项）。某组合是否可下单以 pay-methods/query 为准。
var Currencies = []Currency{
	{Code: "PHP", NameZh: "菲律宾比索", NameEn: "Philippine peso", Kind: "fiat"},
	{Code: "IDR", NameZh: "印尼盾", NameEn: "Indonesian rupiah", Kind: "fiat"},
	{Code: "INR", NameZh: "印度卢比", NameEn: "Indian rupee", Kind: "fiat"},
	{Code: "BDT", NameZh: "孟加拉塔卡", NameEn: "Bangladeshi taka", Kind: "fiat"},
	{Code: "VND", NameZh: "越南盾", NameEn: "Vietnamese dong", Kind: "fiat"},
	{Code: "THB", NameZh: "泰铢", NameEn: "Thai baht", Kind: "fiat"},
	{Code: "MMK", NameZh: "缅元", NameEn: "Myanmar kyat", Kind: "fiat"},
	{Code: "MYR", NameZh: "马来西亚林吉特", NameEn: "Malaysian ringgit", Kind: "fiat"},
	{Code: "BRL", NameZh: "巴西雷亚尔", NameEn: "Brazilian real", Kind: "fiat"},
	{Code: "MXN", NameZh: "墨西哥比索", NameEn: "Mexican peso", Kind: "fiat"},
	{Code: "CNY", NameZh: "人民币", NameEn: "Chinese yuan", Kind: "fiat"},
	{Code: "USDT", NameZh: "泰达币", NameEn: "Tether", Kind: "crypto"},
}
