import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  ArrowsClockwise,
  ChartLineUp,
  Info,
  Lightning,
  Warning,
} from "@phosphor-icons/react";
import { readJsonResponse } from "../apiResponse";
import UiSearchField from "../UiSearchField";
import QuantPageScaffold from "./QuantPageScaffold";
import QuantCandlestickChart from "./QuantCandlestickChart";
import {
  useQuantMarketSocket,
  SOCKET_STATUS,
  SOCKET_STATUS_LABELS,
} from "./useQuantMarketSocket";
import { mergeKline, mergeSnapshot } from "./quantMarketReducer";
import {
  fieldLabel,
  contractTypeLabel,
  statusLabel,
  providerLabel,
  intervalLabel,
} from "./quantMarketLabels";
import "./QuantMarket.css";

const API_BASE = "/api/quant/market";
const DEFAULT_PROVIDER = "BINANCE_USDM";
const DEFAULT_QUOTE = "USDT";
const DEFAULT_SYMBOL = "BTCUSDT";
const SNAP_INTERVAL_MS = 15_000;
const INTERVALS = ["1m", "5m", "15m", "1h", "4h", "1d"];

// 合约规则字段渲染顺序与类型，决定取中文标签与值格式化方式。
const RULE_FIELDS = [
  { key: "symbol", kind: "text" },
  { key: "contractType", kind: "contractType" },
  { key: "status", kind: "status" },
  { key: "baseAsset", kind: "text" },
  { key: "quoteAsset", kind: "text" },
  { key: "marginAsset", kind: "text" },
  { key: "onboardDate", kind: "time" },
  { key: "tickSize", kind: "num" },
  { key: "minPrice", kind: "num" },
  { key: "maxPrice", kind: "num" },
  { key: "stepSize", kind: "num" },
  { key: "minQty", kind: "num" },
  { key: "maxQty", kind: "num" },
  { key: "marketStepSize", kind: "num" },
  { key: "marketMinQty", kind: "num" },
  { key: "marketMaxQty", kind: "num" },
  { key: "minNotional", kind: "num" },
  { key: "pricePrecision", kind: "text" },
  { key: "quantityPrecision", kind: "text" },
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

// 合约规则字段取值：contractType/status 翻译为中文，数值类格式化，其余原样。
function ruleValue(kind, raw) {
  if (raw == null || raw === "") return "—";
  switch (kind) {
    case "contractType": return contractTypeLabel(raw);
    case "status": return statusLabel(raw);
    case "time": return fmtTime(raw);
    case "num": return fmtNum(raw);
    default: return String(raw);
  }
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
  const [restKlines, setRestKlines] = useState([]);
  const [lastKlineUpdate, setLastKlineUpdate] = useState(null);
  const [contractDetail, setContractDetail] = useState(null);
  const [phase, setPhase] = useState("initial-loading");
  const [error, setError] = useState("");
  const [snapshotError, setSnapshotError] = useState("");
  const [reconnectKey, setReconnectKey] = useState(0);
  // 图表 series.update 异常上报（来自 QuantCandlestickChart）。
  const [chartUpdateError, setChartUpdateError] = useState(null);
  const snapshotSeq = useRef(0);
  const klineSeq = useRef(0);

  // WebSocket 仅在初始加载完成且选中合约后启用。
  const ws = useQuantMarketSocket({
    provider,
    symbol,
    interval,
    enabled: phase === "ready" && !!symbol,
  });

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

  // Phase 1: 初始加载 —— health、providers、contracts，再确定 symbol。
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

  // Phase 2: symbol/interval 变化时加载快照与初始 K 线。
  useEffect(() => {
    if (!symbol || phase === "initial-loading" || phase === "error") return;
    const mySeq = ++snapshotSeq.current;
    const myKlineSeq = ++klineSeq.current;
    setSnapshotError("");
    setRestKlines([]);
    setKlines([]);
    setLastKlineUpdate(null);
    setChartUpdateError(null);
    Promise.all([loadSnapshot(symbol), loadKlines(symbol, interval)])
      .then(([snap, ks]) => {
        if (snapshotSeq.current !== mySeq) return;
        if (klineSeq.current !== myKlineSeq) return;
        setSnapshot(snap);
        setRestKlines(ks);
        setKlines(ks);
        const detail = contracts.find((c) => c.symbol === symbol);
        setContractDetail(detail || null);
      })
      .catch((e) => {
        if (snapshotSeq.current !== mySeq) return;
        setSnapshotError(e.message || "行情快照加载失败");
      });
  }, [symbol, interval, phase, contracts, loadSnapshot, loadKlines]);

  // Phase 3: 未平仓量每 15 秒 REST 刷新（WebSocket 不提供该字段），不覆盖 WS 已更新字段。
  useEffect(() => {
    if (!symbol || phase !== "ready") return;
    const timer = window.setInterval(() => {
      loadSnapshot(symbol)
        .then((snap) => {
          if (!snap) return;
          setSnapshot((prev) => (prev ? { ...prev, openInterest: snap.openInterest } : prev));
        })
        .catch((e) => {
          // 未平仓量刷新失败不覆盖已有数据，仅记录警告，避免静默跳过。
          console.warn("未平仓量刷新失败", e?.message);
        });
    }, SNAP_INTERVAL_MS);
    return () => window.clearInterval(timer);
  }, [symbol, phase, loadSnapshot]);

  // WebSocket: K 线增量合并进表格数据，并产出图表单根更新。
  useEffect(() => {
    if (!ws.klineEvent) {
      setLastKlineUpdate(null);
      return;
    }
    setKlines((prev) => {
      const { candles, changed } = mergeKline(prev, ws.klineEvent);
      return changed ? candles : prev;
    });
    setLastKlineUpdate(ws.klineEvent.data);
  }, [ws.klineEvent]);

  // WebSocket: TICKER 合并进快照。
  useEffect(() => {
    if (!ws.tickerEvent) return;
    setSnapshot((prev) => mergeSnapshot(prev, ws.tickerEvent, "TICKER"));
  }, [ws.tickerEvent]);

  // WebSocket: MARK_PRICE 合并进快照。
  useEffect(() => {
    if (!ws.markPriceEvent) return;
    setSnapshot((prev) => mergeSnapshot(prev, ws.markPriceEvent, "MARK_PRICE"));
  }, [ws.markPriceEvent]);

  // WebSocket: BOOK_TICKER 合并进快照。
  useEffect(() => {
    if (!ws.bookTickerEvent) return;
    setSnapshot((prev) => mergeSnapshot(prev, ws.bookTickerEvent, "BOOK_TICKER"));
  }, [ws.bookTickerEvent]);

  const reconnect = () => setReconnectKey((k) => k + 1);
  const manualRefreshKlines = () => {
    if (!symbol) return;
    const myKlineSeq = ++klineSeq.current;
    loadKlines(symbol, interval)
      .then((ks) => {
        if (klineSeq.current !== myKlineSeq) return;
        setRestKlines(ks);
        setKlines(ks);
      })
      .catch((e) => setSnapshotError(e.message || "K 线刷新失败"));
  };
  // QuantCandlestickChart series.update 异常回调：null 表示清除上次错误。
  const handleChartUpdateError = useCallback((err) => {
    setChartUpdateError(err);
  }, []);

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
  const recentCandles = klines.slice(-10).reverse();
  const wsLive = ws.status === SOCKET_STATUS.LIVE;
  const wsLabel = wsLive ? "实时行情" : SOCKET_STATUS_LABELS[ws.status];
  const showReconnect = ws.status === SOCKET_STATUS.FAILED
    || ws.status === SOCKET_STATUS.DISCONNECTED
    || ws.status === SOCKET_STATUS.RECONNECTING;

  return (
    <QuantPageScaffold pageClass="quant-market-page" title="合约行情">
      <div className="quant-workspace-head">
        <div>
          <span className="eyebrow">BINANCE · USDⓈ-M PERPETUAL</span>
          <h3>合约行情</h3>
          <small>Binance U 本位永续公共只读行情 · WebSocket 实时推送 · 不经过 CCXT</small>
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

      {chartUpdateError && (
        <div className="quant-market-notice" role="alert">
          <Warning weight="fill" />K 线更新异常 · {chartUpdateError.error} · 开盘时间 {fmtTime(chartUpdateError.openTime)}
        </div>
      )}

      <section className="quant-market-health" aria-label="连接状态">
        <article className="quant-market-health-card">
          <div className="quant-market-health-head">
            <span>数据源</span><strong>{providerLabel(provider)}</strong>
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
            <span>REST 连接状态</span>
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
            {providers.map((p) => <option key={p.providerId} value={p.providerId}>{providerLabel(p.providerId)}</option>)}
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
            <button type="button" key={itv} className={interval === itv ? "active" : ""} onClick={() => setIntervalValue(itv)}>{intervalLabel(itv)}</button>
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

          <div className="quant-market-ws-status" role="status" aria-live="polite">
            <span className={`ws-dot ws-${ws.status.toLowerCase()}`} aria-hidden="true" />
            <span className="ws-label">{wsLabel}</span>
            {ws.lastKlineTime && <span className="ws-time">最后 K 线 · {fmtTime(ws.lastKlineTime)}</span>}
            {ws.klineStale && wsLive && <span className="ws-stale">K 线流无更新</span>}
            {ws.error && <span className="ws-error">{ws.error}</span>}
            {showReconnect && (
              <button type="button" className="ws-reconnect" onClick={ws.reconnect}>
                <ArrowsClockwise />重连行情
              </button>
            )}
          </div>

          <div className="quant-market-chart">
            {restKlines.length ? (
              <QuantCandlestickChart
                key={`${symbol}-${interval}`}
                candles={restKlines}
                update={lastKlineUpdate}
                onUpdateError={handleChartUpdateError}
              />
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
            {RULE_FIELDS.map(({ key, kind }) => {
              const label = fieldLabel(key);
              return (
                <div key={key}>
                  <span className="rule-zh">{label.zh}</span>
                  <small className="rule-en">{label.en}</small>
                  <strong>{ruleValue(kind, contractDetail[key])}</strong>
                </div>
              );
            })}
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
          <p>Binance USDⓈ-M Futures 官方公共行情 · REST 加载初始快照与 K 线 · WebSocket 实时推送增量 · 不使用 API Key · 不读取账户 · 不提供下单 · 不经过 CCXT · 不写入数据库</p>
        </div>
      </section>
    </QuantPageScaffold>
  );
}
