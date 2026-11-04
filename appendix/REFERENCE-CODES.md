<!-- 由 node _tooling/reference-codes.mjs 生成，请修改 reference-codes.json 后重新生成。 -->
# 国家、币种、银行代码参考表

更新日期：2026-11-05。共 12 个国家/地区、13 个币种、426 个银行/机构代码。

本表记录项目标准字典与区域码册，包含预登记、历史银行和钱包机构，不表示商户已获授权或当前可用。国家/地区以 `countries/query` 返回为准；下单组合以 `pay-methods/query` 返回为准；代付银行必须从 `payout/banks/query` 获取。调用方法与鉴权见 [接口文档](../INTERFACES.md)。

## 下载与字段约定

- [完整 JSON](./reference-codes.json)：唯一维护源；`source` 保存源项目提交和文件 SHA-256，银行项的 `source` 为源文件路径。
- CSV：[国家](./countries.csv)、[币种](./currencies.csv)、[银行代码](./banks.csv)。UTF-8 BOM、CRLF；国家的多币种以分号分隔；疑似公式单元格加单引号。
- `country` 使用 ISO 3166-1 alpha-2；`currency` 使用 ISO 4217，USDT 为平台加密货币例外。所有币种金额均按实际金额 × 10000 传整数。
- 银行唯一键为 `(country, bank_code)`，不能仅按银行代码跨国家合并；`bank_code` 是平台助记码，保留原始大小写，不是 SWIFT、清算行号或上游私有码。
- `bank_name` 沿用源码册机构名，可能含英文或繁体字。HK 的 FPS 是转账轨道，排除在银行清单外。PH 码册内的 USDTTRC / USDTERC 为历史目的地代码，不代表币种或新支付方式。
- MAYA / MAYABANK / MAYABUSINESS、KBZPAY / KBZ、CBPAY / CB、AYAPAY / AYA 等为不同资金目的地，不应互换。
- 未列出银行的国家表示当前源码册未收录，不能据此判断商户不可用。香港为区域扩展项，部署不一定预置；其他国家来自平台基线。

## 国家/地区

| country | 名称 | 英文名称 | currency |
| --- | --- | --- | --- |
| PH | 菲律宾 | Philippines | PHP |
| ID | 印度尼西亚 | Indonesia | IDR |
| IN | 印度 | India | INR |
| BD | 孟加拉国 | Bangladesh | BDT |
| VN | 越南 | Vietnam | VND |
| TH | 泰国 | Thailand | THB |
| MM | 缅甸 | Myanmar | MMK |
| MY | 马来西亚 | Malaysia | MYR |
| BR | 巴西 | Brazil | BRL |
| MX | 墨西哥 | Mexico | MXN |
| CN | 中国 | China | CNY |
| HK | 中国香港 | Hong Kong | HKD |

## 币种

| currency | 名称 | 英文名称 | 类型 |
| --- | --- | --- | --- |
| PHP | 菲律宾比索 | Philippine peso | fiat |
| IDR | 印尼盾 | Indonesian rupiah | fiat |
| INR | 印度卢比 | Indian rupee | fiat |
| BDT | 孟加拉塔卡 | Bangladeshi taka | fiat |
| VND | 越南盾 | Vietnamese dong | fiat |
| THB | 泰铢 | Thai baht | fiat |
| MMK | 缅元 | Myanmar kyat | fiat |
| MYR | 马来西亚林吉特 | Malaysian ringgit | fiat |
| BRL | 巴西雷亚尔 | Brazilian real | fiat |
| MXN | 墨西哥比索 | Mexican peso | fiat |
| CNY | 人民币 | Chinese yuan | fiat |
| USDT | 泰达币 | Tether | crypto |
| HKD | 港币 | Hong Kong dollar | fiat |

## 银行/机构代码

### 菲律宾（PH，110 项）

