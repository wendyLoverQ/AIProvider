// @vitest-environment jsdom
import React from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import RemoteCodex from "./RemoteCodex";

const ok = (data) => Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve({ code: 200, data }) });
const status = { loggedIn: true, loginState: "IDLE", workingDirectory: "/home/ubuntu" };
const conversation = { id: "conversation-1", title: "新对话", status: "READY", messages: [] };
const quota = { rateLimits: { planType: "plus", primary: { usedPercent: 25, windowDurationMins: 10080, resetsAt: 1784981515 } } };

afterEach(() => { cleanup(); delete Element.prototype.scrollIntoView; vi.restoreAllMocks(); });

describe("RemoteCodex", () => {
  it("loads conversations without an access token", async () => {
    const fetchMock = vi.spyOn(globalThis, "fetch").mockImplementation((url) => {
      if (url === "/api/remote-codex/status") return ok({ ...status, loggedIn: false });
      if (url === "/api/remote-codex/conversations") return ok([]);
      throw new Error(`unexpected request: ${url}`);
    });
    render(<RemoteCodex />);
    expect(await screen.findByText("Codex 未登录")).toBeTruthy();
    expect(screen.queryByText("访问密钥")).toBeNull();
    expect(fetchMock.mock.calls.every(([, options]) =>
      !Object.hasOwn(options?.headers || {}, "X-Remote-Codex-Token"))).toBe(true);
  });

  it("loads, creates and sends a basic remote conversation", async () => {
    const fetchMock = vi.spyOn(globalThis, "fetch").mockImplementation((url, options = {}) => {
      if (url === "/api/remote-codex/status") return ok(status);
      if (url === "/api/remote-codex/quota") return ok(quota);
      if (url === "/api/remote-codex/conversations" && options.method === "POST") return ok(conversation);
      if (url === "/api/remote-codex/conversations") return ok([]);
      if (url === "/api/remote-codex/conversations/conversation-1/messages") return ok({ ...conversation, status: "RUNNING" });
      if (url === "/api/remote-codex/conversations/conversation-1") return ok(conversation);
      throw new Error(`unexpected request: ${url}`);
    });

    render(<RemoteCodex />);
    expect(await screen.findByText("Codex 已登录 · 完整权限")).toBeTruthy();
    expect(await screen.findByText("75%")).toBeTruthy();
    expect(screen.getByText("PLUS")).toBeTruthy();
    fireEvent.click(screen.getByRole("button", { name: "新建远程 Codex 对话" }));
    expect(await screen.findByText("这是一个新对话")).toBeTruthy();
    fireEvent.change(screen.getByLabelText("发送给 Codex"), { target: { value: "检查项目" } });
    fireEvent.keyDown(screen.getByLabelText("发送给 Codex"), { key: "Enter", shiftKey: false });

    await waitFor(() => expect(fetchMock.mock.calls.some(([url, options]) =>
      url === "/api/remote-codex/conversations/conversation-1/messages"
      && options.method === "POST"
      && JSON.parse(options.body).content === "检查项目")).toBe(true));
  });

  it("keeps Shift+Enter as a newline and scrolls new messages into view", async () => {
    const scrollMock = vi.fn();
    Element.prototype.scrollIntoView = scrollMock;
    const detail = { ...conversation, messages: [{ id: 1, role: "assistant", content: "已完成", createdAt: "2026-07-18T20:00:00" }] };
    const fetchMock = vi.spyOn(globalThis, "fetch").mockImplementation((url) => {
      if (url === "/api/remote-codex/status") return ok(status);
      if (url === "/api/remote-codex/quota") return ok(quota);
      if (url === "/api/remote-codex/conversations") return ok([conversation]);
      if (url === "/api/remote-codex/conversations/conversation-1") return ok(detail);
      throw new Error(`unexpected request: ${url}`);
    });
    render(<RemoteCodex />);
    expect(await screen.findByText("已完成")).toBeTruthy();
    await waitFor(() => expect(scrollMock).toHaveBeenCalled());
    const textarea = screen.getByLabelText("发送给 Codex");
    fireEvent.change(textarea, { target: { value: "第一行" } });
    fireEvent.keyDown(textarea, { key: "Enter", shiftKey: true });
    expect(textarea.value).toBe("第一行");
    expect(fetchMock.mock.calls.some(([url]) => String(url).endsWith("/messages"))).toBe(false);
  });

  it("opens the OpenAI device page and highlights the generated code", async () => {
    const openMock = vi.spyOn(window, "open").mockReturnValue({});
    vi.spyOn(globalThis, "fetch").mockImplementation((url, options = {}) => {
      if (url === "/api/remote-codex/status") return ok({ ...status, loggedIn: false, loginState: "IDLE", loginOutput: "" });
      if (url === "/api/remote-codex/conversations") return ok([]);
      if (url === "/api/remote-codex/login" && options.method === "POST") return ok({ ...status, loggedIn: false, loginState: "RUNNING", loginOutput: "Enter code ABCD-EFGHJ" });
      throw new Error(`unexpected request: ${url}`);
    });

    render(<RemoteCodex />);
    fireEvent.click(await screen.findByRole("button", { name: "开始设备登录" }));
    expect(await screen.findByText("ABCD-EFGHJ")).toBeTruthy();
    expect(screen.getByRole("link", { name: "打开 OpenAI 授权页" }).getAttribute("href")).toBe("https://auth.openai.com/codex/device");
    expect(openMock).toHaveBeenCalledWith("https://auth.openai.com/codex/device", "_blank", "noopener,noreferrer");
  });
});
