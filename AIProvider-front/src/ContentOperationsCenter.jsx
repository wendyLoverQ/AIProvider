import { useEffect, useMemo, useState } from "react";
import { Broadcast, CheckCircle, Clock, GearSix, Plus, Robot, Warning, XCircle } from "@phosphor-icons/react";
import UiSearchField from "./UiSearchField";
import "./ContentOperationsCenter.css";

const EMPTY = { settings: {}, counters: {}, accounts: [], sources: [], recentPublications: [] };
const tabs = [["overview","总览"],["accounts","账号"],["sources","采集源"],["publishing","发布队列"],["comments","评论维护"],["settings","自动化设置"]];

async function request(path, options) {
  const response = await fetch(`/api/content-operations${path}`, { headers: { "Content-Type": "application/json" }, ...options });
  const result = await response.json();
  if (!response.ok || result.code !== 200) throw new Error(result.message || `请求失败 · ${response.status}`);
  return result.data;
}

const statusLabel = (value) => ({ NOT_CONFIGURED:"未配置", PENDING:"待执行", PUBLISHED:"已发布", FAILED:"失败", READY:"就绪" }[value] || value || "—");

export default function ContentOperationsCenter() {
  const [data, setData] = useState(EMPTY), [tab, setTab] = useState("overview"), [query, setQuery] = useState("");
  const [loading, setLoading] = useState(true), [error, setError] = useState(""), [dialog, setDialog] = useState("");
  const load = async () => { setLoading(true); setError(""); try { setData(await request("/overview")); } catch (e) { setError(e.message); } finally { setLoading(false); } };
  useEffect(() => { load(); }, []);
  const accounts = useMemo(() => data.accounts.filter((x) => `${x.displayName} ${x.accountHandle || ""}`.toLowerCase().includes(query.toLowerCase())), [data.accounts, query]);

  const updateAccount = async (account, patch) => { try { await request(`/accounts/${account.id}`, { method:"PATCH", body:JSON.stringify(patch) }); await load(); } catch (e) { setError(e.message); } };
  const save = async (event, kind) => {
    event.preventDefault(); const values = Object.fromEntries(new FormData(event.currentTarget));
    if (kind === "account") values.publishMode = values.publishMode || "AUTO";
    if (kind === "source") values.pollIntervalMinutes = Number(values.pollIntervalMinutes || 240);
    try { await request(kind === "account" ? "/accounts" : "/sources", { method:"POST", body:JSON.stringify(values) }); setDialog(""); await load(); }
    catch (e) { setError(e.message); }
  };

  if (loading && !data.accounts.length) return <div className="content-ops-state">正在载入内容运营中心…</div>;
  return <section className="content-operations-center">
    <header className="content-ops-hero">
      <div><span>CONTENT OPERATIONS</span><h2>小红书运营控制台</h2><p>采集、生成、发布、评论维护与运行审计集中管理</p></div>
      <div className={`automation-state ${data.settings.automationEnabled ? "on" : "off"}`}><Robot weight="duotone"/><b>{data.settings.automationEnabled ? "自动运行中" : "自动化已暂停"}</b><small>默认 {data.settings.defaultPublishMode === "AUTO" ? "全自动" : "手动"} · 每 {data.settings.crawlIntervalMinutes || 240} 分钟采集</small></div>
    </header>
    {error && <div className="content-ops-error"><Warning/> {error}<button type="button" onClick={() => setError("")}>关闭</button></div>}
    <nav className="content-ops-tabs" aria-label="内容运营分区">{tabs.map(([key,label]) => <button type="button" key={key} className={tab===key?"active":""} onClick={() => setTab(key)}>{label}</button>)}</nav>

    {tab === "overview" && <>
      <div className="content-ops-kpis">
        {[["今日采集",data.counters.collectedToday],["待发布草稿",data.counters.readyDrafts],["今日发布",data.counters.publishedToday],["待回复评论",data.counters.pendingComments],["发布失败",data.counters.failedPublications]].map(([label,value],i)=><article key={label}><span>{label}</span><strong>{value || 0}</strong>{i===4&&value>0?<XCircle/>:<CheckCircle/>}</article>)}
      </div>
      <div className="content-ops-grid">
        <article className="content-ops-panel"><header><div><span>PIPELINE</span><h3>自动化流水线</h3></div><Clock/></header><ol><li className="ready"><b>01</b><span>采集源轮询<small>每 {data.settings.crawlIntervalMinutes || 240} 分钟</small></span></li><li><b>02</b><span>内容清洗与去重<small>保留来源与原文快照</small></span></li><li><b>03</b><span>Gemini 内容改写<small>生成小红书标题、正文和标签</small></span></li><li className="blocked"><b>04</b><span>平台发布适配器<small>尚未配置获授权发布通道</small></span></li><li><b>05</b><span>评论监听与回复<small>发布成功后进入维护队列</small></span></li></ol></article>
        <article className="content-ops-panel"><header><div><span>ACCOUNTS</span><h3>账号运行状态</h3></div><button type="button" onClick={()=>setDialog("account")}><Plus/> 添加账号</button></header>{data.accounts.length?data.accounts.map(a=><AccountRow key={a.id} account={a} onUpdate={updateAccount}/>):<Empty text="还没有配置小红书账号"/>}</article>
      </div>
    </>}

    {tab === "accounts" && <div className="content-ops-panel wide"><header><div><span>XIAOHONGSHU</span><h3>账号与发布模式</h3></div><button type="button" onClick={()=>setDialog("account")}><Plus/> 添加账号</button></header><UiSearchField value={query} onChange={e=>setQuery(e.target.value)} placeholder="搜索账号" aria-label="搜索账号"/>{accounts.length?accounts.map(a=><AccountRow key={a.id} account={a} onUpdate={updateAccount}/>):<Empty text="没有匹配的账号"/>}</div>}
    {tab === "sources" && <div className="content-ops-panel wide"><header><div><span>COLLECTION</span><h3>内容采集源</h3></div><button type="button" onClick={()=>setDialog("source")}><Plus/> 添加采集源</button></header>{data.sources.length?data.sources.map(s=><div className="source-row" key={s.id}><Broadcast/><span><b>{s.name}</b><small>{s.sourceType} · 每 {s.pollIntervalMinutes} 分钟</small></span><code>{s.sourceUrl}</code><i>{statusLabel(s.lastStatus)}</i></div>):<Empty text="还没有内容采集源"/>}</div>}
    {tab === "publishing" && <div className="content-ops-panel wide"><header><div><span>QUEUE</span><h3>发布任务</h3></div></header>{data.recentPublications.length?data.recentPublications.map(p=><div className="publication-row" key={p.id}><span><b>{p.title}</b><small>{p.accountName} · {p.publishMode}</small></span><i className={p.status?.toLowerCase()}>{statusLabel(p.status)}</i></div>):<Empty text="发布队列为空；发布适配器未配置时不会创建虚假任务"/>}</div>}
    {tab === "comments" && <div className="content-ops-panel wide"><Empty text="评论维护将在真实发布成功并取得外部内容 ID 后启动"/></div>}
    {tab === "settings" && <div className="content-ops-settings-stack"><GeminiSettings onError={setError}/><Settings settings={data.settings} onSaved={load} onError={setError}/></div>}

    {dialog && <div className="content-ops-dialog-backdrop" role="presentation"><form className="content-ops-dialog" onSubmit={e=>save(e,dialog)}><header><h3>{dialog==="account"?"添加小红书账号":"添加采集源"}</h3><button type="button" onClick={()=>setDialog("")} aria-label="关闭">×</button></header>{dialog==="account"?<><label>显示名称<input name="displayName" required maxLength="100"/></label><label>账号标识<input name="accountHandle" maxLength="120"/></label><label>发布模式<select name="publishMode" defaultValue="AUTO"><option value="AUTO">全自动</option><option value="MANUAL">手动确认</option></select></label></>:<><label>名称<input name="name" required maxLength="120"/></label><label>类型<select name="sourceType" defaultValue="PROFILE"><option value="PROFILE">账号主页</option><option value="KEYWORD">关键词</option><option value="FEED">订阅源</option><option value="URL">指定页面</option></select></label><label>HTTPS 地址<input name="sourceUrl" type="url" required placeholder="https://"/></label><label>采集周期（分钟）<input name="pollIntervalMinutes" type="number" min="15" max="10080" defaultValue="240"/></label></>}<footer><button type="button" onClick={()=>setDialog("")}>取消</button><button type="submit">保存配置</button></footer></form></div>}
  </section>;
}