| country | bank_code | bank_name |
| --- | --- | --- |
| PH | ALLBANK | ALLBANK (A Thrift Bank) |
| PH | ANZ | ANZ Banking Group Ltd |
| PH | ASENSO | Rural Bank of Guinobatan / Asenso |
| PH | AUB | Asia United Bank Corporation |
| PH | BANANAPAY | BananaPay |
| PH | BANGKOMABUHAY | Bangko Mabuhay |
| PH | BANKO | BanKo, A Subsidiary of BPI |
| PH | BANKOFCHINA | Bank of China |
| PH | BANKOFCOMMERCE | Bank of Commerce |
| PH | BAYAD | CIS Bayad Center / Bayad |
| PH | BDO | BDO Unibank |
| PH | BDONETWORK | BDO Network Bank |
| PH | BIZMOTO | Bizmoto Wallet |
| PH | BPI | BPI / BPI Family Savings Bank |
| PH | BRBDIGITAL | Binangonan Rural Bank / BRBDigital |
| PH | CAMALIGBANK | Camalig Bank |
| PH | CANTILANBANK | Cantilan Bank |
| PH | CARDBANK | Card Bank |
| PH | CARDMRIRBI | CARD MRI RBI |
| PH | CARDSMEBANK | CARD SME Bank |
| PH | CEBUANA | Cebuana Lhuillier Bank / Cebuana Xpress |
| PH | CHINABANK | China Banking Corporation |
| PH | CHINABANKSAVINGS | China Bank Savings |
| PH | CIMB | CIMB Philippines, Inc. |
| PH | CITYSAVINGS | City Savings Bank |
| PH | CITYSTATE | Citystate Savings Bank |
| PH | COINSPH | DCPay / COINS.PH |
| PH | CTBC | CTBC Bank (Philippines) Corporation |
| PH | DBP | Development Bank of the Philippines |
| PH | DUMAGUETEBANK | Dumaguete City Development Bank |
| PH | DUNGGANONBANK | Dungganon Bank (A Microfinance Rural Bank), Inc. |
| PH | EASTWESTBANK | East West Banking Corporation |
| PH | EASYPAYGLOBAL | Easy Pay Global EMI Corp |
| PH | ENTRP | Entrepreneur Rural Bank, Inc. / ENTRP |
| PH | EQUICOM | Equicom Savings Bank, Inc. |
| PH | GCASH | GCash |
| PH | GOTYME | GoTyme Bank |
| PH | GRABPAY | GrabPay |
| PH | HSBC | HSBC |
| PH | INGBANK | ING Bank N.V. |
| PH | IREMIT | I-Remit / iCASH |
| PH | ISLABANK | ISLA Bank |
| PH | JUANCASH | Zybi Tech Inc. / JuanCash |
| PH | KOMO | East West Rural Bank / Komo |
| PH | LANDBANK | LANDBANK / OFBank |
| PH | LAZADAWALLET | Alipay / Lazada Wallet |
| PH | LEGAZPISAVINGS | Legazpi Savings Bank |
| PH | LULUMONEY | Lulu Money |
| PH | LUZONDEV | Luzon Development Bank |
| PH | MALAYANBANK | Malayan Bank Savings and Mortgage Bank, Inc. |
| PH | MARCOPAY | MARCOPAY |
| PH | MAYA | PayMaya Philippines / Maya Wallet |
| PH | MAYABANK | Maya Bank, Inc. |
| PH | MAYABUSINESS | Maya Business |
| PH | MAYBANK | Maybank Philippines |
| PH | METROBANK | Metropolitan Bank and Trust Co |
| PH | MINDANAOCOOP | Mindanao Consolidated CoopBank |
| PH | MONEYMALL | Money Mall Rural Bank, Inc. |
| PH | NATIONLINK | Infoserve / Nationlink |
| PH | NETBANK | Netbank |
| PH | OMNIPAY | OmniPay, Inc. |
| PH | OWNBANK | Own Bank |
| PH | PACIFICACE | Pacific Ace Savings Bank |
| PH | PALAWANPAY | PalawanPay |
| PH | PARTNERRURAL | Partner Rural Bank (Cotabato), Inc. |
| PH | PAYMONGO | PayMongo |
| PH | PAYNAMICS | Paynamics Technologies, Inc |
| PH | PBB | Philippine Business Bank |
| PH | PBCOM | Philippine Bank of Communications |
| PH | PDAX | Philippine Digital Asset Exchange, Inc / PDAX |
| PH | PHILTRUST | PhilTrust Bank |
| PH | PHILTRUSTCO | Philippine Trust Company |
| PH | PNB | Philippine National Bank |
| PH | PNBSAVINGS | PNB Savings Bank |
| PH | PRODUCERSBANK | Producers Bank |
| PH | PSBANK | Philippine Savings Bank |
| PH | QUEENBANK | Queenbank |
| PH | QUEZONCAPITAL | Quezon Capital Rural Bank |
| PH | RANGAYBANK | Rang-Ay Bank |
| PH | RBAPALIT | Rural Bank of Apalit, Inc. |
| PH | RCBC | RCBC / DiskarTech |
| PH | RCBCSAVINGS | RCBC Saving Bank |
| PH | ROBINSONSBANK | Robinsons Bank Corporation |
| PH | SEABANK | MariBank Philippines (formerly Seabank PH) |
| PH | SECURITYBANK | Security Bank Corporation |
| PH | SHOPEEPAY | ShopeePay |
| PH | SPEEDYPAY | SpeedyPay / eMango Pay |
| PH | STANDARDCHARTERED | Standard Chartered Bank |
| PH | STARPAY | Starpay |
| PH | STERLINGBANK | Sterling Bank of Asia, Inc (A Savings Bank) |
| PH | SUNSAVINGS | Sun Savings Bank |
| PH | TAYOCASH | TayoCash |
| PH | TOKTOKWALLET | TOKTOKWALLET |
| PH | TONIKBANK | Tonik Bank |
| PH | TOPJUAN | Topjuan Tech Corporation |
| PH | TRAXIONPAY | TraxionPay / DigiCOOP / COOPNET |
| PH | UCPB | United Coconut Planters Bank (UCPB) |
| PH | UCPBSAVINGS | UCPB Savings Bank |
| PH | UNIONBANK | Union Bank of the Philippines |
| PH | UNIONBANKBA | UnionBank BA |
| PH | UNIONDIGITAL | UnionDigital Bank |
| PH | UNOBANK | UNObank |
| PH | USDTERC | USDT (ERC) |
| PH | USDTTRC | USDT (TRC) |
| PH | USSC | USSC Money Services |
| PH | VBANK | VBank |
| PH | VETERANSBANK | Veterans Bank |
| PH | WEALTHBANK | Wealth Development Bank |
| PH | WISEPILIPINAS | Wise Pilipinas Inc. |
| PH | YUANTABANK | Yuanta Bank |

