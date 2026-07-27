import { describe, expect, it } from "vitest";
import {
  compatibleDatasets,
  compatibleProfiles,
  compatibleStrategies,
  executionContextPayload,
  formatMarketType,
  marketTypesFromDatasets,
  resolveExecutionSelection,
  validateExecutionSelection,
} from "./quantExecutionContext";

const dataset = {
  id: 1,
  status: "CONTIGUOUS",
  gapCount: 0,
  gapSegmentCount: 0,
  marketType: "USDM_PERPETUAL",
  dataType: "KLINE",
};
const strategy = {
  code: "RSI",
  supportedMarketTypes: ["USDM_PERPETUAL"],
  supportedExecutionProfileCodes: ["USDM_PERPETUAL_LONG_ONLY_1X_V1"],
  supportedDirectionModes: ["LONG_ONLY"],
  requiredMarketFeatures: ["OHLCV"],
};
const profile = {
  code: "USDM_PERPETUAL_LONG_ONLY_1X_V1",
  marketType: "USDM_PERPETUAL",
  directionMode: "LONG_ONLY",
  orderSizingMode: "BASE_QUANTITY",
  requiredMarketFeatures: ["OHLCV"],
};

describe("quant execution context", () => {
  it("extracts supported market types and formats USDM", () => {
    expect(marketTypesFromDatasets([dataset, { ...dataset, id: 2 }])).toEqual([
      "USDM_PERPETUAL",
    ]);
    expect(formatMarketType("USDM_PERPETUAL")).toBe("USDT 本位永续合约");
    expect(compatibleDatasets([{ ...dataset, gapCount: 1 }, dataset], "USDM_PERPETUAL")).toEqual([dataset]);
  });

  it("maps KLINE to OHLCV when filtering strategies and profiles", () => {
    expect(compatibleStrategies([strategy], dataset)).toEqual([strategy]);
    expect(compatibleProfiles([profile], dataset, strategy)).toEqual([profile]);
    expect(
      compatibleStrategies(
        [{ ...strategy, requiredMarketFeatures: ["ORDER_BOOK"] }],
        dataset,
      ),
    ).toEqual([]);
  });

  it("auto-selects one compatible profile but does not guess among many", () => {
    const base = {
      datasets: [dataset],
      strategies: [strategy],
      value: {
        marketType: "USDM_PERPETUAL",
        datasetId: "1",
        strategyCode: "RSI",
        executionProfileCode: "",
      },
    };
    expect(
      resolveExecutionSelection({ ...base, profiles: [profile] })
        .executionProfileCode,
    ).toBe(profile.code);
    expect(
      resolveExecutionSelection({
        ...base,
        strategies: [
          {
            ...strategy,
            supportedExecutionProfileCodes: [
              profile.code,
              "ANOTHER",
            ],
          },
        ],
        profiles: [profile, { ...profile, code: "ANOTHER" }],
      }).executionProfileCode,
    ).toBe("");
  });

  it("clears incompatible downstream values without mutating inputs", () => {
    const value = {
      marketType: "USDM_PERPETUAL",
      datasetId: "1",
      strategyCode: "RSI",
      executionProfileCode: profile.code,
    };
    const snapshot = structuredClone(value);
    const resolved = resolveExecutionSelection({
      datasets: [{ ...dataset, marketType: "SPOT" }],
      strategies: [strategy],
      profiles: [profile],
      value,
    });
    expect(resolved).toEqual({
      marketType: "",
      datasetId: "",
      strategyCode: "",
      executionProfileCode: "",
    });
    expect(value).toEqual(snapshot);
  });

  it("validates the whole selection and emits the exact three fields", () => {
    const value = {
      marketType: "USDM_PERPETUAL",
      datasetId: "1",
      strategyCode: "RSI",
      executionProfileCode: profile.code,
    };
    expect(
      validateExecutionSelection({
        datasets: [dataset],
        strategies: [strategy],
        profiles: [profile],
        value,
      }).valid,
    ).toBe(true);
    expect(executionContextPayload(profile)).toEqual({
      executionProfileCode: "USDM_PERPETUAL_LONG_ONLY_1X_V1",
      directionMode: "LONG_ONLY",
      orderSizingMode: "BASE_QUANTITY",
    });
  });
});
