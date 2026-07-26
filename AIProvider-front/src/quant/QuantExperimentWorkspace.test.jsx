/* @vitest-environment jsdom */
import { act, cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import QuantExperimentWorkspace from "./QuantExperimentWorkspace";
import { fetchDatasets, fetchStrategies } from "./quantBacktestsApi";
import { fetchExperiment, fetchExperimentCandidates, fetchExperiments } from "./quantExperimentsApi";

vi.mock("./quantBacktestsApi", () => ({
  fetchStrategies: vi.fn(),
  fetchDatasets: vi.fn(),
  fetchEquity: vi.fn(),
}));

vi.mock("./quantExperimentsApi", async () => {
  const actual = await vi.importActual("./quantExperimentsApi");
  return {
    ...actual,
    fetchExperiments: vi.fn(),
    fetchExperiment: vi.fn(),
    fetchExperimentCandidates: vi.fn(),
  };
});

const strategy = {
  code: "RSI",
  name: "RSI",
  version: "1.0.0",
  minimumRequiredBars: 20,
  parameters: [{ name: "period", defaultValue: 14, minValue: 2, maxValue: 100 }],
};

const dataset = {
  id: 1,
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

const experiment = (status = "COMPLETED") => ({
  experimentId: "experiment-1",
  datasetId: 1,
  symbol: "BTC/USDT",
  intervalCode: "H1",
  strategyCode: "RSI",
  strategyVersion: "1.0.0",
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
    expect(screen.getByText("period")).toBeTruthy();
    expect(screen.getByText("7, 14")).toBeTruthy();
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
    await act(async () => { await vi.advanceTimersByTimeAsync(3000); });
    expect(screen.getAllByText("已完成").length).toBeGreaterThan(0);
    const callsAtTerminal = fetchExperiment.mock.calls.length;
    await act(async () => { await vi.advanceTimersByTimeAsync(9000); });
    expect(fetchExperiment).toHaveBeenCalledTimes(callsAtTerminal);
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
    render(<QuantExperimentWorkspace />);
    await waitFor(() =>
      expect(fetchExperiment.mock.calls.length).toBeGreaterThanOrEqual(2),
    );
    visibility = "hidden";
    act(() => document.dispatchEvent(new Event("visibilitychange")));
    await waitFor(() =>
      expect(detailSignals.some((signal) => signal.aborted)).toBe(true),
    );
    const hiddenCalls = fetchExperiment.mock.calls.length;
    visibility = "visible";
    act(() => document.dispatchEvent(new Event("visibilitychange")));
    await waitFor(() =>
      expect(fetchExperiment.mock.calls.length).toBeGreaterThan(hiddenCalls),
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