### 孟加拉国（BD，65 项）

| country | bank_code | bank_name |
| --- | --- | --- |
| BD | ABBANK | AB Bank PLC |
| BD | AGRANI | Agrani Bank PLC |
| BD | ALARAFAH | Al-Arafah Islami Bank PLC |
| BD | BANKALFALAH | Bank Alfalah Limited |
| BD | BANKASIA | Bank Asia PLC |
| BD | BASIC | BASIC Bank PLC |
| BD | BDBL | Bangladesh Development Bank PLC |
| BD | BENGALCOMMERCIAL | Bengal Commercial Bank PLC |
| BD | BKASH | bKash |
| BD | BKB | Bangladesh Krishi Bank |
| BD | BRACBANK | BRAC Bank PLC |
| BD | CITI | Citibank N.A. Bangladesh |
| BD | CITIZENSBANK | Citizens Bank PLC |
| BD | CITYBANK | The City Bank PLC |
| BD | COMMERCIALCEYLON | Commercial Bank of Ceylon PLC |
| BD | COMMUNITYBANK | Community Bank Bangladesh PLC |
| BD | DHAKABANK | Dhaka Bank PLC |
| BD | DUTCHBANGLA | Dutch-Bangla Bank PLC |
| BD | EASTERNBANK | Eastern Bank PLC (EBL) |
| BD | EXIMBANK | EXIM Bank PLC |
| BD | FIRSTSECURITY | First Security Islami Bank PLC |
| BD | GLOBALISLAMI | Global Islami Bank PLC |
| BD | HABIB | Habib Bank Limited |
| BD | HSBC | HSBC Bangladesh |
| BD | IFICBANK | IFIC Bank PLC |
| BD | ISLAMIBANK | Islami Bank Bangladesh PLC |
| BD | JAMUNABANK | Jamuna Bank PLC |
| BD | JANATA | Janata Bank PLC |
| BD | MCASH | mCash (Islami Bank) |
| BD | MEGHNABANK | Meghna Bank PLC |
| BD | MERCANTILEBANK | Mercantile Bank PLC |
| BD | MIDLANDBANK | Midland Bank PLC |
| BD | MODHUMOTIBANK | Modhumoti Bank PLC |
| BD | MTB | Mutual Trust Bank PLC |
| BD | NAGAD | Nagad |
| BD | NATIONALBANK | National Bank Limited |
| BD | NBP | National Bank of Pakistan |
| BD | NCCBANK | NCC Bank PLC |
| BD | NRBBANK | NRB Bank PLC |
| BD | NRBCBANK | NRB Commercial Bank PLC |
| BD | OKWALLET | OK Wallet (ONE Bank) |
| BD | ONEBANK | ONE Bank PLC |
| BD | PADMABANK | Padma Bank PLC |
| BD | PREMIERBANK | The Premier Bank PLC |
| BD | PRIMEBANK | Prime Bank PLC |
| BD | PROBASHIKALLYAN | Probashi Kallyan Bank |
| BD | PUBALIBANK | Pubali Bank PLC |
| BD | RAKUB | Rajshahi Krishi Unnayan Bank |
| BD | ROCKET | Rocket (Dutch-Bangla Bank Mobile Banking) |
| BD | RUPALI | Rupali Bank PLC |
| BD | SBI | State Bank of India |
| BD | SCB | Standard Chartered Bank (Bangladesh) |
| BD | SHAHJALAL | Shahjalal Islami Bank PLC |
| BD | SIBL | Social Islami Bank PLC |
| BD | SONALI | Sonali Bank PLC |
| BD | SOUTHEASTBANK | Southeast Bank PLC |
| BD | STANDARDBANK | Standard Bank PLC |
| BD | SURECASH | SureCash |
| BD | TAP | Tap (Trust Axiata Pay) |
| BD | TRUSTBANK | Trust Bank PLC |
| BD | UCB | United Commercial Bank PLC |
| BD | UNIONBANK | Union Bank PLC |
| BD | UPAY | Upay (UCB Fintech) |
| BD | UTTARABANK | Uttara Bank PLC |
| BD | WOORI | Woori Bank |

