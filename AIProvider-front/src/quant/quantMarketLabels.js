// quantMarketLabels — 合约行情页面的纯中文标签与枚举翻译。
// 所有用户可见文本均为中文，英文仅作为合约规则的辅助小字。
// 未知枚举值统一显示为“未知状态（原始值）”，绝不误译为“交易中”。

// 合约规则字段的中英文标签，主标签为中文，英文仅作辅助。
const FIELD_LABELS = {
  symbol: { zh: "合约代码", en: "Symbol" },
  contractType: { zh: "合约类型", en: "Contract Type" },
  status: { zh: "交易状态", en: "Status" },
  baseAsset: { zh: "基础资产", en: "Base Asset" },
  quoteAsset: { zh: "报价资产", en: "Quote Asset" },
  marginAsset: { zh: "保证金资产", en: "Margin Asset" },
  onboardDate: { zh: "上线时间", en: "Onboard Date" },
  tickSize: { zh: "价格最小变动单位", en: "Tick Size" },
  minPrice: { zh: "最低允许价格", en: "Min Price" },
  maxPrice: { zh: "最高允许价格", en: "Max Price" },
  stepSize: { zh: "限价单数量步长", en: "Step Size" },
  minQty: { zh: "限价单最小数量", en: "Min Qty" },
  maxQty: { zh: "限价单最大数量", en: "Max Qty" },
  marketStepSize: { zh: "市价单数量步长", en: "Market Step Size" },
  marketMinQty: { zh: "市价单最小数量", en: "Market Min Qty" },
  marketMaxQty: { zh: "市价单最大数量", en: "Market Max Qty" },
  minNotional: { zh: "最小名义价值", en: "Min Notional" },
  pricePrecision: { zh: "价格精度", en: "Price Precision" },
  quantityPrecision: { zh: "数量精度", en: "Quantity Precision" },
};

// 合约类型枚举翻译（Binance USDⓈ-M Futures）。
const CONTRACT_TYPE_LABELS = {
  PERPETUAL: "永续合约",
  CURRENT_QUARTER: "当季合约",
  NEXT_QUARTER: "次季合约",
  CURRENT_QUARTER_DELIVERING: "当季交割中",
  NEXT_QUARTER_DELIVERING: "次季交割中",
  PERPETUAL_DELIVERING: "永续交割中",
};

// 交易状态枚举翻译（Binance USDⓈ-M Futures）。
const STATUS_LABELS = {
  TRADING: "交易中",
  PRE_TRADING: "预交易",
  POST_TRADING: "盘后交易",
  END_OF_DAY: "日终",
  HALT: "停止",
  AUCTION_MATCH: "集合竞价",
  BREAK: "休市",
  PENDING_TRADING: "待上市",
  DELIVERING: "交割中",
};

// 行情数据提供方翻译。
const PROVIDER_LABELS = {
  BINANCE_USDM: "币安 U 本位合约",
};

// K 线周期翻译。
const INTERVAL_LABELS = {
  "1m": "1分钟",
  "5m": "5分钟",
  "15m": "15分钟",
  "1h": "1小时",
  "4h": "4小时",
  "1d": "1天",
};

// 取字段标签，未知 key 回退为原始 key 作为中英文，避免显示空白。
export function fieldLabel(key) {
  return FIELD_LABELS[key] || { zh: String(key), en: String(key) };
}

// 取合约类型中文标签，未知值显示“未知状态（原始值）”。
export function contractTypeLabel(value) {
  if (value == null || value === "") return "未知状态（空）";
  return CONTRACT_TYPE_LABELS[value] || `未知状态（${value}）`;
}

// 取交易状态中文标签，未知值显示“未知状态（原始值）”，绝不误译为交易中。
export function statusLabel(value) {
  if (value == null || value === "") return "未知状态（空）";
  return STATUS_LABELS[value] || `未知状态（${value}）`;
}

// 取数据提供方中文标签，未知值显示“未知状态（原始值）”。
export function providerLabel(value) {
  if (value == null || value === "") return "未知状态（空）";
  return PROVIDER_LABELS[value] || `未知状态（${value}）`;
}

// 取 K 线周期中文标签，未知值显示“未知状态（原始值）”。
export function intervalLabel(code) {
  if (code == null || code === "") return "未知状态（空）";
  return INTERVAL_LABELS[code] || `未知状态（${code}）`;
}

// 导出原始映射，便于测试与调试。
export { FIELD_LABELS, CONTRACT_TYPE_LABELS, STATUS_LABELS, PROVIDER_LABELS, INTERVAL_LABELS };
