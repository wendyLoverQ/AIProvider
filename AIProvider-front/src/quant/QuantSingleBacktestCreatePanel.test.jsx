/* @vitest-environment jsdom */
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import QuantSingleBacktestCreatePanel from "./QuantSingleBacktestCreatePanel";
import { createBacktestRun } from "./quantBacktestsApi";

vi.mock("./quantBacktestsApi", () => ({
  createBacktestRun: vi.fn(),
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
  candleCount: 100,
  earliestOpenTime: "2025-01-01T00:00:00Z",
  latestOpenTime: "2025-01-05T03:00:00Z",
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

function setup(props = {}) {
  const onClose = vi.fn();
  const onCreated = vi.fn();
  const onSavingChange = vi.fn();
  const rendered = render(
    <QuantSingleBacktestCreatePanel
      strategies={[strategy]}
      datasets={[dataset]}
      executionProfiles={[profile]}
      onClose={onClose}
      onCreated={onCreated}
      onSavingChange={onSavingChange}
      {...props}
    />,
  );
  return { onClose, onCreated, onSavingChange, ...rendered };
}

async function chooseContext(initialCapital = "1000.000000000000000001") {
  if (initialCapital !== "")
    fireEvent.change(
      screen.getByLabelText("初始资金（计价资产，当前为 USDT）"),
      { target: { value: initialCapital } },
    );
  fireEvent.change(screen.getByRole("combobox", { name: "数据集 / 交易对" }), {
    target: { value: "1" },
  });
  fireEvent.change(screen.getByRole("combobox", { name: "策略" }), {
    target: { value: "RSI" },
  });
  await waitFor(() =>
    expect(screen.getByRole("combobox", { name: "执行模型" }).value).toBe(
      profile.code,
    ),
  );
}

describe("QuantSingleBacktestCreatePanel", () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it("submits the exact execution context once, blocks close while pending, and closes once on success", async () => {
    let resolveRequest;
    createBacktestRun.mockImplementation(
      () =>
        new Promise((resolve) => {
          resolveRequest = resolve;
        }),
    );
    const { onClose, onCreated } = setup();
    await chooseContext();
    expect(screen.getByLabelText("基础资产数量")).toBeTruthy();
    const submit = screen.getByRole("button", { name: "创建异步回测" });
    fireEvent.click(submit);
    fireEvent.click(submit);
    expect(createBacktestRun).toHaveBeenCalledTimes(1);
    expect(createBacktestRun.mock.calls[0][1]).toBeInstanceOf(AbortSignal);
    expect(createBacktestRun.mock.calls[0][0]).toEqual({
      datasetId: 1,
      startOpenTimeInclusive: "2025-01-01T00:00:00.000Z",
      endOpenTimeExclusive: "2025-01-05T04:00:00.000Z",
      strategyCode: "RSI",
      strategyVersion: "1",
      executionProfileCode: "USDM_PERPETUAL_LONG_ONLY_1X_V1",
      directionMode: "LONG_ONLY",
      orderSizingMode: "BASE_QUANTITY",
      initialCapital: "1000.000000000000000001",
      orderAmount: "1",
      feeRate: "0",
      strategyParameters: { period: 14 },
      forceCloseAtEnd: true,
    });
    expect(screen.getByRole("button", { name: "关闭新建回测" }).disabled).toBe(
      true,
    );
    fireEvent.keyDown(document, { key: "Escape" });
    expect(onClose).not.toHaveBeenCalled();
    resolveRequest({ runId: "run-1" });
    await waitFor(() =>
      expect(onCreated).toHaveBeenCalledWith({ runId: "run-1" }),
    );
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it("does not submit without a compatible profile", async () => {
    setup({ executionProfiles: [{ ...profile, marketType: "SPOT" }] });
    fireEvent.change(screen.getByRole("combobox", { name: "数据集 / 交易对" }), {
      target: { value: "1" },
    });
    fireEvent.change(screen.getByRole("combobox", { name: "策略" }), {
      target: { value: "RSI" },
    });
    fireEvent.click(screen.getByRole("button", { name: "创建异步回测" }));
    expect(createBacktestRun).not.toHaveBeenCalled();
    expect(screen.getByRole("alert").textContent).toContain("执行模型");
  });

  it.each(["", "0", "-1"])("does not submit initialCapital=%s", async (value) => {
    setup();
    await chooseContext(value);
    fireEvent.click(screen.getByRole("button", { name: "创建异步回测" }));
    expect(createBacktestRun).not.toHaveBeenCalled();
    expect(screen.getByRole("alert").textContent).toContain("初始资金");
  });

  it("does not repeatedly preselect a route strategy that is incompatible with the dataset", async () => {
    setup({
      initialStrategyCode: "RSI",
      strategies: [
        {
          ...strategy,
          requiredMarketFeatures: ["ORDER_BOOK"],
        },
      ],
    });
    fireEvent.change(screen.getByRole("combobox", { name: "数据集 / 交易对" }), {
      target: { value: "1" },
    });
    await waitFor(() =>
      expect(screen.getByRole("combobox", { name: "策略" }).value).toBe(""),
    );
    expect(screen.getByRole("alert").textContent).toContain(
      "指定策略当前不可用",
    );
    expect(createBacktestRun).not.toHaveBeenCalled();
  });

  it("keeps the panel after a business failure and suppresses AbortError", async () => {
    createBacktestRun.mockRejectedValueOnce(new Error("回测服务拒绝请求"));
    setup();
    await chooseContext();
    fireEvent.click(screen.getByRole("button", { name: "创建异步回测" }));
    expect(await screen.findByText("回测服务拒绝请求")).toBeTruthy();
    expect(screen.getByRole("dialog", { name: "新建回测" })).toBeTruthy();

    cleanup();
    const aborted = new Error("aborted");
    aborted.name = "AbortError";
    createBacktestRun.mockRejectedValueOnce(aborted);
    setup();
    await chooseContext();
    fireEvent.click(screen.getByRole("button", { name: "创建异步回测" }));
    await waitFor(() => expect(createBacktestRun).toHaveBeenCalledTimes(2));
    expect(screen.queryByText("aborted")).toBeNull();
  });

  it("aborts an active request on unmount and ignores late success", async () => {
    let resolveRequest;
    let requestSignal;
    createBacktestRun.mockImplementation((_body, signal) => {
      requestSignal = signal;
      return new Promise((resolve) => {
        resolveRequest = resolve;
      });
    });
    const { onCreated, unmount } = setup();
    await chooseContext();
    fireEvent.click(screen.getByRole("button", { name: "创建异步回测" }));
    expect(requestSignal.aborted).toBe(false);
    unmount();
    expect(requestSignal.aborted).toBe(true);
    resolveRequest({ runId: "late" });
    await Promise.resolve();
    expect(onCreated).not.toHaveBeenCalled();
  });
});
