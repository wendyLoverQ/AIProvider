import { useEffect, useMemo, useState } from "react";
import {
  ArrowClockwise,
  Broadcast,
  Clock,
  Database,
  GearSix,
  PaperPlaneTilt,
  Plus,
  Robot,
  X,
  XCircle,
} from "@phosphor-icons/react";
import UiSearchField from "./UiSearchField";
import UiToast from "./UiToast";
import { readJsonResponse } from "./apiResponse";
import "./ContentOperationsCenter.css";

const EMPTY = {
  settings: {},
  counters: {},
  accounts: [],
  collectionAccounts: [],
  sources: [],
  recentPublications: [],
};

const TABS = [
  ["overview", "总览"],
  ["accounts", "账号"],
  ["sources", "采集源"],
  ["publishing", "发布队列"],
  ["comments", "评论维护"],
  ["settings", "自动化设置"],
];

const STATUS_LABELS = {
  NOT_CONFIGURED: "未配置",
  PENDING: "待执行",
  PROCESSING: "执行中",
  UNKNOWN: "待人工确认",
  RELEVANT: "AI 相关",
  IRRELEVANT: "非 AI 内容",
  PUBLISHED: "已发布",
  FAILED: "失败",
  SUCCEEDED: "完成",
  SUCCESS: "正常",
  READY: "就绪",
  COLLECTED: "已采集",
};

async function request(path, options) {
  const response = await fetch(`/api/content-operations${path}`, {
    headers: { "Content-Type": "application/json" },
    ...options,
  });
  const result = await readJsonResponse(response, "内容运营服务响应异常");
  if (!response.ok || result.code !== 200) {
    throw new Error(result.message || `请求失败 · ${response.status}`);
  }
  return result.data;
}

const statusLabel = (value) => STATUS_LABELS[value] || value || "—";
const statusClass = (value) => String(value || "pending").toLowerCase();
const formatTime = (value) => value ? new Date(value).toLocaleString("zh-CN") : "—";

function runMessage(run) {
  if (run.errorMessage) return run.errorMessage;
  try {
    const metrics = JSON.parse(run.metricsJson || "{}");
    return {
      NO_CONTENT: "未采集到新内容",
      FILTERED: "采集内容被判定为非 AI",
      ALREADY_EXISTS: "内容已处理，未重复生成",
      UNCERTAIN: "此前发布结果待人工确认",
      PUBLISHED: "已生成并发布",
      COLLECTED: "内容已采集并入库",
    }[metrics.result] || metrics.result || "运行完成";
  } catch {
    return "运行完成";
  }
}

