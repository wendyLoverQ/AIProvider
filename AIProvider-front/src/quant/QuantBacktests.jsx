import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { ArrowsClockwise, CaretLeft, CaretRight, ChartLineUp, Flask, Warning, X } from "@phosphor-icons/react";
import { Area, AreaChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import QuantPageScaffold from "./QuantPageScaffold";
import {
  calculateExpectedBars, compareDecimalStrings, formatDecimal, formatInstant, formatRatio, formatRunStatus, intervalCode, intervalDurationMs, isPositiveDecimal, normalizeDecimalString, toUtcIso, utcInstantToLocalInput,
} from "./quantBacktestsFormat";
import {
  createBacktestRun, fetchDatasets, fetchEquity, fetchNonTerminalRuns, fetchRunDetail, fetchRuns, fetchStrategies, fetchTrades,
} from "./quantBacktestsApi";
import "./QuantBacktests.css";

const TERMINAL = new Set(["COMPLETED", "FAILED"]);
const validDataset = (item) => item && Number.isFinite(Number(item.id)) && item.status === "CONTIGUOUS" && item.gapCount === 0 && item.gapSegmentCount === 0 && item.earliestOpenTime && item.latestOpenTime && item.lastValidatedAt && Number(item.candleCount) > 0;

function LoadState({ label }) { return <div className="quant-loading" role="status">{label}</div>; }
function ErrorState({ label, error, retry }) { return <div className="quant-error" role="alert"><Warning weight="fill" /><div><strong>{label}</strong><span>{error}</span></div><button type="button" className="quant-error-retry" onClick={retry}><ArrowsClockwise />重试</button></div>; }
function Metric({ label, value, tone }) { return <div className={`backtest-metric ${tone || ""}`}><span>{label}</span><strong>{value}</strong></div>; }

function CreatePanel({ strategies, datasets, onClose, onCreated }) {
  const [datasetId, setDatasetId] = useState("");
  const [strategyCode, setStrategyCode] = useState("");
  const [start, setStart] = useState("");
  const [end, setEnd] = useState("");
  const [params, setParams] = useState({});
  const [orderAmount, setOrderAmount] = useState("1");
  const [feeRate, setFeeRate] = useState("0");
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);
  const dataset = datasets.find((item) => String(item.id) === String(datasetId));
  const strategy = strategies.find((item) => item.code === strategyCode);
  useEffect(() => { const closeOnEscape = (event) => { if (event.key === "Escape") onClose(); }; document.addEventListener("keydown", closeOnEscape); return () => document.removeEventListener("keydown", closeOnEscape); }, [onClose]);

  const selectDataset = (value) => {
    const selected = datasets.find((item) => String(item.id) === String(value));
    setDatasetId(value);
    if (!selected) return;
    const duration = intervalDurationMs(selected.interval);
    setStart(utcInstantToLocalInput(selected.earliestOpenTime));
    setEnd(duration ? utcInstantToLocalInput(new Date(new Date(selected.latestOpenTime).getTime() + duration).toISOString()) : "");
  };
  const selectStrategy = (value) => {
    const selected = strategies.find((item) => item.code === value);
    setStrategyCode(value);
    setParams(Object.fromEntries((selected?.parameters || []).map((parameter) => [parameter.name, String(parameter.defaultValue)])));
  };
  const expectedBars = dataset ? calculateExpectedBars(toUtcIso(start), toUtcIso(end), dataset.interval) : null;
  const submit = async (event) => {
    event.preventDefault(); setError("");
    const startIso = toUtcIso(start); const endIso = toUtcIso(end);
    const invalidParameter = (strategy?.parameters || []).find((parameter) => !/^\d+$/.test(params[parameter.name] || "") || Number(params[parameter.name]) < parameter.minValue || Number(params[parameter.name]) > parameter.maxValue);
    if (!dataset || !strategy || !startIso || !endIso) return setError("请完整选择数据集、策略和时间范围");
    if (!intervalDurationMs(dataset.interval)) return setError("当前周期暂不支持前端时间计算");
    const duration = intervalDurationMs(dataset.interval);
    if (new Date(startIso).getTime() % duration !== 0 || new Date(endIso).getTime() % duration !== 0) return setError("开始和结束时间必须对齐数据集周期");
    if (new Date(startIso) >= new Date(endIso) || new Date(startIso) < new Date(dataset.earliestOpenTime) || new Date(endIso).getTime() > new Date(dataset.latestOpenTime).getTime() + intervalDurationMs(dataset.interval)) return setError("时间范围必须有效且处于数据集范围内");
    if (!expectedBars || expectedBars < (strategy.minimumRequiredBars || 1)) return setError(`预计 K 线数不足，至少需要 ${strategy.minimumRequiredBars} 根`);
    if (invalidParameter) return setError(`${invalidParameter.name} 必须为 ${invalidParameter.minValue}～${invalidParameter.maxValue} 的整数`);
    if (!isPositiveDecimal(orderAmount)) return setError("数量必须是大于 0 的十进制字符串");
    if (!normalizeDecimalString(orderAmount)) return setError("数量整数最多 38 位，小数最多 18 位");
    if (normalizeDecimalString(feeRate) == null || compareDecimalStrings(feeRate, "0") < 0 || compareDecimalStrings(feeRate, "0.01") > 0) return setError("手续费必须是 0～0.01 的十进制字符串");
    setSaving(true);
    try {
      const run = await createBacktestRun({ datasetId: Number(dataset.id), startOpenTimeInclusive: startIso, endOpenTimeExclusive: endIso, strategyCode, strategyVersion: strategy.version, strategyParameters: Object.fromEntries(Object.entries(params).map(([key, value]) => [key, Number(value)])), orderAmount, feeRate, forceCloseAtEnd: true });
      onCreated(run); onClose();
    } catch (exception) { setError(exception.message || "创建回测失败"); } finally { setSaving(false); }
  };
  return <aside className="backtest-create-panel" role="dialog" aria-modal="true" aria-label="新建回测"><div className="backtest-panel-head"><div><span className="eyebrow">NEW EXPERIMENT</span><h3>新建回测</h3></div><button type="button" aria-label="关闭新建回测" onClick={onClose}><X /></button></div><form onSubmit={submit}>
    <label>连续历史数据集<select autoFocus value={datasetId} onChange={(event) => selectDataset(event.target.value)}><option value="">请选择已校验数据集</option>{datasets.map((item) => <option key={item.id} value={item.id}>{item.symbol} · {intervalCode(item.interval)} · {item.candleCount?.toLocaleString()} 根</option>)}</select></label>
    {dataset && <p className="backtest-help">{formatInstant(dataset.earliestOpenTime)} ～ {formatInstant(dataset.latestOpenTime)} · 已校验 · 结束时间不包含</p>}
    <label>策略<select value={strategyCode} onChange={(event) => selectStrategy(event.target.value)}><option value="">请选择策略</option>{strategies.map((item) => <option key={item.code} value={item.code}>{item.name} · {item.version}</option>)}</select></label>
    {strategy?.parameters?.map((parameter) => <label key={parameter.name}>{parameter.name}<input type="number" step="1" value={params[parameter.name] || ""} min={parameter.minValue} max={parameter.maxValue} onChange={(event) => setParams((current) => ({ ...current, [parameter.name]: event.target.value }))} /><small>默认 {parameter.defaultValue} · 范围 {parameter.minValue}～{parameter.maxValue}</small></label>)}
    <div className="backtest-form-grid"><label>开始时间<input type="datetime-local" value={start} onChange={(event) => setStart(event.target.value)} /></label><label>结束时间（不包含）<input type="datetime-local" value={end} onChange={(event) => setEnd(event.target.value)} /></label></div>
    <div className="backtest-form-grid"><label>下单数量<input inputMode="decimal" value={orderAmount} onChange={(event) => setOrderAmount(event.target.value)} /></label><label>手续费比例<input inputMode="decimal" value={feeRate} onChange={(event) => setFeeRate(event.target.value)} /><small>例如 0.001 表示 0.1%</small></label></div>
    <p className="backtest-help">提交时按 UTC 绝对时间保存。预计 K 线：{expectedBars ?? "—"} 根</p><p className="backtest-help">固定强制平仓：结束仍持仓时按最后一根 K 线收盘价平仓。</p>
    {error && <div className="backtest-inline-error" role="alert">{error}</div>}<button className="quant-primary-action" type="submit" disabled={saving || !strategies.length || !datasets.length}>{saving ? "正在创建" : "创建异步回测"}</button>
  </form></aside>;
}

