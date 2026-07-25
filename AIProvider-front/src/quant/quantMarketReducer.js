// quantMarketReducer — WebSocket 行情事件合并的纯函数。
// 不依赖 React，不发起网络请求，只做数据合并，便于单测。

// 将多种形式的 openTime/eventTime 统一换算成毫秒时间戳，便于比较。
// 支持数字、数字字符串（秒或毫秒）以及 ISO 字符串。
function toMs(value) {
  if (value == null) return NaN;
  if (typeof value === "number") return value;
  const s = String(value).trim();
  if (s === "") return NaN;
  if (/^\d+$/.test(s)) {
    const n = Number(s);
    // 10 位及以下视为秒，13 位视为毫秒。
    return s.length <= 10 ? n * 1000 : n;
  }
  const d = new Date(s);
  return Number.isNaN(d.getTime()) ? NaN : d.getTime();
}

// 将数值字符串安全转成数字，非法则原样保留（避免破坏显示）。
function toNum(value) {
  if (value == null) return value;
  if (typeof value === "number") return value;
  const n = Number(value);
  return Number.isFinite(n) ? n : value;
}

// 把 WS KLINE 数据归一化为统一的 candle 形状，openTime 统一为 ISO 字符串。
function normalizeCandle(data, event) {
  const openMs = toMs(data.openTime);
  const closeMs = toMs(data.closeTime);
  return {
    openTime: Number.isNaN(openMs) ? data.openTime : new Date(openMs).toISOString(),
    closeTime: Number.isNaN(closeMs) ? data.closeTime : new Date(closeMs).toISOString(),
    open: toNum(data.open),
    high: toNum(data.high),
    low: toNum(data.low),
    close: toNum(data.close),
    volume: toNum(data.volume),
    quoteVolume: toNum(data.quoteVolume),
    tradeCount: data.tradeCount != null ? toNum(data.tradeCount) : data.tradeCount,
    takerBuyBaseVolume: toNum(data.takerBuyBaseVolume),
    takerBuyQuoteVolume: toNum(data.takerBuyQuoteVolume),
    closed: !!data.closed,
    // 记录最后一次 WS 更新时间，用于后续判断旧事件不覆盖新事件。
    eventTime: event && event.eventTime ? event.eventTime : undefined,
  };
}

// 判断到来的 WS 事件是否比已存在 candle 的更新时间更旧。
// REST 来源的 candle 没有 eventTime，永不视为“被旧事件覆盖”。
function isStale(event, existingCandle) {
  if (!existingCandle || !existingCandle.eventTime) return false;
  if (!event || !event.eventTime) return false;
  const existingMs = toMs(existingCandle.eventTime);
  const incomingMs = toMs(event.eventTime);
  if (Number.isNaN(existingMs) || Number.isNaN(incomingMs)) return false;
  // 旧事件不得覆盖新事件，相等时允许更新（幂等重放）。
  return incomingMs < existingMs;
}

function trimToMax(candles, maxCount) {
  if (maxCount <= 0 || candles.length <= maxCount) return candles;
  return candles.slice(candles.length - maxCount);
}