export default function ContentOperationsCenter() {
  const [data, setData] = useState(EMPTY);
  const [tab, setTab] = useState("overview");
  const [query, setQuery] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [dialog, setDialog] = useState(null);
  const [deleteTarget, setDeleteTarget] = useState(null);
  const [selectedPublication, setSelectedPublication] = useState(null);
  const [sourceResult, setSourceResult] = useState(null);
  const [testingSourceId, setTestingSourceId] = useState(null);
  const [historyQuery, setHistoryQuery] = useState("");
  const [historySource, setHistorySource] = useState("");
  const [history, setHistory] = useState([]);
  const [selectedHistory, setSelectedHistory] = useState(null);
  const [automationRuns, setAutomationRuns] = useState([]);

  const load = async () => {
    setLoading(true);
    setError("");
    try {
      setData(await request("/overview"));
    } catch (caught) {
      setError(caught.message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); }, []);

  useEffect(() => {
    if (tab !== "sources") return undefined;
    const timer = setTimeout(() => {
      const source = historySource ? `&sourceId=${historySource}` : "";
      const keyword = historyQuery.trim() ? `&query=${encodeURIComponent(historyQuery.trim())}` : "";
      request(`/items?limit=100${source}${keyword}`).then(setHistory).catch((caught) => setError(caught.message));
    }, 200);
    return () => clearTimeout(timer);
  }, [tab, historyQuery, historySource]);

  useEffect(() => {
    if (tab !== "settings") return;
    request("/automation-runs?limit=20").then(setAutomationRuns).catch((caught) => setError(caught.message));
  }, [tab]);

  const accounts = useMemo(() => {
    const keyword = query.trim().toLowerCase();
    return data.accounts.filter((account) => `${account.displayName} ${account.accountHandle || ""}`.toLowerCase().includes(keyword));
  }, [data.accounts, query]);

  const updateAccount = async (account, patch) => {
    try {
      await request(`/accounts/${account.id}`, { method: "PATCH", body: JSON.stringify(patch) });
      await load();
    } catch (caught) {
      setError(caught.message);
    }
  };

  const saveEntity = async (event, descriptor) => {
    event.preventDefault();
    const values = Object.fromEntries(new FormData(event.currentTarget));
    const kind = typeof descriptor === "string" ? descriptor : descriptor.kind;
    const item = typeof descriptor === "string" ? null : descriptor.item;
    if (kind === "account") values.publishMode = values.publishMode || "AUTO";
    if (item) values.enabled = values.enabled === "on";
    const path = kind === "account" ? "/accounts" : kind === "collector" ? "/collection-accounts" : "/sources";
    try {
      await request(`${path}${item ? `/${item.id}` : ""}`, {
        method: item ? "PUT" : "POST",
        body: JSON.stringify(values),
      });
      setDialog(null);
      setNotice(item ? "配置已更新" : "配置已保存");
      await load();
    } catch (caught) {
      setError(caught.message);
    }
  };

  const removeEntity = async () => {
    if (!deleteTarget) return;
    const path = deleteTarget.kind === "account" ? "/accounts" : deleteTarget.kind === "collector" ? "/collection-accounts" : "/sources";
    try {
      await request(`${path}/${deleteTarget.item.id}`, { method: "DELETE" });
      setDeleteTarget(null);
      setNotice("已删除");
      await load();
    } catch (caught) {
      setError(caught.message);
    }
  };

  const applyDecision = (item, decision) => setSourceResult((current) => ({
    ...current,
    items: current.items.map((entry) => entry.id === item.id ? {
      ...entry,
      relevanceStatus: decision.relevant ? "RELEVANT" : "IRRELEVANT",
      relevanceScore: decision.score,
      relevanceReason: decision.reason,
      relevanceCheckedAt: decision.checkedAt,
    } : entry),
  }));

  const classifyItem = async (item) => {
    setError("");
    try {
      applyDecision(item, await request(`/items/${item.id}/classify`, { method: "POST" }));
    } catch (caught) {
      setError(caught.message);
    }
  };

  const testSource = async (source) => {
    setTestingSourceId(source.id);
    setError("");
    try {
      const result = await request(`/sources/${source.id}/test-fetch`, { method: "POST" });
      setSourceResult(result);
      if (result.items?.[0]) {
        const decision = await request(`/items/${result.items[0].id}/classify`, { method: "POST" });
        setSourceResult((current) => ({
          ...current,
          items: current.items.map((entry) => entry.id === result.items[0].id ? {
            ...entry,
            relevanceStatus: decision.relevant ? "RELEVANT" : "IRRELEVANT",
            relevanceScore: decision.score,
            relevanceReason: decision.reason,
            relevanceCheckedAt: decision.checkedAt,
          } : entry),
        }));
      }
      await load();
    } catch (caught) {
      setError(caught.message);
    } finally {
      setTestingSourceId(null);
    }
  };

  const openPublication = async (publication) => {
    setSelectedPublication(publication);
    try {
      setSelectedPublication(await request(`/publications/${publication.id}`));
    } catch (caught) {
      setError(caught.message);
    }
  };

  const retryPublication = async (publication) => {
    try {
      await request(`/publications/${publication.id}/retry`, { method: "POST" });
      setSelectedPublication(null);
      setNotice("发布任务已重试");
      await load();
    } catch (caught) {
      setError(caught.message);
    }
  };

  if (loading && !data.accounts.length) {
    return <div className="content-ops-state">正在载入内容运营中心…</div>;
  }

  return <section className="content-operations-center">
    <UiToast message={error||notice} tone={error ? "error" : "success"} onDismiss={() => { setError(""); setNotice(""); }} />

    <header className="content-ops-hero">
      <div className="content-ops-title">
        <span className="ops-eyebrow">CONTENT OPERATIONS</span>
        <h2>小红书运营控制台</h2>
        <p>从内容采集到小红书发布的完整运行台</p>
      </div>
      <div className={`automation-state ${data.settings.automationEnabled ? "on" : "off"}`}>
        <Robot weight="duotone" aria-hidden="true" />
        <span><b>{data.settings.automationEnabled ? "自动运行中" : "自动化已暂停"}</b><small>每 {data.settings.crawlIntervalMinutes || 240} 分钟采集</small></span>
      </div>
    </header>

    <nav className="content-ops-tabs" aria-label="内容运营分区">
      {TABS.map(([key, label]) => <button
        type="button"
        key={key}
        className={tab === key ? "active" : ""}
        aria-current={tab === key ? "page" : undefined}
        onClick={() => setTab(key)}
      >{label}</button>)}
    </nav>

    <main className="content-ops-workspace">
      {tab === "overview" && <Overview data={data} onAddAccount={() => setDialog("account")} onUpdateAccount={updateAccount} />}

      {tab === "accounts" && <AccountsWorkspace
        accounts={accounts}
        sources={data.sources}
        query={query}
        onQuery={setQuery}
        onAdd={() => setDialog("account")}
        onEdit={(item) => setDialog({ kind: "account", item })}
        onDelete={(item) => setDeleteTarget({ kind: "account", item })}
        onUpdate={updateAccount}
        onError={setError}
        onSaved={() => setNotice("发布规则已保存")}
        onConnected={load}
      />}

      {tab === "sources" && <SourcesWorkspace
        data={data}
        history={history}
        historyQuery={historyQuery}
        historySource={historySource}
        sourceResult={sourceResult}
        testingSourceId={testingSourceId}
        onAddCollector={() => setDialog("collector")}
        onAddSource={() => setDialog("source")}
        onEdit={(kind, item) => setDialog({ kind, item })}
        onDelete={(kind, item) => setDeleteTarget({ kind, item })}
        onTestSource={testSource}
        onClassify={classifyItem}
        onHistoryQuery={setHistoryQuery}
        onHistorySource={setHistorySource}
        onHistorySelect={setSelectedHistory}
      />}

      {tab === "publishing" && <PublicationQueue publications={data.recentPublications} onSelect={openPublication} />}

      {tab === "comments" && <section className="content-ops-panel content-ops-placeholder">
        <Broadcast weight="duotone" aria-hidden="true" />
        <h3>评论维护尚未接入</h3>
        <p>评论链路启用后，这里将只展示其他用户的一级评论，并排除账号自己的评论。</p>
      </section>}

      {tab === "settings" && <div className="content-ops-settings-stack">
        <Settings
          settings={data.settings}
          runs={automationRuns}
          onSaved={async () => { setNotice("自动化设置已保存"); await load(); }}
          onError={setError}
        />
        <GeminiSettings onError={setError} onNotice={setNotice} />
      </div>}
    </main>

    {dialog && <EntityDialog descriptor={dialog} collectionAccounts={data.collectionAccounts} onSubmit={saveEntity} onClose={() => setDialog(null)} />}
    {deleteTarget && <ConfirmDelete target={deleteTarget} onConfirm={removeEntity} onClose={() => setDeleteTarget(null)} />}
    {selectedPublication && <PublicationDetails publication={selectedPublication} onRetry={retryPublication} onClose={() => setSelectedPublication(null)} />}
    {selectedHistory && <CollectionDetails item={selectedHistory} onClose={() => setSelectedHistory(null)} />}
  </section>;
}

function SectionHeading({ eyebrow, title, description, actions }) {
  return <header className="ops-section-heading">
    <div><span className="ops-eyebrow">{eyebrow}</span><h3>{title}</h3>{description && <p>{description}</p>}</div>
    {actions && <div className="ops-heading-actions">{actions}</div>}
  </header>;
}

