import { afterEach, describe, expect, it, vi } from "vitest";
import {
  createExperiment,
  fetchExperiment,
  fetchExperimentCandidates,
  fetchExperiments,
  parseCandidate,
  parseCandidatePage,
  parseExperimentPage,
  parseExperimentSummary,
} from "./quantExperimentsApi";

const summary = (overrides = {}) => ({
  experimentId: "experiment-1",
  datasetId: 1,
  symbol: "BTC/USDT",
  intervalCode: "H1",
  strategyCode: "RSI_MEAN_REVERSION_LONG_ONLY",
  strategyVersion: "1.0.0",
  executionProfileCode: "USDM_PERPETUAL_LONG_ONLY_1X_V1",
  directionMode: "LONG_ONLY",
  orderSizingMode: "BASE_QUANTITY",
  parameterGrid: { rsiPeriod: [7, 14] },
  candidateCount: 2,
  totalLegs: 4,
  pendingCandidates: 0,
  activeCandidates: 1,
  completedCandidates: 1,
  failedCandidates: 0,
  completedLegs: 2,
  failedLegs: 0,
  status: "RUNNING",
  progressPercent: "50.0",
  trainingStartOpenTimeInclusive: "2025-01-01T00:00:00Z",
  trainingEndOpenTimeExclusive: "2025-09-01T00:00:00Z",
  validationStartOpenTimeInclusive: "2025-09-01T00:00:00Z",
  validationEndOpenTimeExclusive: "2026-01-01T00:00:00Z",
  createdAt: "2026-01-02T00:00:00Z",
  startedAt: null,
  finishedAt: null,
  ...overrides,
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
  parameters: { rsiPeriod: 14 },
  dispatchStatus: "DISPATCHED",
  training: {
    segmentType: "TRAIN",
    runId: "train-1",
    status: "COMPLETED",
    progressPercent: 100,
    metrics,
  },
  validation: {
    segmentType: "VALIDATION",
    runId: "validation-1",
    status: "COMPLETED",
    progressPercent: "100.0",
    metrics,
  },
  ...overrides,
});

const ok = (data) =>
  Promise.resolve({
    ok: true,
    status: 200,
    json: async () => ({ code: 200, data }),
  });

describe("quant experiments API", () => {
  afterEach(() => vi.restoreAllMocks());

  it("posts the frozen create URL and body with AbortSignal", async () => {
    const controller = new AbortController();
    const body = { datasetId: 1, parameterGrid: { rsiPeriod: [7, 14] } };
    const fetchMock = vi.spyOn(globalThis, "fetch").mockImplementation(() =>
      ok({
        experimentId: "new-id",
        candidateCount: 2,
        totalLegs: 4,
      }),
    );
    await expect(createExperiment(body, controller.signal)).resolves.toEqual({
      experimentId: "new-id",
      candidateCount: 2,
      totalLegs: 4,
    });
    expect(fetchMock).toHaveBeenCalledWith(
      "/api/quant/backtests/experiments",
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify(body),
        signal: controller.signal,
      }),
    );
  });

  it("sends list filters and candidate server paging/sorting", async () => {
    const fetchMock = vi
      .spyOn(globalThis, "fetch")
      .mockImplementationOnce(() =>
        ok({ records: [], total: 0, page: 1, pageSize: 20 }),
      )
      .mockImplementationOnce(() =>
        ok({ records: [], total: 0, page: 2, pageSize: 50 }),
      );
    await fetchExperiments({
      status: "RUNNING",
      symbol: "BTC/USDT",
      strategyCode: "RSI",
      page: 1,
      pageSize: 20,
    });
    await fetchExperimentCandidates("id/with space", {
      page: 2,
      pageSize: 50,
      sortBy: "VALIDATION_TOTAL_RETURN_RATIO",
      order: "DESC",
    });
    expect(fetchMock.mock.calls[0][0]).toBe(
      "/api/quant/backtests/experiments?status=RUNNING&symbol=BTC%2FUSDT&strategyCode=RSI&page=1&pageSize=20",
    );
    expect(fetchMock.mock.calls[1][0]).toBe(
      "/api/quant/backtests/experiments/id%2Fwith%20space/candidates?page=2&pageSize=50&sortBy=VALIDATION_TOTAL_RETURN_RATIO&order=DESC",
    );
  });

  it("encodes detail experimentId", async () => {
    const fetchMock = vi
      .spyOn(globalThis, "fetch")
      .mockImplementation(() => ok(summary()));
    await fetchExperiment("id/空 格");
    expect(fetchMock.mock.calls[0][0]).toBe(
      "/api/quant/backtests/experiments/id%2F%E7%A9%BA%20%E6%A0%BC",
    );
  });

  it("rejects HTTP and Result failures", async () => {
    vi.spyOn(globalThis, "fetch")
      .mockResolvedValueOnce({
        ok: false,
        status: 503,
        json: async () => ({ code: 503, message: "服务不可用" }),
      })
      .mockResolvedValueOnce({
        ok: true,
        status: 200,
        json: async () => ({ code: 409, message: "冲突" }),
      });
    await expect(fetchExperiments({})).rejects.toThrow("服务不可用");
    await expect(fetchExperiments({})).rejects.toThrow("冲突");
  });
});