### 越南（VN，44 项）

| country | bank_code | bank_name |
| --- | --- | --- |
| VN | ABBVN | An Binh Commercial Joint Stock Bank (ABBANK) |
| VN | ACB | Asia Commercial Bank (ACB) |
| VN | AGRIBANK | Vietnam Bank for Agriculture and Rural Development (Agribank) |
| VN | BAB | Bac A Commercial Joint Stock Bank |
| VN | BIDV | Bank for Investment and Development of Vietnam (BIDV) |
| VN | BVB | BaoViet Bank |
| VN | CAKE | CAKE by VPBank |
| VN | COOPBANK | Co-operative Bank of Vietnam |
| VN | DONGABANK | DongA Bank |
| VN | EXIMVN | Vietnam Export Import Commercial Joint Stock Bank (Eximbank) |
| VN | GPB | GPBank |
| VN | HDB | HDBank |
| VN | HONGLEONGVN | Hong Leong Bank Vietnam |
| VN | IBKHCM | Industrial Bank of Korea, Ho Chi Minh City Branch |
| VN | INDOVINA | Indovina Bank |
| VN | KIENLONG | Kien Long Commercial Joint Stock Bank |
| VN | LIOBANK | Digital Bank Lio (OCB) |
| VN | LPB | LienVietPostBank |
| VN | MB | Military Commercial Joint Stock Bank (MB) |
| VN | MSB | Vietnam Maritime Commercial Joint Stock Bank (MSB) |
| VN | NAMABANK | Nam A Commercial Joint Stock Bank |
| VN | NCBVN | National Citizen Commercial Joint Stock Bank (NCB) |
| VN | OCB | Orient Commercial Joint Stock Bank (OCB) |
| VN | OCEANBANK | OceanBank |
| VN | PBVN | Public Bank Vietnam |
| VN | PGBANK | Petrolimex Group Commercial Joint Stock Bank (PG Bank) |
| VN | SACOMBANK | Sacombank |
| VN | SAIGONBANK | Saigon Bank for Industry and Trade |
| VN | SCBVN | Saigon Commercial Bank (SCB) |
| VN | SEAVN | Southeast Asia Commercial Joint Stock Bank (SeABank) |
| VN | SHB | Saigon-Hanoi Commercial Joint Stock Bank (SHB) |
| VN | SHBVN | Shinhan Bank Vietnam |
| VN | TECHCOMBANK | Techcombank |
| VN | TPBANK | Tien Phong Commercial Joint Stock Bank (TPBank) |
| VN | UOBVN | United Overseas Bank Vietnam |
| VN | VAB | VietA Bank |
| VN | VIB | Vietnam International Commercial Joint Stock Bank (VIB) |
| VN | VIETBANK | Vietnam Thuong Tin Commercial Joint Stock Bank (VietBank) |
| VN | VIETCAPITAL | VietCapital Bank (Ban Viet) |
| VN | VIETCOMBANK | Joint Stock Commercial Bank for Foreign Trade of Vietnam (Vietcombank) |
| VN | VIETINBANK | Vietnam Joint Stock Commercial Bank for Industry and Trade (VietinBank) |
| VN | VPBANK | Vietnam Prosperity Joint Stock Commercial Bank (VPBank) |
| VN | VRB | Vietnam-Russia Joint Venture Bank |
| VN | WOORIVN | Woori Bank Vietnam |

