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
  parameters: [
    { name: "rsiPeriod", defaultValue: 14, minValue: 2, maxValue: 100 },
    { name: "entryThreshold", defaultValue: 30, minValue: 1, maxValue: 50 },
  ],
}];

const datasets = [{
  id: 1,
  symbol: "BTC/USDT",
  interval: "H1",
  candleCount: 100,
  earliestOpenTime: "2025-01-01T00:00:00Z",
  latestOpenTime: "2025-01-05T03:00:00Z",
}];

function setup(props = {}) {
  const onClose = vi.fn();
  const onCreated = vi.fn();
  render(
    <QuantExperimentCreatePanel
      strategies={strategies}
      datasets={datasets}
      onClose={onClose}
      onCreated={onCreated}
      {...props}
    />,
  );
  return { onClose, onCreated };
}

function chooseRequiredValues() {
  fireEvent.change(screen.getByRole("combobox", { name: "连续历史数据集" }), { target: { value: "1" } });
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
    fireEvent.change(screen.getByLabelText("下单数量"), { target: { value: "1.000000000000000001" } });
    fireEvent.change(screen.getByLabelText("手续费比例"), { target: { value: "0.001000000000000001" } });
    const submit = screen.getByRole("button", { name: "创建异步实验" });
    fireEvent.click(submit);
    fireEvent.click(submit);
    expect(createExperiment).toHaveBeenCalledTimes(1);
    const body = createExperiment.mock.calls[0][0];
    expect(body).toEqual({
      datasetId: 1,
      strategyCode: "RSI_MEAN_REVERSION_LONG_ONLY",
      strategyVersion: "1.0.0",
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
});
