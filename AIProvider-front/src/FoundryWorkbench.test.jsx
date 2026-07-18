// @vitest-environment jsdom
import React from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import FoundryWorkbench from "./FoundryWorkbench";

const ok = (data) => Promise.resolve({
  ok: true,
  status: 200,
  json: () => Promise.resolve({ code: 200, data }),
});

const status = {
  rpcConfigured: true,
  rpcHost: "eth.merkle.io",
  readOnly: true,
  checkedAt: "2026-07-17T06:00:00+08:00",
  tools: [
    { name: "Forge", available: true, version: "forge 1.3.0" },
    { name: "Cast", available: true, version: "cast 1.3.0" },
    { name: "Anvil", available: true, version: "anvil 1.3.0" },
    { name: "Chisel", available: true, version: "chisel 1.3.0" },
  ],
};

afterEach(() => { cleanup(); vi.restoreAllMocks(); });

describe("FoundryWorkbench", () => {
  it("loads real tool status and executes latest block query", async () => {
    const fetchMock = vi.spyOn(globalThis, "fetch")
      .mockImplementationOnce(() => ok(status))
      .mockImplementationOnce(() => ok({ operation: "block-number", result: "22999123", executedAt: "2026-07-17T06:10:00+08:00" }));

    render(<FoundryWorkbench />);
    expect(await screen.findByText("cast 1.3.0")).toBeTruthy();
    expect(screen.getByText("eth.merkle.io")).toBeTruthy();
    fireEvent.click(screen.getByRole("button", { name: /读取最新区块/ }));

    expect(await screen.findByText("22999123")).toBeTruthy();
    expect(fetchMock).toHaveBeenNthCalledWith(2, "/api/foundry/block-number", undefined);
  });

  it("sends contract call as structured JSON instead of a command string", async () => {
    const fetchMock = vi.spyOn(globalThis, "fetch")
      .mockImplementationOnce(() => ok(status))
      .mockImplementationOnce(() => ok({ operation: "call", result: "42", executedAt: "2026-07-17T06:10:00+08:00" }));
    render(<FoundryWorkbench />);
    await screen.findByText("cast 1.3.0");

    fireEvent.change(screen.getByPlaceholderText("0x 开头的 40 位地址"), { target: { value: "0x0000000000000000000000000000000000000002" } });
    fireEvent.change(screen.getByDisplayValue("balanceOf(address)(uint256)"), { target: { value: "totalSupply()(uint256)" } });
    fireEvent.click(screen.getByRole("button", { name: /执行只读 Cast Call/ }));
    await screen.findByText("42");

    const options = fetchMock.mock.calls[1][1];
    expect(fetchMock.mock.calls[1][0]).toBe("/api/foundry/call");
    expect(options.method).toBe("POST");
    expect(JSON.parse(options.body)).toEqual({
      address: "0x0000000000000000000000000000000000000002",
      signature: "totalSupply()(uint256)",
      arguments: [],
    });
  });

  it("keeps operations disabled when Cast or RPC is unavailable", async () => {
    vi.spyOn(globalThis, "fetch").mockImplementation(() => ok({ ...status, rpcConfigured: false }));
    render(<FoundryWorkbench />);
    await screen.findByText("cast 1.3.0");
    await waitFor(() => expect(screen.getByRole("button", { name: /读取最新区块/ }).disabled).toBe(true));
    expect(screen.getByRole("button", { name: /查询 ETH 余额/ }).disabled).toBe(true);
  });
});
