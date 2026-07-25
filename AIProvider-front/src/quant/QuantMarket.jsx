import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  ArrowsClockwise,
  ChartLineUp,
  Info,
  Lightning,
  Pulse,
  Warning,
} from "@phosphor-icons/react";
import { Area, AreaChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { readJsonResponse } from "../apiResponse";
import UiSearchField from "../UiSearchField";
import QuantPageScaffold from "./QuantPageScaffold";
import "./QuantMarket.css";

const API_BASE = "/api/quant/market";
const DEFAULT_PROVIDER = "BINANCE_USDM";
const DEFAULT_QUOTE = "USDT";
const DEFAULT_SYMBOL = "BTCUSDT";
const SNAP_INTERVAL_MS = 15_000;
const INTERVALS = [
  { code: "1m", label: "1m" },
  { code: "5m", label: "5m" },
  { code: "15m", label: "15m" },
  { code: "1h", label: "1h" },
  { code: "4h", label: "4h" },
  { code: "1d", label: "1d" },
];

async function marketGet(path) {
  const response = await fetch(`${API_BASE}${path}`);
  const payload = await readJsonResponse(response, "公共行情服务响应异常");
  if (!response.ok || payload.code !== 200) {
    throw new Error(payload.message || `请求失败 · HTTP ${response.status}`);
  }
  return payload.data;
}

function fmtPrice(value) {
  if (value == null || value === "") return "—";
  const num = Number(value);
  if (!Number.isFinite(num)) return "—";
  return num.toLocaleString("zh-CN", { maximumFractionDigits: num >= 100 ? 2 : 8 });
}
function fmtPct(value) {
  if (value == null || value === "") return "—";
  const num = Number(value);
  if (!Number.isFinite(num)) return "—";
  return `${num >= 0 ? "+" : ""}${num.toFixed(4)}%`;
}
function fmtQty(value) {
  if (value == null || value === "") return "—";
  const num = Number(value);
  if (!Number.isFinite(num)) return "—";
  return new Intl.NumberFormat("zh-CN", { notation: "compact", maximumFractionDigits: 2 }).format(num);
}
function fmtNum(value) {
  if (value == null || value === "") return "—";
  const num = Number(value);
  if (!Number.isFinite(num)) return "—";
  return num.toLocaleString("zh-CN", { maximumFractionDigits: 4 });
}
function fmtTime(iso) {
  if (!iso) return "—";
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return "—";
  return d.toLocaleString("zh-CN", { hour12: false });
}
function chartLabel(iso, interval) {
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return "";
  return interval === "1d"
    ? d.toLocaleDateString("zh-CN", { month: "2-digit", day: "2-digit" })
    : d.toLocaleTimeString("zh-CN", { hour: "2-digit", minute: "2-digit", hour12: false });
}

export default function QuantMarket() {
  const [health, setHealth] = useState(null);
  const [providers, setProviders] = useState([]);
  const [provider, setProvider] = useState(DEFAULT_PROVIDER);
  const [contracts, setContracts] = useState([]);
  const [symbol, setSymbol] = useState("");
  const [query, setQuery] = useState("");
  const [interval, setIntervalValue] = useState("15m");
  const [snapshot, setSnapshot] = useState(null);
  const [klines, setKlines] = useState([]);
  const [contractDetail, setContractDetail] = useState(null);
  const [phase, setPhase] = useState("initial-loading");
  const [error, setError] = useState("");
  const [snapshotError, setSnapshotError] = useState("");
  const [reconnectKey, setReconnectKey] = useState(0);
  const snapshotSeq = useRef(0);
  const klineSeq = useRef(0);

  const filteredContracts = useMemo(() => {
    const needle = query.trim().toUpperCase();
    if (!needle) return contracts;
    return contracts.filter((c) => `${c.symbol} ${c.baseAsset}`.toUpperCase().includes(needle));
  }, [contracts, query]);

  const loadHealth = useCallback(async () => {
    return marketGet(`/health?provider=${encodeURIComponent(provider)}`);
  }, [provider]);

  const loadProviders = useCallback(async () => {
    return marketGet("/providers");
  }, []);

  const loadContracts = useCallback(async () => {
    return marketGet(`/contracts?provider=${encodeURIComponent(provider)}&quoteAsset=${encodeURIComponent(DEFAULT_QUOTE)}`);
  }, [provider]);

  const loadSnapshot = useCallback(async (sym) => {
    return marketGet(`/snapshot?provider=${encodeURIComponent(provider)}&symbol=${encodeURIComponent(sym)}`);
  }, [provider]);

  const loadKlines = useCallback(async (sym, itv) => {
    return marketGet(`/klines?provider=${encodeURIComponent(provider)}&symbol=${encodeURIComponent(sym)}&interval=${encodeURIComponent(itv)}&limit=120`);
  }, [provider]);

  // Phase 1: initial load — health, providers, contracts, then determine symbol
  useEffect(() => {
    let cancelled = false;
    setPhase("initial-loading");
    setError("");
    (async () => {
      try {
        const [h, ps] = await Promise.all([loadHealth(), loadProviders()]);
        if (cancelled) return;
        setHealth(h);
        setProviders(ps);
        const cs = await loadContracts();
        if (cancelled) return;
        setContracts(cs);
        const preferred = cs.find((c) => c.symbol === DEFAULT_SYMBOL);
        const selected = preferred ? preferred.symbol : (cs[0]?.symbol || "");
        if (!selected) throw new Error("合约目录为空，无法选择合约");
        setSymbol(selected);
        setPhase("ready");
      } catch (e) {
        if (!cancelled) {
          setError(e.message || "公共行情初始化失败");
          setPhase("error");
        }
      }
    })();
    return () => { cancelled = true; };
  }, [reconnectKey, loadHealth, loadProviders, loadContracts]);

  // Phase 2: snapshot + klines on symbol/interval change
  useEffect(() => {
    if (!symbol || phase === "initial-loading" || phase === "error") return;
    const mySeq = ++snapshotSeq.current;
    const myKlineSeq = ++klineSeq.current;
    setSnapshotError("");
    Promise.all([loadSnapshot(symbol), loadKlines(symbol, interval)])
      .then(([snap, ks]) => {
        if (snapshotSeq.current !== mySeq) return;
        if (klineSeq.current !== myKlineSeq) return;
        setSnapshot(snap);
        setKlines(ks);
        const detail = contracts.find((c) => c.symbol === symbol);
        setContractDetail(detail || null);
      })
      .catch((e) => {
        if (snapshotSeq.current !== mySeq) return;
        setSnapshotError(e.message || "行情快照加载失败");
      });
  }, [symbol, interval, phase, contracts, loadSnapshot, loadKlines]);

  // Phase 3: auto-refresh snapshot every 15s
  useEffect(() => {
    if (!symbol || phase !== "ready") return;
    const timer = window.setInterval(() => {
      const mySeq = ++snapshotSeq.current;
      loadSnapshot(symbol)
        .then((snap) => {
          if (snapshotSeq.current !== mySeq) return;
          setSnapshot(snap);
          setSnapshotError("");
        })
        .catch((e) => {
          if (snapshotSeq.current !== mySeq) return;
          setSnapshotError(e.message || "快照刷新失败");
        });
    }, SNAP_INTERVAL_MS);
    return () => window.clearInterval(timer);
  }, [symbol, phase, loadSnapshot]);

  // Phase 4: periodic health refresh
  useEffect(() => {
    if (phase !== "ready") return;
    const timer = window.setInterval(() => {
      loadHealth()
        .then((h) => setHealth(h))
        .catch(() => {});
    }, SNAP_INTERVAL_MS);
    return () => window.clearInterval(timer);
  }, [phase, loadHealth]);

  const reconnect = () => setReconnectKey((k) => k + 1);
  const manualRefreshKlines = () => {
    if (!symbol) return;
    const myKlineSeq = ++klineSeq.current;
    loadKlines(symbol, interval)
      .then((ks) => {
        if (klineSeq.current !== myKlineSeq) return;
        setKlines(ks);
      })
      .catch((e) => setSnapshotError(e.message || "K 线刷新失败"));
  };

  if (phase === "initial-loading") {
    return (
      <QuantPageScaffold pageClass="quant-market-page" title="合约行情">
        <div className="quant-loading" role="status" aria-live="polite">
          <div className="quant-loader-dot" /><div className="quant-loader-dot" /><div className="quant-loader-dot" />
          <span>正在连接 Binance USDⓈ-M Futures…</span>
        </div>
      </QuantPageScaffold>
    );
  }
  if (phase === "error") {
    return (
      <QuantPageScaffold pageClass="quant-market-page" title="合约行情">
        <div className="quant-error" role="alert">
          <Warning weight="fill" />
          <div>
            <strong>公共行情连接失败</strong>
            <span>{error}</span>
          </div>
          <button type="button" className="quant-error-retry" onClick={reconnect}>
            <ArrowsClockwise />重新连接
          </button>
        </div>
      </QuantPageScaffold>
    );
  }

  const available = health?.available;
  const positive = Number(snapshot?.priceChangePercent || 0) >= 0;
  const chartData = klines.map((k) => ({ ...k, label: chartLabel(k.openTime, interval) }));
  const recentCandles = klines.slice(-10).reverse();

  return (
    <QuantPageScaffold pageClass="quant-market-page" title="合约行情">
      <div className="quant-workspace-head">
        <div>
          <span className="eyebrow">BINANCE · USDⓈ-M PERPETUAL</span>
          <h3>合约行情</h3>
          <small>Binance U 本位永续公共只读行情 · 不经过 CCXT</small>
        </div>
        <button type="button" className="quant-refresh" onClick={reconnect}>
          <ArrowsClockwise />重新连接
        </button>
      </div>

      {snapshotError && (
        <div className="quant-market-notice" role="alert">
          <Warning weight="fill" />{snapshotError}
        </div>
      )}

      <section className="quant-market-health" aria-label="连接状态">
        <article className="quant-market-health-card">
          <div className="quant-market-health-head">
            <span>数据源</span><strong>Binance</strong>
          </div>
          <div className="quant-market-health-head">
            <span>市场</span><strong>USDⓈ-M 永续</strong>
          </div>
          <div className="quant-market-health-head">
            <span>模式</span><strong>公共只读</strong>
          </div>
        </article>
        <article className="quant-market-health-card">
          <div className="quant-market-health-row">
            <span>连接状态</span>
            <strong className={available ? "online" : "offline"}>
              <i className={available ? "dot online" : "dot"} />{available ? "在线" : "离线"}
            </strong>
          </div>
          <div className="quant-market-health-row">
            <span>请求延迟</span><strong>{health?.latencyMs != null ? `${health.latencyMs} ms` : "—"}</strong>
          </div>
          <div className="quant-market-health-row">
            <span>Binance 服务器时间</span><strong>{fmtTime(health?.serverTime)}</strong>
          </div>
          <div className="quant-market-health-row">
            <span>本机时间差</span><strong>{health?.clockOffsetMs != null ? `${health.clockOffsetMs} ms` : "—"}</strong>
          </div>
          <div className="quant-market-health-row">
            <span>1 分钟请求权重</span><strong>{health?.usedWeight1m || "—"}</strong>
          </div>
          <div className="quant-market-health-row">
            <span>最后检查</span><strong>{fmtTime(health?.checkedAt)}</strong>
          </div>
        </article>
      </section>

      <section className="quant-market-toolbar" aria-label="合约选择">
        <label className="quant-market-select-label">
          <span>数据源</span>
          <select aria-label="行情数据源" value={provider} onChange={(e) => setProvider(e.target.value)}>
            {providers.map((p) => <option key={p.providerId} value={p.providerId}>{p.providerId}</option>)}
          </select>
        </label>
        <span className="quant-market-quote-label">报价资产：{DEFAULT_QUOTE}</span>
        <UiSearchField
          className="quant-market-search"
          aria-label="搜索合约"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="搜索 BTC、ETH…"
        />
        <div className="quant-market-intervals" aria-label="K 线周期">
          {INTERVALS.map((itv) => (
            <button type="button" key={itv.code} className={interval === itv.code ? "active" : ""} onClick={() => setIntervalValue(itv.code)}>{itv.label}</button>
          ))}
        </div>
        <button type="button" className="quant-market-refresh-klines" onClick={manualRefreshKlines}>
          <ArrowsClockwise />刷新 K 线
        </button>
      </section>

      <section className="quant-market-layout" aria-label="行情主区">
        <aside className="quant-market-symbols">
          <div className="quant-market-symbol-head">
            <span>USDT 永续合约</span><b>{filteredContracts.length}</b>
          </div>
          <div className="quant-market-symbol-list">
            {filteredContracts.map((c) => (
              <button type="button" key={c.symbol} className={symbol === c.symbol ? "active" : ""} onClick={() => setSymbol(c.symbol)}>
                <strong>{c.baseAsset}</strong><span>/{c.quoteAsset}</span>
              </button>
            ))}
            {!filteredContracts.length && <p>没有匹配的合约</p>}
          </div>
        </aside>

        <main className="quant-market-main">
          <div className="quant-market-ticker">
            <div>
              <span>{snapshot?.symbol || symbol || "—"}</span>
              <strong>{fmtPrice(snapshot?.lastPrice)}</strong>
            </div>
            <dl>
              <div><dt>标记价格</dt><dd>{fmtPrice(snapshot?.markPrice)}</dd></div>
              <div><dt>指数价格</dt><dd>{fmtPrice(snapshot?.indexPrice)}</dd></div>
              <div><dt>24h 涨跌</dt><dd className={positive ? "up" : "down"}>{fmtPct(snapshot?.priceChangePercent)}</dd></div>
              <div><dt>24h 最高</dt><dd>{fmtPrice(snapshot?.highPrice)}</dd></div>
              <div><dt>24h 最低</dt><dd>{fmtPrice(snapshot?.lowPrice)}</dd></div>
              <div><dt>24h 成交量</dt><dd>{fmtQty(snapshot?.volume)}</dd></div>
              <div><dt>24h 成交额</dt><dd>{fmtQty(snapshot?.quoteVolume)}</dd></div>
              <div><dt>资金费率</dt><dd>{fmtPct(snapshot?.lastFundingRate)}</dd></div>
              <div><dt>下次资金费</dt><dd>{fmtTime(snapshot?.nextFundingTime)}</dd></div>
              <div><dt>未平仓量</dt><dd>{fmtQty(snapshot?.openInterest)}</dd></div>
              <div><dt>最佳买价</dt><dd>{fmtPrice(snapshot?.bidPrice)}</dd></div>
              <div><dt>最佳卖价</dt><dd>{fmtPrice(snapshot?.askPrice)}</dd></div>
              <div><dt>买卖价差</dt><dd>{fmtNum(snapshot?.spread)}</dd></div>
            </dl>
          </div>

          <div className="quant-market-chart">
            {chartData.length ? (
              <ResponsiveContainer width="100%" height="100%">
                <AreaChart data={chartData} margin={{ top: 22, right: 20, left: 0, bottom: 4 }}>
                  <defs>
                    <linearGradient id="quantPriceFill" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="0%" stopColor="var(--accent-primary)" stopOpacity=".42" />
                      <stop offset="100%" stopColor="var(--accent-primary)" stopOpacity="0" />
                    </linearGradient>
                  </defs>
                  <CartesianGrid stroke="var(--border-normal)" strokeDasharray="3 6" vertical={false} />
                  <XAxis dataKey="label" stroke="var(--text-muted)" tick={{ fontSize: 9 }} minTickGap={45} />
                  <YAxis domain={["auto", "auto"]} orientation="right" stroke="var(--text-muted)" tick={{ fontSize: 9 }} tickFormatter={(v) => Number(v).toLocaleString("zh-CN", { notation: "compact", maximumFractionDigits: 2 })} />
                  <Tooltip contentStyle={{ background: "var(--bg-card)", border: "1px solid var(--border-normal)", borderRadius: 10, fontSize: 10 }} formatter={(value) => [fmtPrice(value), "收盘价"]} />
                  <Area type="monotone" dataKey="close" stroke="var(--accent-primary)" strokeWidth={2} fill="url(#quantPriceFill)" isAnimationActive={false} />
                </AreaChart>
              </ResponsiveContainer>
            ) : (
              <div className="quant-market-chart-empty"><ChartLineUp /><span>{snapshotError ? "K 线加载失败" : "加载 K 线…"}</span></div>
            )}
          </div>
        </main>
      </section>

      <section className="quant-market-contract-rules" aria-label="合约规则">
        <header className="quant-section-head">
          <h4><Lightning weight="duotone" />合约规则</h4>
          <small>用于核对精度和过滤器，本次不提供下单</small>
        </header>
        {contractDetail ? (
          <div className="quant-market-rules-grid">
            <div><span>symbol</span><strong>{contractDetail.symbol}</strong></div>
            <div><span>contractType</span><strong>{contractDetail.contractType}</strong></div>
            <div><span>status</span><strong>{contractDetail.status}</strong></div>
            <div><span>baseAsset</span><strong>{contractDetail.baseAsset}</strong></div>
            <div><span>quoteAsset</span><strong>{contractDetail.quoteAsset}</strong></div>
            <div><span>marginAsset</span><strong>{contractDetail.marginAsset}</strong></div>
            <div><span>onboardDate</span><strong>{fmtTime(contractDetail.onboardDate)}</strong></div>
            <div><span>tickSize</span><strong>{fmtNum(contractDetail.tickSize)}</strong></div>
            <div><span>stepSize</span><strong>{fmtNum(contractDetail.stepSize)}</strong></div>
            <div><span>minQty</span><strong>{fmtNum(contractDetail.minQty)}</strong></div>
            <div><span>maxQty</span><strong>{fmtNum(contractDetail.maxQty)}</strong></div>
            <div><span>marketStepSize</span><strong>{fmtNum(contractDetail.marketStepSize)}</strong></div>
            <div><span>marketMinQty</span><strong>{fmtNum(contractDetail.marketMinQty)}</strong></div>
            <div><span>minNotional</span><strong>{fmtNum(contractDetail.minNotional)}</strong></div>
            <div><span>pricePrecision</span><strong>{contractDetail.pricePrecision}</strong></div>
            <div><span>quantityPrecision</span><strong>{contractDetail.quantityPrecision}</strong></div>
          </div>
        ) : (
          <p className="quant-market-empty">合约规则不可用</p>
        )}
      </section>

      <section className="quant-market-klines-table" aria-label="最近 K 线">
        <header className="quant-section-head">
          <h4><ChartLineUp weight="duotone" />最近 K 线</h4>
          <small>最近 10 根 · 时间为本地时区</small>
        </header>
        <div className="quant-market-table-wrap">
          <table>
            <thead>
              <tr>
                <th>开盘时间</th><th>开</th><th>高</th><th>低</th><th>收</th>
                <th>成交量</th><th>成交额</th><th>成交笔数</th><th>已闭合</th>
              </tr>
            </thead>
            <tbody>
              {recentCandles.map((k, i) => (
                <tr key={`${k.openTime}-${i}`}>
                  <td>{fmtTime(k.openTime)}</td>
                  <td>{fmtPrice(k.open)}</td>
                  <td>{fmtPrice(k.high)}</td>
                  <td>{fmtPrice(k.low)}</td>
                  <td>{fmtPrice(k.close)}</td>
                  <td>{fmtQty(k.volume)}</td>
                  <td>{fmtQty(k.quoteVolume)}</td>
                  <td>{k.tradeCount != null ? k.tradeCount : "—"}</td>
                  <td>{k.closed ? "是" : "否"}</td>
                </tr>
              ))}
              {!recentCandles.length && (
                <tr><td colSpan={9} className="quant-market-empty">暂无 K 线数据</td></tr>
              )}
            </tbody>
          </table>
        </div>
      </section>

      <section className="quant-market-source-note" aria-label="数据来源说明">
        <Info weight="duotone" />
        <div>
          <strong>数据来源</strong>
          <p>Binance USDⓈ-M Futures 官方公共 REST 行情 · 不使用 API Key · 不读取账户 · 不提供下单 · 不经过 CCXT · 不写入数据库</p>
        </div>
      </section>
    </QuantPageScaffold>
  );
}
