// @vitest-environment jsdom
import React from "react";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import PromptOptionManager from "./PromptOptionManager";

const item = { id: "black_pantyhose", category: "Clothing", name: "黑丝袜 / 黑色连裤袜", prompt: "black pantyhose", type: "positive", reverseId: null, sortOrder: 11, enabled: true, allowMultiple: true };
const response = (data) => new Response(JSON.stringify({ code: 200, data }), { status: 200, headers: { "Content-Type": "application/json" } });

describe("PromptOptionManager", () => {
  let writes;
  beforeEach(() => { writes = []; vi.stubGlobal("fetch", vi.fn(async (input, options = {}) => { if (options.method) writes.push([String(input), options.method, options.body]); return response(options.method === "DELETE" ? null : { items: [item], total: 3978, page: 1, pageSize: 100, pages: 40 }); })); vi.stubGlobal("confirm", vi.fn(() => true)); });
  afterEach(() => { cleanup(); vi.unstubAllGlobals(); });

  it("searches compact rows and edits an existing option", async () => {
    render(<PromptOptionManager onBack={vi.fn()} />); await screen.findByText("黑丝袜 / 黑色连裤袜");
    expect(fetch).toHaveBeenCalledWith(expect.stringContaining("pageSize=100"), undefined);
    fireEvent.change(screen.getByLabelText("搜索词条"), { target: { value: "pantyhose" } });
    await waitFor(() => expect(fetch).toHaveBeenCalledWith(expect.stringContaining("query=pantyhose"), undefined));
    fireEvent.click(screen.getByRole("button", { name: /黑丝袜/ }));
    fireEvent.change(screen.getByLabelText("中文名称"), { target: { value: "黑色丝袜" } }); fireEvent.click(screen.getByRole("button", { name: "保存" }));
    await waitFor(() => expect(writes.some(([url, method]) => url.endsWith("/black_pantyhose") && method === "PUT")).toBe(true));
  });

  it("creates a new option", async () => {
    render(<PromptOptionManager onBack={vi.fn()} />); await screen.findByText("黑丝袜 / 黑色连裤袜"); fireEvent.click(screen.getByRole("button", { name: "新建词条" }));
    fireEvent.change(screen.getByLabelText("词条 ID"), { target: { value: "One-Boy_One-Girl" } });
    expect(screen.getByLabelText("词条 ID").value).toBe("oneboy_onegirl");
    fireEvent.change(screen.getByLabelText("中文名称"), { target: { value: "一男一女" } }); fireEvent.change(screen.getByLabelText("词条 Prompt"), { target: { value: "1boy, 1girl" } }); fireEvent.click(screen.getByRole("button", { name: "保存" }));
    await waitFor(() => expect(writes.some(([, method]) => method === "POST")).toBe(true));
  });
});
