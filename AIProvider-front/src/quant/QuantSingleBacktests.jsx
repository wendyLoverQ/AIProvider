import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  ArrowsClockwise,
  CaretLeft,
  CaretRight,
  ChartLineUp,
  Flask,
  Warning,
  X,
} from "@phosphor-icons/react";
import {
  Area,
  AreaChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import QuantPageScaffold from "./QuantPageScaffold";
import {
  calculateExpectedBars,
  compareDecimalStrings,
  decimalSubtract,
  formatDecimalString,
  formatInstant,
  formatRatioString,
  formatRunStatus,
  intervalCode,
  intervalDurationMs,
  isPositiveDecimal,
  normalizeDecimalString,
  toUtcIso,
  utcInstantToLocalInput,
  validateEquityResponse,
} from "./quantBacktestsFormat";
import {
  createBacktestRun,
  fetchDatasets,
  fetchEquity,
  fetchNonTerminalRuns,
  fetchRunDetail,
  fetchRuns,
  fetchStrategies,
  fetchTrades,
} from "./quantBacktestsApi";
import "./QuantBacktests.css";

const NON_TERMINAL = new Set([
  "QUEUED",
  "LOADING_SNAPSHOT",
  "RUNNING_ENGINE",
  "PERSISTING",
]);
const validDataset = (item) =>
  item &&
  Number.isSafeInteger(Number(item.id)) &&
  item.status === "CONTIGUOUS" &&
  item.gapCount === 0 &&
  item.gapSegmentCount === 0 &&
  item.earliestOpenTime &&
  item.latestOpenTime &&
  item.lastValidatedAt &&
  Number(item.candleCount) > 0;
function LoadState({ label }) {
  return (
    <div className="quant-loading" role="status">
      {label}
    </div>
  );
}
function ErrorState({ label, error, retry }) {
  return (
    <div className="quant-error" role="alert">
      <Warning weight="fill" />
      <div>
        <strong>{label}</strong>
        <span>{error}</span>
      </div>
      <button type="button" className="quant-error-retry" onClick={retry}>
        <ArrowsClockwise />
        重试
      </button>
    </div>
  );
}
function Metric({ label, value, tone }) {
  return (
    <div className={`backtest-metric ${tone || ""}`}>
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  );
}

function CreatePanelBase({
  strategies,
  datasets,
  initialStrategyCode = "",
  onClose,
  onCreated,
}) {
  const initialStrategy = strategies.find(
    (item) => item.code === initialStrategyCode,
  );
  const [datasetId, setDatasetId] = useState("");
  const [strategyCode, setStrategyCode] = useState(initialStrategy?.code || "");
  const [start, setStart] = useState("");
  const [end, setEnd] = useState("");
  const [params, setParams] = useState(() =>
    Object.fromEntries(
      (initialStrategy?.parameters || []).map((parameter) => [
        parameter.name,
        String(parameter.defaultValue),
      ]),
    ),
  );
  const [orderAmount, setOrderAmount] = useState("1");
  const [feeRate, setFeeRate] = useState("0");
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);
  const dataset = datasets.find(
    (item) => String(item.id) === String(datasetId),
  );
  const strategy = strategies.find((item) => item.code === strategyCode);
  useEffect(() => {
    const close = (event) => {
      if (event.key === "Escape") onClose();
    };
    document.addEventListener("keydown", close);
    return () => document.removeEventListener("keydown", close);
  }, [onClose]);
  useEffect(() => {
    if (!initialStrategyCode) return;
    const selected = strategies.find(
      (item) => item.code === initialStrategyCode,
    );
    setStrategyCode(selected?.code || "");
    setParams(
      Object.fromEntries(
        (selected?.parameters || []).map((parameter) => [
          parameter.name,
          String(parameter.defaultValue),
        ]),
      ),
    );
  }, [initialStrategyCode, strategies]);
  const selectDataset = (value) => {
    const selected = datasets.find((item) => String(item.id) === String(value));
    setDatasetId(value);
    if (!selected) return;
    const duration = intervalDurationMs(selected.interval);
    setStart(utcInstantToLocalInput(selected.earliestOpenTime));
    setEnd(
      duration
        ? utcInstantToLocalInput(
            new Date(
              new Date(selected.latestOpenTime).getTime() + duration,
            ).toISOString(),
          )
        : "",
    );
  };
  const selectStrategy = (value) => {
    const selected = strategies.find((item) => item.code === value);
    setStrategyCode(value);
    setParams(
      Object.fromEntries(
        (selected?.parameters || []).map((parameter) => [
          parameter.name,
          String(parameter.defaultValue),
        ]),
      ),
    );
  };
  const expectedBars = dataset
    ? calculateExpectedBars(toUtcIso(start), toUtcIso(end), dataset.interval)
    : null;
  const submit = async (event) => {
    event.preventDefault();
    setError("");
    const normalizedOrderAmount = normalizeDecimalString(orderAmount, {
      maxIntegerDigits: 20,
      maxFractionDigits: 18,
    });
    const normalizedFeeRate = normalizeDecimalString(feeRate, {
      maxIntegerDigits: 1,
      maxFractionDigits: 18,
    });
    const normalized = {
      strategyCode: strategyCode.trim(),
      strategyVersion: strategy?.version?.trim() || "",
      orderAmount: normalizedOrderAmount,
      feeRate: normalizedFeeRate,
      strategyParameters: Object.fromEntries(
        Object.entries(params).map(([key, value]) => [key, Number(value)]),
      ),
    };
    const startIso = toUtcIso(start);
    const endIso = toUtcIso(end);
    const invalidParameter = (strategy?.parameters || []).find(
      (parameter) =>
        !/^[0-9]+$/.test(params[parameter.name] || "") ||
        Number(params[parameter.name]) < parameter.minValue ||
        Number(params[parameter.name]) > parameter.maxValue,
    );
    if (!dataset || !strategy || !startIso || !endIso)
      return setError("请完整选择数据集、策略和时间范围");
    if (!intervalDurationMs(dataset.interval))
      return setError("当前周期暂不支持前端时间计算");
    const duration = intervalDurationMs(dataset.interval);
    if (
      new Date(startIso).getTime() % duration !== 0 ||
      new Date(endIso).getTime() % duration !== 0
    )
      return setError("开始和结束时间必须对齐数据集周期");
    if (
      new Date(startIso) >= new Date(endIso) ||
      new Date(startIso) < new Date(dataset.earliestOpenTime) ||
      new Date(endIso).getTime() >
        new Date(dataset.latestOpenTime).getTime() + duration
    )
      return setError("时间范围必须有效且处于数据集范围内");
    if (!expectedBars || expectedBars < (strategy.minimumRequiredBars || 1))
      return setError(
        `预计 K 线数不足，至少需要 ${strategy.minimumRequiredBars} 根`,
      );
    if (invalidParameter)
      return setError(
        `${invalidParameter.name} 必须为 ${invalidParameter.minValue}～${invalidParameter.maxValue} 的整数`,
      );
    if (!normalized.orderAmount || !isPositiveDecimal(normalized.orderAmount))
      return setError(
        "数量必须是大于 0、20 位整数且最多 18 位小数的十进制字符串",
      );
    if (
      !normalized.feeRate ||
      compareDecimalStrings(normalized.feeRate, "0") < 0 ||
      compareDecimalStrings(normalized.feeRate, "0.01") > 0
    )
      return setError("手续费必须是 0～0.01、最多 18 位小数的十进制字符串");
    setSaving(true);
    try {
      const run = await createBacktestRun({
        datasetId: Number(dataset.id),
        startOpenTimeInclusive: startIso,
        endOpenTimeExclusive: endIso,
        ...normalized,
        forceCloseAtEnd: true,
      });
      onCreated(run);
      onClose();
    } catch (exception) {
      setError(exception.message || "创建回测失败");
    } finally {
      setSaving(false);
    }
  };
  const requestedStrategyUnavailable =
    initialStrategyCode && strategies.length > 0 && !strategy;
  return (
    <aside
      className="backtest-create-panel"
      role="dialog"
      aria-modal="true"
      aria-label="新建回测"
    >
      <div className="backtest-panel-head">
        <div>
          <span className="eyebrow">NEW EXPERIMENT</span>
          <h3>新建回测</h3>
        </div>
        <button type="button" aria-label="关闭新建回测" onClick={onClose}>
          <X />
        </button>
      </div>
      <form onSubmit={submit}>
        <label>
          连续历史数据集
          <select
            autoFocus
            value={datasetId}
            onChange={(event) => selectDataset(event.target.value)}
          >
            <option value="">请选择已校验数据集</option>
            {datasets.map((item) => (
              <option key={item.id} value={item.id}>
                {item.symbol} · {intervalCode(item.interval)} ·{" "}
                {Number(item.candleCount).toLocaleString()} 根
              </option>
            ))}
          </select>
        </label>
        {dataset && (
          <p className="backtest-help">
            {formatInstant(dataset.earliestOpenTime)} ～{" "}
            {formatInstant(dataset.latestOpenTime)} · 已校验 · 结束时间不包含
          </p>
        )}
        <label>
          策略
          <select
            value={strategyCode}
            onChange={(event) => selectStrategy(event.target.value)}
          >
            <option value="">请选择策略</option>
            {strategies.map((item) => (
              <option key={item.code} value={item.code}>
                {item.name} · {item.version}
              </option>
            ))}
          </select>
        </label>
        {requestedStrategyUnavailable && (
          <p className="strategy-unavailable" role="alert">
            指定策略当前不可用
          </p>
        )}
        {strategy?.parameters?.map((parameter) => (
          <label key={parameter.name}>
            {parameter.name}
            <input
              type="number"
              step="1"
              value={params[parameter.name] || ""}
              min={parameter.minValue}
              max={parameter.maxValue}
              onChange={(event) =>
                setParams((current) => ({
                  ...current,
                  [parameter.name]: event.target.value,
                }))
              }
            />
            <small>
              默认 {parameter.defaultValue} · 范围 {parameter.minValue}～
              {parameter.maxValue}
            </small>
          </label>
        ))}
        <div className="backtest-form-grid">
          <label>
            开始时间
            <input
              type="datetime-local"
              value={start}
              onChange={(event) => setStart(event.target.value)}
            />
          </label>
          <label>
            结束时间（不包含）
            <input
              type="datetime-local"
              value={end}
              onChange={(event) => setEnd(event.target.value)}
            />
          </label>
        </div>
        <div className="backtest-form-grid">
          <label>
            下单数量
            <input
              inputMode="decimal"
              value={orderAmount}
              onChange={(event) => setOrderAmount(event.target.value)}
            />
          </label>
          <label>
            手续费比例
            <input
              inputMode="decimal"
              value={feeRate}
              onChange={(event) => setFeeRate(event.target.value)}
            />
            <small>例如 0.001 表示 0.1%</small>
          </label>
        </div>
        <p className="backtest-help">
          提交时按 UTC 绝对时间保存。预计 K 线：{expectedBars ?? "—"} 根
        </p>
        <p className="backtest-help">
          固定强制平仓：结束仍持仓时按最后一根 K 线收盘价平仓。
        </p>
        {error && (
          <div className="backtest-inline-error" role="alert">
            {error}
          </div>
        )}
        <button
          className="quant-primary-action"
          type="submit"
          disabled={saving || !strategies.length || !datasets.length}
        >
          {saving ? "正在创建" : "创建异步回测"}
        </button>
      </form>
    </aside>
  );
}

