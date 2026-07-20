import { useCallback, useEffect, useState } from "react";
import { Key, LinkSimple, PencilSimple, Plus, ShieldCheck, Trash, UserCircle, X } from "@phosphor-icons/react";
import PlatformBrandIcon from "./PlatformBrandIcon";
import UiSearchField from "./UiSearchField";
import UiToast from "./UiToast";
import { readJsonResponse } from "./apiResponse";
import "./PlatformAccountCenter.css";

const PLATFORM_LABELS = { X: "X", XIAOHONGSHU: "小红书", DOUYIN: "抖音", GEMINI: "Gemini" };
const STATUS_LABELS = { NOT_CONFIGURED: "未配置", PENDING_LOGIN: "等待登录", CONNECTED: "已连接", EXPIRED: "已过期", ERROR: "异常", DISABLED: "已停用" };
const PLATFORM_DEFAULTS = {
  X: { adapterType: "X_WEB", secretType: "COOKIE" },
  XIAOHONGSHU: { adapterType: "XIAOHONGSHU_WEB", secretType: "STORAGE_STATE" },
  DOUYIN: { adapterType: "DOUYIN_WEB", secretType: "STORAGE_STATE" },
  GEMINI: { adapterType: "GEMINI_API", secretType: "API_KEY" },
};

async function request(path, options = {}) {
  const response = await fetch(path, { headers: { "Content-Type": "application/json", ...(options.headers || {}) }, ...options });
  const result = await readJsonResponse(response, "账号中心响应异常");
  if (!response.ok || result.code !== 200) throw new Error(result.message || `请求失败 · ${response.status}`);
  return result.data;
}

function makeForm(account) {
  if (!account) return { platform: "X", displayName: "", accountHandle: "", adapterType: "X_WEB", publicConfigJson: "", secretType: "COOKIE", secret: "", enabled: true };
  const defaults = PLATFORM_DEFAULTS[account.platform] || PLATFORM_DEFAULTS.X;
  return {
    platform: account.platform,
    displayName: account.displayName || "",
    accountHandle: account.accountHandle || "",
    adapterType: account.adapterType || defaults.adapterType,
    publicConfigJson: account.publicConfigJson || "",
    secretType: account.credentialTypes?.[0] || defaults.secretType,
    secret: "",
    enabled: account.enabled !== false,
  };
}

