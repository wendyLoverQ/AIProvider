/* @vitest-environment jsdom */
import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import QuantSingleBacktestRunDetail from "./QuantSingleBacktestRunDetail";

const run = {
  runId: "run-1",
  status: "COMPLETED",
  requestedParameters: {},
  resolvedParameters: {},
  initialCapital: "1000.000000000000000001",
  finalEquity: "1100.000000000000000001",
  totalPnl: "100",
  averageExposureRatio: "0.25",
  maximumExposureRatio: "0.5",
  metrics: {
    totalReturnRatio: "0.1",
    maximumDrawdownRatio: "0.02",
  },
};

describe("QuantSingleBacktestRunDetail capital fields", () => {
  afterEach(cleanup);

  it("shows the persisted account context and capital metrics", () => {
    render(
      <QuantSingleBacktestRunDetail
        run={run}
        equity={{ sampled: false, totalPoints: 0, points: [] }}
      />,
    );
    expect(screen.getByText("1,000")).toBeTruthy();
    expect(screen.getByText("1,100")).toBeTruthy();
    expect(screen.getByText("100")).toBeTruthy();
    expect(screen.getByText("+25.00%")).toBeTruthy();
    expect(screen.getByText("+50.00%")).toBeTruthy();
  });

  it("does not turn a historical null capital into zero", () => {
    render(
      <QuantSingleBacktestRunDetail
        run={{ ...run, initialCapital: null, finalEquity: null, totalPnl: null }}
        equity={{ sampled: false, totalPoints: 0, points: [] }}
      />,
    );
    expect(screen.getAllByText("历史任务未记录").length).toBeGreaterThanOrEqual(3);
  });
});
