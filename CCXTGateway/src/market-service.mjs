const DEFAULT_EXCHANGES = ["okx", "binance", "bybit", "kraken", "coinbase", "kucoin", "bitget", "gate", "mexc"];
const ID_PATTERN = /^[a-z0-9]{2,32}$/;
const SYMBOL_PATTERN = /^[A-Z0-9]{1,20}\/[A-Z0-9]{1,20}(?::[A-Z0-9]{1,20})?$/;
const QUOTE_PATTERN = /^[A-Z0-9]{2,12}$/;

export class InputError extends Error {}

export function createMarketService({ ccxt, allowedExchangeIds = DEFAULT_EXCHANGES, timeout = 10000, httpsProxy } = {}) {
  if (!ccxt) throw new Error("ccxt is required");
  const allowed = [...new Set(allowedExchangeIds.map((value) => String(value).trim().toLowerCase()).filter(Boolean))];
  if (!allowed.length) throw new Error("At least one exchange must be enabled");
  for (const id of allowed) if (!ID_PATTERN.test(id) || !ccxt.exchanges.includes(id)) throw new Error(`Unsupported exchange in allowlist: ${id}`);
  const instances = new Map();

  const exchange = (value) => {
    const id = String(value || "").trim().toLowerCase();
    if (!ID_PATTERN.test(id) || !allowed.includes(id)) throw new InputError("交易所不在允许列表中");
    if (!instances.has(id)) {
      const ExchangeClass = ccxt[id];
      const options = { enableRateLimit: true, timeout };
      if (httpsProxy) options.httpsProxy = httpsProxy;
      instances.set(id, new ExchangeClass(options));
    }
    return instances.get(id);
  };

  const symbol = (value) => {
    const normalized = String(value || "").trim().toUpperCase();
    if (!SYMBOL_PATTERN.test(normalized)) throw new InputError("交易对格式不正确");
    return normalized;
  };

  const positiveLimit = (value, fallback, maximum) => {
    const parsed = value == null || value === "" ? fallback : Number(value);
    if (!Number.isInteger(parsed) || parsed < 1 || parsed > maximum) throw new InputError(`limit 必须在 1 到 ${maximum} 之间`);
    return parsed;
  };

  return {
    health() {
      return { provider: "CCXT", version: ccxt.version, available: true, exchangeCount: allowed.length, checkedAt: new Date().toISOString() };
    },

    exchanges() {
      return allowed.map((id) => {
        const item = exchange(id);
        return {
          id,
          name: item.name,
          countries: item.countries || [],
          capabilities: {
            markets: Boolean(item.has?.fetchMarkets),
            ticker: Boolean(item.has?.fetchTicker),
            ohlcv: Boolean(item.has?.fetchOHLCV),
            orderBook: Boolean(item.has?.fetchOrderBook),
          },
        };
      });
    },

    async markets(exchangeId, quoteValue = "USDT", limitValue = 500) {
      const item = exchange(exchangeId);
      const quote = String(quoteValue || "USDT").trim().toUpperCase();
      if (!QUOTE_PATTERN.test(quote)) throw new InputError("计价币种格式不正确");
      const limit = positiveLimit(limitValue, 500, 2000);
      const catalog = await item.loadMarkets();
      return Object.values(catalog)
        .filter((market) => market?.spot === true && market?.active !== false && market?.quote === quote)
        .sort((left, right) => String(left.base).localeCompare(String(right.base)))
        .slice(0, limit)
        .map((market) => ({ symbol: market.symbol, baseAsset: market.base, quoteAsset: market.quote, active: market.active !== false, spot: true }));
    },

    async ticker(exchangeId, symbolValue) {
      const item = exchange(exchangeId);
      const result = await item.fetchTicker(symbol(symbolValue));
      return pick(result, ["symbol", "timestamp", "datetime", "high", "low", "bid", "ask", "open", "close", "last", "change", "percentage", "baseVolume", "quoteVolume"]);
    },

    async ohlcv(exchangeId, symbolValue, timeframeValue = "15m", limitValue = 240) {
      const item = exchange(exchangeId);
      const timeframe = String(timeframeValue || "").trim();
      if (!item.has?.fetchOHLCV || !item.timeframes || !Object.prototype.hasOwnProperty.call(item.timeframes, timeframe)) {
        throw new InputError("当前交易所不支持这个 K 线周期");
      }
      const limit = positiveLimit(limitValue, 240, 1000);
      const rows = await item.fetchOHLCV(symbol(symbolValue), timeframe, undefined, limit);
      return rows.map(([timestamp, open, high, low, close, volume]) => ({ timestamp, open, high, low, close, volume }));
    },

    async orderBook(exchangeId, symbolValue, limitValue = 20) {
      const item = exchange(exchangeId);
      const limit = positiveLimit(limitValue, 20, 200);
      const result = await item.fetchOrderBook(symbol(symbolValue), limit);
      return {
        symbol: result.symbol,
        timestamp: result.timestamp,
        datetime: result.datetime,
        bids: (result.bids || []).slice(0, limit).map(([price, amount]) => [price, amount]),
        asks: (result.asks || []).slice(0, limit).map(([price, amount]) => [price, amount]),
      };
    },
  };
}

function pick(source, keys) {
  const result = {};
  for (const key of keys) result[key] = source?.[key] ?? null;
  return result;
}

export function parseAllowedExchanges(value) {
  return value ? value.split(",").map((item) => item.trim()).filter(Boolean) : DEFAULT_EXCHANGES;
}

export function parseHttpsProxy(value) {
  if (!value) return undefined;
  let parsed;
  try { parsed = new URL(value); }
  catch { throw new Error("CCXT_HTTPS_PROXY must be a valid URL"); }
  if (!["http:", "https:"].includes(parsed.protocol)) throw new Error("CCXT_HTTPS_PROXY must use http or https");
  return value;
}
