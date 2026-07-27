/* @vitest-environment jsdom */
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import { useState } from "react";
import QuantExecutionContextFields from "./QuantExecutionContextFields";

const datasets = [
  {
    id: 1,
    provider: "BINANCE",
    symbol: "BTCUSDT",
    interval: "H1",
    marketType: "USDM_PERPETUAL",
    dataType: "KLINE",
    status: "CONTIGUOUS",
    gapCount: 0,
    gapSegmentCount: 0,
    candleCount: 100,
    earliestOpenTime: "2025-01-01T00:00:00Z",
    latestOpenTime: "2025-01-05T03:00:00Z",
  },
];
const strategies = [
  {
    code: "RSI",
    name: "RSI",
    version: "1",
    minimumRequiredBars: 20,
    supportedMarketTypes: ["USDM_PERPETUAL"],
    supportedExecutionProfileCodes: ["USDM_PERPETUAL_LONG_ONLY_1X_V1"],
    supportedDirectionModes: ["LONG_ONLY"],
    requiredMarketFeatures: ["OHLCV"],
  },
];
const executionProfiles = [
  {
    code: "USDM_PERPETUAL_LONG_ONLY_1X_V1",
    name: "USDT 本位永续·只做多·1× V1",
    marketType: "USDM_PERPETUAL",
    directionMode: "LONG_ONLY",
    orderSizingMode: "BASE_QUANTITY",
    entryOrderSide: "BUY",
    exitOrderSide: "SELL",
    positionSide: "LONG",
    leverage: "1",
    requiredMarketFeatures: ["OHLCV"],
    limitations: ["不计算资金费率", "不计算保证金与强平"],
  },
];

function Harness({ disabled = false, errors = {} }) {
  const [value, setValue] = useState({
    marketType: "",
    datasetId: "",
    strategyCode: "",
    executionProfileCode: "",
  });
  return (
    <QuantExecutionContextFields
      datasets={datasets}
      strategies={strategies}
      executionProfiles={executionProfiles}
      value={value}
      onChange={setValue}
      disabled={disabled}
      errors={errors}
    />
  );
}

describe("QuantExecutionContextFields", () => {
  afterEach(cleanup);

  it("shows only the supported USDM context and compatible choices", async () => {
    render(<Harness />);
    expect(screen.getByRole("option", { name: "USDT 本位永续合约" })).toBeTruthy();
    expect(screen.queryByText(/现货|做空|多空双向/)).toBeNull();
    fireEvent.change(screen.getByRole("combobox", { name: "数据集 / 交易对" }), {
      target: { value: "1" },
    });
    fireEvent.change(screen.getByRole("combobox", { name: "策略" }), {
      target: { value: "RSI" },
    });
    await waitFor(() =>
      expect(screen.getByRole("combobox", { name: "执行模型" }).value).toBe(
        "USDM_PERPETUAL_LONG_ONLY_1X_V1",
      ),
    );
    expect(screen.getByText("多头")).toBeTruthy();
    expect(screen.getByText("买入")).toBeTruthy();
    expect(screen.getByText("卖出")).toBeTruthy();
    expect(screen.getByText("1×")).toBeTruthy();
    expect(screen.getByText("基础资产数量")).toBeTruthy();
    expect(screen.getByText("不计算资金费率")).toBeTruthy();
    expect(screen.queryByRole("spinbutton", { name: /杠杆/ })).toBeNull();
  });

  it("exposes disabled state and error association", () => {
    render(
      <Harness
        disabled
        errors={{ datasetId: "请选择连续且无缺口的数据集" }}
      />,
    );
    const datasetSelect = screen.getByRole("combobox", {
      name: "数据集 / 交易对",
    });
    expect(datasetSelect.disabled).toBe(true);
    expect(datasetSelect.getAttribute("aria-describedby")).toBe(
      "quant-execution-datasetId-error",
    );
  });
});