### 缅甸（MM，36 项）

| country | bank_code | bank_name |
| --- | --- | --- |
| MM | ABANK | A Bank (Ayeyarwady Farmers Development Bank) |
| MM | AGD | Asia Green Development Bank |
| MM | AYA | AYA Bank (Ayeyarwady Bank) |
| MM | AYAPAY | AYA Pay (AYA Bank) |
| MM | CB | CB Bank (Co-operative Bank) |
| MM | CBPAY | CB Pay (CB Bank) |
| MM | CHDB | Construction and Housing Development Bank |
| MM | CITIZENSPAY | Citizens Pay |
| MM | FPB | First Private Bank |
| MM | GTB | Global Treasure Bank |
| MM | INNWA | Innwa Bank |
| MM | KBZ | KBZ Bank (Kanbawza Bank) |
| MM | KBZPAY | KBZPay (KBZ Bank) |
| MM | MAB | Myanma Apex Bank |
| MM | MADB | Myanmar Agricultural Development Bank |
| MM | MCB | Myanmar Citizens Bank |
| MM | MEB | Myanma Economic Bank |
| MM | MFTB | Myanma Foreign Trade Bank |
| MM | MICB | Myanma Investment and Commercial Bank |
| MM | MMTB | Myanmar Tourism Bank |
| MM | MOB | Myanmar Oriental Bank |
| MM | MPITESAN | M-Pitesan (Ooredoo) |
| MM | MPU | Myanmar Payment Union (MPU card) |
| MM | MYTELPAY | Mytel Pay |
| MM | OKDOLLAR | OK Dollar |
| MM | ONEPAY | OnePay |
| MM | RDB | Rural Development Bank |
| MM | SHWE | Shwe Bank (Shwe Rural and Urban Development Bank) |
| MM | SMIDB | Small and Medium Industrial Development Bank |
| MM | TFB | Tun Foundation Bank |
| MM | TRUEMONEY | TrueMoney Myanmar |
| MM | UAB | UAB Bank (United Amara Bank) |
| MM | UABPAY | UAB Pay (UAB Bank) |
| MM | WAVEPAY | WavePay / Wave Money |
| MM | YADANABON | Yadanabon Bank |
| MM | YOMA | Yoma Bank |

