/* @vitest-environment jsdom */
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import QuantExperimentCreatePanel from "./QuantExperimentCreatePanel";
import { createExperiment } from "./quantExperimentsApi";

vi.mock("./quantExperimentsApi", () => ({
  createExperiment: vi.fn(),
}));

const strategies = [{
  code: "RSI_MEAN_REVERSION_LONG_ONLY",
  name: "RSI 均值回归",
  version: "1.0.0",
  minimumRequiredBars: 20,
  supportedMarketTypes: ["USDM_PERPETUAL"],
  supportedExecutionProfileCodes: ["USDM_PERPETUAL_LONG_ONLY_1X_V1"],
  supportedDirectionModes: ["LONG_ONLY"],
  requiredMarketFeatures: ["OHLCV"],
  parameters: [
    { name: "rsiPeriod", defaultValue: 14, minValue: 2, maxValue: 100 },
    { name: "entryThreshold", defaultValue: 30, minValue: 1, maxValue: 50 },
  ],
}];

const datasets = [{
  id: 1,
  provider: "BINANCE",
  marketType: "USDM_PERPETUAL",
  dataType: "KLINE",
  status: "CONTIGUOUS",
  gapCount: 0,
  gapSegmentCount: 0,
  symbol: "BTC/USDT",
  interval: "H1",
  candleCount: 100,
  earliestOpenTime: "2025-01-01T00:00:00Z",
  latestOpenTime: "2025-01-05T03:00:00Z",
}];
const executionProfiles = [{
  code: "USDM_PERPETUAL_LONG_ONLY_1X_V1",
  name: "USDT 本位永续·只做多·1× V1",
  marketType: "USDM_PERPETUAL",
  directionMode: "LONG_ONLY",
  orderSizingMode: "BASE_QUANTITY",
  entryOrderSide: "BUY",
  exitOrderSide: "SELL",
  positionSide: "LONG",
  leverage: "1",
  limitations: ["不计算资金费率"],
}];

function setup(props = {}) {
  const onClose = vi.fn();
  const onCreated = vi.fn();
  const rendered = render(
    <QuantExperimentCreatePanel
      strategies={strategies}
      datasets={datasets}
      executionProfiles={executionProfiles}
      onClose={onClose}
      onCreated={onCreated}
      {...props}
    />,
  );
  return { onClose, onCreated, ...rendered };
}

function chooseRequiredValues(initialCapital = "1000.000000000000000001") {
  if (initialCapital !== "")
    fireEvent.change(screen.getByLabelText("初始资金（计价资产，当前为 USDT）"), {
      target: { value: initialCapital },
    });
  fireEvent.change(screen.getByRole("combobox", { name: "数据集 / 交易对" }), { target: { value: "1" } });
  fireEvent.change(screen.getByRole("combobox", { name: "策略" }), { target: { value: "RSI_MEAN_REVERSION_LONG_ONLY" } });
}

