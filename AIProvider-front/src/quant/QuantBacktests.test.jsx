/* @vitest-environment jsdom */
import {
  cleanup,
  act,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import QuantBacktests from "./QuantBacktests";
import QuantWalkForwardWorkspace from "./QuantWalkForwardWorkspace";

const strategy = {
  code: "rsi/mean",
  name: "RSI 反转",
  version: "1.0.0",
  minimumRequiredBars: 1,
  supportedMarketTypes: ["USDM_PERPETUAL"],
  supportedExecutionProfileCodes: ["USDM_PERPETUAL_LONG_ONLY_1X_V1"],
  supportedDirectionModes: ["LONG_ONLY"],
  requiredMarketFeatures: ["OHLCV"],
  parameters: [
    { name: "period", defaultValue: 14, minValue: 2, maxValue: 100 },
  ],
};
const dataset = {
  id: 1,
  provider: "BINANCE",
  marketType: "USDM_PERPETUAL",
  dataType: "KLINE",
  status: "CONTIGUOUS",
  gapCount: 0,
  gapSegmentCount: 0,
  earliestOpenTime: "2025-01-01T00:00:00Z",
  latestOpenTime: "2025-01-05T03:00:00Z",
  lastValidatedAt: "2025-01-06T00:00:00Z",
  candleCount: 100,
  symbol: "BTC/USDT",
  interval: "H1",
};
const executionContext = {
  executionProfileCode: "USDM_PERPETUAL_LONG_ONLY_1X_V1",
  directionMode: "LONG_ONLY",
  orderSizingMode: "BASE_QUANTITY",
};
const executionProfile = {
  code: "USDM_PERPETUAL_LONG_ONLY_1X_V1",
  name: "USDT 本位永续·只做多·1× V1",
  description: "USDT 本位永续只做多 1× 执行模型",
  marketType: "USDM_PERPETUAL",
  directionMode: "LONG_ONLY",
  orderSizingMode: "BASE_QUANTITY",
  entryOrderSide: "BUY",
  exitOrderSide: "SELL",
  positionSide: "LONG",
  leverage: "1",
  fillModel: "TA4J_TRADE_ON_NEXT_OPEN",
  transactionCostModel: "LINEAR_FEE_RATE",
  holdingCostModel: "ZERO",
  fundingCostModel: "ZERO_NOT_MODELED",
  liquidationModel: "NONE_NOT_MODELED",
  marginModel: "NONE_NOT_MODELED",
  requiredMarketFeatures: ["OHLCV"],
  limitations: ["不计算资金费率"],
};
const result = (data) => ({
  ok: true,
  status: 200,
  json: async () => ({ code: 200, data }),
});

const walkForwardSummary = { studyId: "wf-1", datasetId: 7, provider: "BINANCE", marketType: "USDM_PERPETUAL", dataType: "KLINE", symbol: "BTCUSDT", intervalCode: "1h", strategyCode: "s", strategyVersion: "1", ...executionContext, parameterGrid: { fast: [5, 7] }, windowMode: "ROLLING", studyStartOpenTimeInclusive: "2024-01-01T00:00:00Z", studyEndOpenTimeExclusive: "2024-01-10T00:00:00Z", trainingBars: 48, validationBars: 24, stepBars: 24, foldCount: 2, candidateCountPerFold: 2, totalChildRuns: 8, selectionMetric: "TRAIN_TOTAL_RETURN_RATIO", minimumTrainTrades: 10, orderAmount: "1", feeRate: "0.001", forceCloseAtEnd: true, status: "COMPLETED_WITH_FAILURES", progressPercent: 100, pendingFolds: 0, activeFolds: 0, completedFolds: 1, failedFolds: 1, selectedParameterChanges: 0, successfulOosFolds: 1, totalOosTradeCount: 1, totalOosFees: "0", totalOosReturnRatio: "0", hasOosGaps: true, errorCode: null, errorMessage: null, createdAt: "2024-01-01T00:00:00Z", startedAt: "2024-01-01T00:00:00Z", finishedAt: "2024-01-10T00:00:00Z", updatedAt: "2024-01-10T00:00:00Z" };
const walkForwardMetrics = { totalReturnRatio: "0", maximumDrawdownRatio: "0", profitFactor: "1", netProfit: "0", winRate: "0", totalFees: "0", buyAndHoldReturnRatio: "0", averageTradeReturnRatio: "0", tradeCount: 0 };
const walkForwardFold = (foldId, status, progressPercent) => ({ foldId, foldIndex: foldId === "fold-a" ? 0 : 1, trainingStartOpenTimeInclusive: "2024-01-01T00:00:00Z", trainingEndOpenTimeExclusive: "2024-01-03T00:00:00Z", validationStartOpenTimeInclusive: "2024-01-03T00:00:00Z", validationEndOpenTimeExclusive: "2024-01-04T00:00:00Z", experimentId: `exp-${foldId}`, experimentStatus: status === "FAILED" ? "FAILED" : "COMPLETED", status, progressPercent, selectedCandidateId: status === "COMPLETED" ? "candidate-1" : null, selectedParameters: status === "COMPLETED" ? { fast: 5 } : null, selectedTrainingRunId: status === "COMPLETED" ? "train-1" : null, selectedValidationRunId: status === "COMPLETED" ? "valid-1" : null, selectionMetricValue: status === "COMPLETED" ? "0" : null, trainingMetrics: status === "COMPLETED" ? walkForwardMetrics : null, validationMetrics: status === "COMPLETED" ? walkForwardMetrics : null, errorCode: status === "FAILED" ? "CHILD_FAILED" : null, errorMessage: status === "FAILED" ? "子任务失败" : null, startedAt: null, finishedAt: null, updatedAt: "2024-01-04T00:00:00Z" });

describe("Quant Walk-forward workspace lifecycle", () => {
  afterEach(() => { cleanup(); vi.restoreAllMocks(); window.history.replaceState({}, "", "/quant/backtests"); });

  const oosEquity = { sampled: false, totalPoints: 1, successfulFolds: 1, missingFolds: 0, hasGaps: false, totalReturnRatio: "0", maximumDrawdownRatio: "0", points: [{ pointIndex: 0, foldIndex: 0, openTime: "2024-01-03T00:00:00Z", indexRatio: "1", drawdownRatio: "0" }] };
  const detailFor = (id, status) => ({ summary: { ...walkForwardSummary, studyId: id, status, progressPercent: status === "RUNNING" ? 25 : 100, pendingFolds: status === "RUNNING" ? 1 : 0, activeFolds: 0, completedFolds: status === "RUNNING" ? 0 : 1, failedFolds: status === "RUNNING" ? 0 : 1 }, parameterFrequencies: [{ parameters: { fast: 5 }, selectedCount: 1, firstFoldIndex: 0, lastFoldIndex: 0 }] });
  const setVisibility = (state) => { Object.defineProperty(document, "visibilityState", { configurable: true, value: state }); document.dispatchEvent(new Event("visibilitychange")); };

  function installOosLifecycleFetch({ detailStatus = "COMPLETED_WITH_FAILURES", oosHandler, studyIds = ["wf-1"] } = {}) {
    const studies = studyIds.map((id) => ({ ...walkForwardSummary, studyId: id, status: detailStatus }));
    const fetchMock = vi.spyOn(globalThis, "fetch").mockImplementation(async (url, options = {}) => {
      if (url.includes("/execution-profiles")) return result([executionProfile]);
      if (url.includes("/strategies")) return result([strategy]);
      if (url.includes("/datasets")) return result([]);
      if (url.includes("/oos-equity")) return oosHandler(options.signal);
      if (url.includes("/folds")) return result({ records: [walkForwardFold("fold-a", "COMPLETED", 100), walkForwardFold("fold-b", "FAILED", 100)], total: 2, page: 1, pageSize: 50 });
      const id = studyIds.find((item) => url.endsWith(`/${item}`));
      if (id) return result(detailFor(id, detailStatus));
      if (url.includes("walk-forward-studies")) return result({ records: studies, total: studies.length, page: 1, pageSize: 20 });
      throw new Error(`unexpected request ${url}`);
    });
    return fetchMock;
  }

  function installWalkForwardFetch() {
    const oos = { sampled: false, totalPoints: 1, successfulFolds: 1, missingFolds: 1, hasGaps: true, totalReturnRatio: "0", maximumDrawdownRatio: "0", points: [{ pointIndex: 0, foldIndex: 0, openTime: "2024-01-03T00:00:00Z", indexRatio: "1", drawdownRatio: "0" }] };
    const fetchMock = vi.spyOn(globalThis, "fetch").mockImplementation(async (url) => {
      if (url.includes("/execution-profiles")) return result([executionProfile]);
      if (url.includes("/strategies")) return result([strategy]);
      if (url.includes("/datasets")) return result([]);
      if (url.includes("/oos-equity")) return result(oos);
      if (url.includes("/folds")) return result({ records: [walkForwardFold("fold-a", "COMPLETED", 100), walkForwardFold("fold-b", "FAILED", 100)], total: 2, page: 1, pageSize: 50 });
      if (url.endsWith("/wf-1")) return result({ summary: walkForwardSummary, parameterFrequencies: [{ parameters: { fast: 5 }, selectedCount: 1, firstFoldIndex: 0, lastFoldIndex: 0 }] });
      if (url.includes("walk-forward-studies")) return result({ records: [walkForwardSummary], total: 1, page: 1, pageSize: 20 });
      throw new Error(`unexpected request ${url}`);
    });
    return fetchMock;
  }

  it("shows a profile failure independently while the Study list still loads", async () => {
    vi.spyOn(globalThis, "fetch").mockImplementation(async (url) => {
      if (url.includes("/execution-profiles"))
        return {
          ok: false,
          status: 500,
          json: async () => ({ code: 500, message: "执行模型服务失败" }),
        };
      if (url.includes("/strategies")) return result([strategy]);
      if (url.includes("/datasets")) return result([]);
      if (url.includes("walk-forward-studies"))
        return result({
          records: [walkForwardSummary],
          total: 1,
          page: 1,
          pageSize: 20,
        });
      throw new Error(`unexpected request ${url}`);
    });
    window.history.replaceState(
      {},
      "",
      "/quant/backtests?mode=walk-forward",
    );
    render(<QuantWalkForwardWorkspace />);
    expect(await screen.findByText(/执行模型服务失败/)).toBeTruthy();
    expect(
      await screen.findByRole("button", { name: /BTCUSDT/ }),
    ).toBeTruthy();
  });

  it("closes once, deep-links, and restores focus after creating a Study", async () => {
    const createdSummary = {
      ...walkForwardSummary,
      studyId: "wf-created",
      strategyCode: strategy.code,
      strategyVersion: strategy.version,
      parameterGrid: { period: [14] },
      status: "QUEUED",
      progressPercent: 0,
      foldCount: 1,
      candidateCountPerFold: 1,
      totalChildRuns: 2,
      pendingFolds: 1,
      activeFolds: 0,
      completedFolds: 0,
      failedFolds: 0,
      selectedParameterChanges: null,
      successfulOosFolds: null,
      totalOosTradeCount: null,
      totalOosFees: null,
      totalOosReturnRatio: null,
      hasOosGaps: null,
    };
    vi.spyOn(globalThis, "fetch").mockImplementation(
      async (url, options = {}) => {
        if (url.includes("/execution-profiles"))
          return result([executionProfile]);
        if (url.includes("/strategies")) return result([strategy]);
        if (url.includes("/datasets")) return result([dataset]);
        if (
          url.endsWith("/walk-forward-studies") &&
          options.method === "POST"
        )
          return result({
            studyId: "wf-created",
            foldCount: 1,
            candidateCountPerFold: 1,
            totalChildRuns: 2,
          });
        if (url.includes("/wf-created/folds"))
          return result({
            records: [],
            total: 0,
            page: 1,
            pageSize: 50,
          });
        if (url.endsWith("/wf-created"))
          return result({
            summary: createdSummary,
            parameterFrequencies: [],
          });
        if (url.includes("walk-forward-studies"))
          return result({
            records: [createdSummary],
            total: 1,
            page: 1,
            pageSize: 20,
          });
        throw new Error(`unexpected request ${url}`);
      },
    );
    window.history.replaceState(
      {},
      "",
      "/quant/backtests?mode=walk-forward",
    );
    render(<QuantWalkForwardWorkspace />);
    const openButton = await screen.findByRole("button", {
      name: "新建滚动验证",
    });
    fireEvent.click(openButton);
    fireEvent.change(screen.getByRole("combobox", { name: "数据集 / 交易对" }), {
      target: { value: "1" },
    });
    fireEvent.change(screen.getByRole("combobox", { name: "策略" }), {
      target: { value: strategy.code },
    });
    fireEvent.change(
      screen.getByLabelText("初始资金（计价资产，当前为 USDT）"),
      { target: { value: "1000" } },
    );
    fireEvent.click(
      screen.getByRole("button", { name: "按 70% / 30% 填充窗口" }),
    );
    fireEvent.click(screen.getByRole("button", { name: "创建滚动验证" }));
    await waitFor(() =>
      expect(
        screen.queryByRole("dialog", { name: "新建滚动验证" }),
      ).toBeNull(),
    );
    expect(new URLSearchParams(window.location.search).get("studyId")).toBe(
      "wf-created",
    );
    await waitFor(() => expect(document.activeElement).toBe(openButton));
  });

  it("keeps foldId in history, restores same-page selection, and exposes failed folds with keyboard semantics", async () => {
    const fetchMock = installWalkForwardFetch();
    window.history.replaceState({}, "", "/quant/backtests?mode=walk-forward&studyId=wf-1&foldPage=1&foldId=fold-a");
    render(<QuantWalkForwardWorkspace />);
    await waitFor(() => expect(screen.getAllByRole("button").filter((item) => item.getAttribute("role") === "button")).toHaveLength(2));
    expect(
      screen.getByText("USDT 本位永续·只做多·1× V1"),
    ).toBeTruthy();
    expect(screen.getByText("只做多")).toBeTruthy();
    expect(screen.getByText("基础资产数量")).toBeTruthy();
    const foldRows = () => screen.getAllByRole("button").filter((row) => row.getAttribute("role") === "button");
    const row = (index) => foldRows().find((item) => item.textContent.trim().startsWith(String(index)));
    expect(row(0).getAttribute("aria-selected")).toBe("true");
    fireEvent.click(row(1));
    expect(new URLSearchParams(window.location.search).get("foldId")).toBe("fold-b");
    window.history.back();
    await waitFor(() => expect(row(0).getAttribute("aria-selected")).toBe("true"));
    fireEvent.keyDown(row(1), { key: "Enter" });
    expect(new URLSearchParams(window.location.search).get("foldId")).toBe("fold-b");
    expect(fetchMock.mock.calls.filter(([url]) => url.includes("oos-equity"))).toHaveLength(1);
  });

  it("retries an aborted terminal OOS request after hidden and visible", async () => {
    const pending = [];
    const fetchMock = installOosLifecycleFetch({ oosHandler: (signal) => new Promise((resolve, reject) => { pending.push({ resolve }); signal.addEventListener("abort", () => { const error = new Error("aborted"); error.name = "AbortError"; reject(error); }); }) });
    window.history.replaceState({}, "", "/quant/backtests?mode=walk-forward&studyId=wf-1");
    render(<QuantWalkForwardWorkspace />);
    await waitFor(() => expect(fetchMock.mock.calls.filter(([url]) => url.includes("oos-equity"))).toHaveLength(1));
    setVisibility("hidden");
    setVisibility("visible");
    await waitFor(() => expect(fetchMock.mock.calls.filter(([url]) => url.includes("oos-equity"))).toHaveLength(2));
    await act(async () => pending[1].resolve(result(oosEquity)));
    await waitFor(() => expect(screen.getByText("Normalized OOS Index")).toBeTruthy());
  });

  it("does not repeat successful OOS on recovery, but manual refresh forces exactly one reload", async () => {
    const fetchMock = installOosLifecycleFetch({ oosHandler: async () => result(oosEquity) });
    window.history.replaceState({}, "", "/quant/backtests?mode=walk-forward&studyId=wf-1");
    render(<QuantWalkForwardWorkspace />);
    await waitFor(() => expect(screen.getByText("Normalized OOS Index")).toBeTruthy());
    setVisibility("hidden");
    setVisibility("visible");
    await waitFor(() => expect(fetchMock.mock.calls.filter(([url]) => url.includes("oos-equity"))).toHaveLength(1));
    fireEvent.click(screen.getByRole("button", { name: "刷新" }));
    await waitFor(() => expect(fetchMock.mock.calls.filter(([url]) => url.includes("oos-equity"))).toHaveLength(2));
    expect(fetchMock.mock.calls.filter(([url]) => url.includes("oos-equity"))).toHaveLength(2);
  });

  it("clears a failed OOS load so hidden recovery can retry successfully", async () => {
    let attempts = 0;
    const fetchMock = installOosLifecycleFetch({ oosHandler: async () => { attempts += 1; if (attempts === 1) { throw new Error("OOS temporary failure"); } return result(oosEquity); } });
    window.history.replaceState({}, "", "/quant/backtests?mode=walk-forward&studyId=wf-1");
    render(<QuantWalkForwardWorkspace />);
    await waitFor(() => expect(screen.getByText(/OOS 指数加载失败：OOS temporary failure/)).toBeTruthy());
    setVisibility("hidden");
    setVisibility("visible");
    await waitFor(() => expect(fetchMock.mock.calls.filter(([url]) => url.includes("oos-equity"))).toHaveLength(2));
    await waitFor(() => expect(screen.getByText("Normalized OOS Index")).toBeTruthy());
    expect(screen.queryByText(/OOS 指数加载失败：OOS temporary failure/)).toBeNull();
  });

  it("ignores a stale OOS response after switching studies", async () => {
    const pending = [];
    const fetchMock = installOosLifecycleFetch({
      studyIds: ["wf-1", "wf-2"],
      oosHandler: (signal) => {
        if (pending.length === 0) return new Promise((resolve, reject) => { pending.push({ resolve }); signal.addEventListener("abort", () => { const error = new Error("aborted"); error.name = "AbortError"; reject(error); }); });
        return result({ ...oosEquity, missingFolds: 1, hasGaps: true });
      },
    });
    window.history.replaceState({}, "", "/quant/backtests?mode=walk-forward&studyId=wf-1");
    render(<QuantWalkForwardWorkspace />);
    await waitFor(() => expect(fetchMock.mock.calls.filter(([url]) => url.includes("oos-equity"))).toHaveLength(1));
    window.history.pushState({}, "", "/quant/backtests?mode=walk-forward&studyId=wf-2");
    window.dispatchEvent(new PopStateEvent("popstate"));
    await waitFor(() => expect(fetchMock.mock.calls.filter(([url]) => url.includes("oos-equity"))).toHaveLength(2));
    await waitFor(() => expect(screen.getByText("Normalized OOS Index")).toBeTruthy());
    await act(async () => pending[0].resolve(result(oosEquity)));
    expect(screen.getByText("存在失败 Fold；OOS 指数只连接成功 Fold，不补造缺失区间。"));
  });
});
const installFetch = () =>
  vi.spyOn(globalThis, "fetch").mockImplementation(async (url) => {
    if (url.includes("/execution-profiles")) return result([executionProfile]);
    if (url.includes("/strategies")) return result([strategy]);
    if (url.includes("/datasets")) return result([dataset]);
    if (url.includes("/experiments"))
      return result({ records: [], total: 0, page: 1, pageSize: 20 });
    if (url.includes("/walk-forward-studies"))
      return result({ records: [], total: 0, page: 1, pageSize: 20 });
    if (url.includes("/runs"))
      return result({ records: [], total: 0, page: 1, pageSize: 20 });
    throw new Error(`unexpected request ${url}`);
  });

describe("Quant backtest strategy handoff", () => {
  afterEach(() => {
    cleanup();
    vi.restoreAllMocks();
    window.history.replaceState({}, "", "/quant/backtests");
  });

  it("opens after data loading, selects the requested strategy, fills defaults, and consumes the query", async () => {
    installFetch();
    window.history.replaceState(
      {},
      "",
      "/quant/backtests?openCreate=1&strategyCode=rsi%2Fmean",
    );
    render(<QuantBacktests />);
    await waitFor(() =>
      expect(screen.getByRole("dialog", { name: "新建回测" })).toBeTruthy(),
    );
    fireEvent.change(screen.getByRole("combobox", { name: "数据集 / 交易对" }), {
      target: { value: "1" },
    });
    await waitFor(() =>
      expect(screen.getByRole("combobox", { name: "策略" }).value).toBe(
        "rsi/mean",
      ),
    );
    expect(screen.getByDisplayValue("14")).toBeTruthy();
    expect(window.location.pathname).toBe("/quant/backtests");
    expect(window.location.search).toBe("");
  });

  it("shows a profile failure independently while the run list still loads", async () => {
    vi.spyOn(globalThis, "fetch").mockImplementation(async (url) => {
      if (url.includes("/execution-profiles"))
        return {
          ok: false,
          status: 500,
          json: async () => ({ code: 500, message: "执行模型服务失败" }),
        };
      if (url.includes("/strategies")) return result([strategy]);
      if (url.includes("/datasets")) return result([dataset]);
      if (url.includes("/runs"))
        return result({ records: [], total: 0, page: 1, pageSize: 20 });
      throw new Error(`unexpected request ${url}`);
    });
    render(<QuantBacktests />);
    expect(await screen.findByText(/执行模型服务失败/)).toBeTruthy();
    expect(await screen.findByText("还没有创建回测")).toBeTruthy();
    expect(screen.queryByText(/策略不可用|数据集不可用/)).toBeNull();
  });

  it("does not select another strategy when the requested code is unavailable", async () => {
    installFetch();
    window.history.replaceState(
      {},
      "",
      "/quant/backtests?openCreate=1&strategyCode=missing",
    );
    render(<QuantBacktests />);
    await waitFor(() =>
      expect(screen.getByRole("dialog", { name: "新建回测" })).toBeTruthy(),
    );
    expect(screen.getByRole("combobox", { name: "策略" }).value).toBe("");
    expect(screen.getByRole("alert").textContent).toContain(
      "指定策略当前不可用",
    );
  });

  it("defaults to the single view and switches tabs through query state without changing pathname", async () => {
    installFetch();
    render(<QuantBacktests />);
    await waitFor(() =>
      expect(screen.getByText("还没有创建回测")).toBeTruthy(),
    );
    expect(
      screen.getByRole("button", { name: "单次回测" }).getAttribute(
        "aria-current",
      ),
    ).toBe("page");
    fireEvent.click(screen.getByRole("button", { name: "参数实验" }));
    await waitFor(() =>
      expect(screen.getByText("当前没有参数实验")).toBeTruthy(),
    );
    expect(window.location.pathname).toBe("/quant/backtests");
    expect(new URLSearchParams(window.location.search).get("mode")).toBe(
      "experiment",
    );
    window.history.back();
    await waitFor(() =>
      expect(
        screen.getByRole("button", { name: "单次回测" }).getAttribute(
          "aria-current",
        ),
      ).toBe("page"),
    );
  });

  it("opens the third walk-forward mode and keeps its route state", async () => {
    installFetch();
    render(<QuantBacktests />);
    await waitFor(() => expect(screen.getByText("还没有创建回测")).toBeTruthy());
    fireEvent.click(screen.getByRole("button", { name: "滚动验证" }));
    await waitFor(() => expect(screen.getByText("当前没有滚动验证 Study")).toBeTruthy());
    expect(new URLSearchParams(window.location.search).get("mode")).toBe("walk-forward");
    expect(window.location.pathname).toBe("/quant/backtests");
  });

  it("loads a real runId deep link instead of falling back to another task", async () => {
    const run = {
      runId: "run/deep",
      status: "FAILED",
      ...executionContext,
      errorCode: "REAL_FAILURE",
      errorMessage: "真实任务失败",
    };
    vi.spyOn(globalThis, "fetch").mockImplementation(async (url) => {
      if (url.includes("/execution-profiles")) return result([executionProfile]);
      if (url.includes("/strategies")) return result([strategy]);
      if (url.includes("/datasets")) return result([]);
      if (url.endsWith("/runs/run%2Fdeep")) return result(run);
      if (url.includes("/runs"))
        return result({
          records: [
            { runId: "other-run", status: "COMPLETED", ...executionContext },
          ],
          total: 1,
          page: 1,
          pageSize: 20,
        });
      throw new Error(`unexpected request ${url}`);
    });
    window.history.replaceState(
      {},
      "",
      "/quant/backtests?mode=single&runId=run%2Fdeep",
    );
    render(<QuantBacktests />);
    await waitFor(() =>
      expect(screen.getByText("真实任务失败")).toBeTruthy(),
    );
    expect(screen.getAllByText("run/deep").length).toBeGreaterThanOrEqual(2);
    expect(screen.getByText("当前选中任务").nextSibling.textContent).toBe(
      "run/deep",
    );
    fireEvent.click(screen.getByRole("button", { name: "清除选择" }));
    expect(new URLSearchParams(window.location.search).has("runId")).toBe(false);
  });

  it("shows a deep-link detail failure without selecting the first list run", async () => {
    vi.spyOn(globalThis, "fetch").mockImplementation(async (url) => {
      if (url.includes("/execution-profiles")) return result([executionProfile]);
      if (url.includes("/strategies")) return result([strategy]);
      if (url.includes("/datasets")) return result([]);
      if (url.endsWith("/runs/missing"))
        return {
          ok: false,
          status: 404,
          json: async () => ({ code: 404, message: "指定回测不存在" }),
        };
      if (url.includes("/runs"))
        return result({
          records: [{ runId: "first-run", status: "COMPLETED", ...executionContext }],
          total: 1,
          page: 1,
          pageSize: 20,
        });
      throw new Error(`unexpected request ${url}`);
    });
    window.history.replaceState(
      {},
      "",
      "/quant/backtests?mode=single&runId=missing",
    );
    render(<QuantBacktests />);
    expect(await screen.findByText("指定回测不存在")).toBeTruthy();
    expect(screen.getByText("当前选中任务").nextSibling.textContent).toBe(
      "missing",
    );
    expect(document.querySelector(".backtest-run-row").className).not.toContain(
      "selected",
    );
  });

  it("re-requests runId when browser back restores the previous deep link", async () => {
    const fetchMock = vi.spyOn(globalThis, "fetch").mockImplementation(async (url) => {
      if (url.includes("/execution-profiles")) return result([executionProfile]);
      if (url.includes("/strategies")) return result([strategy]);
      if (url.includes("/datasets")) return result([]);
      if (url.endsWith("/runs/run-1"))
        return result({ runId: "run-1", status: "FAILED", ...executionContext, errorMessage: "任务一" });
      if (url.endsWith("/runs/run-2"))
        return result({ runId: "run-2", status: "FAILED", ...executionContext, errorMessage: "任务二" });
      if (url.includes("/runs"))
        return result({ records: [], total: 0, page: 1, pageSize: 20 });
      throw new Error(`unexpected request ${url}`);
    });
    window.history.replaceState(
      {},
      "",
      "/quant/backtests?mode=single&runId=run-1",
    );
    render(<QuantBacktests />);
    expect(await screen.findByText("任务一")).toBeTruthy();
    window.history.pushState(
      {},
      "",
      "/quant/backtests?mode=single&runId=run-2",
    );
    window.dispatchEvent(new PopStateEvent("popstate"));
    expect(await screen.findByText("任务二")).toBeTruthy();
    window.history.back();
    expect(await screen.findByText("任务一")).toBeTruthy();
    expect(
      fetchMock.mock.calls.filter(([url]) => url.endsWith("/runs/run-1")),
    ).toHaveLength(2);
  });

  it("restores focus to the exact create button for close, Escape, backdrop, and success", async () => {
    const run = { runId: "created-run", status: "QUEUED", ...executionContext };
    const fetchMock = vi.spyOn(globalThis, "fetch").mockImplementation(async (url, options) => {
      if (url.includes("/execution-profiles")) return result([executionProfile]);
      if (url.includes("/strategies")) return result([strategy]);
      if (url.includes("/datasets")) return result([dataset]);
      if (url.endsWith("/runs") && options?.method === "POST") return result(run);
      if (url.endsWith("/runs/created-run")) return result(run);
      if (url.includes("/runs"))
        return result({ records: [], total: 0, page: 1, pageSize: 20 });
      throw new Error(`unexpected request ${url}`);
    });
    const unrelated = document.createElement("button");
    unrelated.className = "quant-primary-action";
    unrelated.textContent = "其他主按钮";
    document.body.appendChild(unrelated);
    render(<QuantBacktests />);
    const createButton = await screen.findByRole("button", { name: "新建回测" });

    fireEvent.click(createButton);
    fireEvent.click(screen.getByRole("button", { name: "关闭新建回测" }));
    await waitFor(() => expect(document.activeElement).toBe(createButton));

    fireEvent.click(createButton);
    fireEvent.keyDown(document, { key: "Escape" });
    await waitFor(() => expect(document.activeElement).toBe(createButton));

    fireEvent.click(createButton);
    fireEvent.mouseDown(document.querySelector(".backtest-modal-backdrop"));
    await waitFor(() => expect(document.activeElement).toBe(createButton));

    fireEvent.click(createButton);
    fireEvent.change(screen.getByRole("combobox", { name: "数据集 / 交易对" }), {
      target: { value: "1" },
    });
    fireEvent.change(screen.getByRole("combobox", { name: "策略" }), {
      target: { value: "rsi/mean" },
    });
    fireEvent.change(
      screen.getByLabelText("初始资金（计价资产，当前为 USDT）"),
      { target: { value: "1000" } },
    );
    fireEvent.click(screen.getByRole("button", { name: "创建异步回测" }));
    await waitFor(() => expect(document.activeElement).toBe(createButton));
    const postOptions = fetchMock.mock.calls.find(
      ([url, options]) => url.endsWith("/runs") && options?.method === "POST",
    )[1];
    expect(JSON.parse(postOptions.body)).toMatchObject({
      executionProfileCode: "USDM_PERPETUAL_LONG_ONLY_1X_V1",
      directionMode: "LONG_ONLY",
      orderSizingMode: "BASE_QUANTITY",
      orderAmount: "1",
    });
    expect(postOptions.signal).toBeInstanceOf(AbortSignal);
    expect(unrelated).not.toBe(document.activeElement);
    unrelated.remove();
  });
});
