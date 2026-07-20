// @vitest-environment jsdom
import React from "react";
import { act, cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import PlatformAccountCenter from "./PlatformAccountCenter";

const ok=(data)=>new Response(JSON.stringify({code:200,data}),{status:200,headers:{"Content-Type":"application/json"}});
const page={items:[{id:7,platform:"X",accountKind:"SOCIAL",displayName:"X 主账号",accountHandle:"@owner",adapterType:"X_WEB",enabled:true,connectionStatus:"CONNECTED",credentialTypes:["COOKIE"],credentialHints:["Cookie 会话"],lastValidatedAt:"2026-07-21T02:00:00"},{id:9,platform:"GEMINI",accountKind:"API_SERVICE",displayName:"Gemini 主服务",adapterType:"GEMINI_API",enabled:true,connectionStatus:"NOT_CONFIGURED",credentialTypes:[],credentialHints:[]}],total:2,page:1,pageSize:30};

describe("PlatformAccountCenter",()=>{
  afterEach(()=>{cleanup();vi.unstubAllGlobals();});
  it("shows the three account-center sections and reuses the shared live search",async()=>{
    vi.stubGlobal("fetch",vi.fn(async()=>ok(page)));
    render(<PlatformAccountCenter/>);
    expect(await screen.findByText("X 主账号")).toBeTruthy();
    expect(screen.getByRole("heading",{name:"平台账号"})).toBeTruthy();
    expect(screen.getByRole("heading",{name:"API 服务"})).toBeTruthy();
    expect(screen.getByRole("heading",{name:"使用关系"})).toBeTruthy();
    fireEvent.change(screen.getByRole("searchbox",{name:"搜索账号"}),{target:{value:"owner"}});
    await waitFor(()=>expect(fetch).toHaveBeenLastCalledWith(expect.stringContaining("query=owner"),expect.anything()),{timeout:1000});
  });

  it("shows the matching brand icon on every platform account card",async()=>{
    const allPlatforms={...page,items:[
      ...page.items,
      {id:11,platform:"XIAOHONGSHU",accountKind:"SOCIAL",displayName:"小红书主账号",adapterType:"XIAOHONGSHU_WEB",enabled:true,connectionStatus:"CONNECTED",credentialTypes:["STORAGE_STATE"],credentialHints:["浏览器会话"]},
      {id:12,platform:"DOUYIN",accountKind:"SOCIAL",displayName:"抖音主账号",adapterType:"DOUYIN_WEB",enabled:true,connectionStatus:"CONNECTED",credentialTypes:["STORAGE_STATE"],credentialHints:["浏览器会话"]},
    ],total:4};
    vi.stubGlobal("fetch",vi.fn(async()=>ok(allPlatforms)));
    render(<PlatformAccountCenter/>);
    expect(await screen.findByRole("img",{name:"X 品牌图标"})).toBeTruthy();
    expect(screen.getByRole("img",{name:"小红书 品牌图标"})).toBeTruthy();
    expect(screen.getByRole("img",{name:"抖音 品牌图标"})).toBeTruthy();
    expect(screen.getByRole("img",{name:"Gemini 品牌图标"})).toBeTruthy();
  });

  it("creates platform-specific accounts and never renders submitted credentials",async()=>{
    vi.stubGlobal("fetch",vi.fn(async(input,options)=>options?.method==="POST"?ok({...page.items[1],id:10,displayName:"Gemini 备用"}):options?.method==="PUT"?ok({...page.items[1],id:10,displayName:"Gemini 备用",credentialTypes:["API_KEY"],credentialHints:["••••7890"]}):ok(page)));
    render(<PlatformAccountCenter/>);await screen.findByText("X 主账号");
    fireEvent.click(screen.getByRole("button",{name:"新增账号"}));
    fireEvent.click(screen.getByRole("radio",{name:"Gemini"}));
    fireEvent.change(screen.getByLabelText("账号名称"),{target:{value:"Gemini 备用"}});
    fireEvent.change(screen.getByLabelText("API Key"),{target:{value:"top-secret-7890"}});
    fireEvent.click(screen.getByRole("button",{name:"保存账号"}));
    await waitFor(()=>expect(fetch).toHaveBeenCalledWith("/api/platform-accounts/10/secrets/API_KEY",expect.objectContaining({method:"PUT"})));
    expect(document.body.textContent).not.toContain("top-secret-7890");
  });

  it("loads usages before delete and leaves an in-use account visible",async()=>{
    vi.stubGlobal("fetch",vi.fn(async(input,options)=>String(input).endsWith("/usages")?ok([{consumerType:"CONTENT_COLLECTION",consumerId:4,consumerName:"情报采集"}]):ok(page)));
    render(<PlatformAccountCenter/>);await screen.findByText("X 主账号");
    fireEvent.click(screen.getByRole("button",{name:"编辑 X 主账号"}));
    fireEvent.click(screen.getByRole("button",{name:"删除账号"}));
    expect(await screen.findByText("情报采集")).toBeTruthy();
    expect(screen.queryByRole("alertdialog",{name:"确认删除 X 主账号"})).toBeNull();
    expect(screen.getByRole("alert").textContent).toContain("不能删除");
  });
  it("starts a platform QR session and shows only the returned QR image",async()=>{
    const withDouyin={...page,items:[...page.items,{id:11,platform:"DOUYIN",accountKind:"SOCIAL",displayName:"抖音主账号",adapterType:"DOUYIN_WEB",enabled:true,connectionStatus:"NOT_CONFIGURED",credentialTypes:[],credentialHints:[]}]};
    vi.stubGlobal("fetch",vi.fn(async(input,options)=>options?.method==="POST"&&String(input).endsWith("/11/login")?ok({sessionId:"qr-session",status:"WAITING_SCAN",qrImageDataUrl:"data:image/png;base64,AAAA",message:"等待扫码"}):ok(withDouyin)));
    render(<PlatformAccountCenter/>);fireEvent.click(await screen.findByRole("button",{name:"扫码连接 抖音主账号"}));
    expect(await screen.findByRole("dialog",{name:"抖音主账号 扫码登录"})).toBeTruthy();
    expect(screen.getByAltText("抖音 登录二维码").getAttribute("src")).toBe("data:image/png;base64,AAAA");
  });

  it("edits every mutable account field and keeps the existing secret when left blank",async()=>{
    vi.stubGlobal("fetch",vi.fn(async(input,options)=>options?.method==="PUT"?ok({...page.items[0],displayName:"X 工作账号"}):ok(page)));
    render(<PlatformAccountCenter/>);await screen.findByText("X 主账号");
    fireEvent.click(screen.getByRole("button",{name:"编辑 X 主账号"}));
    expect(screen.getByRole("dialog",{name:"编辑 X 主账号"})).toBeTruthy();
    fireEvent.change(screen.getByLabelText("账号名称"),{target:{value:"X 工作账号"}});
    fireEvent.click(screen.getByRole("button",{name:"保存修改"}));
    await waitFor(()=>expect(fetch).toHaveBeenCalledWith("/api/platform-accounts/7",expect.objectContaining({method:"PUT"})));
    expect(fetch.mock.calls.some(([url])=>String(url).includes("/secrets/"))).toBe(false);
  });

  it("checks usages and archives an unused account through a confirmation dialog",async()=>{
    vi.stubGlobal("fetch",vi.fn(async(input,options)=>String(input).endsWith("/usages")?ok([]):options?.method==="DELETE"?ok(null):ok(page)));
    render(<PlatformAccountCenter/>);await screen.findByText("X 主账号");
    fireEvent.click(screen.getByRole("button",{name:"编辑 X 主账号"}));
    fireEvent.click(screen.getByRole("button",{name:"删除账号"}));
    expect(await screen.findByRole("alertdialog",{name:"确认删除 X 主账号"})).toBeTruthy();
    fireEvent.click(screen.getByRole("button",{name:"确认删除"}));
    await waitFor(()=>expect(fetch).toHaveBeenCalledWith("/api/platform-accounts/7",expect.objectContaining({method:"DELETE"})));
  });

  it("disables connection actions for a disabled account",async()=>{
    const disabled={...page,items:[{...page.items[0],platform:"XIAOHONGSHU",displayName:"停用的小红书",enabled:false,connectionStatus:"ERROR"}]};
    vi.stubGlobal("fetch",vi.fn(async()=>ok(disabled)));
    render(<PlatformAccountCenter/>);await screen.findByText("停用的小红书");
    expect(screen.getByRole("button",{name:"扫码连接 停用的小红书"}).disabled).toBe(true);
    expect(screen.getByRole("button",{name:"验证 停用的小红书"}).disabled).toBe(true);
    expect(screen.getAllByText("已停用").length).toBeGreaterThan(0);
  });

  it("automatically dismisses request errors",async()=>{
    vi.useFakeTimers();
    vi.stubGlobal("fetch",vi.fn(async()=>new Response(JSON.stringify({code:409,message:"账号已停用，请先编辑账号并启用"}),{status:409,headers:{"Content-Type":"application/json"}})));
    render(<PlatformAccountCenter/>);
    await act(async()=>{await vi.advanceTimersByTimeAsync(350);});
    expect(screen.getByRole("alert").textContent).toContain("账号已停用");
    await act(async()=>{await vi.advanceTimersByTimeAsync(4000);});
    expect(screen.queryByRole("alert")).toBeNull();
  });
});
