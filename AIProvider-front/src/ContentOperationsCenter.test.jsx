// @vitest-environment jsdom
import React from "react";
import { act, cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import ContentOperationsCenter from "./ContentOperationsCenter";

const ok = (data) => new Response(JSON.stringify({ code:200, data }), { status:200, headers:{"Content-Type":"application/json"} });
const fail = (message) => new Response(JSON.stringify({ code:400, message }), { status:400, headers:{"Content-Type":"application/json"} });
const overview = { settings:{automationEnabled:true,defaultPublishMode:"AUTO",crawlIntervalMinutes:240,commentIntervalMinutes:30,contentModel:"gemini"}, counters:{collectedToday:3,readyDrafts:2,publishedToday:1,pendingComments:4,failedPublications:0}, accounts:[{id:1,displayName:"主账号",accountHandle:"operator",publishMode:"AUTO",enabled:true,adapterStatus:"NOT_CONFIGURED"}], sources:[], recentPublications:[] };

describe("ContentOperationsCenter", () => {
  afterEach(()=>{cleanup();vi.useRealTimers();vi.unstubAllGlobals();});
  it("shows the implemented publishing step and the still-pending comment adapter", async()=>{
    vi.stubGlobal("fetch",vi.fn(async()=>ok(overview))); render(<ContentOperationsCenter/>);
    expect(await screen.findByText("小红书运营控制台")).toBeTruthy();
    expect(screen.getByText("自动运行中")).toBeTruthy();
    expect(screen.getByText("扫码会话 · 自动文字卡 · 幂等发送")).toBeTruthy();
    expect(screen.getByText("尚未接入评论网页适配器")).toBeTruthy();
  });
  it("updates an account between automatic and manual mode", async()=>{
    vi.stubGlobal("fetch",vi.fn(async(input)=>String(input).endsWith("/overview")?ok(overview):ok({...overview.accounts[0],publishMode:"MANUAL"})));
    render(<ContentOperationsCenter/>); fireEvent.click(await screen.findByRole("button",{name:"账号"}));
    fireEvent.change(screen.getByRole("combobox"),{target:{value:"MANUAL"}});
    await waitFor(()=>expect(fetch).toHaveBeenCalledWith("/api/content-operations/accounts/1",expect.objectContaining({method:"PATCH"})));
  });
  it("loads Gemini settings without exposing the existing API key", async()=>{
    const aiConfig={provider:"GEMINI",enabled:true,apiKeyConfigured:true,apiKeyHint:"••••1234",apiBaseUrl:"https://generativelanguage.googleapis.com",model:"gemini-3.5-flash",relevancePrompt:"这是用于测试的人工智能内容相关性判断提示词，长度必须满足后端要求。",contentRewritePrompt:"这是用于测试的小红书内容改写提示词，长度必须满足后端要求。",commentReplyPrompt:"这是用于测试的小红书评论回复提示词，长度必须满足后端要求。",temperature:0.7,maxOutputTokens:2048};
    vi.stubGlobal("fetch",vi.fn(async(input)=>String(input).endsWith("/ai-config")?ok(aiConfig):ok(overview)));
    render(<ContentOperationsCenter/>);fireEvent.click(await screen.findByRole("button",{name:"自动化设置"}));
    expect(await screen.findByText("Gemini 内容生成")).toBeTruthy();expect(screen.getByText("密钥已配置 ••••1234")).toBeTruthy();
    const keyInput=screen.getByPlaceholderText("留空则保留现有密钥");expect(keyInput.value).toBe("");expect(keyInput.type).toBe("password");
  });
  it("shows request failures as a dismissible foreground alert", async()=>{
    vi.stubGlobal("fetch",vi.fn(async()=>fail("X Cookie 导入失败")));render(<ContentOperationsCenter/>);
    expect((await screen.findByRole("alert")).textContent).toContain("X Cookie 导入失败");
    fireEvent.click(screen.getByRole("button",{name:"关闭错误提示"}));
    expect(screen.queryByRole("alert")).toBeNull();
  });
  it("classifies the latest fetched item before showing it as relevant", async()=>{
    const withSource={...overview,sources:[{id:3,name:"Elon Musk",adapterType:"TWITTER_API",externalUid:"44196397",pollIntervalMinutes:240,credentialConfigured:true,credentialHint:"••••abcd",lastStatus:"SUCCESS"}]};
    const item={id:8,authorName:"Elon Musk",rawText:"New AI model",sourceUrl:"https://x.com/8",relevanceStatus:"PENDING"};
    vi.stubGlobal("fetch",vi.fn(async(input)=>{const url=String(input);if(url.endsWith("/sources/3/test-fetch"))return ok({sourceId:3,fetchedCount:1,newCount:1,items:[item]});if(url.endsWith("/items/8/classify"))return ok({contentItemId:8,relevant:true,score:0.94,reason:"讨论 AI 模型",checkedAt:"2026-07-18T22:00:00"});return ok(withSource);}));
    render(<ContentOperationsCenter/>);fireEvent.click(await screen.findByRole("button",{name:"采集源"}));fireEvent.click(screen.getByRole("button",{name:"测试拉取并判断"}));
    expect(await screen.findByText("AI 相关")).toBeTruthy();expect(screen.getByText("相关度 94%")).toBeTruthy();expect(fetch).toHaveBeenCalledWith("/api/content-operations/items/8/classify",expect.objectContaining({method:"POST"}));
  });
  it("runs the bound account test pipeline after a QR session is configured",async()=>{
    const connected={...overview,accounts:[{...overview.accounts[0],sessionConfigured:true,lastConnectedAt:"2026-07-18T22:00:00",adapterStatus:"READY"}]};
    vi.stubGlobal("fetch",vi.fn(async(input)=>{const url=String(input);if(url.endsWith("/accounts/1/sources"))return ok([]);if(url.endsWith("/accounts/1/test-pipeline"))return ok([{sourceId:2,contentItemId:3,result:"PUBLISHED",message:"已生成文字卡并发布到小红书",draft:{title:"AI新进展"}}]);return ok(connected);}));
    render(<ContentOperationsCenter/>);fireEvent.click(await screen.findByRole("button",{name:"账号"}));fireEvent.click(await screen.findByRole("button",{name:"一键测试小红书"}));expect(await screen.findByText("已发布")).toBeTruthy();expect(screen.getByText("AI新进展")).toBeTruthy();expect(fetch).toHaveBeenCalledWith("/api/content-operations/accounts/1/test-pipeline",expect.objectContaining({method:"POST"}));
  });
  it("waits for each QR status request before scheduling the next poll",async()=>{
    let finishPoll;let pollCalls=0;const pendingPoll=new Promise(resolve=>{finishPoll=resolve;});
    vi.stubGlobal("fetch",vi.fn(async(input,init)=>{const url=String(input);if(url.endsWith("/accounts/1/sources"))return ok([]);if(url.endsWith("/accounts/1/xhs-login")&&init?.method==="POST")return ok({sessionId:"qr-session",status:"WAITING_SCAN",qrImageDataUrl:"data:image/png;base64,qr"});if(url.endsWith("/accounts/1/xhs-login/qr-session")){pollCalls++;return pendingPoll;}return ok(overview);}));
    render(<ContentOperationsCenter/>);fireEvent.click(await screen.findByRole("button",{name:"账号"}));vi.useFakeTimers();
    await act(async()=>{fireEvent.click(screen.getByRole("button",{name:"扫码登录小红书"}));await Promise.resolve();await Promise.resolve();});
    expect(screen.getByAltText("小红书登录二维码")).toBeTruthy();
    expect(screen.getByRole("button",{name:"等待扫码确认…"}).disabled).toBe(true);
    await act(async()=>{await vi.advanceTimersByTimeAsync(8000);});expect(pollCalls).toBe(1);
    await act(async()=>{finishPoll(ok({sessionId:"qr-session",status:"WAITING_SCAN",qrImageDataUrl:null}));await Promise.resolve();});
    expect(screen.getByAltText("小红书登录二维码")).toBeTruthy();
    await act(async()=>{await vi.advanceTimersByTimeAsync(2000);});expect(pollCalls).toBe(2);
  });
  it("submits a multiline Netscape Cookie file without flattening it",async()=>{const netscape="# Netscape HTTP Cookie File\n.x.com\tTRUE\t/\tTRUE\t1900000000\tauth_token\tauth\n.x.com\tTRUE\t/\tTRUE\t1900000000\tct0\tcsrf";vi.stubGlobal("fetch",vi.fn(async()=>ok(overview)));render(<ContentOperationsCenter/>);fireEvent.click(await screen.findByRole("button",{name:"采集源"}));fireEvent.click(screen.getByRole("button",{name:"添加内容来源"}));expect(screen.getByRole("combobox",{name:"采集方式"}).value).toBe("TWITTER_WEB");fireEvent.change(screen.getByPlaceholderText("例如：Elon Musk"),{target:{value:"Elon Musk"}});fireEvent.change(screen.getByLabelText("Twitter 用户名"),{target:{value:"elonmusk"}});const cookie=screen.getByLabelText("X Cookie");expect(cookie.tagName).toBe("TEXTAREA");fireEvent.change(cookie,{target:{value:netscape}});fireEvent.click(screen.getByRole("button",{name:"保存配置"}));await waitFor(()=>expect(fetch).toHaveBeenCalledWith("/api/content-operations/sources",expect.objectContaining({method:"POST"})));const call=fetch.mock.calls.find(([url])=>String(url).endsWith("/sources"));expect(JSON.parse(call[1].body).accessToken).toBe(netscape);expect(screen.queryByLabelText("Bearer Token")).toBeNull();});
  it("requires a numeric UID and bearer token for the official API adapter",async()=>{vi.stubGlobal("fetch",vi.fn(async()=>ok(overview)));render(<ContentOperationsCenter/>);fireEvent.click(await screen.findByRole("button",{name:"采集源"}));fireEvent.click(screen.getByRole("button",{name:"添加内容来源"}));fireEvent.change(screen.getByRole("combobox",{name:"采集方式"}),{target:{value:"TWITTER_API"}});expect(screen.getByLabelText("Twitter UID")).toBeTruthy();expect(screen.getByLabelText("Bearer Token")).toBeTruthy();expect(screen.queryByLabelText("Twitter 用户名")).toBeNull();});
});