function RunDetail({ run, loading, error, retry }) {
  const [equity, setEquity] = useState(null); const [equityError, setEquityError] = useState("");
  useEffect(() => { if (!run?.runId || run.status !== "COMPLETED") return undefined; const controller = new AbortController(); setEquity(null); setEquityError(""); fetchEquity(run.runId, 1200, controller.signal).then(setEquity).catch((exception) => { if (exception.name !== "AbortError") setEquityError(exception.message); }); return () => controller.abort(); }, [run]);
  if (loading) return <LoadState label="正在读取任务详情…" />;
  if (error) return <ErrorState label="任务详情加载失败" error={error} retry={retry} />;
  if (!run) return <div className="backtest-empty">选择一个任务查看详情</div>;
  const metrics = run.metrics || {};
  const points = equity?.points || [];
  const validPoints = points.filter((point, index) => point && Number.isInteger(point.pointIndex) && point.pointIndex === index && !Number.isNaN(new Date(point.openTime).getTime()) && Number.isFinite(Number(point.equityRatio)) && Number.isFinite(Number(point.drawdownRatio)));
  const invalidEquity = points.length !== validPoints.length;
  return <div className="backtest-detail"><section className="backtest-card"><header className="quant-section-head"><h4>任务详情</h4><span className={`backtest-status status-${run.status?.toLowerCase()}`}>{formatRunStatus(run.status)}</span></header><dl className="backtest-detail-grid">{[["runId", run.runId], ["datasetId", run.datasetId], ["交易对 / 周期", `${run.symbol || "—"} · ${intervalCode(run.intervalCode)}`], ["策略", `${run.strategyCode || "—"} · ${run.strategyVersion || "—"}`], ["请求参数", JSON.stringify(run.requestedParameters || {})], ["解析参数", JSON.stringify(run.resolvedParameters || {})], ["回测区间", `${formatInstant(run.startOpenTimeInclusive)} ～ ${formatInstant(run.endOpenTimeExclusive)}`], ["数量 / 手续费", `${run.orderAmount ?? "—"} / ${run.feeRate ?? "—"}`], ["K 线数 / 交易数", `${run.barCount ?? "—"} / ${run.tradeCount ?? "—"}`], ["阶段", run.status], ["开始 / 完成", `${formatInstant(run.startedAt)} / ${formatInstant(run.finishedAt)}`]].map(([label, value]) => <div key={label}><dt>{label}</dt><dd>{value || "—"}</dd></div>)}</dl>{run.status === "FAILED" && <div className="backtest-failure"><strong>{run.errorCode || "FAILED"}</strong><span>{run.errorMessage || "回测失败"}</span><span>失败前进度：{run.progressPercent ?? "—"}</span></div>}</section>{run.status === "COMPLETED" && <><section className="backtest-metrics">{[["总收益率", formatRatio(metrics.totalReturnRatio), metrics.totalReturnRatio >= 0 ? "positive" : "negative"], ["最大回撤", formatRatio(metrics.maximumDrawdownRatio)], ["胜率", formatRatio(metrics.winRate)], ["交易数", formatDecimal(run.tradeCount ?? metrics.tradeCount, 0)], ["净利润", formatDecimal(metrics.netProfit)], ["Profit Factor", formatDecimal(metrics.profitFactor)], ["买入持有", formatRatio(metrics.buyAndHoldReturnRatio)], ["总手续费", formatDecimal(metrics.totalFees)]].map(([label, value, tone]) => <Metric key={label} label={label} value={value} tone={tone} />)}</section><section className="backtest-card"><header className="quant-section-head"><h4><ChartLineUp />权益曲线</h4><small>{equity?.sampled ? `图表展示 ${validPoints.length} / ${equity.totalPoints} 个抽样点，完整结果保存在服务器` : ""}</small></header>{equityError ? <ErrorState label="权益曲线加载失败" error={equityError} retry={() => setEquityError("")} /> : invalidEquity ? <div className="backtest-empty">权益曲线数据格式异常</div> : validPoints.length ? <div className="backtest-chart" aria-label="权益和回撤曲线"><ResponsiveContainer width="100%" height="100%"><AreaChart data={validPoints}><CartesianGrid stroke="var(--border-subtle)" /><XAxis dataKey="openTime" tickFormatter={(value) => new Date(value).toLocaleDateString("zh-CN")} /><YAxis tickFormatter={(value) => `${(Number(value) * 100).toFixed(0)}%`} /><Tooltip formatter={(value, name) => [formatRatio(value), name === "equityRatio" ? "权益变化" : "回撤"]} labelFormatter={formatInstant} /><Area type="monotone" dataKey="equityRatio" stroke="var(--accent-primary)" fill="var(--accent-primary)" fillOpacity={0.12} /><Area type="monotone" dataKey="drawdownRatio" stroke="var(--accent-red)" fill="var(--accent-red)" fillOpacity={0.08} /></AreaChart></ResponsiveContainer></div> : <div className="backtest-empty">权益曲线不可用</div>}</section></>}</div>;
}