### 中国（CN，16 项）

| country | bank_code | bank_name |
| --- | --- | --- |
| CN | ABC | 中国农业银行 |
| CN | BOB | 北京银行 |
| CN | BOC | 中国银行 |
| CN | BOCOM | 交通银行 |
| CN | CCB | 中国建设银行 |
| CN | CEB | 中国光大银行 |
| CN | CIB | 兴业银行 |
| CN | CITIC | 中信银行 |
| CN | CMB | 招商银行 |
| CN | CMBC | 中国民生银行 |
| CN | GDB | 广发银行 |
| CN | HXB | 华夏银行 |
| CN | ICBC | 中国工商银行 |
| CN | PAB | 平安银行 |
| CN | PSBC | 中国邮政储蓄银行 |
| CN | SPDB | 浦发银行 |

### 中国香港（HK，155 项）

| country | bank_code | bank_name |
| --- | --- | --- |
| HK | ABC | 中國農業銀行股份有限公司 |
| HK | AIRSTAR | 天星銀行有限公司 |
| HK | ANTBANK | 螞蟻銀行(香港)有限公司 |
| HK | ANZ | 澳新銀行集團有限公司 |
| HK | BANGKOK | 盤谷銀行 |
| HK | BARCLAYS | Barclays Bank Plc. |
| HK | BBVA | 西班牙對外銀行 |
| HK | BDO | 金融銀行有限公司 |
| HK | BEA | 東亞銀行有限公司 |
| HK | BMO | 滿地可銀行 |
| HK | BNI | 印尼國家銀行 |
| HK | BNP | 法國巴黎銀行 |
| HK | BNYMELLON | 紐約梅隆銀行有限公司 |
| HK | BOC | 中國銀行香港分行 |
| HK | BOCHK | 中國銀行（香港）有限公司 |
| HK | BOCOM | 交通銀行股份有限公司 |
| HK | BOCOMHK | 交通銀行(香港)有限公司 |
| HK | BOFA | 美國銀行 |
| HK | BOI | 印度銀行 |
| HK | BOS | 新加坡銀行有限公司 |
| HK | BOSHK | 上海銀行(香港)有限公司 |
| HK | BOT | 臺灣銀行股份有限公司 |
| HK | CACIB | 東方匯理銀行 |
| HK | CATHAYBANK | 國泰銀行 |
| HK | CATHAYUNITED | 國泰世華商業銀行股份有限公司 |
| HK | CBA | 澳洲聯邦銀行 |
| HK | CBHB | 渤海银行股份有限公司 |
| HK | CCB | 中國建設銀行股份有限公司 |
| HK | CCBASIA | 中國建設銀行（亞洲）股份有限公司 |
| HK | CDB | 國家開發銀行 |
| HK | CEB | 中國光大銀行股份有限公司 |
| HK | CHANGHWA | 彰化商業銀行股份有限公司 |
| HK | CHIBA | 千葉銀行 |
| HK | CHIYU | 集友銀行有限公司 |
| HK | CHONGHING | 創興銀行有限公司 |
| HK | CHUGOKU | The Chugoku Bank Limited |
| HK | CIB | 興業銀行股份有限公司 |
| HK | CIBC | 加拿大帝國商業銀行 |
| HK | CIC | Credit Industriel et Commercial |
| HK | CIMB | CIMB Bank Berhad |
| HK | CITIBANK | 花旗銀行 |
| HK | CITIC | 中信銀行股份有限公司 |
| HK | CITIHK | 花旗銀行(香港)有限公司 |
| HK | CLS | CLS Bank International |
| HK | CMB | 招商銀行股份有限公司 |
| HK | CMBC | 中國民生銀行股份有限公司 |
| HK | CMBWLB | 招商永隆銀行有限公司 |
| HK | CNCBI | 中信銀行國際有限公司 |
| HK | CREDITSUISSE | Credit Suisse AG |
| HK | CTBC | 中國信託商業銀行股份有限公司 |
| HK | CZB | 浙商銀行股份有限公司 |
| HK | DAHSING | 大新銀行有限公司 |
| HK | DBS | 星展銀行（香港）有限公司 |
| HK | DBSSG | 星展銀行香港分行 |
| HK | DEUTSCHE | 德意志銀行 |
| HK | DONGGUAN | 東莞銀行股份有限公司 |
| HK | DZBANK | 德國中央合作銀行 |
| HK | EASTWEST | 華美銀行 |
| HK | EFG | 瑞士盈豐銀行股份有限公司 |
| HK | ERSTE | Erste Group Bank AG |
| HK | ESUN | 玉山商業銀行股份有限公司 |
| HK | FAB | First Abu Dhabi Bank PJSC |
| HK | FEIB | 逺東國際商業銀行股份有限公司 |
| HK | FIRSTBANK | 第一商業銀行股份有限公司 |
| HK | FUBONHK | 富邦銀行(香港)有限公司 |
| HK | FUSION | 富融銀行有限公司 |
| HK | GDB | 廣發銀行股份有限公司 |
| HK | HANGSENG | 恒生銀行有限公司 |
| HK | HDFC | HDFC Bank Limited |
| HK | HKMACMU | 香港金融管理局-CMU Digital |
| HK | HKMACTB | 香港金融管理局-CTB A |
| HK | HKMAMBRIDGE | 香港金融管理局-mBridge |
| HK | HKSCC | Hong Kong Securities Clearing Company Limited |
| HK | HONGLEONG | 豐隆銀行有限公司 |
| HK | HSBC | 香港上海滙豐銀行有限公司 |
| HK | HSBCUS | 美國滙豐銀行 |
| HK | HUANAN | 華南商業銀行股份有限公司 |
| HK | HXB | 華夏銀行股份有限公司 |
| HK | IBK | Industrial Bank of Korea |
| HK | ICBC | 中國工商銀行股份有限公司 |
| HK | ICBCASIA | 中國工商銀行（亞洲）有限公司 |
| HK | ICICI | ICICI Bank Limited |
| HK | INDOSUEZ | CA Indosuez (Switzerland) SA |
| HK | ING | ING Bank N.V. |
| HK | INTESA | 義大利聯合聖保羅銀行股份有限公司 |
| HK | IOB | 印度海外銀行 |
| HK | JPMORGAN | 摩根大通銀行 |
| HK | JULIUSBAER | Bank Julius Baer & Co. Ltd. |
| HK | KBC | 比利時聯合銀行 |
| HK | KDB | The Korea Development Bank |
| HK | KEBHANA | KEB Hana Bank |
| HK | KOOKMIN | Kookmin Bank |
| HK | LANDBANKTW | 台灣土地銀行股份有限公司 |
| HK | LGT | LGT 皇家銀行（香港） |
| HK | LIVIBANK | 理慧銀行有限公司 |
| HK | MASHREQ | Mashreq Bank - Public Shareholding Company |
| HK | MAYBANK | 馬來亞銀行 |
| HK | MEGA | 兆豐國際商業銀行 |
| HK | MELLI | Melli Bank plc |
| HK | MIZUHO | 瑞穗銀行 |
| HK | MORGANSTANLEY | 摩根士丹利銀行亞洲有限公司 |
| HK | MOX | Mox Bank Limited |
| HK | MUFG | 三菱UFJ銀行 |
| HK | MUFGTRUST | 三菱UFJ信託銀行 |
| HK | NAB | 澳大利亞國民銀行 |
| HK | NATIXIS | 法國外貿銀行 |
| HK | NBP | 巴基斯坦國民銀行 |
| HK | NCB | 南洋商業銀行有限公司 |
| HK | NONGHYUP | 農協銀行 |
| HK | OBANK | 王道商業銀行股份有限公司 |
| HK | OCBC | Oversea-Chinese Banking Corporation Limited |
| HK | OCBCHK | 華僑銀行 (香港) 有限公司 |
| HK | PAB | 平安銀行股份有限公司 |
| HK | PAOB | PAO Bank Limited |
| HK | PICTET | Banque Pictet & Cie SA |
| HK | PNB | Philippine National Bank |
| HK | PUBLICBANK | 大衆銀行(香港)有限公司 |
| HK | QNB | 卡塔爾國家銀行 |
| HK | RABOBANK | 荷蘭合作銀行 |
| HK | RBC | 加拿大皇家銀行 |
| HK | SAFRASARASIN | Bank J. Safra Sarasin Ltd |
| HK | SANTANDER | 西班牙桑坦德銀行有限公司 |
| HK | SBI | 印度國家銀行 |
| HK | SCB | 渣打銀行（香港）有限公司 |
| HK | SCBUK | 渣打銀行 |
| HK | SCOTIABANK | 加拿大豐業銀行 |
| HK | SCSB | 上海商業儲蓄銀行股份有限公司 |
| HK | SEB | Skandinaviska Enskilda Banken AB |
| HK | SHACOM | 上海商業銀行有限公司 |
| HK | SHIGA | 滋賀銀行 |
| HK | SHINHAN | Shinhan Bank |
| HK | SHINKONG | 臺灣新光商業銀行股份有限公司 |
| HK | SHIZUOKA | 靜岡銀行 |
| HK | SINOPAC | 永豐商業銀行股份有限公司 |
| HK | SMBC | 三井住友銀行 |
| HK | SMTB | 三井住友信託銀行 |
| HK | SOCGEN | 法國興業銀行 |
| HK | SPDB | 上海浦東發展銀行股份有限公司 |
| HK | STATESTREET | 美國道富銀行 |
| HK | TAIPEIFUBON | 台北富邦商業銀行股份有限公司 |
| HK | TAISANG | 大生銀行有限公司 |
| HK | TAISHIN | 台新國際商業銀行股份有限公司 |
| HK | TAIYAU | 大有銀行有限公司 |
| HK | TBB | 臺灣中小企業銀行股份有限公司 |
| HK | TCB | 合作金庫商業銀行股份有限公司 |
| HK | TDBANK | 多倫多道明銀行 |
| HK | UBP | Union Bancaire Privée, UBP SA |
| HK | UBS | 瑞士銀行 |
| HK | UCO | UCO Bank |
| HK | UOB | 大華銀行有限公司 |
| HK | WELAB | Welab Bank Limited |
| HK | WELLSFARGO | 富國銀行香港分行 |
| HK | WOORI | 友利銀行 |
| HK | YUANTA | 元大商業銀行股份有限公司 |
| HK | ZABANK | 眾安銀行有限公司 |

## 来源与更新

源项目：`project-p`；源码提交：`0ef7f9c8101d8e34528e33e462b3a3a32f5ef74b`。文件指纹见 JSON 的 `source.files`。字典不是运行中数据库或商户实时授权的快照。

维护步骤见 [贡献指南](../CONTRIBUTING.md#参考代码表维护)。只编辑 `appendix/reference-codes.json`，运行 `node _tooling/reference-codes.mjs` 同步 Markdown、CSV 和五种语言的国家/币种附录；运行 `node _tooling/reference-codes.mjs --check` 校验生成结果。