function StatusBadge({ value }) {
  return <span className={`ops-status ${statusClass(value)}`}>{statusLabel(value)}</span>;
}

function Overview({ data, onAddAccount, onUpdateAccount }) {
  const metrics = [
    ["今日采集", data.counters.collectedToday, Database],
    ["待发布草稿", data.counters.readyDrafts, Clock],
    ["今日发布", data.counters.publishedToday, PaperPlaneTilt],
    ["待回复评论", data.counters.pendingComments, Broadcast],
    ["发布失败", data.counters.failedPublications, XCircle],
  ];
  return <div className="ops-overview">
    <section className="content-ops-kpis" aria-label="今日运营指标">
      {metrics.map(([label, value, Icon]) => <article key={label} className={label === "发布失败" && value > 0 ? "danger" : ""}>
        <Icon aria-hidden="true" /><span>{label}</span><strong>{value || 0}</strong>
      </article>)}
    </section>
    <div className="ops-overview-grid">
      <section className="content-ops-panel ops-pipeline">
        <SectionHeading eyebrow="PIPELINE" title="自动化流水线" description="每一步都有明确输入、输出和停止原因" />
        <ol>
          {[
            ["01", "采集最新内容", "新内容入库并去重", "ready"],
            ["02", "Gemini 相关性判断", "非 AI 内容在这里停止", "ready"],
            ["03", "生成小红书内容", "标题、正文与标签", "ready"],
            ["04", "按账号规则发布", "扫码会话 · 自动文字卡 · 幂等发送", "ready"],
            ["05", "评论监听与回复", "尚未接入评论网页适配器", "blocked"],
          ].map(([index, title, copy, state]) => <li key={index} className={state}>
            <b>{index}</b><span><strong>{title}</strong><small>{copy}</small></span>
          </li>)}
        </ol>
      </section>
      <section className="content-ops-panel ops-account-summary">
        <SectionHeading eyebrow="ACCOUNTS" title="发布账号" description={`${data.accounts.length} 个账号`} actions={<button type="button" onClick={onAddAccount}><Plus />添加账号</button>} />
        <div className="ops-summary-list">
          {data.accounts.length ? data.accounts.map((account) => <AccountSummaryRow key={account.id} account={account} onUpdate={onUpdateAccount} />) : <Empty text="还没有配置小红书账号" />}
        </div>
      </section>
    </div>
  </div>;
}

function AccountSummaryRow({ account, onUpdate }) {
  return <article className="ops-summary-row">
    <span className="platform-mark">小</span>
    <span className="ops-summary-identity"><b>{account.displayName}</b><small>{account.accountHandle || "未填写账号标识"}</small></span>
    <span className="ops-summary-mode">{account.publishMode === "AUTO" ? "全自动" : "手动确认"}</span>
    <StatusBadge value={account.adapterStatus} />
    <label className="native-switch ops-switch"><input type="checkbox" checked={account.enabled} onChange={(event) => onUpdate(account, { publishMode: account.publishMode, enabled: event.target.checked })} /><span>{account.enabled ? "启用" : "停用"}</span></label>
  </article>;
}

function AccountsWorkspace({ accounts, sources, query, onQuery, onAdd, onEdit, onDelete, onUpdate, onError, onSaved, onConnected }) {
  return <section className="content-ops-panel ops-accounts-workspace">
    <SectionHeading eyebrow="XIAOHONGSHU" title="发布账号" description="每个账号独立配置内容源和发布时机" actions={<button type="button" onClick={onAdd}><Plus />添加账号</button>} />
    <UiSearchField className="ops-account-search" value={query} onChange={(event) => onQuery(event.target.value)} placeholder="搜索账号" aria-label="搜索账号" />
    <div className="ops-account-list">
      {accounts.length ? accounts.map((account) => <article className="ops-account-card" key={account.id}>
        <AccountHeader account={account} onUpdate={onUpdate} onEdit={() => onEdit(account)} onDelete={() => onDelete(account)} />
        <div className="ops-account-body">
          <AccountSourceBinding account={account} sources={sources} onError={onError} onSaved={onSaved} />
          <XhsAccountActions account={account} onError={onError} onConnected={onConnected} />
        </div>
      </article>) : <Empty text="没有匹配的账号" />}
    </div>
  </section>;
}

function AccountHeader({ account, onUpdate, onEdit, onDelete }) {
  return <header className="ops-account-header">
    <span className="platform-mark">小</span>
    <span className="ops-account-identity"><b>{account.displayName}</b><small>{account.accountHandle || "未填写账号标识"}</small></span>
    <label className="ops-compact-field"><span>发布模式</span><select value={account.publishMode} onChange={(event) => onUpdate(account, { publishMode: event.target.value, enabled: account.enabled })}><option value="AUTO">全自动</option><option value="MANUAL">手动确认</option></select></label>
    <label className="native-switch ops-switch"><input type="checkbox" checked={account.enabled} onChange={(event) => onUpdate(account, { publishMode: account.publishMode, enabled: event.target.checked })} /><span>{account.enabled ? "启用" : "停用"}</span></label>
    <StatusBadge value={account.adapterStatus} />
    <span className="row-actions"><button type="button" onClick={onEdit}>编辑</button><button type="button" className="danger" onClick={onDelete}>删除</button></span>
  </header>;
}

