/* @vitest-environment jsdom */
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import QuantBacktests from "./QuantBacktests";

const strategy = { code: "rsi/mean", name: "RSI 反转", version: "1.0.0", parameters: [{ name: "period", defaultValue: 14, minValue: 2, maxValue: 100 }] };
const result = (data) => ({ ok: true, status: 200, json: async () => ({ code: 200, data }) });
const installFetch = () => vi.spyOn(globalThis, "fetch").mockImplementation(async (url) => {
  if (url.includes("/strategies")) return result([strategy]);
  if (url.includes("/datasets")) return result([]);
  if (url.includes("/runs")) return result({ records: [], total: 0, page: 1, pageSize: 20 });
  throw new Error(`unexpected request ${url}`);
});

describe("Quant backtest strategy handoff", () => {
  afterEach(() => { cleanup(); vi.restoreAllMocks(); window.history.replaceState({}, "", "/quant/backtests"); });

  it("opens after data loading, selects the requested strategy, fills defaults, and consumes the query", async () => {
    installFetch();
    window.history.replaceState({}, "", "/quant/backtests?openCreate=1&strategyCode=rsi%2Fmean");
    render(<QuantBacktests />);
    await waitFor(() => expect(screen.getByRole("dialog", { name: "新建回测" })).toBeTruthy());
    expect(screen.getByRole("combobox", { name: "策略" }).value).toBe("rsi/mean");
    expect(screen.getByDisplayValue("14")).toBeTruthy();
    expect(window.location.pathname).toBe("/quant/backtests");
    expect(window.location.search).toBe("");
  });

  it("does not select another strategy when the requested code is unavailable", async () => {
    installFetch();
    window.history.replaceState({}, "", "/quant/backtests?openCreate=1&strategyCode=missing");
    render(<QuantBacktests />);
    await waitFor(() => expect(screen.getByRole("dialog", { name: "新建回测" })).toBeTruthy());
    expect(screen.getByRole("combobox", { name: "策略" }).value).toBe("");
    expect(screen.getByRole("alert").textContent).toContain("指定策略当前不可用");
  });
});
