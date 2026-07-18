// @vitest-environment jsdom
import React from "react";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import ContentOperationsCenter from "./ContentOperationsCenter";

const ok = (data) => new Response(JSON.stringify({ code:200, data }), { status:200, headers:{"Content-Type":"application/json"} });
const overview = { settings:{automationEnabled:true,defaultPublishMode:"AUTO",crawlIntervalMinutes:240,commentIntervalMinutes:30,contentModel:"gemini"}, counters:{collectedToday:3,readyDrafts:2,publishedToday:1,pendingComments:4,failedPublications:0}, accounts:[{id:1,displayName:"主账号",accountHandle:"operator",publishMode:"AUTO",enabled:true,adapterStatus:"NOT_CONFIGURED"}], sources:[], recentPublications:[] };

describe("ContentOperationsCenter", () => {
  afterEach(()=>{cleanup();vi.unstubAllGlobals();});
  it("shows the automation pipeline without claiming an unavailable adapter", async()=>{
    vi.stubGlobal("fetch",vi.fn(async()=>ok(overview))); render(<ContentOperationsCenter/>);
    expect(await screen.findByText("小红书运营控制台")).toBeTruthy();
    expect(screen.getByText("自动运行中")).toBeTruthy();
    expect(screen.getByText("尚未配置获授权发布通道")).toBeTruthy();
  });
  it("updates an account between automatic and manual mode", async()=>{
    vi.stubGlobal("fetch",vi.fn(async(input)=>String(input).endsWith("/overview")?ok(overview):ok({...overview.accounts[0],publishMode:"MANUAL"})));
    render(<ContentOperationsCenter/>); fireEvent.click(await screen.findByRole("button",{name:"账号"}));
    fireEvent.change(screen.getByRole("combobox"),{target:{value:"MANUAL"}});
    await waitFor(()=>expect(fetch).toHaveBeenCalledWith("/api/content-operations/accounts/1",expect.objectContaining({method:"PATCH"})));
  });
  it("loads Gemini settings without exposing the existing API key", async()=>{
    const aiConfig={provider:"GEMINI",enabled:true,apiKeyConfigured:true,apiKeyHint:"••••1234",apiBaseUrl:"https://generativelanguage.googleapis.com",model:"gemini-3.5-flash",contentRewritePrompt:"这是用于测试的小红书内容改写提示词，长度必须满足后端要求。",commentReplyPrompt:"这是用于测试的小红书评论回复提示词，长度必须满足后端要求。",temperature:0.7,maxOutputTokens:2048};
    vi.stubGlobal("fetch",vi.fn(async(input)=>String(input).endsWith("/ai-config")?ok(aiConfig):ok(overview)));
    render(<ContentOperationsCenter/>);fireEvent.click(await screen.findByRole("button",{name:"自动化设置"}));
    expect(await screen.findByText("Gemini 内容生成")).toBeTruthy();expect(screen.getByText("密钥已配置 ••••1234")).toBeTruthy();
    const keyInput=screen.getByPlaceholderText("留空则保留现有密钥");expect(keyInput.value).toBe("");expect(keyInput.type).toBe("password");
  });
});