function Trades({ run }) { const [page, setPage] = useState(1); const [state, setState] = useState("idle"); const [data, setData] = useState({ records: [], total: 0 }); useEffect(() => { setPage(1); }, [run?.runId]); useEffect(() => { if (!run?.runId || run.status !== "COMPLETED") return undefined; const controller = new AbortController(); setState("loading"); fetchTrades(run.runId, page, 100, controller.signal).then((result) => { setData(result); setState("ready"); }).catch((exception) => { if (exception.name !== "AbortError") setState("error"); }); return () => controller.abort(); }, [run, page]); if (run?.status !== "COMPLETED") return null; const totalPages = Math.max(1, Math.ceil(data.total / 100)); return <section className="backtest-card"><header className="quant-section-head"><h4>交易记录</h4><small>{data.total} 条</small></header>{state === "loading" ? <LoadState label="正在读取交易…" /> : state === "error" ? <div className="backtest-empty">交易记录加载失败</div> : <><div className="backtest-table-wrap"><table><thead><tr>{["编号", "入场时间", "入场价", "退出时间", "退出价", "数量", "净利润", "收益率", "持有 Bar", "退出原因"].map((label) => <th key={label}>{label}</th>)}</tr></thead><tbody>{data.records.map((trade) => <tr key={trade.tradeNo}><td>{trade.tradeNo}</td><td>{formatInstant(trade.entryTime)}</td><td>{formatDecimal(trade.entryPrice)}</td><td>{formatInstant(trade.exitTime)}</td><td>{formatDecimal(trade.exitPrice)}</td><td>{formatDecimal(trade.amount)}</td><td>{formatDecimal(trade.netProfit)}</td><td>{formatRatio(trade.returnRatio)}</td><td>{trade.barsHeld ?? "—"}</td><td>{trade.exitReason === "END_OF_SERIES" || trade.forcedExit ? "期末强平" : "策略退出"}</td></tr>)}{!data.records.length && <tr><td colSpan="10" className="backtest-empty">已完成但没有交易</td></tr>}</tbody></table></div><div className="backtest-pagination"><button type="button" disabled={page <= 1} onClick={() => setPage((value) => value - 1)}><CaretLeft />上一页</button><span>第 {page} / {totalPages} 页</span><button type="button" disabled={page >= totalPages} onClick={() => setPage((value) => value + 1)}>下一页<CaretRight /></button></div></>}</section>; }

