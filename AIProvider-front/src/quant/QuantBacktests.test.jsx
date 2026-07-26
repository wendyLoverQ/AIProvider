/* @vitest-environment jsdom */
import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import QuantBacktests from "./QuantBacktests";

const strategy = {
  code: "rsi/mean",
  name: "RSI 反转",
  version: "1.0.0",
  minimumRequiredBars: 1,
  parameters: [
    { name: "period", defaultValue: 14, minValue: 2, maxValue: 100 },
  ],
};
const dataset = {
  id: 1,
  status: "CONTIGUOUS",
  gapCount: 0,
  gapSegmentCount: 0,
  earliestOpenTime: "2025-01-01T00:00:00Z",
  latestOpenTime: "2025-01-05T03:00:00Z",
  lastValidatedAt: "2025-01-06T00:00:00Z",
  candleCount: 100,
  symbol: "BTC/USDT",
  interval: "H1",
};
const result = (data) => ({
  ok: true,
  status: 200,
  json: async () => ({ code: 200, data }),
});
const installFetch = () =>
  vi.spyOn(globalThis, "fetch").mockImplementation(async (url) => {
    if (url.includes("/strategies")) return result([strategy]);
    if (url.includes("/datasets")) return result([]);
    if (url.includes("/experiments"))
      return result({ records: [], total: 0, page: 1, pageSize: 20 });
    if (url.includes("/runs"))
      return result({ records: [], total: 0, page: 1, pageSize: 20 });
    throw new Error(`unexpected request ${url}`);
  });