function SourcesWorkspace({ data, history, historyQuery, historySource, sourceResult, testingSourceId, onAddCollector, onAddSource, onEdit, onDelete, onTestSource, onClassify, onHistoryQuery, onHistorySource, onHistorySelect }) {
  return <div className="ops-sources-stack">
    <section className="content-ops-panel ops-source-management">
      <SectionHeading eyebrow="COLLECTION" title="采集账号与内容源" description="凭据归采集账号管理，内容源只负责指定采集对象" actions={<><button type="button" onClick={onAddCollector}><Plus />添加采集账号</button><button type="button" className="primary" onClick={onAddSource} disabled={!data.collectionAccounts.length}><Plus />添加内容来源</button></>} />

      <div className="ops-subsection">
        <header><h4>采集账号</h4><span>{data.collectionAccounts.length}</span></header>
        <div className="ops-collector-grid" aria-label="采集账号">
          {data.collectionAccounts.length ? data.collectionAccounts.map((account) => <article key={account.id}>
            <span className="source-platform">X</span>
            <span><b>{account.displayName}</b><small>{account.adapterType === "TWITTER_WEB" ? "Cookie 登录" : "官方 API"}</small></span>
            <StatusBadge value={account.credentialConfigured ? account.enabled ? "READY" : "NOT_CONFIGURED" : "NOT_CONFIGURED"} />
            <span className="row-actions"><button type="button" onClick={() => onEdit("collector", account)}>编辑</button><button type="button" className="danger" onClick={() => onDelete("collector", account)}>删除</button></span>
          </article>) : <p className="ops-inline-empty">先添加一个 X 采集账号，Cookie 只需配置一次。</p>}
        </div>
      </div>

      <div className="ops-subsection">
        <header><h4>内容来源</h4><span>{data.sources.length}</span></header>
        <div className="ops-data-list ops-source-list">
          <div className="ops-list-head" aria-hidden="true"><span>来源</span><span>采集对象</span><span>采集账号</span><span>状态</span><span>操作</span></div>
          {data.sources.length ? data.sources.map((source) => <article className="source-row" key={source.id}>
            <span className="ops-source-name"><span className="source-platform">X</span><b>{source.name}</b></span>
            <span>{source.adapterType === "TWITTER_WEB" ? `@${source.externalHandle}` : `UID ${source.externalUid}`}</span>
            <span>{source.collectionAccountName || "未绑定"}</span>
            <StatusBadge value={source.lastStatus} />
            <span className="ops-source-actions"><button type="button" onClick={() => onTestSource(source)} disabled={testingSourceId === source.id}>{testingSourceId === source.id ? "拉取并判断中…" : "测试拉取并判断"}</button><button type="button" onClick={() => onEdit("source", source)}>编辑</button><button type="button" className="danger" onClick={() => onDelete("source", source)}>删除</button></span>
          </article>) : <Empty text="还没有内容采集源" />}
        </div>
      </div>

      {sourceResult && <SourcePreview result={sourceResult} onClassify={onClassify} />}
    </section>

    <CollectionHistory items={history} sources={data.sources} query={historyQuery} sourceId={historySource} onQuery={onHistoryQuery} onSource={onHistorySource} onSelect={onHistorySelect} />
  </div>;
}

function AccountSourceBinding({ account, sources, onError, onSaved }) {
  const [rules, setRules] = useState([]);
  const [saving, setSaving] = useState(false);
  const [loaded, setLoaded] = useState(false);

  useEffect(() => {
    setLoaded(false);
    request(`/accounts/${account.id}/source-rules`).then((value) => {
      setRules((value || []).map((rule) => ({ ...rule, sourceId: Number(rule.sourceId) })));
      setLoaded(true);
    }).catch((caught) => onError(caught.message));
  }, [account.id, onError]);

  const ruleFor = (id) => rules.find((rule) => rule.sourceId === id);
  const change = (sourceId, patch) => setRules((current) => {
    const existing = current.find((rule) => rule.sourceId === sourceId) || { sourceId, enabled: true, publishTiming: "IMMEDIATE", publishIntervalMinutes: 30 };
    return [...current.filter((rule) => rule.sourceId !== sourceId), { ...existing, ...patch }];
  });
  const toggle = (sourceId) => ruleFor(sourceId)
    ? setRules((current) => current.filter((rule) => rule.sourceId !== sourceId))
    : change(sourceId, { enabled: true });
  const save = async () => {
    setSaving(true);
    try {
      await request(`/accounts/${account.id}/sources`, { method: "PUT", body: JSON.stringify({ rules }) });
      onSaved?.();
    } catch (caught) {
      onError(caught.message);
    } finally {
      setSaving(false);
    }
  };

  return <section className="account-source-binding" aria-label={`${account.displayName}发布规则`}>
    <header><div><h4>内容源发布规则</h4><p>选择这个账号要消费的来源</p></div><button type="button" onClick={save} disabled={saving || !loaded}>{saving ? "保存中…" : "保存发布规则"}</button></header>
    {!loaded ? <p className="ops-inline-empty">读取中…</p> : sources.length ? <div className="account-source-rules">
      {sources.map((source) => {
        const rule = ruleFor(source.id);
        return <article key={source.id}>
          <label className="source-rule-check"><input type="checkbox" checked={Boolean(rule)} onChange={() => toggle(source.id)} /><span>{source.name}</span></label>
          <select aria-label={`${source.name}发布时机`} value={rule?.publishTiming || "IMMEDIATE"} onChange={(event) => change(source.id, { publishTiming: event.target.value })} disabled={!rule}><option value="IMMEDIATE">入库后立即发布</option><option value="INTERVAL">按间隔检查并发布</option></select>
          {rule?.publishTiming === "INTERVAL" && <label className="rule-interval"><input aria-label={`${source.name}发布间隔`} type="number" min="1" value={rule.publishIntervalMinutes || 30} onChange={(event) => change(source.id, { publishIntervalMinutes: Number(event.target.value) })} /><span>分钟</span></label>}
        </article>;
      })}
    </div> : <p className="ops-inline-empty">请先添加内容源</p>}
  </section>;
}

