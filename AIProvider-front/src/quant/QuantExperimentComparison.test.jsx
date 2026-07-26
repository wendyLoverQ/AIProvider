/* @vitest-environment jsdom */
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import QuantExperimentComparison from "./QuantExperimentComparison";
import { fetchEquity } from "./quantBacktestsApi";

vi.mock("./quantBacktestsApi", () => ({
  fetchEquity: vi.fn(),
}));

const equity = (ratio) => ({
  sampled: true,
  totalPoints: 3,
  points: [
    { pointIndex: 0, openTime: "2025-01-01T00:00:00Z", equityRatio: "1", drawdownRatio: "0" },
    { pointIndex: 2, openTime: "2025-01-01T02:00:00Z", equityRatio: ratio, drawdownRatio: "0.01" },
  ],
});

const metrics = {
  totalReturnRatio: "0.1",
  maximumDrawdownRatio: "0.02",
  profitFactor: "1.5",
  netProfit: "10",
  winRate: "0.6",
  tradeCount: 4,
  totalFees: "0.1",
  buyAndHoldReturnRatio: "0.03",
  averageTradeReturnRatio: "0.025",
};

const candidate = (overrides = {}) => ({
  candidateId: "candidate-1",
  candidateIndex: 0,
  parameters: { period: 14 },
  dispatchStatus: "DISPATCHED",
  training: { segmentType: "TRAIN", runId: "train-1", status: "COMPLETED", metrics },
  validation: { segmentType: "VALIDATION", runId: "validation-1", status: "COMPLETED", metrics: { ...metrics, totalReturnRatio: "0.05" } },
  ...overrides,
});

describe("QuantExperimentComparison", () => {
  beforeEach(() => {
    window.history.replaceState({}, "", "/quant/backtests?mode=experiment&experimentId=experiment-1&candidatePage=2&candidateSort=TRAIN_NET_PROFIT&candidateOrder=DESC");
  });

  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
    window.history.replaceState({}, "", "/quant/backtests?mode=experiment&experimentId=experiment-1&candidatePage=2&candidateSort=TRAIN_NET_PROFIT&candidateOrder=DESC");
  });

  it("loads two completed segment curves independently and shows objective metric deltas", async () => {
    fetchEquity
      .mockResolvedValueOnce(equity("1.1"))
      .mockResolvedValueOnce(equity("1.05"));
    render(<QuantExperimentComparison candidate={candidate()} strategy={{ parameters: [{ name: "period" }] }} />);
    await waitFor(() => expect(fetchEquity).toHaveBeenCalledTimes(2));
    expect(fetchEquity.mock.calls[0][0]).toBe("train-1");
    expect(fetchEquity.mock.calls[1][0]).toBe("validation-1");
    expect(screen.getByText(/验证收益率 - 训练收益率：-5.00%/)).toBeTruthy();
    expect(screen.getByText(/验证最大回撤 - 训练最大回撤：\+0.00%/)).toBeTruthy();
    await waitFor(() =>
      expect(screen.getAllByText("展示 2 / 3 个抽样点")).toHaveLength(2),
    );
  });

  it("keeps one curve when the other fails and does not request failed segments", async () => {
    fetchEquity
      .mockResolvedValueOnce(equity("1.1"))
      .mockRejectedValueOnce(new Error("VALIDATION 曲线失败"));
    const { rerender } = render(<QuantExperimentComparison candidate={candidate()} />);
    await waitFor(() => expect(screen.getByText("VALIDATION 曲线失败")).toBeTruthy());
    expect(screen.getAllByText("展示 2 / 3 个抽样点")).toHaveLength(1);
    fetchEquity.mockClear();
    rerender(<QuantExperimentComparison candidate={candidate({
      candidateId: "candidate-2",
      training: { ...candidate().training, status: "FAILED", errorCode: "RUN_FAILED", errorMessage: "真实错误" },
      validation: { ...candidate().validation, status: "RUNNING_ENGINE" },
    })} />);
    await waitFor(() => expect(screen.getByText("当前状态：FAILED")).toBeTruthy());
    expect(fetchEquity).not.toHaveBeenCalled();
  });

  it("aborts stale curve requests when switching candidates and navigates to a run deep link", async () => {
    const signals = [];
    fetchEquity.mockImplementation((_runId, _points, signal) => {
      signals.push(signal);
      return new Promise(() => {});
    });
    const { rerender } = render(<QuantExperimentComparison candidate={candidate()} />);
    await waitFor(() => expect(fetchEquity).toHaveBeenCalledTimes(2));
    rerender(<QuantExperimentComparison candidate={candidate({
      candidateId: "candidate-2",
      training: { ...candidate().training, runId: "train-2" },
      validation: { ...candidate().validation, runId: "validation-2" },
    })} />);
    await waitFor(() => expect(signals.slice(0, 2).every((signal) => signal.aborted)).toBe(true));
    fireEvent.click(screen.getByRole("button", { name: "查看 TRAIN 原始任务" }));
    expect(window.location.pathname).toBe("/quant/backtests");
    expect(new URLSearchParams(window.location.search).get("mode")).toBe("single");
    expect(new URLSearchParams(window.location.search).get("runId")).toBe("train-2");
    expect(new URLSearchParams(window.location.search).has("experimentId")).toBe(false);
    window.history.back();
    await waitFor(() => {
      const params = new URLSearchParams(window.location.search);
      expect(params.get("mode")).toBe("experiment");
      expect(params.get("experimentId")).toBe("experiment-1");
      expect(params.get("candidatePage")).toBe("2");
      expect(params.get("candidateSort")).toBe("TRAIN_NET_PROFIT");
      expect(params.get("candidateOrder")).toBe("DESC");
    });
  });
});
