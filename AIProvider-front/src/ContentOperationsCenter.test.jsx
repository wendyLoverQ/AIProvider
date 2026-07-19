// @vitest-environment jsdom
import React from "react";
import { act, cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import ContentOperationsCenter from "./ContentOperationsCenter";

const ok = (data) => new Response(JSON.stringify({ code:200, data }), { status:200, headers:{"Content-Type":"application/json"} });
const fail = (message) => new Response(JSON.stringify({ code:400, message }), { status:400, headers:{"Content-Type":"application/json"} });
const overview = { settings:{automationEnabled:true,defaultPublishMode:"AUTO",crawlIntervalMinutes:240,commentIntervalMinutes:30,contentModel:"gemini"}, counters:{collectedToday:3,readyDrafts:2,publishedToday:1,pendingComments:4,failedPublications:0}, accounts:[{id:1,displayName:"主账号",accountHandle:"operator",publishMode:"AUTO",enabled:true,adapterStatus:"NOT_CONFIGURED"}], collectionAccounts:[{id:4,platform:"TWITTER",displayName:"主 X 账号",adapterType:"TWITTER_WEB",credentialConfigured:true,enabled:true}], sources:[], recentPublications:[] };

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
  it("configures an independent interval publishing rule for each account and source",async()=>{
    const configured={...overview,sources:[{id:3,name:"OpenAI",adapterType:"TWITTER_WEB",externalHandle:"OpenAI",enabled:true}]};
    vi.stubGlobal("fetch",vi.fn(async(input,options)=>{const url=String(input);if(url.endsWith("/source-rules"))return ok([{sourceId:3,sourceName:"OpenAI",enabled:true,publishTiming:"INTERVAL",publishIntervalMinutes:1}]);if(options?.method==="PUT")return ok([3]);return ok(configured);}));
    render(<ContentOperationsCenter/>);fireEvent.click(await screen.findByRole("button",{name:"账号"}));
    expect((await screen.findByRole("combobox",{name:"OpenAI发布时机"})).value).toBe("INTERVAL");
    expect(screen.getByRole("spinbutton",{name:"OpenAI发布间隔"}).getAttribute("min")).toBe("1");
    fireEvent.click(screen.getByRole("button",{name:"保存发布规则"}));
    await waitFor(()=>expect(fetch).toHaveBeenCalledWith("/api/content-operations/accounts/1/sources",expect.objectContaining({method:"PUT"})));
    const call=fetch.mock.calls.find(([url,options])=>String(url).endsWith("/accounts/1/sources")&&options?.method==="PUT");expect(JSON.parse(call[1].body).rules[0]).toMatchObject({sourceId:3,publishTiming:"INTERVAL",publishIntervalMinutes:1});
  });
  it("shows request failures as a dismissible foreground alert", async()=>{
    vi.stubGlobal("fetch",vi.fn(async()=>fail("X Cookie 导入失败")));render(<ContentOperationsCenter/>);
    expect((await screen.findByRole("alert")).textContent).toContain("X Cookie 导入失败");
    fireEvent.click(screen.getByRole("button",{name:"关闭消息"}));
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
  it("stores a multiline Netscape Cookie once on a collection account",async()=>{const netscape="# Netscape HTTP Cookie File\n.x.com\tTRUE\t/\tTRUE\t1900000000\tauth_token\tauth\n.x.com\tTRUE\t/\tTRUE\t1900000000\tct0\tcsrf";vi.stubGlobal("fetch",vi.fn(async()=>ok(overview)));render(<ContentOperationsCenter/>);fireEvent.click(await screen.findByRole("button",{name:"采集源"}));fireEvent.click(screen.getByRole("button",{name:"添加采集账号"}));fireEvent.change(screen.getByPlaceholderText("例如：我的 X 采集账号"),{target:{value:"备用 X 账号"}});const cookie=screen.getByLabelText("X Cookie");expect(cookie.tagName).toBe("TEXTAREA");fireEvent.change(cookie,{target:{value:netscape}});fireEvent.click(screen.getByRole("button",{name:"保存配置"}));await waitFor(()=>expect(fetch).toHaveBeenCalledWith("/api/content-operations/collection-accounts",expect.objectContaining({method:"POST"})));const call=fetch.mock.calls.find(([url])=>String(url).endsWith("/collection-accounts"));expect(JSON.parse(call[1].body).accessToken).toBe(netscape);});
  it("binds a new source to an existing collection account without asking for Cookie again",async()=>{vi.stubGlobal("fetch",vi.fn(async()=>ok(overview)));render(<ContentOperationsCenter/>);fireEvent.click(await screen.findByRole("button",{name:"采集源"}));fireEvent.click(screen.getByRole("button",{name:"添加内容来源"}));expect(screen.getByRole("combobox",{name:"采集账号"}).value).toBe("4");expect(screen.queryByLabelText("X Cookie")).toBeNull();fireEvent.change(screen.getByPlaceholderText("例如：Elon Musk"),{target:{value:"Elon Musk"}});fireEvent.change(screen.getByLabelText("Twitter 用户名"),{target:{value:"elonmusk"}});fireEvent.click(screen.getByRole("button",{name:"保存配置"}));await waitFor(()=>expect(fetch).toHaveBeenCalledWith("/api/content-operations/sources",expect.objectContaining({method:"POST"})));const call=fetch.mock.calls.find(([url])=>String(url).endsWith("/sources"));expect(JSON.parse(call[1].body).collectionAccountId).toBe("4");});
  it("opens a failed publication and shows its stored reason",async()=>{const failed={...overview,recentPublications:[{id:9,title:"失败任务",accountName:"主账号",publishMode:"AUTO",status:"FAILED",attemptCount:1,errorCode:"XHS_PUBLISH_FAILED",errorMessage:"查找图片上传控件超时",scheduledAt:"2026-07-19T01:00:00"}]};vi.stubGlobal("fetch",vi.fn(async()=>ok(failed)));render(<ContentOperationsCenter/>);fireEvent.click(await screen.findByRole("button",{name:"发布队列"}));fireEvent.click(screen.getByRole("button",{name:"查看失败任务发布详情"}));expect(screen.getByRole("dialog",{name:"发布任务详情"})).toBeTruthy();expect(screen.getByText("查找图片上传控件超时")).toBeTruthy();expect(screen.getByText("XHS_PUBLISH_FAILED")).toBeTruthy();});
  it("groups publishing work and loads the complete task content",async()=>{const queued={...overview,recentPublications:[{id:10,title:"发送中的任务",accountName:"主账号",publishMode:"AUTO",status:"PROCESSING",attemptCount:1},{id:11,title:"完成任务",accountName:"主账号",publishMode:"AUTO",status:"PUBLISHED",attemptCount:1}]};vi.stubGlobal("fetch",vi.fn(async input=>String(input).endsWith("/publications/10")?ok({...queued.recentPublications[0],body:"完整发布正文",tagsJson:'["AI"]',sourceName:"OpenAI",sourceText:"采集原文"}):ok(queued)));render(<ContentOperationsCenter/>);fireEvent.click(await screen.findByRole("button",{name:"发布队列"}));expect(screen.getByText("发送中与待处理")).toBeTruthy();expect(screen.getByText("已完成")).toBeTruthy();fireEvent.click(screen.getByRole("button",{name:"查看发送中的任务发布详情"}));expect(await screen.findByText("完整发布正文")).toBeTruthy();expect(screen.getAllByText("采集原文").length).toBe(2);});
  it("queries collection history and opens its details",async()=>{const withSource={...overview,sources:[{id:2,name:"OpenAI",adapterType:"TWITTER_WEB",externalHandle:"OpenAI",lastStatus:"SUCCESS"}]};const item={id:7,sourceId:2,sourceName:"OpenAI",authorName:"OpenAI",rawText:"历史采集正文",relevanceStatus:"RELEVANT",collectedAt:"2026-07-19T10:00:00"};vi.stubGlobal("fetch",vi.fn(async input=>String(input).includes("/items?")?ok([item]):ok(withSource)));render(<ContentOperationsCenter/>);fireEvent.click(await screen.findByRole("button",{name:"采集源"}));expect(await screen.findByText("作者 / 来源")).toBeTruthy();expect(screen.getByText("内容摘要")).toBeTruthy();expect(screen.getByText("采集时间")).toBeTruthy();expect(screen.getByText("历史采集正文")).toBeTruthy();fireEvent.change(screen.getByRole("textbox",{name:"查询采集历史"}),{target:{value:"AI"}});await waitFor(()=>expect(fetch).toHaveBeenCalledWith(expect.stringContaining("query=AI"),expect.anything()));fireEvent.click(screen.getByRole("button",{name:/历史采集正文/}));expect(screen.getByRole("dialog",{name:"采集内容详情"})).toBeTruthy();});
  it("puts compact automation controls before Gemini and shows run outcomes",async()=>{const runs=[{id:1,status:"FAILED",startedAt:"2026-07-19T10:00:00",errorMessage:"X Cookie 已失效"}];vi.stubGlobal("fetch",vi.fn(async input=>String(input).includes("automation-runs")?ok(runs):String(input).endsWith("/ai-config")?ok({provider:"GEMINI",enabled:false,apiKeyConfigured:false,apiBaseUrl:"https://example.com",model:"gemini",relevancePrompt:"这是足够长的相关性提示词模板内容。",contentRewritePrompt:"这是足够长的内容改写提示词模板内容。",commentReplyPrompt:"这是足够长的评论回复提示词模板内容。",temperature:.7,maxOutputTokens:2048}):ok(overview)));render(<ContentOperationsCenter/>);fireEvent.click(await screen.findByRole("button",{name:"自动化设置"}));expect(await screen.findByText("最近自动运行")).toBeTruthy();expect(screen.getByText("X Cookie 已失效")).toBeTruthy();expect(screen.getByLabelText("内容采集周期").value).toBe("240");});
  it("requires a bearer token for an official API collection account",async()=>{vi.stubGlobal("fetch",vi.fn(async()=>ok(overview)));render(<ContentOperationsCenter/>);fireEvent.click(await screen.findByRole("button",{name:"采集源"}));fireEvent.click(screen.getByRole("button",{name:"添加采集账号"}));fireEvent.change(screen.getByRole("combobox",{name:"采集方式"}),{target:{value:"TWITTER_API"}});expect(screen.getByLabelText("Bearer Token")).toBeTruthy();expect(screen.queryByLabelText("X Cookie")).toBeNull();});
});