function XhsAccountActions({ account, onError, onConnected }) {
  const [login, setLogin] = useState(null);
  const [starting, setStarting] = useState(false);
  const [testing, setTesting] = useState(false);
  const [results, setResults] = useState(null);

  useEffect(() => {
    if (!login?.sessionId || login.status !== "WAITING_SCAN") return undefined;
    let cancelled = false;
    let timer;
    const poll = async () => {
      try {
        const next = await request(`/accounts/${account.id}/xhs-login/${login.sessionId}`);
        if (cancelled) return;
        setLogin((previous) => ({ ...previous, ...next, qrImageDataUrl: next.qrImageDataUrl || previous?.qrImageDataUrl }));
        if (next.status === "CONNECTED") await onConnected();
        else if (next.status === "WAITING_SCAN") timer = setTimeout(poll, 2000);
      } catch (caught) {
        if (!cancelled) onError(caught.message);
      }
    };
    timer = setTimeout(poll, 2000);
    return () => { cancelled = true; clearTimeout(timer); };
  }, [account.id, login?.sessionId, login?.status, onConnected, onError]);

  const start = async () => {
    setStarting(true);
    setResults(null);
    try { setLogin(await request(`/accounts/${account.id}/xhs-login`, { method: "POST" })); }
    catch (caught) { onError(caught.message); }
    finally { setStarting(false); }
  };

  const test = async () => {
    setTesting(true);
    setResults(null);
    try {
      setResults(await request(`/accounts/${account.id}/test-pipeline`, { method: "POST" }));
      await onConnected();
    } catch (caught) {
      onError(caught.message);
    } finally {
      setTesting(false);
    }
  };

  const waiting = login?.status === "WAITING_SCAN";
  return <section className="xhs-account-actions" aria-label={`${account.displayName} 小红书连接与测试`}>
    <header><div><h4>连接与链路测试</h4><p>{account.sessionConfigured ? `会话已配置${account.lastConnectedAt ? ` · ${formatTime(account.lastConnectedAt)}` : ""}` : "扫码后才能执行真实发布链路"}</p></div><StatusBadge value={account.sessionConfigured ? "READY" : "NOT_CONFIGURED"} /></header>
    <div className="ops-action-row"><button type="button" onClick={start} disabled={starting || waiting}>{starting ? "正在打开扫码页…" : waiting ? "等待扫码确认…" : account.sessionConfigured ? "重新扫码登录" : "扫码登录小红书"}</button><button type="button" className="primary" onClick={test} disabled={testing || !account.sessionConfigured}>{testing ? "拉取、判断并发布中…" : "一键测试小红书"}</button></div>
    {login?.qrImageDataUrl && waiting && <div className="xhs-qr"><img src={login.qrImageDataUrl} alt="小红书登录二维码" /><span>{login.message || "请用小红书 App 扫码并在手机上确认"}</span></div>}
    {login?.status === "CONNECTED" && <p className="xhs-success">扫码登录成功，会话已加密保存。</p>}
    {login?.status === "EXPIRED" && <p className="xhs-expired">{login.message || "扫码会话已过期，请重新发起。"}</p>}
    {results && <div className="pipeline-test-results">{results.map((result) => <article key={`${result.sourceId}-${result.contentItemId || "none"}`}><b>{({ FILTERED: "已过滤", ALREADY_EXISTS: "已去重", PUBLISHED: "已发布", UNCERTAIN: "待确认", NO_CONTENT: "无内容" })[result.result] || result.result}</b><span>{result.message}</span>{result.draft && <small>{result.draft.title}</small>}</article>)}</div>}
  </section>;
}

function PublicationQueue({ publications, onSelect }) {
  const active = publications.filter((publication) => publication.status !== "PUBLISHED");
  const completed = publications.filter((publication) => publication.status === "PUBLISHED");
  return <section className="content-ops-panel publication-queue">
    <SectionHeading eyebrow="QUEUE" title="发布队列" description="任务、发布账号和执行结果在同一行展示" />
    <PublicationGroup title="发送中与待处理" items={active} onSelect={onSelect} />
    <PublicationGroup title="已完成" items={completed} onSelect={onSelect} />
    {!publications.length && <Empty text="发布队列为空；自动任务生成后会在这里显示" />}
  </section>;
}

function PublicationGroup({ title, items, onSelect }) {
  return <section className="publication-group">
    <header><h4>{title}</h4><span>{items.length}</span></header>
    {items.length ? <div className="ops-data-list">
      {items.map((publication) => <button type="button" className="publication-row" key={publication.id} onClick={() => onSelect(publication)} aria-label={`查看${publication.title}发布详情`}>
        <span><b>{publication.title}</b><small>#{publication.id} · {publication.publishMode === "AUTO" ? "自动" : "手动"} · 尝试 {publication.attemptCount} 次</small></span>
        <strong className="publication-account">{publication.accountName || "未指定账号"}</strong>
        <StatusBadge value={publication.status} />
        <span className="ops-row-link">查看详情</span>
      </button>)}
    </div> : <p className="publication-group-empty">暂无任务</p>}
  </section>;
}

function CollectionHistory({ items, sources, query, sourceId, onQuery, onSource, onSelect }) {
  return <section className="content-ops-panel collection-history">
    <SectionHeading eyebrow="COLLECTION HISTORY" title="采集历史" description="查询已入库内容和 Gemini 判断结果" actions={<div className="collection-history-filters"><UiSearchField value={query} onChange={(event) => onQuery(event.target.value)} placeholder="查询作者、来源或正文" aria-label="查询采集历史" /><select aria-label="按采集源筛选" value={sourceId} onChange={(event) => onSource(event.target.value)}><option value="">全部来源</option>{sources.map((source) => <option key={source.id} value={source.id}>{source.name}</option>)}</select></div>} />
    <div className="collection-history-list">
      {items.length ? <div className="collection-history-head" aria-hidden="true"><span>作者 / 来源</span><span>内容摘要</span><span>采集时间</span><span>判断</span><span>操作</span></div> : null}
      {items.length ? items.map((item) => <button type="button" key={item.id} className="collection-history-row" onClick={() => onSelect(item)} aria-label={`查看采集内容 ${item.rawText}`}>
        <span className="collection-history-identity"><b>{item.authorName || item.sourceName}</b><small>{item.sourceName}</small></span>
        <p>{item.rawText}</p>
        <time dateTime={item.collectedAt}>{formatTime(item.collectedAt)}</time>
        <StatusBadge value={item.relevanceStatus} />
        <span className="ops-row-link">详情</span>
      </button>) : <p className="publication-group-empty">没有匹配的采集记录</p>}
    </div>
  </section>;
}