export default function PlatformAccountCenter() {
  const [query, setQuery] = useState("");
  const [platform, setPlatform] = useState("");
  const [status, setStatus] = useState("");
  const [accounts, setAccounts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [editor, setEditor] = useState(null);
  const [form, setForm] = useState(makeForm());
  const [saving, setSaving] = useState(false);
  const [usageAccount, setUsageAccount] = useState(null);
  const [usages, setUsages] = useState([]);
  const [confirmDelete, setConfirmDelete] = useState(null);
  const [login, setLogin] = useState(null);
  const [validatingId, setValidatingId] = useState(null);

  const load = useCallback(async (signal) => {
    setLoading(true);
    try {
      const params = new URLSearchParams({ page: "1", pageSize: "30" });
      if (query.trim()) params.set("query", query.trim());
      if (platform) params.set("platform", platform);
      if (status) params.set("status", status);
      const data = await request(`/api/platform-accounts?${params}`, { signal });
      setAccounts(data?.items || []);
      setError("");
    } catch (exception) {
      if (exception.name !== "AbortError") setError(exception.message);
    } finally {
      if (!signal?.aborted) setLoading(false);
    }
  }, [query, platform, status]);

  useEffect(() => {
    const controller = new AbortController();
    const timer = setTimeout(() => load(controller.signal), 300);
    return () => { clearTimeout(timer); controller.abort(); };
  }, [load]);

  useEffect(() => {
    if (!login?.sessionId || login.status !== "WAITING_SCAN") return undefined;
    let stopped = false;
    let timer;
    const poll = async () => {
      try {
        const next = await request(`/api/platform-accounts/${login.account.id}/login/${login.sessionId}`);
        if (stopped) return;
        setLogin((current) => current ? { ...current, ...next } : current);
        if (next.status === "CONNECTED") {
          setNotice("扫码登录成功");
          await load();
        } else if (next.status === "WAITING_SCAN") timer = setTimeout(poll, 1800);
      } catch (exception) {
        if (!stopped) setError(exception.message);
      }
    };
    timer = setTimeout(poll, 1800);
    return () => { stopped = true; clearTimeout(timer); };
  }, [login?.account?.id, login?.sessionId, login?.status, load]);

  const openCreate = () => { setForm(makeForm()); setEditor({ mode: "create" }); };
  const openEdit = (account) => { setForm(makeForm(account)); setEditor({ mode: "edit", account }); };
  const closeEditor = () => { if (!saving) setEditor(null); };
  const changePlatform = (value) => setForm((current) => ({
    ...current,
    platform: value,
    ...PLATFORM_DEFAULTS[value],
    publicConfigJson: value === "GEMINI" ? '{"apiBaseUrl":"https://generativelanguage.googleapis.com"}' : "",
  }));

  const save = async (event) => {
    event.preventDefault();
    setSaving(true);
    try {
      const payload = {
        displayName: form.displayName,
        accountHandle: form.accountHandle || null,
        adapterType: form.adapterType,
        publicConfigJson: form.publicConfigJson || null,
        enabled: form.enabled,
      };
      let account;
      if (editor.mode === "create") {
        account = await request("/api/platform-accounts", { method: "POST", body: JSON.stringify({ platform: form.platform, ...payload }) });
      } else {
        account = await request(`/api/platform-accounts/${editor.account.id}`, { method: "PUT", body: JSON.stringify(payload) });
      }
      if (form.secret.trim()) {
        await request(`/api/platform-accounts/${account.id}/secrets/${form.secretType}`, {
          method: "PUT",
          body: JSON.stringify({ value: form.secret, hint: secretHint(form.secretType, form.secret) }),
        });
      }
      setEditor(null);
      setNotice(editor.mode === "create" ? "账号已保存" : "账号已更新");
      await load();
    } catch (exception) {
      setError(exception.message);
    } finally {
      setSaving(false);
    }
  };

  const showUsages = async (account) => {
    setUsageAccount(account);
    try { setUsages(await request(`/api/platform-accounts/${account.id}/usages`) || []); }
    catch (exception) { setError(exception.message); setUsages([]); }
  };

  const prepareDelete = async () => {
    const account = editor.account;
    try {
      const currentUsages = await request(`/api/platform-accounts/${account.id}/usages`) || [];
      if (currentUsages.length) {
        setUsageAccount(account);
        setUsages(currentUsages);
        setError("账号仍被业务模块使用，不能删除");
        setEditor(null);
        return;
      }
      setConfirmDelete(account);
    } catch (exception) { setError(exception.message); }
  };

  const remove = async () => {
    if (!confirmDelete) return;
    try {
      await request(`/api/platform-accounts/${confirmDelete.id}`, { method: "DELETE" });
      setConfirmDelete(null);
      setEditor(null);
      setNotice("账号已删除");
      await load();
    } catch (exception) { setError(exception.message); }
  };

  const validate = async (account) => {
    setValidatingId(account.id);
    try {
      await request(`/api/platform-accounts/${account.id}/validate`, { method: "POST" });
      setNotice(`${account.displayName} 验证成功`);
      await load();
    } catch (exception) { setError(exception.message); }
    finally { setValidatingId(null); }
  };

  const startLogin = async (account) => {
    try {
      const session = await request(`/api/platform-accounts/${account.id}/login`, { method: "POST" });
      setLogin({ account, ...session });
      if (session.status === "CONNECTED") { setNotice("账号已连接"); await load(); }
    } catch (exception) { setError(exception.message); }
  };

  const social = accounts.filter((item) => item.accountKind === "SOCIAL");
  const apis = accounts.filter((item) => item.accountKind === "API_SERVICE");

  return <section className="platform-account-center" aria-label="账号中心">
    <header className="account-center-hero">
      <div><span>ACCOUNT VAULT</span><h2>账号中心</h2><p>跨设备保存平台会话和 API Key，敏感信息只显示脱敏提示。</p></div>
      <button type="button" className="account-primary" onClick={openCreate}><Plus />新增账号</button>
    </header>
    <div className="account-toolbar">
      <UiSearchField aria-label="搜索账号" type="search" value={query} onChange={(event) => setQuery(event.target.value)} placeholder="搜索名称或 Handle" />
      <select aria-label="按平台筛选" value={platform} onChange={(event) => setPlatform(event.target.value)}><option value="">全部平台</option>{Object.entries(PLATFORM_LABELS).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select>
      <select aria-label="按连接状态筛选" value={status} onChange={(event) => setStatus(event.target.value)}><option value="">全部状态</option>{Object.entries(STATUS_LABELS).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select>
    </div>
    <div className="account-sections">
      <AccountSection title="平台账号" icon={<UserCircle />} items={social} loading={loading} onEdit={openEdit} onUsages={showUsages} onValidate={validate} onLogin={startLogin} validatingId={validatingId} />
      <AccountSection title="API 服务" icon={<Key />} items={apis} loading={loading} onEdit={openEdit} onUsages={showUsages} onValidate={validate} onLogin={startLogin} validatingId={validatingId} />
      <section className="account-section usage-section">
        <header><LinkSimple /><div><h3>使用关系</h3><span>删除前自动检查业务引用</span></div></header>
        {usageAccount ? <><h4>{usageAccount.displayName}</h4>{usages.length ? <ul>{usages.map((item) => <li key={`${item.consumerType}-${item.consumerId}`}><b>{item.consumerName || item.consumerType}</b><span>{item.consumerType} · #{item.consumerId}</span></li>)}</ul> : <p className="account-empty">当前没有业务模块引用该账号。</p>}<footer><button type="button" onClick={() => setUsageAccount(null)}>关闭</button></footer></> : <p className="account-empty">从账号卡片进入，可查看该账号被哪些业务模块使用。</p>}
      </section>
    </div>
    {editor ? <AccountEditor editor={editor} form={form} setForm={setForm} saving={saving} onSubmit={save} onClose={closeEditor} onPlatformChange={changePlatform} onDelete={prepareDelete} /> : null}
    {confirmDelete ? <div className="account-dialog-backdrop"><section className="account-confirm-dialog" role="alertdialog" aria-modal="true" aria-label={`确认删除 ${confirmDelete.displayName}`}><Trash /><h3>删除 {confirmDelete.displayName}？</h3><p>账号及其加密凭据将被删除，此操作不可撤销。</p><footer><button type="button" onClick={() => setConfirmDelete(null)}>取消</button><button type="button" className="account-danger" onClick={remove}>确认删除</button></footer></section></div> : null}
    {login ? <div className="account-dialog-backdrop"><section className="account-dialog account-login-dialog" role="dialog" aria-modal="true" aria-label={`${login.account.displayName} 扫码登录`}><header><div><span>{PLATFORM_LABELS[login.account.platform]}</span><h3>扫码连接 {login.account.displayName}</h3></div><button type="button" aria-label="关闭扫码登录" onClick={() => setLogin(null)}><X /></button></header>{login.qrImageDataUrl && login.status === "WAITING_SCAN" ? <img src={login.qrImageDataUrl} alt={`${PLATFORM_LABELS[login.account.platform]} 登录二维码`} /> : null}<p>{login.message || "正在检查登录状态…"}</p><footer><button type="button" onClick={() => setLogin(null)}>关闭</button></footer></section></div> : null}
    <UiToast message={error || notice} tone={error ? "error" : "success"} onDismiss={() => { setError(""); setNotice(""); }} />
  </section>;
}

function AccountSection({ title, icon, items, loading, onEdit, onUsages, onValidate, onLogin, validatingId }) {
  return <section className="account-section">
    <header>{icon}<div><h3>{title}</h3><span>{items.length} 个账号</span></div></header>
    {loading ? <p className="account-empty">正在同步账号状态…</p> : items.length ? <div className="account-card-list">{items.map((item) => {
      const disabled = item.enabled === false;
      const shownStatus = disabled ? "DISABLED" : item.connectionStatus;
      return <article className="account-card" key={item.id}>
        <header><div className="account-card-brand"><PlatformBrandIcon platform={item.platform} label={PLATFORM_LABELS[item.platform] || item.platform} /><div><span>{PLATFORM_LABELS[item.platform] || item.platform}</span><small>{item.adapterType}</small></div></div><span className={`account-status status-${String(shownStatus).toLowerCase()}`}>{STATUS_LABELS[shownStatus] || shownStatus}</span></header>
        <h4>{item.displayName}</h4><p>{item.accountHandle || "未填写账号 Handle"}</p>
        <dl><div><dt>凭据</dt><dd>{item.credentialHints?.filter(Boolean).join(" · ") || "尚未配置"}</dd></div><div><dt>最近验证</dt><dd>{item.lastValidatedAt ? new Date(item.lastValidatedAt).toLocaleString("zh-CN") : "从未验证"}</dd></div></dl>
        {item.lastErrorMessage && !disabled ? <small className="account-error">{item.lastErrorMessage}</small> : null}
        <div className="account-card-actions">
          <button type="button" onClick={() => onEdit(item)} aria-label={`编辑 ${item.displayName}`}><PencilSimple />编辑</button>
          {item.platform === "XIAOHONGSHU" || item.platform === "DOUYIN" ? <button type="button" onClick={() => onLogin(item)} disabled={disabled} aria-label={`扫码连接 ${item.displayName}`}>扫码连接</button> : null}
          <button type="button" onClick={() => onValidate(item)} disabled={disabled || validatingId === item.id} aria-label={`验证 ${item.displayName}`}>{validatingId === item.id ? "验证中…" : "验证连接"}</button>
          <button type="button" onClick={() => onUsages(item)} aria-label={`查看 ${item.displayName} 使用关系`}>使用关系</button>
        </div>
      </article>;
    })}</div> : <p className="account-empty">暂无匹配账号。</p>}
  </section>;
}

function AccountEditor({ editor, form, setForm, saving, onSubmit, onClose, onPlatformChange, onDelete }) {
  const editing = editor.mode === "edit";
  const title = editing ? `编辑 ${editor.account.displayName}` : "新增账号";
  const secretLabel = form.platform === "GEMINI" ? "API Key" : form.platform === "X" ? "Cookie / Token" : "浏览器会话";
  return <div className="account-dialog-backdrop"><form className="account-dialog account-editor-dialog" role="dialog" aria-modal="true" aria-label={title} onSubmit={onSubmit}>
    <header className="account-editor-header"><div className="account-editor-identity"><PlatformBrandIcon platform={form.platform} label={PLATFORM_LABELS[form.platform]} /><div><span>{editing ? "ACCOUNT SETTINGS" : "NEW ACCOUNT"}</span><h3>{title}</h3></div></div><button type="button" aria-label={`关闭${title}`} onClick={onClose}><X /></button></header>
    {!editing ? <fieldset className="account-platform-picker"><legend>选择平台</legend>{Object.entries(PLATFORM_LABELS).map(([value, label]) => <label key={value}><input type="radio" name="platform" value={value} aria-label={label} checked={form.platform === value} onChange={() => onPlatformChange(value)} /><PlatformBrandIcon platform={value} label={label} /><span>{label}</span></label>)}</fieldset> : null}
    <section className="account-form-group"><header><span>账号信息</span><small>用于识别和选择账号</small></header><div className="account-form-grid"><label>账号名称<input aria-label="账号名称" required maxLength="100" value={form.displayName} onChange={(event) => setForm({ ...form, displayName: event.target.value })} /></label><label>账号 Handle<input aria-label="账号 Handle" value={form.accountHandle} onChange={(event) => setForm({ ...form, accountHandle: event.target.value })} placeholder="可选" /></label>{editing ? <label className="account-enabled-field"><input type="checkbox" checked={form.enabled} onChange={(event) => setForm({ ...form, enabled: event.target.checked })} /><span>启用这个账号</span></label> : null}</div></section>
    <section className={`account-form-group account-credentials-group ${form.platform === "X" ? "has-secret-type" : ""}`}><header><span>登录凭据</span><small>{editing ? "留空则保留现有凭据" : "可以先保存，之后再补充"}</small></header>{form.platform === "X" ? <label>凭据类型<select aria-label="X 凭据类型" value={form.secretType} onChange={(event) => setForm({ ...form, secretType: event.target.value })}><option value="COOKIE">Cookie</option><option value="STORAGE_STATE">浏览器会话</option><option value="BEARER_TOKEN">Bearer Token</option></select></label> : null}<label>{secretLabel}<textarea aria-label={form.platform === "GEMINI" ? "API Key" : "登录凭据"} value={form.secret} onChange={(event) => setForm({ ...form, secret: event.target.value })} placeholder={form.platform === "GEMINI" ? "输入 Gemini API Key" : "粘贴凭据，或保存后使用扫码连接"} /></label><p><ShieldCheck />提交后立即加密，页面不会回显原值。</p></section>
    <footer>{editing ? <button type="button" className="account-delete-trigger" onClick={onDelete}><Trash />删除账号</button> : <span />}<div><button type="button" onClick={onClose}>取消</button><button type="submit" className="account-primary" disabled={saving}>{saving ? "保存中…" : editing ? "保存修改" : "保存账号"}</button></div></footer>
  </form></div>;
}

function secretHint(type, value) {
  if (type === "API_KEY" || type === "BEARER_TOKEN") return `••••${value.slice(-4)}`;
  return type === "COOKIE" ? "Cookie 会话" : "浏览器会话";
}
