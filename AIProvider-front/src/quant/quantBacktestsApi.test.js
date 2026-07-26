import { afterEach, describe, expect, it, vi } from "vitest";
import { fetchDatasets, fetchRuns, fetchTrades, parsePage } from "./quantBacktestsApi";

const response = (data) => ({ ok: true, status: 200, json: async () => ({ code: 200, data }) });

describe("quant backtest API contracts", () => {
  afterEach(() => vi.restoreAllMocks());

  it("uses the market-data datasets endpoint and accepts an array", async () => {
    const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValue(response([]));
    await expect(fetchDatasets()).resolves.toEqual([]);
    expect(fetchMock).toHaveBeenCalledWith("/api/quant/market-data/datasets?status=CONTIGUOUS&page=1&pageSize=100", expect.anything());
    expect(fetchMock.mock.calls[0][0]).not.toContain("/api/quant/backtests/market-data");
  });

  it("rejects paged responses without records or with zero-based values", () => {
    expect(() => parsePage({ records: [], total: 0, page: 0, pageSize: 20 })).toThrow();
    expect(() => parsePage({ records: [], total: 0, page: 1, pageSize: 0 })).toThrow();
    expect(() => parsePage({ records: [], total: 0, page: 1, pageSize: 20 })).not.toThrow();
  });

  it("keeps runs and trades on the records page protocol", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(response({ records: [], total: 0, page: 1, pageSize: 20 }));
    await expect(fetchRuns()).resolves.toMatchObject({ records: [] });
    await expect(fetchTrades("run-1")).resolves.toMatchObject({ records: [] });
    vi.restoreAllMocks();
    vi.spyOn(globalThis, "fetch").mockResolvedValue(response({ records: [] }));
    await expect(fetchRuns()).rejects.toThrow("回测服务响应格式异常");
  });
});
