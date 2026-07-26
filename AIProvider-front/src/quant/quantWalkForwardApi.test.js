import { describe, expect, it, vi, beforeEach } from "vitest";
import { createWalkForwardStudy, fetchWalkForwardFolds } from "./quantWalkForwardApi";

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
});