export default function QuantBacktests() {
  const [strategies, setStrategies] = useState([]); const [datasets, setDatasets] = useState([]); const [invalidDatasetCount, setInvalidDatasetCount] = useState(0); const [runs, setRuns] = useState([]); const [selectedId, setSelectedId] = useState(null); const [detail, setDetail] = useState(null); const [errors, setErrors] = useState({}); const [loading, setLoading] = useState(true); const [refreshing, setRefreshing] = useState(false); const [showCreate, setShowCreate] = useState(false); const detailRequest = useRef(0); const createButtonRef = useRef(null);
  const loadLists = useCallback(async (signal) => { setRefreshing(true); setErrors({}); const [strategyResult, datasetResult, runResult] = await Promise.allSettled([fetchStrategies(signal), fetchDatasets(signal), fetchRuns(1, 20, signal)]); if (strategyResult.status === "fulfilled") setStrategies(strategyResult.value); else if (strategyResult.reason?.name !== "AbortError") setErrors((current) => ({ ...current, strategies: strategyResult.reason.message })); if (datasetResult.status === "fulfilled") { const all = datasetResult.value.records; setDatasets(all.filter(validDataset)); setInvalidDatasetCount(all.filter((item) => !validDataset(item)).length); } else if (datasetResult.reason?.name !== "AbortError") setErrors((current) => ({ ...current, datasets: datasetResult.reason.message })); if (runResult.status === "fulfilled") setRuns(runResult.value.records); else if (runResult.reason?.name !== "AbortError") setErrors((current) => ({ ...current, runs: runResult.reason.message })); setLoading(false); setRefreshing(false); }, []);
  const loadDetail = useCallback((id) => { if (!id) return; const requestId = ++detailRequest.current; const controller = new AbortController(); setDetail(null); fetchRunDetail(id, controller.signal).then((result) => { if (requestId === detailRequest.current) setDetail(result); }).catch((exception) => { if (requestId === detailRequest.current && exception.name !== "AbortError") setErrors((current) => ({ ...current, detail: exception.message })); }); return () => controller.abort(); }, []);
  useEffect(() => { const controller = new AbortController(); loadLists(controller.signal); return () => controller.abort(); }, [loadLists]);
  useEffect(() => { setErrors((current) => ({ ...current, detail: "" })); if (selectedId) return loadDetail(selectedId); return undefined; }, [selectedId, loadDetail]);
  const refresh = useCallback(async () => { const controller = new AbortController(); await loadLists(controller.signal); if (selectedId) loadDetail(selectedId); }, [loadLists, loadDetail, selectedId]);
  useEffect(() => { let timer; let active = false; let controller; const poll = async () => { if (document.visibilityState !== "visible" || active) return; active = true; controller = new AbortController(); try { const result = await fetchNonTerminalRuns(controller.signal); if (!Array.isArray(result)) throw new Error("回测服务响应格式异常"); if (result.length) await refresh(); } catch (exception) { if (exception.name !== "AbortError") setErrors((current) => ({ ...current, runs: exception.message })); } finally { active = false; } }; const schedule = () => { clearInterval(timer); if (document.visibilityState === "visible") { poll(); timer = window.setInterval(poll, 3000); } else if (controller) controller.abort(); }; document.addEventListener("visibilitychange", schedule); schedule(); return () => { document.removeEventListener("visibilitychange", schedule); if (controller) controller.abort(); }; }, [refresh]);
  const selectedRun = runs.find((run) => String(run.runId) === String(selectedId)); const counts = useMemo(() => ({ active: runs.filter((run) => !TERMINAL.has(run.status)).length, completed: runs.filter((run) => run.status === "COMPLETED").length, failed: runs.filter((run) => run.status === "FAILED").length }), [runs]);
  return <QuantPageScaffold pageClass="quant-backtests-page"><div className="quant-workspace-head"><div><span className="eyebrow">QUANT · BACKTEST LAB</span><h3>回测实验</h3><small>基于已校验历史数据运行确定性策略回测</small></div><div className="backtest-head-actions"><button type="button" className="quant-refresh" onClick={refresh} disabled={refreshing}><ArrowsClockwise className={refreshing ? "spin" : ""} />刷新</button><button ref={createButtonRef} type="button" className="quant-primary-action" onClick={() => setShowCreate(true)}><Flask />新建回测</button></div></div>
    <div className="quant-status-grid backtest-summary"><Metric label="排队 / 运行中" value={counts.active} /><Metric label="本页已完成" value={counts.completed} /><Metric label="本页失败" value={counts.failed} /><Metric label="当前选中任务" value={selectedId || "—"} /></div>
    {errors.strategies && <div className="backtest-notice" role="alert">策略不可用：{errors.strategies}</div>}{errors.datasets && <div className="backtest-notice" role="alert">数据集不可用：{errors.datasets}</div>}{invalidDatasetCount > 0 && <div className="backtest-notice" role="alert">已隐藏 {invalidDatasetCount} 个不满足连续性或校验条件的数据集</div>}{errors.runs && <ErrorState label="任务列表加载失败" error={errors.runs} retry={refresh} />}
    {loading ? <LoadState label="正在读取回测工作台…" /> : <div className="backtest-main-grid"><section className="backtest-card backtest-run-list"><header className="quant-section-head"><h4>回测任务</h4><small>{runs.length ? "当前页 20 条以内" : "还没有创建回测"}</small></header>{!runs.length ? <div className="backtest-empty">还没有创建回测</div> : runs.map((run) => <button type="button" className={`backtest-run-row ${String(run.runId) === String(selectedId) ? "selected" : ""}`} key={run.runId} onClick={() => setSelectedId(run.runId)}><span className={`backtest-status status-${run.status?.toLowerCase()}`}>{formatRunStatus(run.status)}</span><strong>{run.symbol || "—"} · {intervalCode(run.intervalCode)}</strong><span>{run.strategyCode || "—"}</span><span>{formatInstant(run.startOpenTimeInclusive)} ～ {formatInstant(run.endOpenTimeExclusive)}</span><span>Bar {run.barCount ?? "—"} · 交易 {run.tradeCount ?? "—"} · {run.status === "COMPLETED" ? formatRatio(run.metrics?.totalReturnRatio) : "—"} · 回撤 {run.status === "COMPLETED" ? formatRatio(run.metrics?.maximumDrawdownRatio) : "—"}</span><span>排队 {formatInstant(run.queuedAt)}{run.status === "FAILED" ? ` · ${run.errorCode || "FAILED"}` : ""}</span></button>)}</section><div><RunDetail run={detail || selectedRun} loading={Boolean(selectedId) && !detail && !errors.detail} error={errors.detail} retry={() => loadDetail(selectedId)} /><Trades run={detail || selectedRun} /></div></div>}
    {showCreate && <div className="backtest-modal-backdrop" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) { setShowCreate(false); createButtonRef.current?.focus(); } }}><CreatePanel strategies={strategies} datasets={datasets} onClose={() => { setShowCreate(false); createButtonRef.current?.focus(); }} onCreated={(run) => { setSelectedId(run.runId); loadDetail(run.runId); refresh(); }} /></div>}
  </QuantPageScaffold>;
}