describe("quant experiment strict parsers", () => {
  it("accepts valid summaries, candidates, and empty pages", () => {
    expect(parseExperimentSummary(summary()).experimentId).toBe("experiment-1");
    expect(parseCandidate(candidate()).training.segmentType).toBe("TRAIN");
    expect(
      parseExperimentPage({ records: [], total: 0, page: 1, pageSize: 20 })
        .records,
    ).toEqual([]);
    expect(
      parseCandidatePage({ records: [], total: 0, page: 1, pageSize: 50 })
        .records,
    ).toEqual([]);
  });

  it.each([
    ["status", summary({ status: "PAUSED" })],
    ["progress", summary({ progressPercent: "100.1" })],
    ["parameterGrid", summary({ parameterGrid: { rsiPeriod: [14.5] } })],
    ["count", summary({ candidateCount: -1 })],
    ["time", summary({ validationEndOpenTimeExclusive: "bad" })],
  ])("rejects invalid experiment %s", (_label, value) => {
    expect(() => parseExperimentSummary(value)).toThrow();
  });

  it.each([
    ["terminal progress", summary({ status: "COMPLETED", progressPercent: 99 })],
    ["non-terminal progress", summary({ status: "RUNNING", progressPercent: 100 })],
    ["candidate count", summary({ activeCandidates: 3 })],
    ["completed leg count", summary({ completedLegs: 5 })],
    ["failed leg count", summary({ failedLegs: 5 })],
  ])("rejects inconsistent experiment %s", (_label, value) => {
    expect(() => parseExperimentSummary(value)).toThrow();
  });

  it.each([
    ["dispatch status", candidate({ dispatchStatus: "CANCELLED" })],
    ["parameters", candidate({ parameters: { rsiPeriod: 14.5 } })],
    [
      "training type",
      candidate({
        training: { ...candidate().training, segmentType: "VALIDATION" },
      }),
    ],
    [
      "missing run",
      candidate({ validation: { ...candidate().validation, runId: "" } }),
    ],
    [
      "metric",
      candidate({
        training: {
          ...candidate().training,
          metrics: { ...metrics, totalReturnRatio: "1e-3" },
        },
      }),
    ],
    [
      "segment progress",
      candidate({
        training: {
          ...candidate().training,
          progressPercent: 101,
        },
      }),
    ],
    [
      "segment status",
      candidate({
        training: {
          ...candidate().training,
          status: "PAUSED",
        },
      }),
    ],
  ])("rejects invalid candidate %s", (_label, value) => {
    expect(() => parseCandidate(value)).toThrow();
  });

  it("accepts backend decimal numbers and the pre-dispatch segment state", () => {
    expect(
      parseCandidate(
        candidate({
          training: {
            ...candidate().training,
            status: "NOT_CREATED",
            progressPercent: 0,
            metrics: null,
          },
        }),
      ).training.status,
    ).toBe("NOT_CREATED");
    expect(
      parseCandidate(
        candidate({
          training: {
            ...candidate().training,
            metrics: { ...metrics, totalReturnRatio: 0.1 },
          },
        }),
      ).training.metrics.totalReturnRatio,
    ).toBe(0.1);
  });

  it("rejects non-Page values and any bad record", () => {
    expect(() => parseExperimentPage([])).toThrow();
    expect(() =>
      parseExperimentPage({
        records: [summary({ status: "BEST" })],
        total: 1,
        page: 1,
        pageSize: 20,
      }),
    ).toThrow();
    expect(() =>
      parseCandidatePage({
        records: [candidate({ candidateId: "" })],
        total: 1,
        page: 1,
        pageSize: 50,
      }),
    ).toThrow();
  });
});