describe("Quant backtest strategy handoff", () => {
  afterEach(() => {
    cleanup();
    vi.restoreAllMocks();
    window.history.replaceState({}, "", "/quant/backtests");
  });

  it("opens after data loading, selects the requested strategy, fills defaults, and consumes the query", async () => {
    installFetch();
    window.history.replaceState(
      {},
      "",
      "/quant/backtests?openCreate=1&strategyCode=rsi%2Fmean",
    );
    render(<QuantBacktests />);
    await waitFor(() =>
      expect(screen.getByRole("dialog", { name: "新建回测" })).toBeTruthy(),
    );
    expect(screen.getByRole("combobox", { name: "策略" }).value).toBe(
      "rsi/mean",
    );
    expect(screen.getByDisplayValue("14")).toBeTruthy();
    expect(window.location.pathname).toBe("/quant/backtests");
    expect(window.location.search).toBe("");
  });

  it("does not select another strategy when the requested code is unavailable", async () => {
    installFetch();
    window.history.replaceState(
      {},
      "",
      "/quant/backtests?openCreate=1&strategyCode=missing",
    );
    render(<QuantBacktests />);
    await waitFor(() =>
      expect(screen.getByRole("dialog", { name: "新建回测" })).toBeTruthy(),
    );
    expect(screen.getByRole("combobox", { name: "策略" }).value).toBe("");
    expect(screen.getByRole("alert").textContent).toContain(
      "指定策略当前不可用",
    );
  });

  it("defaults to the single view and switches tabs through query state without changing pathname", async () => {
    installFetch();
    render(<QuantBacktests />);
    await waitFor(() =>
      expect(screen.getByText("还没有创建回测")).toBeTruthy(),
    );
    expect(
      screen.getByRole("button", { name: "单次回测" }).getAttribute(
        "aria-current",
      ),
    ).toBe("page");
    fireEvent.click(screen.getByRole("button", { name: "参数实验" }));
    await waitFor(() =>
      expect(screen.getByText("当前没有参数实验")).toBeTruthy(),
    );
    expect(window.location.pathname).toBe("/quant/backtests");
    expect(new URLSearchParams(window.location.search).get("mode")).toBe(
      "experiment",
    );
    window.history.back();
    await waitFor(() =>
      expect(
        screen.getByRole("button", { name: "单次回测" }).getAttribute(
          "aria-current",
        ),
      ).toBe("page"),
    );
  });

  it("loads a real runId deep link instead of falling back to another task", async () => {
    const run = {
      runId: "run/deep",
      status: "FAILED",
      errorCode: "REAL_FAILURE",
      errorMessage: "真实任务失败",
    };
    vi.spyOn(globalThis, "fetch").mockImplementation(async (url) => {
      if (url.includes("/strategies")) return result([strategy]);
      if (url.includes("/datasets")) return result([]);
      if (url.endsWith("/runs/run%2Fdeep")) return result(run);
      if (url.includes("/runs"))
        return result({
          records: [
            { runId: "other-run", status: "COMPLETED" },
          ],
          total: 1,
          page: 1,
          pageSize: 20,
        });
      throw new Error(`unexpected request ${url}`);
    });
    window.history.replaceState(
      {},
      "",
      "/quant/backtests?mode=single&runId=run%2Fdeep",
    );
    render(<QuantBacktests />);
    await waitFor(() =>
      expect(screen.getByText("真实任务失败")).toBeTruthy(),
    );
    expect(screen.getAllByText("run/deep").length).toBeGreaterThanOrEqual(2);
    expect(screen.getByText("当前选中任务").nextSibling.textContent).toBe(
      "run/deep",
    );
    fireEvent.click(screen.getByRole("button", { name: "清除选择" }));
    expect(new URLSearchParams(window.location.search).has("runId")).toBe(false);
  });

  it("shows a deep-link detail failure without selecting the first list run", async () => {
    vi.spyOn(globalThis, "fetch").mockImplementation(async (url) => {
      if (url.includes("/strategies")) return result([strategy]);
      if (url.includes("/datasets")) return result([]);
      if (url.endsWith("/runs/missing"))
        return {
          ok: false,
          status: 404,
          json: async () => ({ code: 404, message: "指定回测不存在" }),
        };
      if (url.includes("/runs"))
        return result({
          records: [{ runId: "first-run", status: "COMPLETED" }],
          total: 1,
          page: 1,
          pageSize: 20,
        });
      throw new Error(`unexpected request ${url}`);
    });
    window.history.replaceState(
      {},
      "",
      "/quant/backtests?mode=single&runId=missing",
    );
    render(<QuantBacktests />);
    expect(await screen.findByText("指定回测不存在")).toBeTruthy();
    expect(screen.getByText("当前选中任务").nextSibling.textContent).toBe(
      "missing",
    );
    expect(document.querySelector(".backtest-run-row").className).not.toContain(
      "selected",
    );
  });

  it("re-requests runId when browser back restores the previous deep link", async () => {
    const fetchMock = vi.spyOn(globalThis, "fetch").mockImplementation(async (url) => {
      if (url.includes("/strategies")) return result([strategy]);
      if (url.includes("/datasets")) return result([]);
      if (url.endsWith("/runs/run-1"))
        return result({ runId: "run-1", status: "FAILED", errorMessage: "任务一" });
      if (url.endsWith("/runs/run-2"))
        return result({ runId: "run-2", status: "FAILED", errorMessage: "任务二" });
      if (url.includes("/runs"))
        return result({ records: [], total: 0, page: 1, pageSize: 20 });
      throw new Error(`unexpected request ${url}`);
    });
    window.history.replaceState(
      {},
      "",
      "/quant/backtests?mode=single&runId=run-1",
    );
    render(<QuantBacktests />);
    expect(await screen.findByText("任务一")).toBeTruthy();
    window.history.pushState(
      {},
      "",
      "/quant/backtests?mode=single&runId=run-2",
    );
    window.dispatchEvent(new PopStateEvent("popstate"));
    expect(await screen.findByText("任务二")).toBeTruthy();
    window.history.back();
    expect(await screen.findByText("任务一")).toBeTruthy();
    expect(
      fetchMock.mock.calls.filter(([url]) => url.endsWith("/runs/run-1")),
    ).toHaveLength(2);
  });

  it("restores focus to the exact create button for close, Escape, backdrop, and success", async () => {
    const run = { runId: "created-run", status: "QUEUED" };
    vi.spyOn(globalThis, "fetch").mockImplementation(async (url, options) => {
      if (url.includes("/strategies")) return result([strategy]);
      if (url.includes("/datasets")) return result([dataset]);
      if (url.endsWith("/runs") && options?.method === "POST") return result(run);
      if (url.endsWith("/runs/created-run")) return result(run);
      if (url.includes("/runs"))
        return result({ records: [], total: 0, page: 1, pageSize: 20 });
      throw new Error(`unexpected request ${url}`);
    });
    const unrelated = document.createElement("button");
    unrelated.className = "quant-primary-action";
    unrelated.textContent = "其他主按钮";
    document.body.appendChild(unrelated);
    render(<QuantBacktests />);
    const createButton = await screen.findByRole("button", { name: "新建回测" });

    fireEvent.click(createButton);
    fireEvent.click(screen.getByRole("button", { name: "关闭新建回测" }));
    await waitFor(() => expect(document.activeElement).toBe(createButton));

    fireEvent.click(createButton);
    fireEvent.keyDown(document, { key: "Escape" });
    await waitFor(() => expect(document.activeElement).toBe(createButton));

    fireEvent.click(createButton);
    fireEvent.mouseDown(document.querySelector(".backtest-modal-backdrop"));
    await waitFor(() => expect(document.activeElement).toBe(createButton));

    fireEvent.click(createButton);
    fireEvent.change(screen.getByRole("combobox", { name: "连续历史数据集" }), {
      target: { value: "1" },
    });
    fireEvent.change(screen.getByRole("combobox", { name: "策略" }), {
      target: { value: "rsi/mean" },
    });
    fireEvent.click(screen.getByRole("button", { name: "创建异步回测" }));
    await waitFor(() => expect(document.activeElement).toBe(createButton));
    expect(unrelated).not.toBe(document.activeElement);
    unrelated.remove();
  });
});