function Settings({ settings, runs, onSaved, onError }) {
  const submit = async (event) => {
    event.preventDefault();
    const values = Object.fromEntries(new FormData(event.currentTarget));
    values.automationEnabled = values.automationEnabled === "on";
    values.crawlIntervalMinutes = Number(values.crawlIntervalMinutes);
    values.commentIntervalMinutes = Number(values.commentIntervalMinutes);
    try {
      await request("/settings", { method: "PUT", body: JSON.stringify(values) });
      await onSaved();
    } catch (caught) {
      onError(caught.message);
    }
  };

  return <section className="content-ops-panel settings-form">
    <SectionHeading eyebrow="ORCHESTRATION" title="自动化运行" description="采集只负责入库，发送时机由每个发布账号单独设置" actions={<GearSix aria-hidden="true" />} />
    <form className="settings-inline" onSubmit={submit}>
      <label className="automation-toggle native-switch"><input name="automationEnabled" type="checkbox" defaultChecked={settings.automationEnabled} /><span>自动运行</span></label>
      <label className="ops-compact-field"><span>默认发布</span><select name="defaultPublishMode" defaultValue={settings.defaultPublishMode || "AUTO"}><option value="AUTO">全自动</option><option value="MANUAL">手动确认</option></select></label>
      <label className="ops-compact-field"><span>采集周期</span><span className="number-with-unit"><input aria-label="内容采集周期" name="crawlIntervalMinutes" type="number" min="1" defaultValue={settings.crawlIntervalMinutes || 240} /><span>分钟</span></span></label>
      <input name="commentIntervalMinutes" type="hidden" value={settings.commentIntervalMinutes || 30} />
      <button type="submit">保存</button>
    </form>
    <p className="ops-settings-note">评论链路尚未接入，因此不展示无效配置 · 最后更新：{settings.updatedAt ? formatTime(settings.updatedAt) : "尚未保存"}</p>
    <AutomationRuns runs={runs} />
  </section>;
}

function AutomationRuns({ runs }) {
  return <section className="automation-runs">
    <header><div><h4>最近自动运行</h4><p>采集和发送分别记录；未生成任务时显示停止原因</p></div><span>{runs.length}</span></header>
    <div className="ops-data-list">
      {runs.length ? runs.slice(0, 8).map((run) => <article key={run.id}>
        <StatusBadge value={run.status} />
        <time>{formatTime(run.startedAt)}</time>
        <span>{runMessage(run)}</span>
      </article>) : <p className="publication-group-empty">暂无自动运行记录</p>}
    </div>
  </section>;
}

function GeminiSettings({ onError, onNotice }) {
  const [config, setConfig] = useState(null);
  const [testing, setTesting] = useState(false);
  const [showKey, setShowKey] = useState(false);

  useEffect(() => { request("/ai-config").then(setConfig).catch((caught) => onError(caught.message)); }, [onError]);
  if (!config) return <section className="content-ops-panel">正在读取 Gemini 配置…</section>;

  const submit = async (event) => {
    event.preventDefault();
    const form = event.currentTarget;
    const values = Object.fromEntries(new FormData(form));
    values.enabled = values.enabled === "on";
    values.temperature = Number(values.temperature);
    values.maxOutputTokens = Number(values.maxOutputTokens);
    try {
      setConfig(await request("/ai-config", { method: "PUT", body: JSON.stringify(values) }));
      form.elements.apiKey.value = "";
      onNotice("Gemini 配置已保存");
    } catch (caught) {
      onError(caught.message);
    }
  };

  const test = async () => {
    setTesting(true);
    try {
      const result = await request("/ai-config/test", { method: "POST" });
      onNotice(`${result.text} · ${result.latencyMs}ms`);
    } catch (caught) {
      onError(caught.message);
    } finally {
      setTesting(false);
    }
  };

  return <form className="content-ops-panel gemini-form" onSubmit={submit}>
    <SectionHeading eyebrow="GEMINI PROVIDER" title="Gemini 内容生成" description="相关性判断、内容改写和评论回复共用此配置" actions={<span className={`gemini-key ${config.apiKeyConfigured ? "ready" : ""}`}>{config.apiKeyConfigured ? `密钥已配置 ${config.apiKeyHint || ""}` : "密钥未配置"}</span>} />
    <label className="gemini-enable native-switch"><input name="enabled" type="checkbox" defaultChecked={config.enabled} /><span><b>启用 Gemini</b><small>关闭后不会执行判断和生成</small></span></label>
    <div className="gemini-layout">
      <fieldset className="gemini-section"><legend>连接与参数</legend>
        <div className="gemini-provider-grid">
          <label className="gemini-api-key">API Key<span className="secret-input"><input name="apiKey" type={showKey ? "text" : "password"} autoComplete="new-password" placeholder={config.apiKeyConfigured ? "留空则保留现有密钥" : "输入 Gemini API Key"} /><button type="button" onClick={() => setShowKey((value) => !value)}>{showKey ? "隐藏" : "显示"}</button></span><small>后端加密保存，页面不会读取现有明文</small></label>
          <label>模型<input name="model" required defaultValue={config.model} /></label>
          <label>API 地址<input name="apiBaseUrl" type="url" required defaultValue={config.apiBaseUrl} /></label>
          <div className="gemini-parameter-grid">
            <label>生成温度<input name="temperature" type="number" min="0" max="2" step="0.05" required defaultValue={config.temperature} /><small>0 更稳定，2 更发散</small></label>
            <label>最大输出 Token<input name="maxOutputTokens" type="number" min="128" max="65536" required defaultValue={config.maxOutputTokens} /><small>限制单次生成长度</small></label>
          </div>
        </div>
      </fieldset>
      <fieldset className="gemini-section gemini-prompt-section"><legend>提示词模板</legend>
        <div className="gemini-prompt-grid">
          <label>AI 内容相关性判断提示词<textarea name="relevancePrompt" required minLength="20" maxLength="12000" defaultValue={config.relevancePrompt} /><small>非 AI 内容不会继续生成和发布</small></label>
          <label>小红书内容改写提示词<textarea name="contentRewritePrompt" required minLength="20" maxLength="12000" defaultValue={config.contentRewritePrompt} /></label>
          <label>评论回复提示词<textarea name="commentReplyPrompt" required minLength="20" maxLength="12000" defaultValue={config.commentReplyPrompt} /></label>
        </div>
      </fieldset>
    </div>
    <footer><button type="submit">保存 Gemini 配置</button><button type="button" onClick={test} disabled={testing || !config.enabled || !config.apiKeyConfigured}>{testing ? "测试中…" : "测试连接"}</button></footer>
  </form>;
}

