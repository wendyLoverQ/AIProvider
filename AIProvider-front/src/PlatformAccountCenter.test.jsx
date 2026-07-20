// @vitest-environment jsdom
import React from "react";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
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

  it("creates platform-specific accounts and never renders submitted credentials",async()=>{
    vi.stubGlobal("fetch",vi.fn(async(input,options)=>options?.method==="POST"?ok({...page.items[1],id:10,displayName:"Gemini 备用"}):options?.method==="PUT"?ok({...page.items[1],id:10,displayName:"Gemini 备用",credentialTypes:["API_KEY"],credentialHints:["••••7890"]}):ok(page)));
    render(<PlatformAccountCenter/>);await screen.findByText("X 主账号");
    fireEvent.click(screen.getByRole("button",{name:"新增账号"}));
    fireEvent.change(screen.getByLabelText("平台"),{target:{value:"GEMINI"}});
    fireEvent.change(screen.getByLabelText("账号名称"),{target:{value:"Gemini 备用"}});
    fireEvent.change(screen.getByLabelText("API Key"),{target:{value:"top-secret-7890"}});
    fireEvent.click(screen.getByRole("button",{name:"保存账号"}));
    await waitFor(()=>expect(fetch).toHaveBeenCalledWith("/api/platform-accounts/10/secrets/API_KEY",expect.objectContaining({method:"PUT"})));
    expect(document.body.textContent).not.toContain("top-secret-7890");
  });

  it("loads usages before archive and leaves an in-use account visible",async()=>{
    vi.stubGlobal("fetch",vi.fn(async(input,options)=>String(input).endsWith("/usages")?ok([{consumerType:"CONTENT_COLLECTION",consumerId:4,consumerName:"情报采集"}]):ok(page)));
    render(<PlatformAccountCenter/>);await screen.findByText("X 主账号");
    fireEvent.click(screen.getByRole("button",{name:"查看 X 主账号 使用关系"}));
    expect(await screen.findByText("情报采集")).toBeTruthy();
    expect(screen.getByRole("button",{name:"归档账号"}).disabled).toBe(true);
  });
});
