import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  ArrowsClockwise,
  CaretLeft,
  CaretRight,
  Flask,
  Warning,
} from "@phosphor-icons/react";
import QuantPageScaffold from "./QuantPageScaffold";
import QuantSingleBacktestCreatePanel from "./QuantSingleBacktestCreatePanel";
import QuantSingleBacktestRunDetail from "./QuantSingleBacktestRunDetail";
import QuantSingleBacktestTrades from "./QuantSingleBacktestTrades";
import {
  formatInstant,
  formatRatioString,
  formatRunStatus,
  intervalCode,
  validateEquityResponse,
} from "./quantBacktestsFormat";
import {
  fetchDatasets,
  fetchEquity,
  fetchExecutionProfiles,
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

function Metric({ label, value }) {
  return (
    <div className="backtest-metric">
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
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
  const [executionProfiles, setExecutionProfiles] = useState([]);
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
  const [tradePage, setTradePage] = useState(1);
  const [tradeState, setTradeState] = useState("idle");
  const [tradeError, setTradeError] = useState("");
  const [errors, setErrors] = useState({});
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [showCreate, setShowCreate] = useState(false);
  const [creating, setCreating] = useState(false);
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
    if (creating) return;
    setShowCreate(false);
    requestAnimationFrame(() => createButtonRef.current?.focus());
  }, [creating]);

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

  const loadLists = useCallback(
    async (page = 1, signal) => {
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
        profiles: "",
        runs: "",
      }));
      const [strategyResult, datasetResult, profileResult, runResult] =
        await Promise.allSettled([
          fetchStrategies(requestSignal),
          fetchDatasets(requestSignal),
          fetchExecutionProfiles(requestSignal),
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
      if (profileResult.status === "fulfilled") {
        setExecutionProfiles(profileResult.value);
        setErrors((value) => ({ ...value, profiles: "" }));
      } else if (profileResult.reason?.name !== "AbortError")
        setErrors((value) => ({
          ...value,
          profiles: profileResult.reason.message,
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
    [],
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
  }, [loadLists]);

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
  }, [detail, runs, selectedId, tradePage, loadEquity, loadTrades]);

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
            ref={createButtonRef}
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
      {errors.profiles && (
        <div className="backtest-notice" role="alert">
          执行模型不可用：{errors.profiles}
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
      )}
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
            <QuantSingleBacktestRunDetail
              run={displayRun}
              loading={Boolean(selectedId) && !detail && !errors.detail}
              error={errors.detail}
              retry={() => loadDetail(selectedId)}
              equity={equity}
              equityError={equityError}
              retryEquity={() => loadEquity(displayRun?.runId)}
              executionProfile={executionProfiles.find(
                (item) => item.code === displayRun?.executionProfileCode,
              )}
            />
            <QuantSingleBacktestTrades
              run={displayRun}
              page={tradePage}
              data={tradeData}
              state={tradeState}
              error={tradeError}
              onPage={setTradePage}
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
            if (event.target === event.currentTarget) closeCreatePanel();
          }}
        >
          <QuantSingleBacktestCreatePanel
            strategies={strategies}
            datasets={datasets}
            executionProfiles={executionProfiles}
            initialStrategyCode={initialStrategyCode}
            onClose={closeCreatePanel}
            onSavingChange={setCreating}
            onCreated={async (run) => {
              selectRun(run.runId);
              await refresh();
            }}
          />
        </div>
      )}
    </QuantPageScaffold>
  );
}