function AccountRow({account,onUpdate}) { return <div className="account-row"><span className="platform-mark">小</span><span><b>{account.displayName}</b><small>{account.accountHandle || "未填写账号标识"}</small></span><label>模式<select value={account.publishMode} onChange={e=>onUpdate(account,{publishMode:e.target.value,enabled:account.enabled})}><option value="AUTO">全自动</option><option value="MANUAL">手动</option></select></label><label className="native-switch"><input type="checkbox" checked={account.enabled} onChange={e=>onUpdate(account,{publishMode:account.publishMode,enabled:e.target.checked})}/><span>{account.enabled?"启用":"停用"}</span></label><i className={account.adapterStatus === "READY" ? "ready" : "blocked"}>{statusLabel(account.adapterStatus)}</i></div>; }
function Empty({text}) { return <div className="content-ops-empty"><Broadcast weight="duotone"/><p>{text}</p></div>; }
function Settings({settings,onSaved,onError}) { const submit=async e=>{e.preventDefault();const v=Object.fromEntries(new FormData(e.currentTarget));v.automationEnabled=v.automationEnabled==="on";v.crawlIntervalMinutes=Number(v.crawlIntervalMinutes);v.commentIntervalMinutes=Number(v.commentIntervalMinutes);try{await request("/settings",{method:"PUT",body:JSON.stringify(v)});await onSaved();}catch(x){onError(x.message);}};return <form className="content-ops-panel wide settings-form" onSubmit={submit}><header><div><span>ORCHESTRATION</span><h3>自动化设置</h3></div><GearSix/></header><label className="check-line"><input name="automationEnabled" type="checkbox" defaultChecked={settings.automationEnabled}/><span><b>启用自动运行</b><small>按计划执行已具备适配器的步骤</small></span></label><label>默认发布模式<select name="defaultPublishMode" defaultValue={settings.defaultPublishMode || "AUTO"}><option value="AUTO">全自动</option><option value="MANUAL">手动确认</option></select></label><label>采集周期（分钟）<input name="crawlIntervalMinutes" type="number" min="15" max="10080" defaultValue={settings.crawlIntervalMinutes || 240}/></label><label>评论周期（分钟）<input name="commentIntervalMinutes" type="number" min="5" max="1440" defaultValue={settings.commentIntervalMinutes || 30}/></label><button type="submit">保存自动化设置</button></form>;}

