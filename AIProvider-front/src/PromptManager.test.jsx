// @vitest-environment jsdom
import React from "react";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import PromptManager from "./PromptManager";

const response = (data) => new Response(JSON.stringify({ code: 200, data }), {
  status: 200,
  headers: { "Content-Type": "application/json" },
});

describe("PromptManager", () => {
  let saved;

  beforeEach(() => {
    saved = null;
    vi.stubGlobal("fetch", vi.fn(async (_input, options = {}) => {
      if (options.method === "POST") {
        saved = JSON.parse(options.body);
        return response({ id: 9 });
      }
      return response([]);
    }));
  });

  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });

  it("starts blank and only serializes non-empty Prompt fields", async () => {
    render(<PromptManager />);
    await waitFor(() => expect(fetch).toHaveBeenCalled());
    expect(screen.getByLabelText("正向 Prompt").value).toBe("");
    expect(screen.getByLabelText("反向 Prompt").value).toBe("");
    fireEvent.change(screen.getByLabelText("方案名称"), { target: { value: "空方案" } });
    fireEvent.change(screen.getByLabelText("正向 Prompt"), { target: { value: "0" } });
    fireEvent.click(screen.getByRole("button", { name: "保存" }));
    await waitFor(() => expect(saved).not.toBeNull());
    expect(saved.parameters).toEqual({ positivePrompt: "0" });
    expect(saved).not.toHaveProperty("workflowId");
  });
});
