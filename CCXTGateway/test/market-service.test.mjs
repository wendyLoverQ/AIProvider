import assert from "node:assert/strict";
import test from "node:test";
import { createMarketService, InputError, parseAllowedExchanges, parseHttpsProxy } from "../src/market-service.mjs";

class FakeExchange {
  constructor(options) {
    FakeExchange.lastOptions = options;
    this.options = options;
    this.name = "Fake Exchange";
    this.countries = ["US"];
    this.has = { fetchMarkets: true, fetchTicker: true, fetchOHLCV: true, fetchOrderBook: true };
    this.timeframes = { "15m": "15m" };
  }
  async loadMarkets() {
    return {
      btc: { symbol: "BTC/USDT", base: "BTC", quote: "USDT", spot: true, active: true },
      old: { symbol: "OLD/USDT", base: "OLD", quote: "USDT", spot: true, active: false },
      swap: { symbol: "BTC/USDT:USDT", base: "BTC", quote: "USDT", spot: false, active: true },
    };
  }
  async fetchTicker(symbol) { return { symbol, last: 101, bid: 100, ask: 102, info: { secret: "not exposed" } }; }
  async fetchOHLCV() { return [[1, 10, 12, 9, 11, 50]]; }
  async fetchOrderBook(symbol) { return { symbol, timestamp: 1, datetime: "now", bids: [[10, 2], [9, 3]], asks: [[11, 4], [12, 5]] }; }
}

const fakeCcxt = { version: "test", exchanges: ["fake"], fake: FakeExchange };

test("normalizes the public market-data surface", async () => {
  const service = createMarketService({ ccxt: fakeCcxt, allowedExchangeIds: ["fake"] });
  assert.equal(service.health().exchangeCount, 1);
  assert.equal(service.exchanges()[0].capabilities.ohlcv, true);
  assert.deepEqual(await service.markets("fake", "USDT", 20), [{ symbol: "BTC/USDT", baseAsset: "BTC", quoteAsset: "USDT", active: true, spot: true }]);
  assert.equal((await service.ticker("fake", "btc/usdt")).last, 101);
  assert.equal((await service.ticker("fake", "btc/usdt")).info, undefined);
  assert.deepEqual(await service.ohlcv("fake", "BTC/USDT", "15m", 10), [{ timestamp: 1, open: 10, high: 12, low: 9, close: 11, volume: 50 }]);
  assert.equal((await service.orderBook("fake", "BTC/USDT", 1)).bids.length, 1);
});

test("rejects arbitrary exchanges, symbols, timeframes, and limits before a network call", async () => {
  const service = createMarketService({ ccxt: fakeCcxt, allowedExchangeIds: ["fake"] });
  await assert.rejects(service.ticker("evil", "BTC/USDT"), InputError);
  await assert.rejects(service.ticker("fake", "BTCUSDT"), InputError);
  await assert.rejects(service.ohlcv("fake", "BTC/USDT", "7m", 10), InputError);
  await assert.rejects(service.orderBook("fake", "BTC/USDT", 500), InputError);
});

test("parses an explicit exchange allowlist", () => {
  assert.deepEqual(parseAllowedExchanges("binance, okx ,kraken"), ["binance", "okx", "kraken"]);
});

test("passes a validated HTTPS proxy to CCXT", () => {
  const service = createMarketService({ ccxt: fakeCcxt, allowedExchangeIds: ["fake"], httpsProxy: parseHttpsProxy("http://127.0.0.1:7890") });
  service.exchanges();
  assert.equal(FakeExchange.lastOptions.httpsProxy, "http://127.0.0.1:7890");
  assert.throws(() => parseHttpsProxy("file:///tmp/proxy"), /http or https/);
});
