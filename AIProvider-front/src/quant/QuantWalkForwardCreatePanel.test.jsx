/* @vitest-environment jsdom */
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import QuantWalkForwardCreatePanel from "./QuantWalkForwardCreatePanel";
import { createWalkForwardStudy } from "./quantWalkForwardApi";

vi.mock("./quantWalkForwardApi", () => ({
  createWalkForwardStudy: vi.fn(),
}));

const strategy = {
  code: "RSI",
  name: "RSI",
  version: "1",
  minimumRequiredBars: 2,
  parameters: [{ name: "period", defaultValue: 14, minValue: 2, maxValue: 100 }],
  supportedMarketTypes: ["USDM_PERPETUAL"],
  supportedExecutionProfileCodes: ["USDM_PERPETUAL_LONG_ONLY_1X_V1"],
  supportedDirectionModes: ["LONG_ONLY"],
  requiredMarketFeatures: ["OHLCV"],
};
const dataset = {
  id: 1,
  provider: "BINANCE",
  symbol: "BTCUSDT",
  interval: "H1",
  marketType: "USDM_PERPETUAL",
  dataType: "KLINE",
  status: "CONTIGUOUS",
  gapCount: 0,
  gapSegmentCount: 0,
  candleCount: 240,
  earliestOpenTime: "2025-01-01T00:00:00Z",
  latestOpenTime: "2025-01-10T23:00:00Z",
};
const profile = {
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
  limitations: ["不计算资金费率"],
};

describe("QuantWalkForwardCreatePanel", () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it("shares one explicit execution context across the whole study request", async () => {
    createWalkForwardStudy.mockResolvedValue({
      studyId: "wf-1",
      foldCount: 2,
      candidateCountPerFold: 1,
      totalChildRuns: 4,
    });
    const onCreated = vi.fn();
    render(
      <QuantWalkForwardCreatePanel
        strategies={[strategy]}
        datasets={[dataset]}
        executionProfiles={[profile]}
        onClose={vi.fn()}
        onCreated={onCreated}
      />,
    );
    fireEvent.change(screen.getByRole("combobox", { name: "数据集 / 交易对" }), {
      target: { value: "1" },
    });
    fireEvent.change(screen.getByRole("combobox", { name: "策略" }), {
      target: { value: "RSI" },
    });
    fireEvent.click(
      screen.getByRole("button", { name: "按 70% / 30% 填充窗口" }),
    );
    const submit = screen.getByRole("button", { name: "创建滚动验证" });
    fireEvent.click(submit);
    fireEvent.click(submit);
    await waitFor(() => expect(createWalkForwardStudy).toHaveBeenCalledTimes(1));
    expect(createWalkForwardStudy.mock.calls[0][0]).toMatchObject({
      datasetId: 1,
      strategyCode: "RSI",
      executionProfileCode: "USDM_PERPETUAL_LONG_ONLY_1X_V1",
      directionMode: "LONG_ONLY",
      orderSizingMode: "BASE_QUANTITY",
      orderAmount: "1",
    });
    expect(createWalkForwardStudy.mock.calls[0][1]).toBeInstanceOf(AbortSignal);
    await waitFor(() => expect(onCreated).toHaveBeenCalledTimes(1));
  });

  it("keeps the panel open and does not report AbortError as a business error", async () => {
    const aborted = new Error("aborted");
    aborted.name = "AbortError";
    createWalkForwardStudy.mockRejectedValue(aborted);
    render(
      <QuantWalkForwardCreatePanel
        strategies={[strategy]}
        datasets={[dataset]}
        executionProfiles={[profile]}
        onClose={vi.fn()}
        onCreated={vi.fn()}
      />,
    );
    fireEvent.change(screen.getByRole("combobox", { name: "数据集 / 交易对" }), {
      target: { value: "1" },
    });
    fireEvent.change(screen.getByRole("combobox", { name: "策略" }), {
      target: { value: "RSI" },
    });
    fireEvent.click(
      screen.getByRole("button", { name: "按 70% / 30% 填充窗口" }),
    );
    fireEvent.click(screen.getByRole("button", { name: "创建滚动验证" }));
    await waitFor(() => expect(createWalkForwardStudy).toHaveBeenCalled());
    expect(screen.queryByText("aborted")).toBeNull();
    expect(screen.getByRole("dialog", { name: "新建滚动验证" })).toBeTruthy();
  });

  it("does not submit without a compatible profile", async () => {
    render(
      <QuantWalkForwardCreatePanel
        strategies={[strategy]}
        datasets={[dataset]}
        executionProfiles={[{ ...profile, marketType: "SPOT" }]}
        onClose={vi.fn()}
        onCreated={vi.fn()}
      />,
    );
    fireEvent.change(screen.getByRole("combobox", { name: "数据集 / 交易对" }), {
      target: { value: "1" },
    });
    fireEvent.change(screen.getByRole("combobox", { name: "策略" }), {
      target: { value: "RSI" },
    });
    fireEvent.click(
      screen.getByRole("button", { name: "按 70% / 30% 填充窗口" }),
    );
    fireEvent.click(screen.getByRole("button", { name: "创建滚动验证" }));
    expect(createWalkForwardStudy).not.toHaveBeenCalled();
    expect(screen.getByRole("alert").textContent).toContain("执行模型");
  });

  it("blocks close while pending and keeps the panel after a business failure", async () => {
    let rejectRequest;
    createWalkForwardStudy.mockImplementation(
      () =>
        new Promise((_resolve, reject) => {
          rejectRequest = reject;
        }),
    );
    const onClose = vi.fn();
    render(
      <QuantWalkForwardCreatePanel
        strategies={[strategy]}
        datasets={[dataset]}
        executionProfiles={[profile]}
        onClose={onClose}
        onCreated={vi.fn()}
      />,
    );
    fireEvent.change(screen.getByRole("combobox", { name: "数据集 / 交易对" }), {
      target: { value: "1" },
    });
    fireEvent.change(screen.getByRole("combobox", { name: "策略" }), {
      target: { value: "RSI" },
    });
    fireEvent.click(
      screen.getByRole("button", { name: "按 70% / 30% 填充窗口" }),
    );
    fireEvent.click(screen.getByRole("button", { name: "创建滚动验证" }));
    expect(
      screen.getByRole("button", { name: "关闭新建滚动验证" }).disabled,
    ).toBe(true);
    fireEvent.keyDown(document, { key: "Escape" });
    expect(onClose).not.toHaveBeenCalled();
    rejectRequest(new Error("Study 创建失败"));
    expect(await screen.findByText("Study 创建失败")).toBeTruthy();
    expect(screen.getByRole("dialog", { name: "新建滚动验证" })).toBeTruthy();
  });
});