function GeminiSettings({onError}) {
  const [config,setConfig]=useState(null),[saved,setSaved]=useState(""),[testing,setTesting]=useState(false),[showKey,setShowKey]=useState(false);
  useEffect(()=>{request("/ai-config").then(setConfig).catch(e=>onError(e.message));},[onError]);
  if(!config)return <div className="content-ops-panel wide">正在读取 Gemini 配置…</div>;
  const submit=async e=>{e.preventDefault();setSaved("");const form=e.currentTarget;const v=Object.fromEntries(new FormData(form));v.enabled=v.enabled==="on";v.temperature=Number(v.temperature);v.maxOutputTokens=Number(v.maxOutputTokens);try{const next=await request("/ai-config",{method:"PUT",body:JSON.stringify(v)});setConfig(next);setSaved("Gemini 配置已保存");form.elements.apiKey.value="";}catch(x){onError(x.message);}};
  const test=async()=>{setTesting(true);setSaved("");try{const result=await request("/ai-config/test",{method:"POST"});setSaved(`${result.text} · ${result.latencyMs}ms`);}catch(x){onError(x.message);}finally{setTesting(false);}};
  return <form className="content-ops-panel wide gemini-form" onSubmit={submit}>
    <header><div><span>GEMINI PROVIDER</span><h3>Gemini 内容生成</h3></div><span className={config.apiKeyConfigured?"gemini-key ready":"gemini-key"}>{config.apiKeyConfigured?`密钥已配置 ${config.apiKeyHint || ""}`:"密钥未配置"}</span></header>
    <label className="check-line"><input name="enabled" type="checkbox" defaultChecked={config.enabled}/><span><b>启用 Gemini 生成</b><small>用于小红书内容改写和评论回复</small></span></label>
    <div className="gemini-field-row"><label>API Key<div className="secret-input"><input name="apiKey" type={showKey?"text":"password"} autoComplete="new-password" placeholder={config.apiKeyConfigured?"留空则保留现有密钥":"输入 Gemini API Key"}/><button type="button" onClick={()=>setShowKey(v=>!v)}>{showKey?"隐藏":"显示"}</button></div><small>仅提交给后端加密保存，页面不会读取现有明文</small></label><label>模型<input name="model" required defaultValue={config.model}/></label></div>
    <label>API 地址<input name="apiBaseUrl" type="url" required defaultValue={config.apiBaseUrl}/></label>
    <div className="gemini-field-row compact"><label>生成温度<input name="temperature" type="number" min="0" max="2" step="0.05" required defaultValue={config.temperature}/></label><label>最大输出 Token<input name="maxOutputTokens" type="number" min="128" max="65536" required defaultValue={config.maxOutputTokens}/></label></div>
    <label>小红书内容改写提示词<textarea name="contentRewritePrompt" required minLength="20" maxLength="12000" defaultValue={config.contentRewritePrompt}/></label>
    <label>评论回复提示词<textarea name="commentReplyPrompt" required minLength="20" maxLength="12000" defaultValue={config.commentReplyPrompt}/></label>
    <footer><button type="submit">保存 Gemini 配置</button><button type="button" onClick={test} disabled={testing||!config.enabled||!config.apiKeyConfigured}>{testing?"测试中…":"测试连接"}</button>{saved&&<span>{saved}</span>}</footer>
  </form>;
}
