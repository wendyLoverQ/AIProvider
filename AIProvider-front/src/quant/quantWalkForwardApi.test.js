import { describe, expect, it, vi, beforeEach } from "vitest";
import { createWalkForwardStudy, fetchWalkForwardFolds, fetchWalkForwardOosEquity, fetchWalkForwardStudies, parseIntegerArrayObject, parseIntegerScalarObject, parseParameterFrequency, parseWalkForwardFold, parseWalkForwardOosEquity, parseWalkForwardStudySummary } from "./quantWalkForwardApi";

const summary = {
  studyId: "wf-1", datasetId: 7, provider: "BINANCE", marketType: "SPOT", dataType: "KLINE", symbol: "BTCUSDT", intervalCode: "1h", strategyCode: "s", strategyVersion: "1", parameterGrid: { p: [1] }, windowMode: "ROLLING", studyStartOpenTimeInclusive: "2024-01-01T00:00:00Z", studyEndOpenTimeExclusive: "2024-01-10T00:00:00Z", trainingBars: 48, validationBars: 24, stepBars: 24, foldCount: 2, candidateCountPerFold: 1, totalChildRuns: 4, selectionMetric: "TRAIN_TOTAL_RETURN_RATIO", minimumTrainTrades: 10, orderAmount: "1", feeRate: "0.001", forceCloseAtEnd: true, status: "RUNNING", progressPercent: 50, pendingFolds: 1, activeFolds: 1, completedFolds: 0, failedFolds: 0, selectedParameterChanges: null, successfulOosFolds: null, hasOosGaps: null, totalOosTradeCount: null, totalOosFees: null, totalOosReturnRatio: null, errorCode: null, errorMessage: null, createdAt: "2024-01-01T00:00:00Z", startedAt: null, finishedAt: null, updatedAt: "2024-01-01T00:00:00Z",
};

