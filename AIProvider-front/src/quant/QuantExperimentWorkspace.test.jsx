/* @vitest-environment jsdom */
import { act, cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import QuantExperimentWorkspace from "./QuantExperimentWorkspace";
import { fetchDatasets, fetchExecutionProfiles, fetchStrategies } from "./quantBacktestsApi";
import { createExperiment, fetchExperiment, fetchExperimentCandidates, fetchExperiments } from "./quantExperimentsApi";

vi.mock("./quantBacktestsApi", () => ({
  fetchStrategies: vi.fn(),
  fetchDatasets: vi.fn(),
  fetchExecutionProfiles: vi.fn(),
  fetchEquity: vi.fn(),
}));

vi.mock("./quantExperimentsApi", async () => {
  const actual = await vi.importActual("./quantExperimentsApi");
  return {
    ...actual,
    fetchExperiments: vi.fn(),
    fetchExperiment: vi.fn(),
    fetchExperimentCandidates: vi.fn(),
    createExperiment: vi.fn(),
  };
});

const strategy = {
  code: "RSI",
  name: "RSI",
  version: "1.0.0",
  minimumRequiredBars: 20,
  supportedMarketTypes: ["USDM_PERPETUAL"],
  supportedExecutionProfileCodes: ["USDM_PERPETUAL_LONG_ONLY_1X_V1"],
  supportedDirectionModes: ["LONG_ONLY"],
  requiredMarketFeatures: ["OHLCV"],
  parameters: [{ name: "period", defaultValue: 14, minValue: 2, maxValue: 100 }],
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
  latestOpenTime: "2025-02-01T00:00:00Z",
  lastValidatedAt: "2025-02-02T00:00:00Z",
  candleCount: 745,
  symbol: "BTC/USDT",
  interval: "H1",
};
const executionProfile = {
  code: "USDM_PERPETUAL_LONG_ONLY_1X_V1",
  name: "USDT 本位永续·只做多·1× V1",
  marketType: "USDM_PERPETUAL",
  directionMode: "LONG_ONLY",
  orderSizingMode: "BASE_QUANTITY",
  entryOrderSide: "BUY",
  exitOrderSide: "SELL",
  positionSide: "LONG",
  leverage: "1",
  limitations: ["不计算资金费率"],
};

const experiment = (status = "COMPLETED") => ({
  experimentId: "experiment-1",
  datasetId: 1,
  symbol: "BTC/USDT",
  intervalCode: "H1",
  strategyCode: "RSI",
  strategyVersion: "1.0.0",
  executionProfileCode: "USDM_PERPETUAL_LONG_ONLY_1X_V1",
  directionMode: "LONG_ONLY",
  orderSizingMode: "BASE_QUANTITY",
  parameterGrid: { period: [7, 14] },
  candidateCount: 2,
  pendingCandidates: status === "QUEUED" ? 2 : 0,
  activeCandidates: status === "RUNNING" ? 1 : 0,
  completedCandidates: status === "COMPLETED" ? 2 : 0,
  failedCandidates: status === "COMPLETED_WITH_FAILURES" ? 1 : 0,
  completedLegs: status === "COMPLETED" ? 4 : 0,
  failedLegs: status === "COMPLETED_WITH_FAILURES" ? 1 : 0,
  status,
  progressPercent: status === "COMPLETED" ? 100 : 25,
  trainingStartOpenTimeInclusive: "2025-01-01T00:00:00Z",
  trainingEndOpenTimeExclusive: "2025-01-20T00:00:00Z",
  validationStartOpenTimeInclusive: "2025-01-20T00:00:00Z",
  validationEndOpenTimeExclusive: "2025-02-01T01:00:00Z",
  orderAmount: "1",
  feeRate: "0.001",
  forceCloseAtEnd: true,
  createdAt: "2025-02-02T00:00:00Z",
  startedAt: "2025-02-02T00:00:01Z",
  finishedAt: status === "COMPLETED" ? "2025-02-02T00:01:00Z" : null,
});

const candidatePage = {
  records: [],
  total: 0,
  page: 1,
  pageSize: 50,
};

const failedCandidate = {
  candidateId: "candidate-1",
  candidateIndex: 0,
  parameters: { period: 14 },
  dispatchStatus: "FAILED",
  training: {
    segmentType: "TRAIN",
    runId: "train-1",
    status: "FAILED",
    metrics: null,
    errorCode: "RUN_FAILED",
    errorMessage: "训练失败",
  },
  validation: {
    segmentType: "VALIDATION",
    runId: "validation-1",
    status: "NOT_CREATED",
    metrics: null,
  },
};

describe("QuantExperimentWorkspace", () => {
  beforeEach(() => {
    window.history.replaceState({}, "", "/quant/backtests?mode=experiment");
    fetchStrategies.mockResolvedValue([strategy]);
    fetchDatasets.mockResolvedValue([dataset]);
    fetchExecutionProfiles.mockResolvedValue([executionProfile]);
    fetchExperiments.mockResolvedValue({ records: [], total: 0, page: 1, pageSize: 20 });
    fetchExperimentCandidates.mockResolvedValue(candidatePage);
  });

  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
    vi.useRealTimers();
  });

  it("shows loading then the real empty state without selecting a fake experiment", async () => {
    render(<QuantExperimentWorkspace />);
    expect(screen.getByText("正在读取参数实验…")).toBeTruthy();
    await waitFor(() => expect(screen.getByText("当前没有参数实验")).toBeTruthy());
    expect(screen.getByText("请选择一个参数实验")).toBeTruthy();
    expect(fetchExperiment).not.toHaveBeenCalled();
  });

  it("loads an experimentId deep link, renders counts/grid, and sends candidate route paging/sorting", async () => {
    window.history.replaceState({}, "", "/quant/backtests?mode=experiment&experimentId=experiment-1&candidatePage=2&candidateSort=TRAIN_NET_PROFIT&candidateOrder=DESC");
    fetchExperiments.mockResolvedValue({ records: [experiment()], total: 1, page: 1, pageSize: 20 });
    fetchExperiment.mockResolvedValue(experiment());
    fetchExperimentCandidates.mockResolvedValue({ ...candidatePage, page: 2 });
    render(<QuantExperimentWorkspace />);
    await waitFor(() => expect(screen.getByText("实验详情")).toBeTruthy());
    expect(screen.getByText("USDT 本位永续·只做多·1× V1")).toBeTruthy();
    await waitFor(() =>
      expect(
        screen.getByRole("button", { name: /BTC\/USDT/ }).textContent,
      ).toContain("USDT 本位永续·只做多·1× V1"),
    );
    expect(screen.getByText("period")).toBeTruthy();
    expect(screen.getByText("7, 14")).toBeTruthy();
    expect(screen.getByText("历史任务未记录")).toBeTruthy();
    expect(screen.getByText(/待处理 0 · 活跃 0 · 完成 2 · 失败 0/)).toBeTruthy();
    expect(fetchExperimentCandidates).toHaveBeenCalledWith(
      "experiment-1",
      { page: 2, pageSize: 50, sortBy: "TRAIN_NET_PROFIT", order: "DESC" },
      expect.any(AbortSignal),
    );
    fireEvent.change(screen.getByRole("combobox", { name: "候选排序字段" }), { target: { value: "VALIDATION_WIN_RATE" } });
    await waitFor(() => expect(new URLSearchParams(window.location.search).get("candidatePage")).toBe("1"));
    expect(new URLSearchParams(window.location.search).get("candidateSort")).toBe("VALIDATION_WIN_RATE");
  });

  it("loads a selected list item and resets filters to page one", async () => {
    fetchExperiments.mockResolvedValue({ records: [experiment()], total: 21, page: 1, pageSize: 20 });
    fetchExperiment.mockResolvedValue(experiment("COMPLETED_WITH_FAILURES"));
    render(<QuantExperimentWorkspace />);
    const row = await screen.findByRole("button", { name: /BTC\/USDT/ });
    fireEvent.click(row);
    await waitFor(() => expect(fetchExperiment).toHaveBeenCalledWith("experiment-1", expect.any(AbortSignal)));
    expect(new URLSearchParams(window.location.search).get("experimentId")).toBe("experiment-1");
    fireEvent.change(screen.getByLabelText("实验状态筛选"), { target: { value: "FAILED" } });
    await waitFor(() => expect(fetchExperiments).toHaveBeenCalledWith(
      expect.objectContaining({ status: "FAILED", page: 1, pageSize: 20 }),
      expect.any(AbortSignal),
    ));
  });

  it("keeps page two after the debounce and resets only when filters change", async () => {
    fetchExperiments.mockImplementation(({ page, status }) =>
      Promise.resolve({
        records: [],
        total: 21,
        page,
        pageSize: 20,
        status,
      }),
    );
    render(<QuantExperimentWorkspace />);
    await waitFor(() => expect(screen.getByText("第 1 / 2 页")).toBeTruthy());
    fireEvent.click(screen.getByRole("button", { name: "下一页" }));
    await waitFor(() =>
      expect(fetchExperiments).toHaveBeenCalledWith(
        expect.objectContaining({ page: 2 }),
        expect.any(AbortSignal),
      ),
    );
    expect(screen.getByText("第 2 / 2 页")).toBeTruthy();
    await new Promise((resolve) => setTimeout(resolve, 350));
    expect(screen.getByText("第 2 / 2 页")).toBeTruthy();
    expect(fetchExperiments.mock.calls.at(-1)[0].page).toBe(2);
    fireEvent.change(screen.getByLabelText("实验状态筛选"), {
      target: { value: "FAILED" },
    });
    await waitFor(() =>
      expect(fetchExperiments).toHaveBeenCalledWith(
        expect.objectContaining({ status: "FAILED", page: 1 }),
        expect.any(AbortSignal),
      ),
    );
  });

  it("accepts only the last rapid filter response", async () => {
    let resolveOld;
    const old = {
      ...experiment(),
      experimentId: "old-filter",
      symbol: "OLD/USDT",
    };
    const latest = {
      ...experiment(),
      experimentId: "latest-filter",
      symbol: "ETH/USDT",
    };
    fetchExperiments.mockImplementation(({ status, symbol, page }) => {
      if (status === "FAILED" && !symbol)
        return new Promise((resolve) => {
          resolveOld = resolve;
        });
      if (symbol === "ETH/USDT")
        return Promise.resolve({
          records: [latest],
          total: 1,
          page,
          pageSize: 20,
        });
      return Promise.resolve({ records: [], total: 0, page, pageSize: 20 });
    });
    render(<QuantExperimentWorkspace />);
    await waitFor(() => expect(screen.getByText("当前没有参数实验")).toBeTruthy());
    fireEvent.change(screen.getByLabelText("实验状态筛选"), {
      target: { value: "FAILED" },
    });
    await waitFor(() => expect(resolveOld).toBeTypeOf("function"));
    fireEvent.change(screen.getByLabelText("交易对筛选"), {
      target: { value: "ETH/USDT" },
    });
    expect(await screen.findByRole("button", { name: /ETH\/USDT/ })).toBeTruthy();
    await act(async () => {
      resolveOld({ records: [old], total: 1, page: 1, pageSize: 20 });
      await Promise.resolve();
    });
    expect(screen.queryByRole("button", { name: /OLD\/USDT/ })).toBeNull();
    expect(screen.getByRole("button", { name: /ETH\/USDT/ })).toBeTruthy();
  });

  it("clears shared errors on a successful refresh", async () => {
    fetchStrategies
      .mockRejectedValueOnce(new Error("旧策略错误"))
      .mockResolvedValueOnce([strategy]);
    render(<QuantExperimentWorkspace />);
    expect(await screen.findByText(/旧策略错误/)).toBeTruthy();
    fireEvent.click(screen.getByRole("button", { name: "刷新" }));
    await waitFor(() => expect(screen.queryByText(/旧策略错误/)).toBeNull());
  });

  it("does not let an older profile failure overwrite a newer success", async () => {
    let rejectOld;
    fetchExecutionProfiles
      .mockImplementationOnce(
        () =>
          new Promise((_resolve, reject) => {
            rejectOld = reject;
          }),
      )
      .mockResolvedValueOnce([executionProfile]);
    render(<QuantExperimentWorkspace />);
    fireEvent.click(screen.getByRole("button", { name: "刷新" }));
    await waitFor(() =>
      expect(fetchExecutionProfiles).toHaveBeenCalledTimes(2),
    );
    await act(async () => {
      rejectOld(new Error("迟到的旧错误"));
      await Promise.resolve();
    });
    expect(screen.queryByText(/迟到的旧错误/)).toBeNull();
  });

  it("shows a profile failure independently while list loading still succeeds", async () => {
    fetchExecutionProfiles.mockRejectedValue(new Error("执行模型服务失败"));
    render(<QuantExperimentWorkspace />);
    expect(await screen.findByText(/执行模型服务失败/)).toBeTruthy();
    expect(await screen.findByText("当前没有参数实验")).toBeTruthy();
    expect(screen.queryByText(/策略不可用|数据集不可用/)).toBeNull();
  });

  it("keeps the dataset failure isolated and does not expose strategies without a dataset", async () => {
    fetchDatasets.mockRejectedValue(new Error("数据集服务失败"));
    render(<QuantExperimentWorkspace />);
    expect(await screen.findByText(/数据集服务失败/)).toBeTruthy();
    fireEvent.click(screen.getByRole("button", { name: "新建参数实验" }));
    expect(screen.queryByRole("option", { name: /RSI/ })).toBeNull();
    expect(fetchStrategies).toHaveBeenCalled();
  });

  it("polls a running experiment immediately and every three seconds, then stops at terminal state", async () => {
    vi.useFakeTimers();
    window.history.replaceState({}, "", "/quant/backtests?mode=experiment&experimentId=experiment-1");
    fetchExperiments.mockResolvedValue({ records: [experiment("RUNNING")], total: 1, page: 1, pageSize: 20 });
    fetchExperiment
      .mockResolvedValueOnce(experiment("RUNNING"))
      .mockResolvedValueOnce(experiment("RUNNING"))
      .mockResolvedValueOnce(experiment("COMPLETED"));
    render(<QuantExperimentWorkspace />);
    await act(async () => {
      await Promise.resolve();
      await Promise.resolve();
      await Promise.resolve();
      await Promise.resolve();
    });
    expect(fetchExperiment.mock.calls.length).toBeGreaterThanOrEqual(2);
    const candidatesBeforeTerminal = fetchExperimentCandidates.mock.calls.length;
    const listsBeforeTerminal = fetchExperiments.mock.calls.length;
    await act(async () => { await vi.advanceTimersByTimeAsync(3000); });
    expect(screen.getAllByText("已完成").length).toBeGreaterThan(0);
    expect(fetchExperimentCandidates.mock.calls.length).toBeGreaterThan(
      candidatesBeforeTerminal,
    );
    expect(fetchExperiments.mock.calls.length).toBeGreaterThan(
      listsBeforeTerminal,
    );
    const callsAtTerminal = fetchExperiment.mock.calls.length;
    const candidateCallsAtTerminal = fetchExperimentCandidates.mock.calls.length;
    const listCallsAtTerminal = fetchExperiments.mock.calls.length;
    await act(async () => { await vi.advanceTimersByTimeAsync(9000); });
    expect(fetchExperiment).toHaveBeenCalledTimes(callsAtTerminal);
    expect(fetchExperimentCandidates).toHaveBeenCalledTimes(
      candidateCallsAtTerminal,
    );
    expect(fetchExperiments).toHaveBeenCalledTimes(listCallsAtTerminal);
  });

  it("aborts active polling while hidden and immediately resumes when visible", async () => {
    let visibility = "visible";
    vi.spyOn(document, "visibilityState", "get").mockImplementation(
      () => visibility,
    );
    window.history.replaceState(
      {},
      "",
      "/quant/backtests?mode=experiment&experimentId=experiment-1",
    );
    const detailSignals = [];
    const candidateSignals = [];
    const listSignals = [];
    fetchExperiments.mockResolvedValue({
      records: [experiment("RUNNING")],
      total: 1,
      page: 1,
      pageSize: 20,
    });
    fetchExperiment.mockImplementation((_id, signal) => {
      detailSignals.push(signal);
      return Promise.resolve(experiment("RUNNING"));
    });
    fetchExperimentCandidates.mockImplementation((_id, _filters, signal) => {
      candidateSignals.push(signal);
      return Promise.resolve(candidatePage);
    });
    fetchExperiments.mockImplementation((_filters, signal) => {
      listSignals.push(signal);
      return Promise.resolve({
        records: [experiment("RUNNING")],
        total: 1,
        page: 1,
        pageSize: 20,
      });
    });
    render(<QuantExperimentWorkspace />);
    await waitFor(() =>
      expect(fetchExperiment.mock.calls.length).toBeGreaterThanOrEqual(2),
    );
    visibility = "hidden";
    act(() => document.dispatchEvent(new Event("visibilitychange")));
    await waitFor(() =>
      expect(detailSignals.some((signal) => signal.aborted)).toBe(true),
    );
    expect(candidateSignals.some((signal) => signal.aborted)).toBe(true);
    expect(listSignals.some((signal) => signal.aborted)).toBe(true);
    const hiddenCalls = fetchExperiment.mock.calls.length;
    visibility = "visible";
    act(() => document.dispatchEvent(new Event("visibilitychange")));
    await waitFor(() =>
      expect(fetchExperiment.mock.calls.length).toBeGreaterThan(hiddenCalls),
    );
    expect(fetchExperimentCandidates.mock.calls.length).toBeGreaterThan(1);
    expect(fetchExperiments.mock.calls.length).toBeGreaterThan(1);
  });

  it("blocks every close path while creating and sends one abortable POST", async () => {
    let resolveCreate;
    createExperiment.mockImplementation(
      () =>
        new Promise((resolve) => {
          resolveCreate = resolve;
        }),
    );
    render(<QuantExperimentWorkspace />);
    await waitFor(() => expect(screen.getByRole("button", { name: "新建参数实验" })).toBeTruthy());
    fireEvent.click(screen.getByRole("button", { name: "新建参数实验" }));
    fireEvent.change(screen.getByRole("combobox", { name: "数据集 / 交易对" }), {
      target: { value: "1" },
    });
    fireEvent.change(screen.getByRole("combobox", { name: "策略" }), {
      target: { value: "RSI" },
    });
    fireEvent.change(
      screen.getByLabelText("初始资金（计价资产，当前为 USDT）"),
      { target: { value: "1000" } },
    );
    fireEvent.click(screen.getByRole("button", { name: "按 70% / 30% 填充" }));
    const submit = screen.getByRole("button", { name: "创建异步实验" });
    fireEvent.click(submit);
    fireEvent.click(submit);
    expect(createExperiment).toHaveBeenCalledTimes(1);
    expect(createExperiment.mock.calls[0][1]).toBeInstanceOf(AbortSignal);
    expect(screen.getByRole("button", { name: "关闭新建参数实验" }).disabled).toBe(true);
    fireEvent.keyDown(document, { key: "Escape" });
    fireEvent.mouseDown(document.querySelector(".backtest-modal-backdrop"));
    expect(screen.getByRole("dialog", { name: "新建参数实验" })).toBeTruthy();
    resolveCreate({ experimentId: "created", candidateCount: 1, totalLegs: 2 });
    fetchExperiment.mockResolvedValue({ ...experiment(), experimentId: "created" });
    await waitFor(() =>
      expect(screen.queryByRole("dialog", { name: "新建参数实验" })).toBeNull(),
    );
    expect(new URLSearchParams(window.location.search).get("experimentId")).toBe(
      "created",
    );
    await waitFor(() =>
      expect(document.activeElement).toBe(
        screen.getByRole("button", { name: "新建参数实验" }),
      ),
    );
  });

  it("does not let an old experiment response overwrite a newer selection", async () => {
    let resolveOld;
    window.history.replaceState(
      {},
      "",
      "/quant/backtests?mode=experiment&experimentId=old-experiment",
    );
    const newer = { ...experiment(), experimentId: "new-experiment", symbol: "ETH/USDT" };
    fetchExperiments.mockResolvedValue({
      records: [newer],
      total: 1,
      page: 1,
      pageSize: 20,
    });
    fetchExperiment.mockImplementation((id) => {
      if (id === "old-experiment") {
        return new Promise((resolve) => {
          resolveOld = resolve;
        });
      }
      return Promise.resolve(newer);
    });
    render(<QuantExperimentWorkspace />);
    const row = await screen.findByRole("button", { name: /ETH\/USDT/ });
    fireEvent.click(row);
    await waitFor(() =>
      expect(screen.getByText(/ETH\/USDT · 1h/)).toBeTruthy(),
    );
    await act(async () => {
      resolveOld({ ...experiment(), experimentId: "old-experiment", symbol: "OLD/USDT" });
      await Promise.resolve();
    });
    expect(screen.queryByText(/OLD\/USDT · 1h/)).toBeNull();
    expect(screen.getAllByText(/ETH\/USDT · 1h/).length).toBeGreaterThan(0);
  });

  it("does not let an old terminal poll disrupt the newly selected experiment", async () => {
    let resolveOldTerminal;
    let oldCalls = 0;
    const newer = {
      ...experiment(),
      experimentId: "new-experiment",
      symbol: "ETH/USDT",
    };
    window.history.replaceState(
      {},
      "",
      "/quant/backtests?mode=experiment&experimentId=old-experiment",
    );
    fetchExperiments.mockResolvedValue({
      records: [newer],
      total: 1,
      page: 1,
      pageSize: 20,
    });
    fetchExperiment.mockImplementation((id) => {
      if (id === "new-experiment") return Promise.resolve(newer);
      oldCalls += 1;
      if (oldCalls === 1)
        return Promise.resolve({
          ...experiment("RUNNING"),
          experimentId: "old-experiment",
          symbol: "OLD/USDT",
        });
      return new Promise((resolve) => {
        resolveOldTerminal = resolve;
      });
    });
    render(<QuantExperimentWorkspace />);
    await waitFor(() => expect(resolveOldTerminal).toBeTypeOf("function"));
    fireEvent.click(await screen.findByRole("button", { name: /ETH\/USDT/ }));
    await waitFor(() => expect(screen.getByText(/ETH\/USDT · 1h/)).toBeTruthy());
    const oldCandidateCalls = fetchExperimentCandidates.mock.calls.filter(
      ([id]) => id === "old-experiment",
    ).length;
    await act(async () => {
      resolveOldTerminal({
        ...experiment("COMPLETED"),
        experimentId: "old-experiment",
        symbol: "OLD/USDT",
      });
      await Promise.resolve();
      await Promise.resolve();
    });
    expect(screen.queryByText(/OLD\/USDT · 1h/)).toBeNull();
    expect(
      fetchExperimentCandidates.mock.calls.filter(
        ([id]) => id === "old-experiment",
      ),
    ).toHaveLength(oldCandidateCalls);
  });

  it("aborts shared strategy and dataset requests on unmount", () => {
    const signals = [];
    fetchStrategies.mockImplementation((signal) => {
      signals.push(signal);
      return new Promise(() => {});
    });
    fetchDatasets.mockImplementation((signal) => {
      signals.push(signal);
      return new Promise(() => {});
    });
    const { unmount } = render(<QuantExperimentWorkspace />);
    expect(signals).toHaveLength(2);
    expect(signals.every((signal) => !signal.aborted)).toBe(true);
    unmount();
    expect(signals.every((signal) => signal.aborted)).toBe(true);
  });

  it("preserves a selected candidate only while it remains on the refreshed page", async () => {
    window.history.replaceState(
      {},
      "",
      "/quant/backtests?mode=experiment&experimentId=experiment-1",
    );
    fetchExperiments.mockResolvedValue({
      records: [experiment()],
      total: 1,
      page: 1,
      pageSize: 20,
    });
    fetchExperiment.mockResolvedValue(experiment());
    fetchExperimentCandidates
      .mockResolvedValueOnce({
        records: [failedCandidate],
        total: 1,
        page: 1,
        pageSize: 50,
      })
      .mockResolvedValueOnce({
        records: [failedCandidate],
        total: 1,
        page: 1,
        pageSize: 50,
      })
      .mockResolvedValueOnce(candidatePage);
    render(<QuantExperimentWorkspace />);
    const candidateButton = await screen.findByRole("button", {
      name: "查看候选 0",
    });
    fireEvent.click(candidateButton);
    expect(screen.getByText("TRAIN / VALIDATION 对照")).toBeTruthy();
    fireEvent.click(screen.getByRole("button", { name: "刷新" }));
    await waitFor(() =>
      expect(screen.getByText("TRAIN / VALIDATION 对照")).toBeTruthy(),
    );
    fireEvent.click(screen.getByRole("button", { name: "刷新" }));
    await waitFor(() =>
      expect(screen.queryByText("TRAIN / VALIDATION 对照")).toBeNull(),
    );
  });
});