function SourcePreview({ result, onClassify }) {
  return <section className="source-preview">
    <header><div><span className="ops-eyebrow">FETCH RESULT</span><h4>最新拉取结果</h4></div><p>读取 {result.fetchedCount} 条 · 新增 {result.newCount} 条</p></header>
    {result.items?.length ? result.items.map((item) => <article key={item.id}>
      <header><b>{item.authorName}</b><time>{formatTime(item.publishedAt)}</time></header>
      <p>{item.rawText}</p>
      <footer><StatusBadge value={item.relevanceStatus} />{item.relevanceScore != null && <small>相关度 {Math.round(Number(item.relevanceScore) * 100)}%</small>}{item.relevanceReason && <small>{item.relevanceReason}</small>}<button type="button" onClick={() => onClassify(item)}>用 Gemini 判断</button><a href={item.sourceUrl} target="_blank" rel="noreferrer">查看原推文</a></footer>
    </article>) : <Empty text="这个来源暂时没有可展示的内容" />}
  </section>;
}

function EntityDialog({ descriptor, collectionAccounts, onSubmit, onClose }) {
  const kind = typeof descriptor === "string" ? descriptor : descriptor.kind;
  const item = typeof descriptor === "string" ? null : descriptor.item;
  const title = `${item ? "编辑" : "添加"}${kind === "account" ? "小红书账号" : kind === "collector" ? " X 采集账号" : "内容来源"}`;
  return <div className="content-ops-dialog-backdrop">
    <form className="content-ops-dialog" role="dialog" aria-modal="true" aria-labelledby="content-ops-dialog-title" onSubmit={(event) => onSubmit(event, descriptor)}>
      <header><div><span className="ops-eyebrow">CONFIGURATION</span><h3 id="content-ops-dialog-title">{title}</h3></div><button type="button" autoFocus onClick={onClose} aria-label="关闭"><X /></button></header>
      <div className="ops-dialog-fields">
        {kind === "account" ? <>
          <label>显示名称<input name="displayName" required maxLength="100" defaultValue={item?.displayName || ""} /></label>
          <label>账号标识<input name="accountHandle" maxLength="120" defaultValue={item?.accountHandle || ""} /></label>
          <label>发布模式<select name="publishMode" defaultValue={item?.publishMode || "AUTO"}><option value="AUTO">全自动</option><option value="MANUAL">手动确认</option></select></label>
          {item && <label className="native-switch ops-switch"><input name="enabled" type="checkbox" defaultChecked={item.enabled} /><span>启用账号</span></label>}
        </> : kind === "collector" ? <CollectionAccountFormFields item={item} /> : <SourceFormFields collectionAccounts={collectionAccounts} item={item} />}
      </div>
      <footer><button type="button" onClick={onClose}>取消</button><button type="submit">保存配置</button></footer>
    </form>
  </div>;
}

function ConfirmDelete({ target, onConfirm, onClose }) {
  const name = target.item.displayName || target.item.name;
  return <div className="content-ops-dialog-backdrop">
    <section className="content-ops-dialog confirm-delete" role="alertdialog" aria-modal="true" aria-labelledby="confirm-delete-title">
      <header><h3 id="confirm-delete-title">确认删除</h3><button type="button" autoFocus onClick={onClose} aria-label="关闭"><X /></button></header>
      <p>删除“{name}”后不会删除已有采集和发布历史，但它将不再参与自动任务。</p>
      <footer><button type="button" onClick={onClose}>取消</button><button type="button" className="danger" onClick={onConfirm}>确认删除</button></footer>
    </section>
  </div>;
}

function PublicationDetails({ publication, onRetry, onClose }) {
  let tags = [];
  try { tags = Array.isArray(publication.tagsJson) ? publication.tagsJson : JSON.parse(publication.tagsJson || "[]"); } catch { tags = []; }
  return <div className="content-ops-dialog-backdrop">
    <section className="content-ops-dialog publication-details" role="dialog" aria-modal="true" aria-labelledby="publication-details-title">
      <header><div><span className="ops-eyebrow">PUBLICATION #{publication.id}</span><h3 id="publication-details-title">发布任务详情</h3></div><button type="button" autoFocus onClick={onClose} aria-label="关闭"><X /></button></header>
      <dl>
        <Detail label="标题" value={publication.title} /><Detail label="发布账号" value={publication.accountName} /><Detail label="状态" value={statusLabel(publication.status)} /><Detail label="执行次数" value={publication.attemptCount} /><Detail label="计划时间" value={formatTime(publication.scheduledAt)} /><Detail label="完成时间" value={formatTime(publication.publishedAt)} />
        {publication.sourceName && <Detail label="采集来源" value={`${publication.sourceName} · ${publication.sourceAuthor || "—"}`} />}
        {publication.modelName && <Detail label="生成模型" value={publication.modelName} />}
        <Detail wide label="发布正文" value={publication.body || "详情读取中…"} />
        {tags.length > 0 && <Detail wide label="标签" value={tags.map((tag) => `#${tag}`).join("  ")} />}
        {publication.sourceText && <Detail wide label="采集原文" value={publication.sourceText} />}
        {publication.errorCode && <Detail label="错误代码" value={publication.errorCode} />}
        <Detail wide label="执行结果" value={publication.errorMessage || publication.externalPostUrl || "当前任务没有错误信息"} />
      </dl>
      <footer>{publication.sourceUrl && <a href={publication.sourceUrl} target="_blank" rel="noreferrer">查看采集原文</a>}{publication.status === "FAILED" && <button type="button" className="primary" onClick={() => onRetry(publication)}><ArrowClockwise />重试发布</button>}<button type="button" onClick={onClose}>关闭</button></footer>
    </section>
  </div>;
}