function RunDetail({
  run,
  loading,
  error,
  retry,
  equity,
  equityError,
  retryEquity,
}) {
  if (loading) return <LoadState label="正在读取任务详情…" />;
  if (error)
    return <ErrorState label="任务详情加载失败" error={error} retry={retry} />;
  if (!run) return <div className="backtest-empty">选择一个任务查看详情</div>;
  const metrics = run.metrics || {};
  const points = equity?.points || [];
  const valid = validateEquityResponse(equity);
  const chartPoints = valid
    ? points.map((point) => ({
        ...point,
        equityReturnRatio: decimalSubtract(point.equityRatio, "1"),
        drawdownValue: point.drawdownRatio,
      }))
    : [];
  const warnings = Array.isArray(run.warnings) ? run.warnings : [];
  return (
    <div className="backtest-detail">
      <section className="backtest-card">
        <header className="quant-section-head">
          <h4>任务详情</h4>
          <span
            className={`backtest-status status-${run.status?.toLowerCase()}`}
          >
            {formatRunStatus(run.status)}
          </span>
        </header>
        <dl className="backtest-detail-grid">
          {[
            ["runId", run.runId],
            ["datasetId", run.datasetId],
            [
              "交易对 / 周期",
              `${run.symbol || "—"} · ${intervalCode(run.intervalCode)}`,
            ],
            [
              "策略",
              `${run.strategyCode || "—"} · ${run.strategyVersion || "—"}`,
            ],
            ["请求参数", JSON.stringify(run.requestedParameters || {})],
            ["解析参数", JSON.stringify(run.resolvedParameters || {})],
            [
              "回测区间",
              `${formatInstant(run.startOpenTimeInclusive)} ～ ${formatInstant(run.endOpenTimeExclusive)}`,
            ],
            [
              "数量 / 手续费",
              `${run.orderAmount ?? "—"} / ${run.feeRate ?? "—"}`,
            ],
            [
              "K 线数 / 交易数",
              `${run.barCount ?? "—"} / ${run.tradeCount ?? "—"}`,
            ],
            ["成交模型", run.executionModel || "—"],
            ["阶段", run.status],
            [
              "开始 / 完成",
              `${formatInstant(run.startedAt)} / ${formatInstant(run.finishedAt)}`,
            ],
          ].map(([label, value]) => (
            <div key={label}>
              <dt>{label}</dt>
              <dd>{value || "—"}</dd>
            </div>
          ))}
        </dl>
        {warnings.length > 0 && (
          <div className="backtest-warnings">
            <strong>警告</strong>
            {warnings.map((warning, index) => (
              <span key={`${warning}-${index}`}>{warning}</span>
            ))}
          </div>
        )}
        {run.status === "FAILED" && (
          <div className="backtest-failure">
            <strong>{run.errorCode || "FAILED"}</strong>
            <span>{run.errorMessage || "回测失败"}</span>
            <span>失败前进度：{run.progressPercent ?? "—"}</span>
          </div>
        )}
      </section>
      {run.status === "COMPLETED" && (
        <>
          <section className="backtest-metrics">
            {[
              [
                "总收益率",
                formatRatioString(metrics.totalReturnRatio),
                compareDecimalStrings(metrics.totalReturnRatio, "0") >= 0
                  ? "positive"
                  : "negative",
              ],
              ["最大回撤", formatRatioString(metrics.maximumDrawdownRatio)],
              ["胜率", formatRatioString(metrics.winRate)],
              [
                "交易数",
                formatDecimalString(run.tradeCount ?? metrics.tradeCount, 0),
              ],
              ["净利润", formatDecimalString(metrics.netProfit)],
              ["Profit Factor", formatDecimalString(metrics.profitFactor)],
              ["买入持有", formatRatioString(metrics.buyAndHoldReturnRatio)],
              ["总手续费", formatDecimalString(metrics.totalFees)],
            ].map(([label, value, tone]) => (
              <Metric key={label} label={label} value={value} tone={tone} />
            ))}
          </section>
          <section className="backtest-card">
            <header className="quant-section-head">
              <h4>
                <ChartLineUp />
                权益曲线
              </h4>
              <small>
                {equity?.sampled
                  ? `图表展示 ${chartPoints.length} / ${equity.totalPoints} 个抽样点，完整结果保存在服务器`
                  : ""}
              </small>
            </header>
            {equityError ? (
              <ErrorState
                label="权益曲线加载失败"
                error={equityError}
                retry={retryEquity}
              />
            ) : !valid ? (
              <div className="backtest-empty">权益曲线数据格式异常</div>
            ) : chartPoints.length ? (
              <div className="backtest-chart" aria-label="权益和回撤曲线">
                <ResponsiveContainer width="100%" height="100%">
                  <AreaChart data={chartPoints}>
                    <CartesianGrid stroke="var(--border-subtle)" />
                    <XAxis
                      dataKey="openTime"
                      tickFormatter={(value) =>
                        new Date(value).toLocaleDateString("zh-CN")
                      }
                    />
                    <YAxis
                      tickFormatter={(value) =>
                        `${(Number(value) * 100).toFixed(0)}%`
                      }
                    />
                    <Tooltip
                      formatter={(value, name) => [
                        formatRatioString(String(value)),
                        name === "equityReturnRatio" ? "权益变化" : "回撤",
                      ]}
                      labelFormatter={formatInstant}
                    />
                    <Area
                      type="monotone"
                      dataKey="equityReturnRatio"
                      stroke="var(--accent-primary)"
                      fill="var(--accent-primary)"
                      fillOpacity={0.12}
                    />
                    <Area
                      type="monotone"
                      dataKey="drawdownValue"
                      stroke="var(--accent-red)"
                      fill="var(--accent-red)"
                      fillOpacity={0.08}
                    />
                  </AreaChart>
                </ResponsiveContainer>
              </div>
            ) : (
              <div className="backtest-empty">权益曲线不可用</div>
            )}
          </section>
        </>
      )}
    </div>
  );
}