describe("QuantExperimentCreatePanel", () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it("loads real choices, initializes one default per parameter, and updates candidate counts", () => {
    setup();
    chooseRequiredValues();
    expect(screen.getByLabelText("rsiPeriod 候选值").value).toBe("14");
    expect(screen.getByLabelText("entryThreshold 候选值").value).toBe("30");
    expect(screen.getByText("候选组合：1")).toBeTruthy();
    expect(screen.getByText(/回测任务：2（TRAIN 1 \+ VALIDATION 1）/)).toBeTruthy();
    fireEvent.change(screen.getByLabelText("rsiPeriod 候选值"), { target: { value: "7, 14, 21" } });
    fireEvent.change(screen.getByLabelText("entryThreshold 候选值"), { target: { value: "20,25,30" } });
    expect(screen.getByText("候选组合：9")).toBeTruthy();
    expect(screen.getByText(/回测任务：18/)).toBeTruthy();
  });

  it("blocks duplicate, out-of-range, and over-limit grids", () => {
    setup();
    chooseRequiredValues();
    fireEvent.change(screen.getByLabelText("rsiPeriod 候选值"), { target: { value: "14,14" } });
    expect(screen.getByText(/候选值不能重复/)).toBeTruthy();
    fireEvent.change(screen.getByLabelText("rsiPeriod 候选值"), { target: { value: "101" } });
    expect(screen.getByText(/不能大于 100/)).toBeTruthy();
    fireEvent.change(screen.getByLabelText("rsiPeriod 候选值"), { target: { value: "2,3,4,5,6,7,8,9,10" } });
    fireEvent.change(screen.getByLabelText("entryThreshold 候选值"), { target: { value: "1,2,3,4,5,6,7,8" } });
    expect(screen.getByText("候选组合：72")).toBeTruthy();
    expect(screen.getByRole("button", { name: "创建异步实验" }).disabled).toBe(true);
  });

  it("fills an explicit adjacent 70/30 split and reports impossible splits", () => {
    setup();
    chooseRequiredValues();
    expect(screen.getByLabelText("TRAIN 开始").value).toBe("");
    fireEvent.click(screen.getByRole("button", { name: "按 70% / 30% 填充" }));
    expect(screen.getByLabelText("TRAIN 开始").value).not.toBe("");
    expect(screen.getByLabelText("TRAIN 结束（不包含）").value).toBe(screen.getByLabelText("VALIDATION 开始").value);
    cleanup();
    setup({ datasets: [{ ...datasets[0], candleCount: 30 }] });
    chooseRequiredValues();
    fireEvent.click(screen.getByRole("button", { name: "按 70% / 30% 填充" }));
    expect(screen.getByRole("alert").textContent).toContain("无法切分");
  });

  it("submits the exact frozen request, prevents duplicates, and returns the server experiment", async () => {
    let resolveRequest;
    createExperiment.mockImplementation(() => new Promise((resolve) => { resolveRequest = resolve; }));
    const { onCreated, onClose } = setup();
    chooseRequiredValues();
    fireEvent.change(screen.getByLabelText("rsiPeriod 候选值"), { target: { value: "7,14" } });
    fireEvent.click(screen.getByRole("button", { name: "按 70% / 30% 填充" }));
    fireEvent.change(screen.getByLabelText("基础资产数量"), { target: { value: "1.000000000000000001" } });
    fireEvent.change(screen.getByLabelText("手续费比例"), { target: { value: "0.001000000000000001" } });
    const submit = screen.getByRole("button", { name: "创建异步实验" });
    fireEvent.click(submit);
    fireEvent.click(submit);
    expect(createExperiment).toHaveBeenCalledTimes(1);
    const body = createExperiment.mock.calls[0][0];
    expect(createExperiment.mock.calls[0][1]).toBeInstanceOf(AbortSignal);
    expect(body).toEqual({
      datasetId: 1,
      strategyCode: "RSI_MEAN_REVERSION_LONG_ONLY",
      strategyVersion: "1.0.0",
      executionProfileCode: "USDM_PERPETUAL_LONG_ONLY_1X_V1",
      directionMode: "LONG_ONLY",
      orderSizingMode: "BASE_QUANTITY",
      initialCapital: "1000.000000000000000001",
      parameterGrid: { rsiPeriod: [7, 14], entryThreshold: [30] },
      trainingStartOpenTimeInclusive: new Date(screen.getByLabelText("TRAIN 开始").value).toISOString(),
      trainingEndOpenTimeExclusive: new Date(screen.getByLabelText("TRAIN 结束（不包含）").value).toISOString(),
      validationStartOpenTimeInclusive: new Date(screen.getByLabelText("VALIDATION 开始").value).toISOString(),
      validationEndOpenTimeExclusive: new Date(screen.getByLabelText("VALIDATION 结束（不包含）").value).toISOString(),
      orderAmount: "1.000000000000000001",
      feeRate: "0.001000000000000001",
      forceCloseAtEnd: true,
    });
    resolveRequest({ experimentId: "new-id", candidateCount: 2, totalLegs: 4 });
    await waitFor(() => expect(onCreated).toHaveBeenCalledWith({ experimentId: "new-id", candidateCount: 2, totalLegs: 4 }));
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it("keeps the form and shows a real service error", async () => {
    createExperiment.mockRejectedValue(new Error("服务端拒绝该参数网格"));
    const { onClose } = setup();
    chooseRequiredValues();
    fireEvent.click(screen.getByRole("button", { name: "按 70% / 30% 填充" }));
    fireEvent.click(screen.getByRole("button", { name: "创建异步实验" }));
    await waitFor(() => expect(screen.getByRole("alert").textContent).toContain("服务端拒绝该参数网格"));
    expect(screen.getByLabelText("rsiPeriod 候选值").value).toBe("14");
    expect(onClose).not.toHaveBeenCalled();
  });

  it.each([
    ["BACKTEST_EXPERIMENT_RANGE_INVALID：TRAIN 区间无效"],
    ["BACKTEST_EXPERIMENT_GRID_INVALID：参数组合不合法"],
  ])("preserves the backend validation message %s", async (message) => {
    createExperiment.mockRejectedValue(new Error(message));
    setup();
    chooseRequiredValues();
    fireEvent.click(screen.getByRole("button", { name: "按 70% / 30% 填充" }));
    fireEvent.click(screen.getByRole("button", { name: "创建异步实验" }));
    await waitFor(() =>
      expect(screen.getByRole("alert").textContent).toContain(message),
    );
  });

  it("shows the default-bars reference without claiming larger candidates are validated", () => {
    setup();
    chooseRequiredValues();
    fireEvent.change(screen.getByLabelText("rsiPeriod 候选值"), {
      target: { value: "100" },
    });
    expect(
      screen.getByText(/默认参数最低需要 20 根 K 线；参数网格的最终最低 K 线要求由后端逐组合校验/),
    ).toBeTruthy();
    fireEvent.click(screen.getByRole("button", { name: "按 70% / 30% 填充" }));
    expect(screen.getByText(/更大周期候选可能被后端拒绝/)).toBeTruthy();
  });

  it("aborts creation on unmount and ignores a late success", async () => {
    let resolveRequest;
    let requestSignal;
    createExperiment.mockImplementation((_body, signal) => {
      requestSignal = signal;
      return new Promise((resolve) => {
        resolveRequest = resolve;
      });
    });
    const { onCreated, unmount } = setup();
    chooseRequiredValues();
    fireEvent.click(screen.getByRole("button", { name: "按 70% / 30% 填充" }));
    fireEvent.click(screen.getByRole("button", { name: "创建异步实验" }));
    expect(requestSignal.aborted).toBe(false);
    unmount();
    expect(requestSignal.aborted).toBe(true);
    resolveRequest({ experimentId: "late", candidateCount: 1, totalLegs: 2 });
    await Promise.resolve();
    await Promise.resolve();
    expect(onCreated).not.toHaveBeenCalled();
  });

  it("does not show AbortError as a business failure", async () => {
    const aborted = new Error("aborted");
    aborted.name = "AbortError";
    createExperiment.mockRejectedValue(aborted);
    setup();
    chooseRequiredValues();
    fireEvent.click(
      screen.getByRole("button", { name: "按 70% / 30% 填充" }),
    );
    fireEvent.click(screen.getByRole("button", { name: "创建异步实验" }));
    await waitFor(() => expect(createExperiment).toHaveBeenCalled());
    expect(screen.queryByText("aborted")).toBeNull();
    expect(screen.getByRole("dialog", { name: "新建参数实验" })).toBeTruthy();
  });

  it("rejects overlapping ranges and invalid decimal precision before submit", () => {
    setup();
    chooseRequiredValues();
    fireEvent.click(
      screen.getByRole("button", { name: "按 70% / 30% 填充" }),
    );
    fireEvent.change(screen.getByLabelText("VALIDATION 开始"), {
      target: { value: screen.getByLabelText("TRAIN 开始").value },
    });
    fireEvent.click(screen.getByRole("button", { name: "创建异步实验" }));
    expect(screen.getByRole("alert").textContent).toContain("TRAIN 开始");
    expect(createExperiment).not.toHaveBeenCalled();
    fireEvent.change(screen.getByLabelText("VALIDATION 开始"), {
      target: { value: screen.getByLabelText("TRAIN 结束（不包含）").value },
    });
    fireEvent.change(screen.getByLabelText("手续费比例"), {
      target: { value: "0.0000000000000000001" },
    });
    fireEvent.click(screen.getByRole("button", { name: "创建异步实验" }));
    expect(screen.getByRole("alert").textContent).toContain("最多 18 位小数");
    expect(createExperiment).not.toHaveBeenCalled();
  });

  it("does not submit without a compatible execution profile", () => {
    setup({
      executionProfiles: [
        { ...executionProfiles[0], marketType: "SPOT" },
      ],
    });
    chooseRequiredValues();
    fireEvent.click(
      screen.getByRole("button", { name: "按 70% / 30% 填充" }),
    );
    fireEvent.click(screen.getByRole("button", { name: "创建异步实验" }));
    expect(createExperiment).not.toHaveBeenCalled();
    expect(screen.getByRole("alert").textContent).toContain("执行模型");
  });

  it.each(["", "0", "-1"])("does not submit initialCapital=%s", (value) => {
    setup();
    chooseRequiredValues(value);
    fireEvent.click(screen.getByRole("button", { name: "按 70% / 30% 填充" }));
    fireEvent.click(screen.getByRole("button", { name: "创建异步实验" }));
    expect(createExperiment).not.toHaveBeenCalled();
    expect(screen.getByRole("alert").textContent).toContain("初始资金");
  });
});
