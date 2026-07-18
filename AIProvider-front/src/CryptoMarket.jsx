import { useEffect, useMemo, useState } from "react";
import { ArrowClockwise, ChartLineUp, Pulse, Warning } from "@phosphor-icons/react";
import { Area, AreaChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import UiSearchField from "./UiSearchField";
import "./CryptoMarket.css";

const INTERVALS = ["5m", "15m", "1h", "4h", "1d"];

async function marketApi(path) {
  const response = await fetch(`/api/crypto-market${path}`);
  const result = await response.json().catch(() => null);
  if (!response.ok || !result || result.code !== 200) throw new Error(result?.message || `行情请求失败 · ${response.status}`);
  return result.data;
}

export default function CryptoMarket() {
  const [health, setHealth] = useState(null);
  const [exchanges, setExchanges] = useState([]);
  const [exchange, setExchange] = useState("");
  const [markets, setMarkets] = useState([]);
  const [symbol, setSymbol] = useState("");
  const [query, setQuery] = useState("");
  const [interval, setIntervalValue] = useState("15m");
  const [ticker, setTicker] = useState(null);
  const [ohlcv, setOhlcv] = useState([]);
  const [orderBook, setOrderBook] = useState({ bids: [], asks: [] });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [refreshKey, setRefreshKey] = useState(0);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    Promise.all([marketApi("/health"), marketApi("/exchanges")])
      .then(([nextHealth, nextExchanges]) => {
        if (cancelled) return;
        setHealth(nextHealth);
        setExchanges(nextExchanges);
        const preferred = nextExchanges.find((item) => item.id === "okx") || nextExchanges[0];
        setExchange((current) => nextExchanges.some((item) => item.id === current) ? current : (preferred?.id || ""));
        setError("");
      })
      .catch((requestError) => { if (!cancelled) setError(requestError.message); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [refreshKey]);

  useEffect(() => {
    if (!exchange) return;
    let cancelled = false;
    setLoading(true);
    marketApi(`/symbols?exchange=${encodeURIComponent(exchange)}&quote=USDT&limit=500`)
      .then((items) => {
        if (cancelled) return;
        setMarkets(items);
        setSymbol((current) => items.some((item) => item.symbol === current) ? current : (items.find((item) => item.symbol === "BTC/USDT") || items[0])?.symbol || "");
        setError("");
      })
      .catch((requestError) => { if (!cancelled) { setMarkets([]); setSymbol(""); setError(requestError.message); } })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [exchange]);

  useEffect(() => {
    if (!exchange || !symbol) return undefined;
    let cancelled = false;
    const params = `exchange=${encodeURIComponent(exchange)}&symbol=${encodeURIComponent(symbol)}`;
    const load = (includeChart) => {
      const requests = [marketApi(`/ticker?${params}`), marketApi(`/depth?${params}&limit=20`)];
      if (includeChart) requests.push(marketApi(`/klines?${params}&interval=${encodeURIComponent(interval)}&limit=240`));
      Promise.all(requests).then(([nextTicker, nextBook, nextOhlcv]) => {
        if (cancelled) return;
        setTicker(nextTicker);
        setOrderBook(nextBook);
        if (includeChart) setOhlcv(nextOhlcv);
        setError("");
      }).catch((requestError) => { if (!cancelled) setError(requestError.message); });
    };
    load(true);
    const timer = window.setInterval(() => load(false), 15000);
    return () => { cancelled = true; window.clearInterval(timer); };
  }, [exchange, symbol, interval, refreshKey]);

  const filteredMarkets = useMemo(() => {
    const needle = query.trim().toUpperCase();
    return needle ? markets.filter((item) => `${item.symbol} ${item.baseAsset}`.toUpperCase().includes(needle)) : markets;
  }, [markets, query]);
  const chartData = useMemo(() => ohlcv.map((item) => ({ ...item, label: chartLabel(item.timestamp, interval) })), [ohlcv, interval]);
  const currentExchange = exchanges.find((item) => item.id === exchange);
  const positive = Number(ticker?.percentage || 0) >= 0;

  if (loading && !exchanges.length) return <div className="crypto-market-state"><Pulse className="crypto-market-pulse" /><strong>正在连接 CCXT 行情网关</strong><span>加载统一交易所目录…</span></div>;
  if (error && !exchanges.length) return <div className="crypto-market-state crypto-market-state--error"><Warning /><strong>CCXT 行情服务不可用</strong><span>{error}</span><button type="button" onClick={() => setRefreshKey((value) => value + 1)}><ArrowClockwise />重新连接</button></div>;

  return (
    <section className="crypto-market">
      <header className="crypto-market__header">
        <div><span>CCXT UNIFIED MARKET DATA</span><strong>{currentExchange?.name || exchange}</strong><small>只读公共行情 · 不接收 API Key · 不提供下单</small></div>
        <div className="crypto-market__health"><i className={health?.available ? "online" : ""} /><span>{health?.available ? "网关在线" : "状态未知"}</span><b>v{health?.version || "-"}</b><em>{health?.exchangeCount || exchanges.length} 家交易所</em></div>
      </header>

      <div className="crypto-market__toolbar">
        <label>交易所<select aria-label="交易所" value={exchange} onChange={(event) => setExchange(event.target.value)}>{exchanges.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</select></label>
        <div className="crypto-market__intervals" aria-label="K 线周期">{INTERVALS.map((item) => <button type="button" key={item} className={interval === item ? "active" : ""} onClick={() => setIntervalValue(item)}>{item}</button>)}</div>
        <button type="button" className="crypto-market__refresh" onClick={() => setRefreshKey((value) => value + 1)}><ArrowClockwise />刷新</button>
      </div>

      {error && <div className="crypto-market__notice"><Warning />{error}</div>}
      <div className="crypto-market__layout">
        <aside className="crypto-market__symbols">
          <UiSearchField className="crypto-market-search" aria-label="搜索交易对" value={query} onChange={(event) => setQuery(event.target.value)} placeholder="搜索 BTC、ETH…" />
          <div className="crypto-market__symbol-head"><span>USDT 现货市场</span><b>{filteredMarkets.length}</b></div>
          <div className="crypto-market__symbol-list">
            {filteredMarkets.map((item) => <button type="button" key={item.symbol} className={symbol === item.symbol ? "active" : ""} onClick={() => setSymbol(item.symbol)}><strong>{item.baseAsset}</strong><span>/{item.quoteAsset}</span></button>)}
            {!filteredMarkets.length && <p>没有匹配的交易对</p>}
          </div>
        </aside>

        <main className="crypto-market__main">
          <div className="crypto-market__ticker">
            <div><span>{symbol || "--"}</span><strong>{price(ticker?.last)}</strong></div>
            <dl><div><dt>24h 涨跌</dt><dd className={positive ? "up" : "down"}>{signed(ticker?.percentage)}%</dd></div><div><dt>24h 最高</dt><dd>{price(ticker?.high)}</dd></div><div><dt>24h 最低</dt><dd>{price(ticker?.low)}</dd></div><div><dt>成交量</dt><dd>{compact(ticker?.baseVolume)}</dd></div></dl>
          </div>
          <div className="crypto-market__chart">
            {chartData.length ? <ResponsiveContainer width="100%" height="100%"><AreaChart data={chartData} margin={{ top: 22, right: 20, left: 0, bottom: 4 }}><defs><linearGradient id="ccxtPriceFill" x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stopColor="#9a76ff" stopOpacity=".42"/><stop offset="100%" stopColor="#9a76ff" stopOpacity="0"/></linearGradient></defs><CartesianGrid stroke="#2d2940" strokeDasharray="3 6" vertical={false}/><XAxis dataKey="label" stroke="#756f82" tick={{ fontSize: 9 }} minTickGap={45}/><YAxis domain={["auto", "auto"]} orientation="right" stroke="#756f82" tick={{ fontSize: 9 }} tickFormatter={axisPrice}/><Tooltip contentStyle={{ background: "#1c1922", border: "1px solid #41394f", borderRadius: 10, fontSize: 10 }} formatter={(value) => [price(value), "收盘价"]}/><Area type="monotone" dataKey="close" stroke="#a982ff" strokeWidth={2} fill="url(#ccxtPriceFill)" isAnimationActive={false}/></AreaChart></ResponsiveContainer> : <div className="crypto-market__chart-empty"><ChartLineUp /><span>{loading ? "加载 K 线…" : "暂无 K 线数据"}</span></div>}
          </div>
        </main>

        <aside className="crypto-market__book">
          <header><strong>订单簿</strong><span>价格 / 数量</span></header>
          <OrderRows rows={[...(orderBook.asks || [])].reverse()} side="ask" />
          <div className="crypto-market__spread"><span>最新成交</span><strong>{price(ticker?.last)}</strong></div>
          <OrderRows rows={orderBook.bids || []} side="bid" />
        </aside>
      </div>
    </section>
  );
}

function OrderRows({ rows, side }) {
  const max = Math.max(1, ...rows.map((item) => Number(item[1]) || 0));
  return <div className={`crypto-market__orders ${side}`}>{rows.slice(0, 10).map(([levelPrice, amount], index) => <div key={`${levelPrice}-${index}`}><i style={{ width: `${Math.min(100, (Number(amount) / max) * 100)}%` }} /><span>{price(levelPrice)}</span><b>{compact(amount)}</b></div>)}</div>;
}

const price = (value) => value == null || !Number.isFinite(Number(value)) ? "--" : Number(value).toLocaleString("zh-CN", { maximumFractionDigits: Number(value) >= 100 ? 2 : 8 });
const axisPrice = (value) => Number(value).toLocaleString("zh-CN", { notation: "compact", maximumFractionDigits: 2 });
const compact = (value) => value == null || !Number.isFinite(Number(value)) ? "--" : new Intl.NumberFormat("zh-CN", { notation: "compact", maximumFractionDigits: 2 }).format(Number(value));
const signed = (value) => value == null || !Number.isFinite(Number(value)) ? "--" : `${Number(value) >= 0 ? "+" : ""}${Number(value).toFixed(2)}`;
const chartLabel = (timestamp, interval) => new Date(Number(timestamp)).toLocaleString("zh-CN", interval === "1d" ? { month: "2-digit", day: "2-digit" } : { hour: "2-digit", minute: "2-digit", hour12: false });