function Trades({ run, page, data, state, error, onPage, retry }) {
  if (run?.status !== "COMPLETED") return null;
  const totalPages = Math.max(1, Math.ceil((data?.total || 0) / 100));
  return (
    <section className="backtest-card">
      <header className="quant-section-head">
        <h4>交易记录</h4>
        <small>{data?.total || 0} 条</small>
      </header>
      {state === "loading" ? (
        <LoadState label="正在读取交易…" />
      ) : state === "error" ? (
        <ErrorState
          label="交易记录加载失败"
          error={error || data?.error || "交易接口请求失败"}
          retry={retry}
        />
      ) : (
        <>
          <div className="backtest-table-wrap">
            <table>
              <thead>
                <tr>
                  {[
                    "编号",
                    "入场时间",
                    "入场价",
                    "退出时间",
                    "退出价",
                    "数量",
                    "净利润",
                    "收益率",
                    "持有 Bar",
                    "退出原因",
                  ].map((label) => (
                    <th key={label}>{label}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {(data?.records || []).map((trade) => (
                  <tr key={trade.tradeNo}>
                    <td>{trade.tradeNo}</td>
                    <td>{formatInstant(trade.entryTime)}</td>
                    <td>{formatDecimalString(trade.entryPrice)}</td>
                    <td>{formatInstant(trade.exitTime)}</td>
                    <td>{formatDecimalString(trade.exitPrice)}</td>
                    <td>{formatDecimalString(trade.amount)}</td>
                    <td>{formatDecimalString(trade.netProfit)}</td>
                    <td>{formatRatioString(trade.returnRatio)}</td>
                    <td>{trade.barsHeld ?? "—"}</td>
                    <td>
                      {trade.exitReason === "END_OF_SERIES" || trade.forcedExit
                        ? "期末强平"
                        : "策略退出"}
                    </td>
                  </tr>
                ))}
                {!(data?.records || []).length && (
                  <tr>
                    <td colSpan="10" className="backtest-empty">
                      已完成但没有交易
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
          <div className="backtest-pagination">
            <button
              type="button"
              disabled={page <= 1}
              onClick={() => onPage(page - 1)}
            >
              <CaretLeft />
              上一页
            </button>
            <span>
              第 {page} / {totalPages} 页
            </span>
            <button
              type="button"
              disabled={page >= totalPages}
              onClick={() => onPage(page + 1)}
            >
              下一页
              <CaretRight />
            </button>
          </div>
        </>
      )}
    </section>
  );
}

export default function QuantSingleBacktests() {
  const [initialStrategyCode] = useState(
    () => new URLSearchParams(window.location.search).get("strategyCode") || "",
  );
  const openCreateFromQuery = useRef(
    new URLSearchParams(window.location.search).get("openCreate") === "1",
  );
  const [strategies, setStrategies] = useState([]);
  const [datasets, setDatasets] = useState([]);
  const [invalidDatasetCount, setInvalidDatasetCount] = useState(0);
  const [runs, setRuns] = useState([]);
  const [runPage, setRunPage] = useState(1);
  const runPageSize = 20;
  const [runTotal, setRunTotal] = useState(0);
  const [selectedId, setSelectedId] = useState(
    () => new URLSearchParams(window.location.search).get("runId") || null,
  );
  const [detail, setDetail] = useState(null);
  const [equity, setEquity] = useState(null);
  const [equityError, setEquityError] = useState("");
  const [tradeData, setTradeData] = useState({ records: [], total: 0 });
  const [tradeState, setTradeState] = useState("idle");
  const [tradeError, setTradeError] = useState("");
  const [tradePage, setTradePage] = useState(1);
  const [errors, setErrors] = useState({});
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [showCreate, setShowCreate] = useState(false);
  const listAbortRef = useRef(null);
  const detailAbortRef = useRef(null);
  const equityAbortRef = useRef(null);
  const tradeAbortRef = useRef(null);
  const pollAbortRef = useRef(null);
  const inFlightRef = useRef(false);
  const pollTimerRef = useRef(null);
  const wasPollingRef = useRef(false);
  const sequence = useRef({ list: 0, detail: 0, equity: 0, trade: 0 });
  const createButtonRef = useRef(null);
  const closeCreatePanel = useCallback(() => {
    setShowCreate(false);
    requestAnimationFrame(() => {
      createButtonRef.current?.focus();
      document.querySelector("button.quant-primary-action")?.focus();
    });
  }, []);
  const selectRun = useCallback((runId, { replace = false } = {}) => {
    setSelectedId(runId || null);
    const url = new URL(window.location.href);
    url.searchParams.set("mode", "single");
    if (runId) url.searchParams.set("runId", runId);
    else url.searchParams.delete("runId");
    window.history[replace ? "replaceState" : "pushState"](
      {},
      "",
      `${url.pathname}?${url.searchParams.toString()}`,
    );
  }, []);
  useEffect(() => {
    if (!showCreate)
      requestAnimationFrame(() => {
        createButtonRef.current?.focus();
        document.querySelector("button.quant-primary-action")?.focus();
      });
  }, [showCreate]);
  const loadLists = useCallback(
    async (page = runPage, signal) => {
      listAbortRef.current?.abort();
      const controller = signal ? null : new AbortController();
      const requestSignal = signal || controller.signal;
      listAbortRef.current = controller;
      const current = ++sequence.current.list;
      setRefreshing(true);
      setErrors((value) => ({
        ...value,
        strategies: "",
        datasets: "",
        runs: "",
      }));
      const [strategyResult, datasetResult, runResult] =
        await Promise.allSettled([
          fetchStrategies(requestSignal),
          fetchDatasets(requestSignal),
          fetchRuns(page, runPageSize, requestSignal),
        ]);
      if (current !== sequence.current.list) return;
      if (strategyResult.status === "fulfilled") {
        setStrategies(strategyResult.value);
        setErrors((value) => ({ ...value, strategies: "" }));
      } else if (strategyResult.reason?.name !== "AbortError")
        setErrors((value) => ({
          ...value,
          strategies: strategyResult.reason.message,
        }));
      if (datasetResult.status === "fulfilled") {
        const all = datasetResult.value;
        setDatasets(all.filter(validDataset));
        setInvalidDatasetCount(
          all.filter((item) => !validDataset(item)).length,
        );
        setErrors((value) => ({ ...value, datasets: "" }));
      } else if (datasetResult.reason?.name !== "AbortError")
        setErrors((value) => ({
          ...value,
          datasets: datasetResult.reason.message,
        }));
      if (runResult.status === "fulfilled") {
        setRuns(runResult.value.records);
        setRunTotal(runResult.value.total);
        setRunPage(runResult.value.page);
        setErrors((value) => ({ ...value, runs: "" }));
      } else if (runResult.reason?.name !== "AbortError")
        setErrors((value) => ({ ...value, runs: runResult.reason.message }));
      setLoading(false);
      setRefreshing(false);
    },
    [runPage],
  );
  const loadDetail = useCallback(async (id) => {
    if (!id) return;
    detailAbortRef.current?.abort();
    const controller = new AbortController();
    detailAbortRef.current = controller;
    const current = ++sequence.current.detail;
    setDetail(null);
    setErrors((value) => ({ ...value, detail: "" }));
    try {
      const result = await fetchRunDetail(id, controller.signal);
      if (current === sequence.current.detail) {
        setDetail(result);
        setErrors((value) => ({ ...value, detail: "" }));
      }
    } catch (exception) {
      if (
        exception.name !== "AbortError" &&
        current === sequence.current.detail
      )
        setErrors((value) => ({ ...value, detail: exception.message }));
    }
  }, []);
  const loadEquity = useCallback(async (id) => {
    if (!id) return;
    equityAbortRef.current?.abort();
    const controller = new AbortController();
    equityAbortRef.current = controller;
    const current = ++sequence.current.equity;
    setEquity(null);
    setEquityError("");
    try {
      const result = await fetchEquity(id, 1200, controller.signal);
      if (!validateEquityResponse(result))
        throw new Error("权益曲线数据格式异常");
      if (current === sequence.current.equity) setEquity(result);
    } catch (exception) {
      if (
        exception.name !== "AbortError" &&
        current === sequence.current.equity
      )
        setEquityError(exception.message);
    }
  }, []);
  const loadTrades = useCallback(async (id, page = 1) => {
    if (!id) return;
    tradeAbortRef.current?.abort();
    const controller = new AbortController();
    tradeAbortRef.current = controller;
    const current = ++sequence.current.trade;
    setTradeState("loading");
    setTradeError("");
    setTradeData({ records: [], total: 0 });
    try {
      const result = await fetchTrades(id, page, 100, controller.signal);
      if (current === sequence.current.trade) {
        setTradeData(result);
        setTradeState("ready");
        setTradeError("");
      }
    } catch (exception) {
      if (
        exception.name !== "AbortError" &&
        current === sequence.current.trade
      ) {
        const message = exception.message || "交易接口请求失败";
        setTradeState("error");
        setTradeError(message);
        setTradeData({ records: [], total: 0, error: message });
      }
    }
  }, []);
  const refresh = useCallback(async () => {
    await loadLists(runPage);
    if (selectedId) await loadDetail(selectedId);
  }, [loadLists, loadDetail, runPage, selectedId]);
  useEffect(() => {
    loadLists(1);
    return () => {
      listAbortRef.current?.abort();
      detailAbortRef.current?.abort();
      equityAbortRef.current?.abort();
      tradeAbortRef.current?.abort();
      pollAbortRef.current?.abort();
      clearInterval(pollTimerRef.current);
    };
  }, []);
  useEffect(() => {
    if (!openCreateFromQuery.current || loading || errors.strategies) return;
    setShowCreate(true);
    openCreateFromQuery.current = false;
    const url = new URL(window.location.href);
    url.searchParams.delete("mode");
    url.searchParams.delete("openCreate");
    url.searchParams.delete("strategyCode");
    window.history.replaceState(
      {},
      "",
      `${url.pathname}?${url.searchParams.toString()}`,
    );
  }, [loading, errors.strategies, initialStrategyCode]);
  useEffect(() => {
    const onPopState = () => {
      const params = new URLSearchParams(window.location.search);
      setSelectedId(params.get("runId") || null);
    };
    window.addEventListener("popstate", onPopState);
    return () => window.removeEventListener("popstate", onPopState);
  }, []);
  useEffect(() => {
    setErrors((current) => ({ ...current, detail: "" }));
    setTradePage(1);
    setTradeData({ records: [], total: 0 });
    setTradeState("idle");
    if (selectedId) loadDetail(selectedId);
    else {
      setDetail(null);
      setEquity(null);
    }
  }, [selectedId, loadDetail]);
  useEffect(() => {
    const activeRun =
      detail || runs.find((run) => String(run.runId) === String(selectedId));
    if (activeRun?.status === "COMPLETED") {
      loadEquity(activeRun.runId);
      loadTrades(activeRun.runId, tradePage);
    } else {
      setEquity(null);
      setEquityError("");
      setTradeData({ records: [], total: 0 });
    }
  }, [detail?.runId, detail?.status, tradePage]);
  const selectedRun = runs.find(
    (run) => String(run.runId) === String(selectedId),
  );
  const selectedOrDetailedStatus =
    detail && String(detail.runId) === String(selectedId)
      ? detail.status
      : selectedRun?.status;
  const hasKnownNonTerminal =
    runs.some((run) => NON_TERMINAL.has(run.status)) ||
    NON_TERMINAL.has(selectedOrDetailedStatus);
  useEffect(() => {
    clearInterval(pollTimerRef.current);
    if (document.visibilityState !== "visible") {
      pollAbortRef.current?.abort();
      return undefined;
    }
    if (!hasKnownNonTerminal) {
      wasPollingRef.current = false;
      return undefined;
    }
    wasPollingRef.current = true;
    const poll = async () => {
      if (inFlightRef.current || document.visibilityState !== "visible") return;
      inFlightRef.current = true;
      pollAbortRef.current?.abort();
      const controller = new AbortController();
      pollAbortRef.current = controller;
      try {
        const result = await fetchNonTerminalRuns(controller.signal);
        if (result.length === 0) {
          wasPollingRef.current = false;
          await refresh();
        } else {
          await loadLists(runPage, controller.signal);
        }
      } catch (exception) {
        if (exception.name !== "AbortError")
          setErrors((current) => ({ ...current, runs: exception.message }));
      } finally {
        inFlightRef.current = false;
      }
    };
    poll();
    pollTimerRef.current = window.setInterval(poll, 3000);
    return () => {
      clearInterval(pollTimerRef.current);
      pollAbortRef.current?.abort();
    };
  }, [hasKnownNonTerminal, refresh, loadLists, runPage]);
  useEffect(() => {
    const onVisibility = () => {
      if (document.visibilityState === "visible" && wasPollingRef.current)
        refresh();
      else if (document.visibilityState !== "visible")
        pollAbortRef.current?.abort();
    };
    document.addEventListener("visibilitychange", onVisibility);
    return () => document.removeEventListener("visibilitychange", onVisibility);
  }, [refresh]);
  const displayRun = detail || selectedRun;
  const counts = useMemo(
    () => ({
      active: runs.filter((run) => NON_TERMINAL.has(run.status)).length,
      completed: runs.filter((run) => run.status === "COMPLETED").length,
      failed: runs.filter((run) => run.status === "FAILED").length,
    }),
    [runs],
  );
  const totalRunPages = Math.max(1, Math.ceil(runTotal / runPageSize));
  return (
    <QuantPageScaffold pageClass="quant-backtests-page">
      <div className="quant-workspace-head">
        <div>
          <span className="eyebrow">QUANT · BACKTEST LAB</span>
          <h3>回测实验</h3>
          <small>基于已校验历史数据运行确定性策略回测</small>
        </div>
        <div className="backtest-head-actions">
          <button
            type="button"
            className="quant-refresh"
            onClick={refresh}
            disabled={refreshing}
          >
            <ArrowsClockwise className={refreshing ? "spin" : ""} />
            刷新
          </button>
          {selectedId && (
            <button
              type="button"
              className="quant-secondary-action"
              onClick={() => selectRun(null)}
            >
              清除选择
            </button>
          )}
          <button
            type="button"
            className="quant-primary-action"
            onClick={() => setShowCreate(true)}
          >
            <Flask />
            新建回测
          </button>
        </div>
      </div>
      <div className="quant-status-grid backtest-summary">
        <Metric label="排队 / 运行中" value={counts.active} />
        <Metric label="本页已完成" value={counts.completed} />
        <Metric label="本页失败" value={counts.failed} />
        <Metric label="当前选中任务" value={selectedId || "—"} />
      </div>
      {errors.strategies && (
        <div className="backtest-notice" role="alert">
          策略不可用：{errors.strategies}
        </div>
      )}
      {errors.datasets && (
        <div className="backtest-notice" role="alert">
          数据集不可用：{errors.datasets}
        </div>
      )}
      {invalidDatasetCount > 0 && (
        <div className="backtest-notice" role="alert">
          已隐藏 {invalidDatasetCount} 个不满足连续性或校验条件的数据集
        </div>
      )}
      {errors.runs && (
        <ErrorState
          label="任务列表加载失败"
          error={errors.runs}
          retry={refresh}
        />
      )}{" "}
      {loading ? (
        <LoadState label="正在读取回测工作台…" />
      ) : (
        <div className="backtest-main-grid">
          <section className="backtest-card backtest-run-list">
            <header className="quant-section-head">
              <h4>回测任务</h4>
              <small>{runTotal} 条</small>
            </header>
            {!runs.length ? (
              <div className="backtest-empty">还没有创建回测</div>
            ) : (
              runs.map((run) => (
                <button
                  type="button"
                  className={`backtest-run-row ${String(run.runId) === String(selectedId) ? "selected" : ""}`}
                  key={run.runId}
                  onClick={() => selectRun(run.runId)}
                >
                  <span
                    className={`backtest-status status-${run.status?.toLowerCase()}`}
                  >
                    {formatRunStatus(run.status)}
                  </span>
                  <strong>
                    {run.symbol || "—"} · {intervalCode(run.intervalCode)}
                  </strong>
                  <span>{run.strategyCode || "—"}</span>
                  <span>
                    {formatInstant(run.startOpenTimeInclusive)} ～{" "}
                    {formatInstant(run.endOpenTimeExclusive)}
                  </span>
                  <span>
                    Bar {run.barCount ?? "—"} · 交易 {run.tradeCount ?? "—"} ·{" "}
                    {run.status === "COMPLETED"
                      ? formatRatioString(run.metrics?.totalReturnRatio)
                      : "—"}{" "}
                    · 回撤{" "}
                    {run.status === "COMPLETED"
                      ? formatRatioString(run.metrics?.maximumDrawdownRatio)
                      : "—"}
                  </span>
                  <span>
                    排队 {formatInstant(run.queuedAt)}
                    {run.status === "FAILED"
                      ? ` · ${run.errorCode || "FAILED"}`
                      : ""}
                  </span>
                </button>
              ))
            )}
            <div className="backtest-pagination">
              <button
                type="button"
                disabled={runPage <= 1}
                onClick={() => loadLists(runPage - 1)}
              >
                <CaretLeft />
                上一页
              </button>
              <span>
                第 {runPage} / {totalRunPages} 页
              </span>
              <button
                type="button"
                disabled={runPage >= totalRunPages}
                onClick={() => loadLists(runPage + 1)}
              >
                下一页
                <CaretRight />
              </button>
            </div>
          </section>
          <div>
            <RunDetail
              run={displayRun}
              loading={Boolean(selectedId) && !detail && !errors.detail}
              error={errors.detail}
              retry={() => loadDetail(selectedId)}
              equity={equity}
              equityError={equityError}
              retryEquity={() => loadEquity(displayRun?.runId)}
            />
            <Trades
              run={displayRun}
              page={tradePage}
              data={tradeData}
              state={tradeState}
              error={tradeError}
              onPage={(page) => setTradePage(page)}
              retry={() => loadTrades(displayRun?.runId, tradePage)}
            />
          </div>
        </div>
      )}
      {showCreate && (
        <div
          className="backtest-modal-backdrop"
          role="presentation"
          onMouseDown={(event) => {
            if (event.target === event.currentTarget) {
              setShowCreate(false);
              createButtonRef.current?.focus();
            }
          }}
        >
          <CreatePanelBase
            strategies={strategies}
            datasets={datasets}
            initialStrategyCode={initialStrategyCode}
            onClose={closeCreatePanel}
            onCreated={(run) => {
              selectRun(run.runId);
              refresh();
            }}
          />
        </div>
      )}
    </QuantPageScaffold>
  );
}
