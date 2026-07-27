import { afterEach, describe, expect, it, vi } from "vitest";
import {
  fetchDatasets,
  fetchExecutionProfiles,
  fetchRuns,
  fetchTrades,
  parseExecutionProfileList,
  parsePage,
  parseRunDetail,
  parseStrategyList,
  parseTradePage,
} from "./quantBacktestsApi";

const response = (data) => ({ ok: true, status: 200, json: async () => ({ code: 200, data }) });
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
  limitations: ["不计算资金费率", "不计算强平"],
};

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

  it("loads execution profiles from the frozen URL with AbortSignal", async () => {
    const controller = new AbortController();
    const fetchMock = vi
      .spyOn(globalThis, "fetch")
      .mockResolvedValue(response([executionProfile]));
    await expect(fetchExecutionProfiles(controller.signal)).resolves.toEqual([
      executionProfile,
    ]);
    expect(fetchMock).toHaveBeenCalledWith(
      "/api/quant/backtests/execution-profiles",
      expect.objectContaining({ signal: controller.signal }),
    );
  });

  it("strictly validates strategy capabilities and execution profiles", () => {
    const strategy = {
      code: "RSI",
      version: "1",
      supportedMarketTypes: ["USDM_PERPETUAL"],
      supportedExecutionProfileCodes: [
        "USDM_PERPETUAL_LONG_ONLY_1X_V1",
      ],
      supportedDirectionModes: ["LONG_ONLY"],
      requiredMarketFeatures: ["OHLCV"],
    };
    expect(parseStrategyList([strategy])).toEqual([strategy]);
    expect(() =>
      parseStrategyList([
        { ...strategy, supportedDirectionModes: ["LONG_ONLY", "LONG_ONLY"] },
      ]),
    ).toThrow("策略响应格式异常");
    expect(parseExecutionProfileList([executionProfile])).toEqual([
      executionProfile,
    ]);
    expect(() =>
      parseExecutionProfileList([
        { ...executionProfile, orderSizingMode: "QUOTE_NOTIONAL" },
      ]),
    ).toThrow("执行模型数据格式异常");
  });

  it("requires execution context on runs and direction sides on trades", () => {
    expect(
      parseRunDetail({
        runId: "run-1",
        status: "COMPLETED",
        executionProfileCode: "USDM_PERPETUAL_LONG_ONLY_1X_V1",
        directionMode: "LONG_ONLY",
        orderSizingMode: "BASE_QUANTITY",
      }),
    ).toBeTruthy();
    expect(() =>
      parseRunDetail({ runId: "run-1", status: "COMPLETED" }),
    ).toThrow("回测详情响应格式异常");
    const page = {
      records: [
        {
          tradeNo: 1,
          positionSide: "LONG",
          entryOrderSide: "BUY",
          exitOrderSide: "SELL",
        },
      ],
      total: 1,
      page: 1,
      pageSize: 20,
    };
    expect(parseTradePage(page)).toBe(page);
    expect(() =>
      parseTradePage({
        ...page,
        records: [{ ...page.records[0], positionSide: undefined }],
      }),
    ).toThrow("回测成交响应格式异常");
  });
});
