// @vitest-environment jsdom
import React from "react";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import PromptManager from "./PromptManager";
import { emptySelectedOptions } from "./promptComposer";

const catalog = {
  generalNegativePrompt: "low quality, bad hands",
  options: [
    { id: "solo", category: "Character", name: "单人", positivePrompt: "solo", negativePrompt: "crowd", allowMultiple: true },
    { id: "girl", category: "Character", name: "女孩", positivePrompt: "1girl", negativePrompt: "male", allowMultiple: true },
    { id: "black_pantyhose", category: "Clothing", name: "黑丝袜 / 黑色连裤袜", positivePrompt: "black pantyhose, sheer black tights", negativePrompt: "bare legs", allowMultiple: true },
    { id: "masterpiece", category: "Quality", name: "杰作", positivePrompt: "masterpiece", negativePrompt: "low quality", allowMultiple: true },
  ],
};
const response = (data) => new Response(JSON.stringify({ code: 200, data }), { status: 200, headers: { "Content-Type": "application/json" } });

describe("PromptManager", () => {
  let saved;
  beforeEach(() => {
    saved = null;
    window.history.replaceState({}, "", "/prompts");
    vi.stubGlobal("fetch", vi.fn(async (input, options = {}) => {
      const url = String(input);
      if (url === "/api/prompt-catalog") return response(catalog);
      if (options.method === "POST") { saved = JSON.parse(options.body); return response({ id: 9 }); }
      return response([]);
    }));
  });
  afterEach(() => { cleanup(); vi.unstubAllGlobals(); });

  it("builds and saves a complete structured scheme", async () => {
    render(<PromptManager />);
    await screen.findByLabelText("搜索人物");
    fireEvent.change(screen.getByLabelText("方案名称"), { target: { value: "结构化方案" } });
    fireEvent.change(screen.getByLabelText("搜索人物"), { target: { value: "单人" } });
    fireEvent.click(screen.getByRole("button", { name: /单人/ }));
    fireEvent.change(screen.getByLabelText("搜索人物"), { target: { value: "女孩" } });
    fireEvent.click(screen.getByRole("button", { name: /女孩/ }));
    expect(screen.getByLabelText("最终正向 Prompt").value).toBe("solo, 1girl");
    expect(screen.getByLabelText("最终反向 Prompt").value).toBe("low quality, bad hands, crowd, male");
    fireEvent.change(screen.getByLabelText("最终正向 Prompt"), { target: { value: "temporary final edit" } });
    fireEvent.click(screen.getByRole("button", { name: "保存" }));
    await waitFor(() => expect(saved).not.toBeNull());
    expect(saved).toEqual({
      name: "结构化方案", selectedOptions: { Quality: [], Character: ["solo", "girl"], Clothing: [] },
      positiveExtra: "temporary final edit", negativeExtra: "", positivePrompt: "temporary final edit",
      negativePrompt: "low quality, bad hands, crowd, male", remark: "", isDefault: false,
    });
    expect(saved).not.toHaveProperty("parameters");
    expect(screen.queryByLabelText("备注")).toBeNull();
  });

  it("searches Chinese and English Prompt text in the redesigned picker", async () => {
    render(<PromptManager />);
    const search = await screen.findByLabelText("搜索服装");
    fireEvent.focus(search);
    expect(screen.getByText("常用词条")).toBeTruthy();
    fireEvent.change(search, { target: { value: "黑丝袜" } });
    expect(screen.getByRole("button", { name: /黑丝袜/ })).toBeTruthy();
    fireEvent.change(search, { target: { value: "pantyhose" } });
    expect(screen.getByRole("button", { name: /black pantyhose/ })).toBeTruthy();
    fireEvent.pointerDown(document.body);
    expect(screen.queryByText("找到 1 项")).toBeNull();
  });

  it("restores every saved selection and final Prompt from the edit query", async () => {
    const selectedOptions = { ...emptySelectedOptions(), Quality: ["masterpiece"] };
    const preset = { id: 7, name: "已保存", selectedOptions, positiveExtra: "extra", negativeExtra: "", positivePrompt: "saved final", negativePrompt: "saved negative", remark: "memo", isDefault: true };
    window.history.replaceState({}, "", "/prompts?edit=7");
    fetch.mockImplementation(async (input) => String(input) === "/api/prompt-catalog" ? response(catalog) : response([preset]));
    render(<PromptManager />);
    expect(await screen.findByDisplayValue("已保存")).toBeTruthy();
    expect(screen.getByText("杰作")).toBeTruthy();
    expect(screen.getByLabelText("最终正向 Prompt").value).toBe("saved final");
    expect(screen.getByLabelText("是否默认方案").checked).toBe(true);
  });
});