// mergeKline — 把 WS KLINE 事件合并进 candle 数组。
// 规则：
//   1. openTime 等于最后一根 → 替换最后一根，不重复。
//   2. openTime 大于最后一根 → 追加，保留最多 maxCount 根。
//   3. openTime 小于最后一根 → 命中同时间则更新；未命中返回原数组（由调用方 REST 重新同步）。
//   4. 旧事件不得覆盖新事件（比较 eventTime）。
//   5. 返回 { candles, changed }。
export function mergeKline(candles, event, maxCount = 120) {
  const source = Array.isArray(candles) ? candles : [];
  if (!event || !event.data) return { candles: source, changed: false };
  const data = event.data;
  const incomingOpenMs = toMs(data.openTime);
  if (Number.isNaN(incomingOpenMs)) return { candles: source, changed: false };

  const next = source.slice();

  if (!next.length) {
    next.push(normalizeCandle(data, event));
    return { candles: trimToMax(next, maxCount), changed: true };
  }

  const lastIndex = next.length - 1;
  const lastOpenMs = toMs(next[lastIndex].openTime);

  if (incomingOpenMs === lastOpenMs) {
    if (isStale(event, next[lastIndex])) return { candles: source, changed: false };
    next[lastIndex] = normalizeCandle(data, event);
    return { candles: next, changed: true };
  }

  if (incomingOpenMs > lastOpenMs) {
    next.push(normalizeCandle(data, event));
    return { candles: trimToMax(next, maxCount), changed: true };
  }

  // incomingOpenMs < lastOpenMs：查找同时间的 candle。
  const idx = next.findIndex((c) => toMs(c.openTime) === incomingOpenMs);
  if (idx === -1) {
    // 未命中，保持不变，调用方将走 REST 重新同步。
    return { candles: source, changed: false };
  }
  if (isStale(event, next[idx])) return { candles: source, changed: false };
  next[idx] = normalizeCandle(data, event);
  return { candles: next, changed: true };
}

// mergeSnapshot — 把 WS TICKER/MARK_PRICE/BOOK_TICKER 事件合并进快照对象。
// 只覆盖事件提供的字段，不删除快照已有字段；不覆盖 openInterest（由 REST 单独刷新）。
export function mergeSnapshot(snapshot, wsEvent, type) {
  const base = snapshot && typeof snapshot === "object" ? snapshot : {};
  if (!wsEvent || !wsEvent.data) return base;
  const data = wsEvent.data;
  const merged = { ...base };

  switch (type) {
    case "TICKER":
      if (data.lastPrice != null) merged.lastPrice = data.lastPrice;
      if (data.priceChange != null) merged.priceChange = data.priceChange;
      if (data.priceChangePercent != null) merged.priceChangePercent = data.priceChangePercent;
      if (data.highPrice != null) merged.highPrice = data.highPrice;
      if (data.lowPrice != null) merged.lowPrice = data.lowPrice;
      if (data.volume != null) merged.volume = data.volume;
      if (data.quoteVolume != null) merged.quoteVolume = data.quoteVolume;
      if (wsEvent.symbol) merged.symbol = wsEvent.symbol;
      break;
    case "MARK_PRICE":
      if (data.markPrice != null) merged.markPrice = data.markPrice;
      if (data.indexPrice != null) merged.indexPrice = data.indexPrice;
      if (data.estimatedSettlePrice != null) merged.estimatedSettlePrice = data.estimatedSettlePrice;
      if (data.lastFundingRate != null) merged.lastFundingRate = data.lastFundingRate;
      if (data.interestRate != null) merged.interestRate = data.interestRate;
      if (data.nextFundingTime != null) merged.nextFundingTime = data.nextFundingTime;
      break;
    case "BOOK_TICKER":
      if (data.bidPrice != null) merged.bidPrice = data.bidPrice;
      if (data.bidQuantity != null) merged.bidQuantity = data.bidQuantity;
      if (data.askPrice != null) merged.askPrice = data.askPrice;
      if (data.askQuantity != null) merged.askQuantity = data.askQuantity;
      // 买卖价差实时重算。
      if (data.bidPrice != null && data.askPrice != null) {
        const spread = Number(data.askPrice) - Number(data.bidPrice);
        if (Number.isFinite(spread)) merged.spread = spread;
      }
      break;
    default:
      return base;
  }

  merged._wsUpdatedAt = wsEvent.eventTime || new Date().toISOString();
  return merged;
}

// shouldIgnoreEvent — 判断事件是否属于当前选中的 symbol/interval，不属于则忽略。
// TICKER/MARK_PRICE/BOOK_TICKER 没有 interval 字段，只比较 symbol。
export function shouldIgnoreEvent(event, currentSymbol, currentInterval) {
  if (!event) return true;
  if (currentSymbol && event.symbol && event.symbol !== currentSymbol) return true;
  if (currentInterval && event.interval && event.interval !== currentInterval) return true;
  return false;
}
