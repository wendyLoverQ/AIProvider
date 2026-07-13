import { useEffect, useMemo, useState } from "react";
import { Copy, FloppyDisk, Plus, Star, Trash, Warning } from "@phosphor-icons/react";
import "./PromptManager.css";

const PROMPT_FIELDS = ["positivePrompt", "negativePrompt"];
const emptyParameters = () => ({ positivePrompt: "", negativePrompt: "" });
const emptyDraft = () => ({ id: null, title: "", outputFolder: "", notes: "", defaultPreset: false, parameters: emptyParameters() });

async function request(path, options) {
  const response = await fetch(path, options);
  const payload = await response.json();
  if (!response.ok || payload.code !== 200) throw new Error(payload.message || `请求失败 · ${response.status}`);
  return payload.data;
}

export default function PromptManager() {
  const [items, setItems] = useState([]);
  const [query, setQuery] = useState("");
  const [draft, setDraft] = useState(emptyDraft);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const filtered = useMemo(() => items.filter((item) => item.title.toLowerCase().includes(query.trim().toLowerCase())), [items, query]);

  const select = (item) => setDraft({ ...item, notes: item.notes || "", parameters: { ...emptyParameters(), ...(item.parameters || {}) } });
  const load = async (preferredId) => {
    const next = await request("/api/comfy-presets");
    setItems(next || []);
    const selected = next?.find((item) => String(item.id) === String(preferredId)) || next?.find((item) => item.defaultPreset) || next?.[0];
    if (selected) select(selected);
  };
  useEffect(() => { load().catch((e) => setError(e.message)); }, []);

  const setRoot = (key, value) => setDraft((current) => ({ ...current, [key]: value }));
  const setParameter = (key, value) => setDraft((current) => ({ ...current, parameters: { ...current.parameters, [key]: value } }));
  const payload = () => ({
    title: draft.title.trim(),
    outputFolder: draft.outputFolder,
    notes: draft.notes,
    parameters: Object.fromEntries(PROMPT_FIELDS.map((key) => [key, draft.parameters[key]]).filter(([, value]) => value !== "" && value !== null && value !== undefined)),
  });
  const save = async () => {
    if (!draft.title.trim()) return setError("请填写方案名称");
    setBusy(true); setError("");
    try {
      let preferredId = draft.id;
      if (draft.id) await request(`/api/comfy-presets/${draft.id}`, { method: "PUT", headers: { "Content-Type": "application/json" }, body: JSON.stringify(payload()) });
      else {
        const created = await request("/api/comfy-presets", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(payload()) });
        preferredId = created.id;
      }
      await load(preferredId);
    } catch (e) { setError(e.message); } finally { setBusy(false); }
  };
  const remove = async () => {
    if (!draft.id || !window.confirm(`删除 Prompt 方案“${draft.title}”？`)) return;
    setBusy(true);
    try { await request(`/api/comfy-presets/${draft.id}`, { method: "DELETE" }); setDraft(emptyDraft()); await load(); }
    catch (e) { setError(e.message); } finally { setBusy(false); }
  };
  const markDefault = async () => {
    if (!draft.id) return setError("请先保存方案");
    setBusy(true);
    try { await request(`/api/comfy-presets/${draft.id}/default`, { method: "POST" }); await load(draft.id); }
    catch (e) { setError(e.message); } finally { setBusy(false); }
  };
  const createNew = () => setDraft(emptyDraft());
  const duplicate = () => setDraft({ ...draft, id: null, title: `${draft.title || "未命名方案"} - 副本`, defaultPreset: false, parameters: { ...draft.parameters } });
  const p = draft.parameters;

  return <div className="prompt-manager">
    {error && <div className="prompt-manager-error"><Warning />{error}<button onClick={() => setError("")}>×</button></div>}
    <aside className="prompt-scheme-list">
      <header><div><span>PROMPT LIBRARY</span><h2>Prompt 方案</h2></div><button onClick={createNew} title="新建方案"><Plus /></button></header>
      <input className="prompt-search" value={query} onChange={(e) => setQuery(e.target.value)} placeholder="搜索方案名称…" />
      <div className="prompt-list-scroll">
        {filtered.map((item) => <button key={item.id} className={String(item.id) === String(draft.id) ? "active" : ""} onClick={() => select(item)}>
          <span>{item.title}</span>{item.defaultPreset && <Star weight="fill" />}<small>{item.parameters?.positivePrompt ? "已填写正向 Prompt" : "空 Prompt"}</small>
        </button>)}
        {!filtered.length && <p>没有匹配的 Prompt 方案</p>}
      </div>
    </aside>
    <section className="prompt-editor">
      <header><div><span>{draft.id ? `方案 #${draft.id}` : "新方案"}</span><h2>{draft.title || "未命名 Prompt 方案"}</h2></div><div className="prompt-editor-actions">
        <button onClick={createNew}><Plus />新建</button><button onClick={duplicate}><Copy />复制</button><button onClick={save} disabled={busy}><FloppyDisk />保存</button><button onClick={markDefault} disabled={busy || !draft.id} className={draft.defaultPreset ? "is-default" : ""}><Star weight={draft.defaultPreset ? "fill" : "regular"} />设为默认</button><button onClick={remove} disabled={busy || !draft.id} className="danger"><Trash />删除</button>
      </div></header>
      <div className="prompt-editor-form">
        <label className="wide">方案名称<input value={draft.title} maxLength="100" onChange={(e) => setRoot("title", e.target.value)} /></label>
        <label className="wide">正向 Prompt<textarea rows="7" value={p.positivePrompt ?? ""} onChange={(e) => setParameter("positivePrompt", e.target.value)} /></label>
        <label className="wide">反向 Prompt<textarea rows="5" value={p.negativePrompt ?? ""} onChange={(e) => setParameter("negativePrompt", e.target.value)} /></label>
        <label className="wide">备注<textarea rows="3" maxLength="1000" value={draft.notes} onChange={(e) => setRoot("notes", e.target.value)} /></label>
      </div>
    </section>
  </div>;
}