describe("quant walk-forward API", () => {
  beforeEach(() => vi.restoreAllMocks());

  it("posts the frozen creation contract", async () => {
    const body = { datasetId: 7, strategyCode: "s", strategyVersion: "1", parameterGrid: { p: [1] }, studyStartOpenTimeInclusive: "2024-01-01T00:00:00Z", studyEndOpenTimeExclusive: "2024-01-10T00:00:00Z", trainingBars: 48, validationBars: 24, selectionMetric: "TRAIN_TOTAL_RETURN_RATIO", minimumTrainTrades: 10, orderAmount: "1", feeRate: "0.001", forceCloseAtEnd: true };
    const created = { studyId: "wf-1", foldCount: 2, candidateCountPerFold: 1, totalChildRuns: 4 };
    vi.spyOn(globalThis, "fetch").mockResolvedValue(new Response(JSON.stringify({ code: 200, data: created }), { status: 200, headers: { "Content-Type": "application/json" } }));
    await expect(createWalkForwardStudy(body)).resolves.toEqual(created);
    expect(fetch).toHaveBeenCalledWith("/api/quant/backtests/walk-forward-studies", expect.objectContaining({ method: "POST", body: JSON.stringify(body) }));
  });

  it("uses encoded study and strict pagination query", async () => {
    const response = { records: [], total: 0, page: 2, pageSize: 50 };
    vi.spyOn(globalThis, "fetch").mockResolvedValue(new Response(JSON.stringify({ code: 200, data: response }), { status: 200, headers: { "Content-Type": "application/json" } }));
    await expect(fetchWalkForwardFolds("wf/1", { page: 2, pageSize: 50 })).resolves.toEqual(response);
    expect(fetch).toHaveBeenCalledWith("/api/quant/backtests/walk-forward-studies/wf%2F1/folds?page=2&pageSize=50", expect.any(Object));
  });

  it("passes list filters and OOS limit with an AbortSignal", async () => {
    const response = { records: [], total: 0, page: 1, pageSize: 20 };
    const oos = { sampled: false, totalPoints: 0, successfulFolds: 0, missingFolds: 0, hasGaps: false, totalReturnRatio: null, maximumDrawdownRatio: null, points: [] };
    const fetchMock = vi.spyOn(globalThis, "fetch").mockImplementation(async (url) => new Response(JSON.stringify({ code: 200, data: url.includes("oos-equity") ? oos : response }), { status: 200, headers: { "Content-Type": "application/json" } }));
    const controller = new AbortController();
    await fetchWalkForwardStudies({ status: "RUNNING", symbol: "BTCUSDT", strategyCode: "s", page: 2, pageSize: 20 }, controller.signal);
    await fetchWalkForwardOosEquity("wf-1", 1200, controller.signal);
    expect(fetchMock.mock.calls[0][0]).toContain("status=RUNNING");
    expect(fetchMock.mock.calls[0][0]).toContain("symbol=BTCUSDT");
    expect(fetchMock.mock.calls[1][0]).toContain("oos-equity?limit=1200");
    expect(fetchMock.mock.calls.every(([, options]) => options.signal === controller.signal)).toBe(true);
  });

  it("rejects invalid frozen status, metric, window, progress, and child count", () => {
    expect(() => parseWalkForwardStudySummary({ ...summary, status: "CANCELLED" })).toThrow();
    expect(() => parseWalkForwardStudySummary({ ...summary, selectionMetric: "VALIDATION_TOTAL_RETURN_RATIO" })).toThrow();
    expect(() => parseWalkForwardStudySummary({ ...summary, windowMode: "EXPANDING" })).toThrow();
    expect(() => parseWalkForwardStudySummary({ ...summary, status: "COMPLETED", progressPercent: 99 })).toThrow();
    expect(() => parseWalkForwardStudySummary({ ...summary, totalChildRuns: 2 })).toThrow();
    expect(() => parseWalkForwardStudySummary({ ...summary, completedFolds: 1 })).toThrow();
  });

  it("keeps parameter grids and selected scalar parameters as separate contracts", () => {
    expect(parseIntegerArrayObject({ fast: [5, 7] }, "invalid")).toEqual({ fast: [5, 7] });
    expect(() => parseIntegerArrayObject({ fast: 7 }, "invalid")).toThrow();
    expect(() => parseIntegerArrayObject({ fast: [5, 5] }, "invalid")).toThrow();
    expect(parseIntegerScalarObject({ fast: 7 }, "invalid")).toEqual({ fast: 7 });
    expect(() => parseIntegerScalarObject({ fast: [7] }, "invalid")).toThrow();
    expect(() => parseIntegerScalarObject({ fast: "7" }, "invalid")).toThrow();
    expect(() => parseParameterFrequency({ parameters: { fast: 7 }, selectedCount: 1, firstFoldIndex: 0, lastFoldIndex: 0 })).not.toThrow();
  });

  it("accepts completed and failed folds at terminal progress", () => {
    const base = { foldId: "fold-1", foldIndex: 0, trainingStartOpenTimeInclusive: "2024-01-01T00:00:00Z", trainingEndOpenTimeExclusive: "2024-01-03T00:00:00Z", validationStartOpenTimeInclusive: "2024-01-03T00:00:00Z", validationEndOpenTimeExclusive: "2024-01-04T00:00:00Z", experimentId: "exp-1", experimentStatus: "FAILED", startedAt: null, finishedAt: null, updatedAt: "2024-01-04T00:00:00Z", errorCode: null, errorMessage: null };
    const metrics = { totalReturnRatio: "0", maximumDrawdownRatio: "0", profitFactor: "1", netProfit: "0", winRate: "0", totalFees: "0", buyAndHoldReturnRatio: "0", averageTradeReturnRatio: "0", tradeCount: 0 };
    expect(parseWalkForwardFold({ ...base, status: "COMPLETED", progressPercent: 100, selectedCandidateId: "candidate-1", selectedParameters: { fast: 7 }, selectedTrainingRunId: "train-1", selectedValidationRunId: "valid-1", selectionMetricValue: "0", trainingMetrics: metrics, validationMetrics: metrics })).toBeTruthy();
    expect(parseWalkForwardFold({ ...base, status: "FAILED", progressPercent: 100, selectedCandidateId: null, selectedParameters: null, selectedTrainingRunId: null, selectedValidationRunId: null, selectionMetricValue: null, trainingMetrics: null, validationMetrics: null })).toBeTruthy();
    expect(() => parseWalkForwardFold({ ...base, status: "FAILED", progressPercent: 99, selectedCandidateId: null, selectedParameters: null, selectedTrainingRunId: null, selectedValidationRunId: null, selectionMetricValue: null, trainingMetrics: null, validationMetrics: null })).toThrow();
    expect(() => parseWalkForwardFold({ ...base, status: "PENDING", progressPercent: 100, selectedCandidateId: null, selectedParameters: null, selectedTrainingRunId: null, selectedValidationRunId: null, selectionMetricValue: null, trainingMetrics: null, validationMetrics: null })).toThrow();
    expect(() => parseWalkForwardFold({ ...base, status: "COMPLETED", progressPercent: 100, selectedCandidateId: "candidate-1", selectedParameters: { fast: [7] }, selectedTrainingRunId: "train-1", selectedValidationRunId: "valid-1", selectionMetricValue: "0", trainingMetrics: metrics, validationMetrics: metrics })).toThrow();
  });

  it("rejects malformed OOS points and inconsistent gaps", () => {
    const point = { pointIndex: 0, foldIndex: 0, openTime: "2024-01-01T00:00:00Z", indexRatio: "1", drawdownRatio: "0" };
    expect(() => parseWalkForwardOosEquity({ sampled: false, totalPoints: 2, successfulFolds: 1, missingFolds: 0, hasGaps: false, totalReturnRatio: "0", maximumDrawdownRatio: "0", points: [point] })).toThrow();
    expect(() => parseWalkForwardOosEquity({ sampled: true, totalPoints: 2, successfulFolds: 1, missingFolds: 1, hasGaps: false, totalReturnRatio: null, maximumDrawdownRatio: null, points: [point, { ...point, pointIndex: 1 }] })).toThrow();
  });
});
