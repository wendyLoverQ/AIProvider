import { useEffect, useMemo, useRef, useState } from "react";
import { ArrowLeft, FloppyDisk, Plus, Trash, Warning } from "@phosphor-icons/react";
import { buildPromptCategories, PROMPT_CATEGORIES } from "./promptComposer";
import UiSearchField from "./UiSearchField";
import { readJsonResponse } from "./apiResponse";
import "./PromptOptionManager.css";

const emptyDraft = () => ({ id: "", category: "Clothing", name: "", prompt: "", type: "positive", reverseId: "", sortOrder: 100, enabled: true, allowMultiple: true, persisted: false });
const MULTIPLE_CATEGORIES = new Set(["Character", "Appearance", "Special", "Clothing", "Artist", "Relationship", "Action", "Composition", "Eyes", "Hair", "Background", "Lighting", "Style", "Quality"]);
const PAGE_SIZE = 100;

async function request(path, options) {
  const response = await fetch(path, options); const payload = await readJsonResponse(response, "Prompt 词条服务响应异常");
  if (!response.ok || payload.code !== 200) throw new Error(payload.message || `请求失败 · ${response.status}`);
  return payload.data;
}

export default function PromptOptionManager({ onBack, initialCategory = "" }) {
  const [items, setItems] = useState([]);
  const [draft, setDraft] = useState(emptyDraft);
  const [query, setQuery] = useState("");
  const [category, setCategory] = useState(initialCategory);
  const [status, setStatus] = useState("all");
  const [page, setPage] = useState(1);
  const [total, setTotal] = useState(0);
  const [pages, setPages] = useState(0);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const latestLoad = useRef(0);
  const definitions = useMemo(() => buildPromptCategories(PROMPT_CATEGORIES.map((item) => ({ category: item.category, allowMultiple: MULTIPLE_CATEGORIES.has(item.category) }))), []);
  const categoryByKey = useMemo(() => Object.fromEntries(definitions.map((item) => [item.category, item])), [definitions]);

  const load = async (preferredId, requestedPage = page) => {
    const loadId = ++latestLoad.current;
    const params = new URLSearchParams({ page: String(requestedPage), pageSize: String(PAGE_SIZE), status });
    if (query.trim()) params.set("query", query.trim());
    if (category) params.set("category", category);
    const next = await request(`/api/prompt-options?${params}`);
    if (loadId !== latestLoad.current) return;
    setItems(next?.items || []); setTotal(next?.total || 0); setPages(next?.pages || 0);
    const selected = next?.items?.find((item) => item.id === preferredId);
    if (selected) setDraft({ ...selected, persisted: true });
  };
  useEffect(() => {
    const timer = window.setTimeout(() => load().catch((exception) => setError(exception.message)), query.trim() ? 300 : 0);
    return () => window.clearTimeout(timer);
  }, [page, query, category, status]);
  const set = (key, value) => setDraft((current) => ({ ...current, [key]: value }));
  const changeCategory = (value) => setDraft((current) => ({ ...current, category: value, allowMultiple: current.type === "positive" && Boolean(categoryByKey[value]?.multiple) }));
  const changeType = (value) => setDraft((current) => ({ ...current, type: value, allowMultiple: value === "positive" && Boolean(categoryByKey[current.category]?.multiple), reverseId: value === "negative" ? "" : current.reverseId }));
  const save = async () => {
    setBusy(true); setError("");
    const payload = { id: draft.id.trim(), category: draft.category, name: draft.name.trim(), prompt: (draft.prompt ?? draft.positivePrompt ?? "").trim(), type: draft.type, reverseId: draft.reverseId?.trim() || null, sortOrder: Number(draft.sortOrder), enabled: draft.enabled, allowMultiple: draft.allowMultiple };
    try {
      await request(draft.persisted ? `/api/prompt-options/${draft.id}` : "/api/prompt-options", { method: draft.persisted ? "PUT" : "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(payload) });
      await load(payload.id, page);
    } catch (exception) { setError(exception.message); } finally { setBusy(false); }
  };
  const remove = async () => {
    if (!draft.persisted || !window.confirm(`删除词条“${draft.name}”？`)) return;
    setBusy(true); setError("");
    try { await request(`/api/prompt-options/${draft.id}`, { method: "DELETE" }); setDraft(emptyDraft()); const nextPage = items.length === 1 ? Math.max(1, page - 1) : page; setPage(nextPage); await load(undefined, nextPage); }
    catch (exception) { setError(exception.message); } finally { setBusy(false); }
  };
  return <div className="prompt-option-manager">
    {error && <div className="prompt-option-error"><Warning />{error}<button onClick={() => setError("")}>×</button></div>}
    <header className="prompt-option-page-head">
      <div><button onClick={onBack}><ArrowLeft />返回 Prompt 方案</button><span>TERM LIBRARY</span><h2>Prompt 词条管理</h2><small>搜索、创建、编辑、停用或删除数据库词条</small></div>
      <button className="primary" onClick={() => setDraft(emptyDraft())}><Plus />新建词条</button>
    </header>
    <section className="prompt-option-workspace">
      <aside className="prompt-option-list">
        <div className="prompt-option-filters">
          <UiSearchField aria-label="搜索词条" value={query} onChange={(event) => { setQuery(event.target.value); setPage(1); }} placeholder="中文、英文或 ID" />
          <div><select aria-label="筛选分类" value={category} onChange={(event) => { setCategory(event.target.value); setPage(1); }}><option value="">全部分类</option>{definitions.map((item) => <option key={item.category} value={item.category}>{item.label}</option>)}</select><select aria-label="筛选状态" value={status} onChange={(event) => { setStatus(event.target.value); setPage(1); }}><option value="all">全部状态</option><option value="enabled">已启用</option><option value="disabled">已停用</option></select></div>
          <small>共 {total} 个词条 · 第 {pages ? page : 0} / {pages} 页</small>
        </div>
        <div className="prompt-option-list-scroll">{items.map((item) => <button key={item.id} className={`${draft.persisted && draft.id === item.id ? "active" : ""} ${item.enabled ? "" : "disabled"}`} onClick={() => setDraft({ ...item, persisted: true })}>
          <span><strong>{item.name}</strong><small> · {item.prompt || item.positivePrompt}</small></span><em>{item.type === "negative" ? "反向 · " : "正向 · "}{categoryByKey[item.category]?.label || item.category}</em>
        </button>)}{!items.length && <p>没有匹配词条</p>}</div>
        <nav className="prompt-option-pagination" aria-label="词条分页"><button disabled={page <= 1} onClick={() => setPage((current) => current - 1)}>上一页</button><span>{pages ? page : 0} / {pages}</span><button disabled={!pages || page >= pages} onClick={() => setPage((current) => current + 1)}>下一页</button></nav>
      </aside>
      <main className="prompt-option-editor">
        <header><div><span>{draft.persisted ? "编辑词条" : "新词条"}</span><h3>{draft.name || "未命名词条"}</h3></div><div><button onClick={save} disabled={busy}><FloppyDisk />保存</button><button className="danger" onClick={remove} disabled={busy || !draft.persisted}><Trash />删除</button></div></header>
        <div className="prompt-option-form">
          <label><span className="prompt-option-field-label">词条 ID <small>创建后不可修改</small></span><input aria-label="词条 ID" value={draft.id} readOnly={draft.persisted} maxLength="64" onChange={(event) => set("id", event.target.value.toLowerCase().replace(/[^a-z0-9_]/g, ""))} placeholder="小写字母、数字或下划线" /></label>
          <label><span className="prompt-option-field-label">分类 <small>决定组合器中的位置</small></span><select aria-label="词条分类" value={draft.category} onChange={(event) => changeCategory(event.target.value)}>{definitions.map((item) => <option key={item.category} value={item.category}>{item.label}</option>)}</select></label>
          <label><span className="prompt-option-field-label">中文名称</span><input aria-label="中文名称" value={draft.name} maxLength="100" onChange={(event) => set("name", event.target.value)} placeholder="黑丝袜 / 黑色连裤袜" /></label>
          <label><span className="prompt-option-field-label">排序</span><input aria-label="词条排序" type="number" min="0" max="100000" value={draft.sortOrder} onChange={(event) => set("sortOrder", event.target.value)} /></label>
          <label><span className="prompt-option-field-label">类型</span><select aria-label="词条类型" value={draft.type} onChange={(event) => changeType(event.target.value)}><option value="positive">正向</option><option value="negative">反向</option></select></label>
          <label><span className="prompt-option-field-label">关联反向词条 ID <small>正向词条可留空</small></span><input aria-label="关联反向词条 ID" value={draft.reverseId || ""} disabled={draft.type === "negative"} onChange={(event) => set("reverseId", event.target.value)} placeholder="neg_black_thighhighs" /></label>
          <label className="wide"><span className="prompt-option-field-label">Prompt 词</span><textarea aria-label="词条 Prompt" rows="4" maxLength="500" value={draft.prompt ?? draft.positivePrompt ?? ""} onChange={(event) => set("prompt", event.target.value)} placeholder="black pantyhose, sheer black tights" /></label>
          <div className="prompt-option-checks wide">
            <label className="check"><input aria-label="词条是否启用" type="checkbox" checked={draft.enabled} onChange={(event) => set("enabled", event.target.checked)} /><span><b>启用词条</b><small>停用后不再出现在组合器中</small></span></label>
            <label className="check"><input aria-label="词条允许多选" type="checkbox" checked={draft.allowMultiple} disabled /><span><b>{draft.allowMultiple ? "分类允许多选" : "分类为单选"}</b><small>由当前分类规则自动决定</small></span></label>
          </div>
        </div>
      </main>
    </section>
  </div>;
}
