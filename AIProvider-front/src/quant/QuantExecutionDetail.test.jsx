/* @vitest-environment jsdom */
import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import QuantSingleBacktestRunDetail from "./QuantSingleBacktestRunDetail";
import QuantSingleBacktestTrades from "./QuantSingleBacktestTrades";

vi.mock("recharts", () => ({
  Area: () => null,
  AreaChart: ({ children }) => <div>{children}</div>,
  CartesianGrid: () => null,
  ResponsiveContainer: ({ children }) => <div>{children}</div>,
  Tooltip: () => null,
  XAxis: () => null,
  YAxis: () => null,
}));

const profile = {
  code: "USDM_PERPETUAL_LONG_ONLY_1X_V1",
  name: "USDT 本位永续·只做多·1× V1",
  fillModel: "TA4J_TRADE_ON_NEXT_OPEN",
  limitations: [
    "不计算资金费率",
    "不计算保证金占用",
    "不计算强平",
  ],
};
const run = {
  runId: "run-1",
  datasetId: 1,
  symbol: "BTCUSDT",
  intervalCode: "H1",
  marketType: "USDM_PERPETUAL",
  strategyCode: "RSI",
  strategyVersion: "1",
  executionProfileCode: profile.code,
  directionMode: "LONG_ONLY",
  orderSizingMode: "BASE_QUANTITY",
  requestedParameters: { period: 14 },
  resolvedParameters: { period: 14 },
  startOpenTimeInclusive: "2025-01-01T00:00:00Z",
  endOpenTimeExclusive: "2025-01-05T00:00:00Z",
  orderAmount: "0.01",
  feeRate: "0.001",
  status: "COMPLETED",
  barCount: 96,
  tradeCount: 1,
  metrics: {},
};

describe("Quant execution detail rendering", () => {
  afterEach(cleanup);

  it("shows the frozen execution semantics and limitations on a run", () => {
    render(
      <QuantSingleBacktestRunDetail
        run={run}
        executionProfile={profile}
        equity={{ sampled: false, totalPoints: 0, points: [] }}
      />,
    );
    expect(screen.getByText("USDT 本位永续合约")).toBeTruthy();
    expect(screen.getByText(profile.name)).toBeTruthy();
    expect(screen.getByText("只做多")).toBeTruthy();
    expect(screen.getByText("基础资产数量")).toBeTruthy();
    expect(screen.getByText("0.01 / 0.001")).toBeTruthy();
    expect(screen.getByText("TA4J_TRADE_ON_NEXT_OPEN")).toBeTruthy();
    profile.limitations.forEach((limitation) =>
      expect(screen.getByText(limitation)).toBeTruthy(),
    );
  });

  it("shows position and order sides without calling a forced end exit liquidation", () => {
    render(
      <QuantSingleBacktestTrades
        run={run}
        page={1}
        state="ready"
        data={{
          total: 1,
          records: [
            {
              tradeNo: 1,
              positionSide: "LONG",
              entryOrderSide: "BUY",
              exitOrderSide: "SELL",
              entryTime: "2025-01-01T00:00:00Z",
              entryPrice: "100",
              exitTime: "2025-01-02T00:00:00Z",
              exitPrice: "101",
              amount: "0.01",
              netProfit: "1",
              returnRatio: "0.01",
              barsHeld: 24,
              forcedExit: true,
              exitReason: "END_OF_SERIES",
            },
          ],
        }}
        onPage={vi.fn()}
        retry={vi.fn()}
      />,
    );
    expect(screen.getByText("多头")).toBeTruthy();
    expect(screen.getByText("买入")).toBeTruthy();
    expect(screen.getByText("卖出")).toBeTruthy();
    expect(screen.getByText("期末强制平仓")).toBeTruthy();
    expect(screen.queryByText("期末强平")).toBeNull();
  });
});