function CollectionDetails({ item, onClose }) {
  return <div className="content-ops-dialog-backdrop">
    <section className="content-ops-dialog collection-details" role="dialog" aria-modal="true" aria-labelledby="collection-details-title">
      <header><div><span className="ops-eyebrow">COLLECTION #{item.id}</span><h3 id="collection-details-title">采集内容详情</h3></div><button type="button" autoFocus onClick={onClose} aria-label="关闭"><X /></button></header>
      <dl><Detail label="采集来源" value={item.sourceName} /><Detail label="作者" value={item.authorName || "—"} /><Detail label="发布时间" value={formatTime(item.publishedAt)} /><Detail label="采集时间" value={formatTime(item.collectedAt)} /><Detail label="相关性" value={`${statusLabel(item.relevanceStatus)}${item.relevanceScore != null ? ` · ${Math.round(Number(item.relevanceScore) * 100)}%` : ""}`} />{item.relevanceReason && <Detail label="判断原因" value={item.relevanceReason} />}<Detail wide label="采集正文" value={item.rawText} /></dl>
      <footer>{item.sourceUrl && <a href={item.sourceUrl} target="_blank" rel="noreferrer">查看原文</a>}<button type="button" onClick={onClose}>关闭</button></footer>
    </section>
  </div>;
}

function Detail({ label, value, wide = false }) {
  return <div className={wide ? "ops-detail-wide" : ""}><dt>{label}</dt><dd>{value ?? "—"}</dd></div>;
}

function SourceFormFields({ collectionAccounts, item }) {
  const [accountId, setAccountId] = useState(String(item?.collectionAccountId || collectionAccounts[0]?.id || ""));
  const selected = collectionAccounts.find((account) => String(account.id) === accountId);
  const adapter = item?.adapterType || selected?.adapterType;
  return <>
    <input type="hidden" name="platform" value="TWITTER" />
    {!item && <label>采集账号<select aria-label="采集账号" name="collectionAccountId" required value={accountId} onChange={(event) => setAccountId(event.target.value)}>{collectionAccounts.map((account) => <option key={account.id} value={account.id}>{account.displayName} · {account.adapterType === "TWITTER_WEB" ? "Cookie" : "API"}</option>)}</select><small>凭据从采集账号读取，不需要再次粘贴 Cookie。</small></label>}
    <label>来源名称<input name="name" required maxLength="120" placeholder="例如：Elon Musk" defaultValue={item?.name || ""} /></label>
    {adapter === "TWITTER_API" ? <label>Twitter UID<input name="externalUid" required inputMode="numeric" pattern="[0-9]+" maxLength="30" placeholder="数字用户 ID" defaultValue={item?.externalUid || ""} /></label> : <label>Twitter 用户名<input aria-label="Twitter 用户名" name="externalHandle" required pattern="@?[A-Za-z0-9_]{1,15}" maxLength="16" placeholder="例如：elonmusk" defaultValue={item?.externalHandle || ""} /><small>可填写 elonmusk 或 @elonmusk。</small></label>}
    {item && <label className="native-switch ops-switch"><input name="enabled" type="checkbox" defaultChecked={item.enabled} /><span>启用内容源</span></label>}
    <small>采集周期使用“自动化设置”中的全局周期。</small>
  </>;
}

function CollectionAccountFormFields({ item }) {
  const [adapter, setAdapter] = useState(item?.adapterType || "TWITTER_WEB");
  return <>
    <label>账号名称<input name="displayName" required maxLength="100" placeholder="例如：我的 X 采集账号" defaultValue={item?.displayName || ""} /></label>
    {item ? <>
      <label className="native-switch ops-switch"><input name="enabled" type="checkbox" defaultChecked={item.enabled} /><span>启用采集账号</span></label>
      {adapter === "TWITTER_WEB" ? <label>更新 X Cookie<textarea aria-label="更新 X Cookie" name="accessToken" maxLength="20000" spellCheck="false" placeholder="留空则保留现有 Cookie" /><small>Cookie 失效时在这里替换；留空不会清除现有凭据。</small></label> : <label>更新 Bearer Token<input aria-label="更新 Bearer Token" name="accessToken" type="password" autoComplete="new-password" placeholder="留空则保留现有 Token" /></label>}
    </> : <>
      <label>采集方式<select aria-label="采集方式" name="adapterType" value={adapter} onChange={(event) => setAdapter(event.target.value)}><option value="TWITTER_WEB">X 登录 Cookie（免 API Key）</option><option value="TWITTER_API">官方 API Bearer</option></select></label>
      {adapter === "TWITTER_WEB" ? <label>X Cookie<textarea aria-label="X Cookie" name="accessToken" required maxLength="20000" spellCheck="false" placeholder="# Netscape HTTP Cookie File&#10;.x.com  TRUE  /  TRUE  ...  auth_token  ...&#10;.x.com  TRUE  /  TRUE  ...  ct0  ..." /><small>完整 Cookie 只配置一次，之后内容源直接绑定此账号。</small></label> : <label>Bearer Token<input aria-label="Bearer Token" name="accessToken" type="password" required autoComplete="new-password" /><small>保存后可供多个内容源复用。</small></label>}
    </>}
  </>;
}

function Empty({ text }) {
  return <div className="content-ops-empty"><Broadcast weight="duotone" aria-hidden="true" /><p>{text}</p></div>;
}
