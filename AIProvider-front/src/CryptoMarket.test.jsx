// @vitest-environment jsdom
import React from "react";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import CryptoMarket from "./CryptoMarket";

const envelope = (data) => Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve({ code: 200, data }) });

describe("CryptoMarket", () => {
  beforeEach(() => {
    vi.stubGlobal("ResizeObserver", class { observe() {} unobserve() {} disconnect() {} });
    vi.stubGlobal("fetch", vi.fn((url) => {
      if (url.endsWith("/health")) return envelope({ provider: "CCXT", version: "4.5.66", available: true, exchangeCount: 2 });
      if (url.endsWith("/exchanges")) return envelope([{ id: "binance", name: "Binance" }, { id: "okx", name: "OKX" }]);
      if (url.includes("/symbols")) return envelope([{ exchangeId: "okx", symbol: "BTC/USDT", baseAsset: "BTC", quoteAsset: "USDT" }, { exchangeId: "okx", symbol: "ETH/USDT", baseAsset: "ETH", quoteAsset: "USDT" }]);
      if (url.includes("/ticker")) return envelope({ symbol: "BTC/USDT", last: 64000, percentage: 2.5, high: 65000, low: 62000, baseVolume: 1200 });
      if (url.includes("/klines")) return envelope([{ timestamp: 1, open: 10, high: 12, low: 9, close: 11, volume: 5 }]);
      if (url.includes("/depth")) return envelope({ bids: [[63999, 1]], asks: [[64001, 2]] });
      throw new Error(`Unexpected URL: ${url}`);
    }));
  });

  afterEach(() => { cleanup(); vi.unstubAllGlobals(); });

  it("loads CCXT exchanges, markets, ticker, chart, and order book", async () => {
    render(<CryptoMarket />);
    await waitFor(() => expect(screen.getAllByText("64,000").length).toBeGreaterThan(0));
    expect(screen.getByRole("combobox", { name: "交易所" }).value).toBe("okx");
    expect(screen.getByRole("button", { name: "BTC/USDT" })).toBeTruthy();
    expect(screen.getByText("+2.50%")).toBeTruthy();
    expect(screen.getByText("v4.5.66")).toBeTruthy();
  });

  it("reuses the unified search control and requests a selected timeframe", async () => {
    render(<CryptoMarket />);
    await waitFor(() => expect(screen.getByRole("button", { name: "BTC/USDT" })).toBeTruthy());
    const search = screen.getByRole("textbox", { name: "搜索交易对" });
    fireEvent.change(search, { target: { value: "ETH" } });
    expect(search.value).toBe("ETH");
    expect(screen.queryByRole("button", { name: "BTC/USDT" })).toBeNull();
    fireEvent.click(screen.getByRole("button", { name: "1h" }));
    await waitFor(() => expect(fetch.mock.calls.some(([url]) => url.includes("interval=1h"))).toBe(true));
  });
});
