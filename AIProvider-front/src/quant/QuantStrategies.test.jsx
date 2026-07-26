/* @vitest-environment jsdom */
import { cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import QuantStrategies from "./QuantStrategies";
import { fetchQuantStrategies } from "./quantStrategiesApi";

vi.mock("./quantStrategiesApi", () => ({ fetchQuantStrategies: vi.fn() }));

const strategies = [
  { code: "ema", name: "EMA 趋势", version: "1.0.0", description: "均线趋势研究", minimumRequiredBars: 30, parameters: [{ name: "period", defaultValue: 20, minValue: 2, maxValue: 200 }] },
  { code: "rsi", name: "RSI 反转", version: "2.0.0", description: "相对强弱研究", minimumRequiredBars: 40, parameters: [] },
];

describe("Quant strategy research workspace", () => {
  afterEach(() => { cleanup(); vi.restoreAllMocks(); window.history.replaceState({}, "", "/quant/strategies"); });

  it("loads, selects the first strategy, searches, switches details, and navigates to backtests", async () => {
    fetchQuantStrategies.mockResolvedValue(strategies);
    const popState = vi.fn();
    window.addEventListener("popstate", popState);
    render(<QuantStrategies />);
    expect(screen.getByRole("status").textContent).toContain("正在读取策略目录");
    await waitFor(() => expect(screen.getAllByText("EMA 趋势").length).toBeGreaterThan(0));
    expect(screen.getByText("已注册策略数").parentElement.textContent).toContain("2");
    expect(screen.getByText("均线趋势研究")).toBeTruthy();
    const directory = within(screen.getByRole("region", { name: "策略目录" }));
    fireEvent.change(screen.getByRole("textbox", { name: "搜索策略" }), { target: { value: "相对强弱" } });
    expect(directory.getByText("RSI 反转")).toBeTruthy();
    expect(directory.queryByText("EMA 趋势")).toBeNull();
    fireEvent.click(directory.getByRole("button", { name: /RSI 反转/ }));
    expect(screen.getByText("该策略没有可配置参数")).toBeTruthy();
    fireEvent.click(screen.getByRole("button", { name: "用此策略创建回测" }));
    expect(window.location.pathname).toBe("/quant/backtests");
    expect(new URLSearchParams(window.location.search).get("openCreate")).toBe("1");
    expect(new URLSearchParams(window.location.search).get("strategyCode")).toBe("rsi");
    expect(popState).toHaveBeenCalled();
    window.removeEventListener("popstate", popState);
  });

  it("shows API errors and retries into a successful state", async () => {
    fetchQuantStrategies.mockRejectedValueOnce(new Error("网络不可用")).mockResolvedValueOnce(strategies);
    render(<QuantStrategies />);
    await waitFor(() => expect(screen.getByRole("alert").textContent).toContain("网络不可用"));
    fireEvent.click(screen.getByRole("button", { name: "重试" }));
    await waitFor(() => expect(screen.getAllByText("EMA 趋势").length).toBeGreaterThan(0));
    expect(screen.queryByText("网络不可用")).toBeNull();
  });

  it("renders an honest empty state without local fallback strategies", async () => {
    fetchQuantStrategies.mockResolvedValue([]);
    render(<QuantStrategies />);
    await waitFor(() => expect(screen.getByText("当前没有已注册策略")).toBeTruthy());
    expect(screen.queryByText("EMA")).toBeNull();
  });
});
